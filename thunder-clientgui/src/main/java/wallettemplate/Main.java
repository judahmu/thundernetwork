package wallettemplate;

import network.thunder.client.api.ThunderContext;

import com.google.common.util.concurrent.*;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.input.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.*;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.DeterministicSeed;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import wallettemplate.controls.NotificationBarPane;
import wallettemplate.utils.BitcoinUIModel;
import wallettemplate.utils.GuiUtils;
import wallettemplate.utils.TextFieldValidator;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;

import static wallettemplate.utils.GuiUtils.*;

public class Main extends Application {
    public static String APP_NAME = "ThunderWallet";

    public static NetworkParameters params = TestNet3Params.get();
    public static WalletAppKit bitcoin;
    public static Main instance;
    public static ThunderContext thunderContext;

    private StackPane uiStack;
    private Pane mainUI;
    public MainController controller;
    public NotificationBarPane notificationBar;
    public Stage mainWindow;

    public static int CLIENTID = 1;
    public static String REQUEST;




    @Override
    public void start(Stage mainWindow) throws Exception {
        try {
            realStart(mainWindow);
        } catch (Throwable e) {
            GuiUtils.crashAlert(e);
            throw e;
        }
    }

    private void realStart(Stage mainWindow) throws IOException {
        this.mainWindow = mainWindow;
        instance = this;
        // Show the crash dialog for any exceptions that we don't handle and that hit the main loop.
        GuiUtils.handleCrashesOnThisThread();

        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            // We could match the Mac Aqua style here, except that (a) Modena doesn't look that bad, and (b)
            // the date picker widget is kinda broken in AquaFx and I can't be bothered fixing it.
            // AquaFx.style();
        }

        // Load the GUI. The MainController class will be automagically created and wired up.
        URL location = getClass().getResource("main.fxml");
        FXMLLoader loader = new FXMLLoader(location);
        mainUI = loader.load();
        controller = loader.getController();
        // Configure the window with a StackPane so we can overlay things on top of the main UI, and a
        // NotificationBarPane so we can slide messages and progress bars in from the bottom. Note that
        // ordering of the construction and connection matters here, otherwise we get (harmless) CSS error
        // spew to the logs.
        notificationBar = new NotificationBarPane(mainUI);
        mainWindow.setTitle(APP_NAME);
        uiStack = new StackPane();
        Scene scene = new Scene(uiStack);
        TextFieldValidator.configureScene(scene);   // Add CSS that we need.
        scene.getStylesheets().add(getClass().getResource("wallet.css").toString());
        uiStack.getChildren().add(notificationBar);
        mainWindow.setScene(scene);

        // Make log output concise.
        BriefLogFormatter.init();
        // Tell bitcoinj to run event handlers on the JavaFX UI thread. This keeps things simple and means
        // we cannot forget to switch threads when adding event handlers. Unfortunately, the DownloadListener
        // we give to the app kit is currently an exception and runs on a library thread. It'll get fixed in
        // a future version.
        Threading.USER_THREAD = Platform::runLater;
        // Create the app kit. It won't do any heavyweight initialization until after we start it.
        setupWalletKit(null);

        if (bitcoin.isChainFileLocked()) {
            if(REQUEST != null) {
                PaymentProtocolClientSocket.sendPaymentRequest(REQUEST);
                Platform.exit();
                return;
            }
            informationalAlert("Already running", "This application is already running and cannot be started twice.");
            Platform.exit();
            return;
        }
        PaymentProtocolServerSocket.init();

        mainWindow.show();

        WalletSetPasswordController.estimateKeyDerivationTimeMsec();

        bitcoin.addListener(new Service.Listener() {
            @Override
            public void failed(Service.State from, Throwable failure) {
                GuiUtils.crashAlert(failure);
            }
        }, Platform::runLater);
        bitcoin.startAsync();

        ThunderContext.instance.setErrorListener(s -> Platform.runLater(() -> {
            NotificationBarPane.Item item = Main.instance.notificationBar.getItem();
            BitcoinUIModel.syncProgress.set(1.0);
            if (item != null) {
                item.cancel();
                item = null;
            }
            informationalAlert("Thunder Error", s);

        }));



