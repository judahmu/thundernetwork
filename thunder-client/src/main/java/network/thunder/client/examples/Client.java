/*
 *  ThunderNetwork - Server Client Architecture to send Off-Chain Bitcoin Payments
 *  Copyright (C) 2015 Mats Jerratsch <matsjj@gmail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package network.thunder.client.examples;

import java.sql.Connection;

import javax.sql.DataSource;

import org.bitcoinj.utils.BriefLogFormatter;

import network.thunder.client.api.ThunderContext;
import network.thunder.client.database.MySQLConnection;
import network.thunder.client.etc.Tools;
import network.thunder.client.wallet.KeyChain;

public class Client {

    /*
     * Create a new channel
     */
    KeyChain keychain;

    public static String channel1 = Tools.byteToString(Tools.stringToByte58("oVddFMVCgFRyQq7ouzHy9DW82C3sRgZZr8J7rQdDbD9G"));
    public static String channel2 = Tools.byteToString(Tools.stringToByte58("kBze7cTJN8i65Xfah56DF2BhAFyTnQV7vq1Hs1N591YU"));

    public static boolean createNewChannels = true;

    public static void main(String[] args) throws Exception {
        BriefLogFormatter.init();

        DataSource dataSource1 = MySQLConnection.getDataSource();
        Connection conn1 = dataSource1.getConnection();

        KeyChain keyChain = new KeyChain(conn1);
        keyChain.conn = conn1;
        keyChain.start();

        ThunderContext.init(keyChain.wallet, keyChain.peerGroup);

        if (createNewChannels) {
            ThunderContext.instance.openChannel(10000, 10000, 50);
            return;
        }
    }

}
