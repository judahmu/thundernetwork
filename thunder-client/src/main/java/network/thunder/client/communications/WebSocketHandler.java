package network.thunder.client.communications;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Future;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import com.google.gson.Gson;

import network.thunder.client.api.ThunderContext;
import network.thunder.client.communications.objects.WebSocketAddListener;
import network.thunder.client.database.objects.Channel;
import network.thunder.client.etc.Constants;

/** Created by PC on 17.08.2015. */
public class WebSocketHandler {

	HashMap<Integer, EventSocket> sessionHashMap = new HashMap<>();

	public void connectToServer(Channel channel, ThunderContext context) {
		boolean connected = false;
		while (!connected) {
			WebSocketClient client = new WebSocketClient();
			URI uri = URI.create("ws://" + Constants.SERVER_URL + "/websocket");
			System.out.println(uri);
			try {
				client.start();
				EventSocket socket = new EventSocket();
				socket.channel = channel;
				socket.context = context;
				Future<Session> fut = client.connect(socket, uri);
				Session session = fut.get();

				Message message = new Message(new WebSocketAddListener(), Type.WEBSOCKET_OPEN,
						channel.getClientKeyOnClient());

				session.getRemote().sendString(new Gson().toJson(message));
				new Timer().scheduleAtFixedRate(new TimerTask() {
					@Override
					public void run() {
						try {
							session.getRemote().sendPing(null);
						} catch (Exception e) {
							e.printStackTrace();
							cancel();
						}
					}
				}, 0, 5000);

				connected = socket.isConnected();
				sessionHashMap.put(channel.getId(), socket);
				try {
					context.updateChannel();
				} catch (Exception e) {
					e.printStackTrace();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
			}
		}
	}

	public void closeConnection(int channelId) {
		EventSocket socket = sessionHashMap.get(channelId);
		if (socket != null) {
			socket.canceled = true;
			try {
				socket.session.disconnect();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public class EventSocket extends WebSocketAdapter {
		public Channel channel;
		public ThunderContext context;

		public boolean canceled = false;
		public Session session;

		@Override
		public void onWebSocketConnect(Session sess) {
			super.onWebSocketConnect(sess);
			this.session = sess;
			System.out.println("Socket Connected: " + sess);
		}

		@Override
		public void onWebSocketText(String message) {
			/** TODO: Do some more checking on the message we actually
			 * received. */
			super.onWebSocketText(message);
			try {
				if (!canceled)
					context.updateChannel();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onWebSocketClose(int statusCode, String reason) {
			super.onWebSocketClose(statusCode, reason);
			System.out.println("Socket Closed: [" + statusCode + "] " + reason);
			if (!canceled)
				connectToServer(channel, context);

		}

		@Override
		public void onWebSocketError(Throwable cause) {
			super.onWebSocketError(cause);
		}
	}
}