        scene.getAccelerators().put(KeyCombination.valueOf("Shortcut+F"), () -> bitcoin.peerGroup().getDownloadPeer().close());
    }

    public void setupWalletKit(@Nullable DeterministicSeed seed) {
        // If seed is non-null it means we are restoring from backup.
//        bitcoin = new WalletAppKit(params, new File(System.getProperty("user.home")+"\\Documents"), APP_NAME + "-" + params.getPaymentProtocolId()+CLIENTID) {
        bitcoin = new WalletAppKit(params, new File("."), APP_NAME + "-" + params.getPaymentProtocolId()+CLIENTID) {
            @Override
            protected void onSetupCompleted() {
                // Don't make the user wait for confirmations for now, as the intention is they're sending it
                // their own money!
//                bitcoin.wallet().allowSpendingUnconfirmedTransactions();

                Platform.runLater(controller::onBitcoinSetup);

            }
        };
        // Now configure and start the appkit. This will take a second or two - we could show a temporary splash screen
        // or progress widget to keep the user engaged whilst we initialise, but we don't.
        if (params == RegTestParams.get()) {
            bitcoin.connectToLocalHost();   // You should run a regtest mode bitcoind locally.
        } else if (params == TestNet3Params.get()) {
//            bitcoin.connectToLocalHost();   // You should run a regtest mode bitcoind locally.
            // As an example!
            // bitcoin.useTor();
            // bitcoin.setDiscovery(new HttpDiscovery(params, URI.create("http://localhost:8080/peers"), ECKey.fromPublicOnly(BaseEncoding.base16().decode("02cba68cfd0679d10b186288b75a59f9132b1b3e222f6332717cb8c4eb2040f940".toUpperCase()))));
        }
        bitcoin.setDownloadListener(controller.progressBarUpdater())
               .setBlockingStartup(false)
               .setUserAgent(APP_NAME, "1.0");
        if (seed != null)
            bitcoin.restoreWalletFromSeed(seed);


    }

    private Node stopClickPane = new Pane();

    public class OverlayUI<T> {
        public Node ui;
        public T controller;

        public OverlayUI(Node ui, T controller) {
            this.ui = ui;
            this.controller = controller;
        }

        public void show() {
            checkGuiThread();
            if (currentOverlay == null) {
                uiStack.getChildren().add(stopClickPane);
                uiStack.getChildren().add(ui);
                blurOut(mainUI);
                //darken(mainUI);
                fadeIn(ui);
                zoomIn(ui);
            } else {
                // Do a quick transition between the current overlay and the next.
                // Bug here: we don't pay attention to changes in outsideClickDismisses.
                explodeOut(currentOverlay.ui);
                fadeOutAndRemove(uiStack, currentOverlay.ui);
                uiStack.getChildren().add(ui);
                ui.setOpacity(0.0);
                fadeIn(ui, 100);
                zoomIn(ui, 100);
            }
            currentOverlay = this;
        }

        public void outsideClickDismisses() {
            stopClickPane.setOnMouseClicked((ev) -> done());
        }

        public void done() {
            checkGuiThread();
            if (ui == null) return;  // In the middle of being dismissed and got an extra click.
            explodeOut(ui);
            fadeOutAndRemove(uiStack, ui, stopClickPane);
            blurIn(mainUI);
            //undark(mainUI);
            this.ui = null;
            this.controller = null;
            currentOverlay = null;
        }
    }

    @Nullable
    private OverlayUI currentOverlay;

    public <T> OverlayUI<T> overlayUI(Node node, T controller) {
        checkGuiThread();
        OverlayUI<T> pair = new OverlayUI<T>(node, controller);
        // Auto-magically set the overlayUI member, if it's there.
        try {
            controller.getClass().getField("overlayUI").set(controller, pair);
        } catch (IllegalAccessException | NoSuchFieldException ignored) {
        }
        pair.show();
        return pair;
    }

    /** Loads the FXML file with the given name, blurs out the main UI and puts this one on top. */
    public <T> OverlayUI<T> overlayUI(String name) {
        try {
            checkGuiThread();
            // Load the UI from disk.
            URL location = GuiUtils.getResource(name);
            FXMLLoader loader = new FXMLLoader(location);
            Pane ui = loader.load();
            T controller = loader.getController();
            OverlayUI<T> pair = new OverlayUI<T>(ui, controller);
            // Auto-magically set the overlayUI member, if it's there.
            try {
                if (controller != null)
                    controller.getClass().getField("overlayUI").set(controller, pair);
            } catch (IllegalAccessException | NoSuchFieldException ignored) {
                ignored.printStackTrace();
            }
            pair.show();
            return pair;
        } catch (IOException e) {
            throw new RuntimeException(e);  // Can't happen.
        }
    }

    /** Loads the FXML file with the given name, blurs out the main UI and puts this one on top. */
    public <T> OverlayUI<T> overlayUI(Pane ui, T controller) {
        checkGuiThread();
        OverlayUI<T> pair = new OverlayUI<T>(ui, controller);
        // Auto-magically set the overlayUI member, if it's there.
        try {
            if (controller != null)
                controller.getClass().getField("overlayUI").set(controller, pair);
        } catch (IllegalAccessException | NoSuchFieldException ignored) {
            ignored.printStackTrace();
        }
        pair.show();
        return pair;
    }



    @Override
    public void stop() throws Exception {
        bitcoin.stopAsync();
        bitcoin.awaitTerminated();
        // Forcibly terminate the JVM because Orchid likes to spew non-daemon threads everywhere.
        Runtime.getRuntime().exit(0);
    }

    public static void main(String[] args) {

        try {
            int id = Integer.parseInt(args[0]);
            CLIENTID = id;
        } catch (Exception e ) {
            try {
                REQUEST = args[0];
            } catch(Exception f) { }


        }


        launch(args);
    }
}
