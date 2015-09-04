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
package network.thunder.client.etc;

import java.util.ArrayList;
import java.util.List;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicHierarchy;
import org.bitcoinj.crypto.DeterministicKey;

public class KeyDerivation {

    /** Call to get the MasterKey for a new Channel
     *
     * @param number Query the Database to get the latest unused number
     * @return DeterministicKey for the new Channel */
    public static DeterministicKey getMasterKey(int number) {

        DeterministicKey hd = DeterministicKey.deserializeB58(SideConstants.KEY_B58, Constants.getNetwork());
        DeterministicHierarchy hi = new DeterministicHierarchy(hd);

        List<ChildNumber> childList = new ArrayList<ChildNumber>();
        ChildNumber childNumber = new ChildNumber(number, true);
        childList.add(childNumber);

        DeterministicKey key = hi.get(childList, true, true);
        return key;

    }

    /** Creates a List x Elements long
     *
     * @param x Length of the Chain
     * @return Chain of Childs, Hardened, all ascending from Child 0 */
    public static List<ChildNumber> getChildList(int x) {
        List<ChildNumber> childList = new ArrayList<ChildNumber>();
        ChildNumber childNumber = new ChildNumber(0, true);

        for (int i = 0; i < x; i++) {
            childList.add(childNumber);
        }
        return childList;
    }

    public static boolean compareDeterministicKeys(DeterministicKey key1, String key2) {
        DeterministicKey key22 = DeterministicKey.deserializeB58(key2, Constants.getNetwork());

        if (key1.getPublicKeyAsHex().equals(key22.getPublicKeyAsHex())) {
            if (Tools.byteToString(key1.getChainCode()).equals(Tools.byteToString(key22.getChainCode()))) {
                if (Tools.byteToString(key1.getPrivKeyBytes()).equals(Tools.byteToString(key22.getPrivKeyBytes()))) {
                    if (Tools.byteToString(key1.getSecretBytes()).equals(Tools.byteToString(key22.getSecretBytes()))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /** Get the Key for x days ahead.
     *
     * @param key
     * @param days
     * @return */
    public static DeterministicKey calculateKeyChain(DeterministicKey key, int days) {
        /*
         * TODO: This change makes it possible for near-infinite depth, however, doing so will lead to poor performance.
         *          Save the depth-masterkey into the database aswell, this will save its computation every time,
         *          computing it only when we 'climb' up one depth..
         */
        DeterministicKey keyTemp = key;
        ChildNumber childNumber = new ChildNumber(0, true);
        List<ChildNumber> childList = new ArrayList<ChildNumber>();
        childList.add(childNumber);

        for (int i = 0; i < days; ++i) {
            DeterministicHierarchy hi = new DeterministicHierarchy(keyTemp);
            keyTemp = hi.get(childList, true, true);
        }
        return keyTemp;
    }

    /** Get the Key for x days ahead.
     *
     * @param key
     * @param days
     * @return */
    public static DeterministicKey calculateKeyChain(String masterKey, int days) {
        DeterministicKey key = DeterministicKey.deserializeB58(masterKey, Constants.getNetwork());
        return calculateKeyChain(key, days);
    }

    public static ECKey bruteForceKey(String masterKey, String pubkey) {
        DeterministicKey hd = DeterministicKey.deserializeB58(masterKey, Constants.getNetwork());

        DeterministicHierarchy hi = new DeterministicHierarchy(hd);

        for (int j = 0; j < 1000; ++j) {
            List<ChildNumber> childList = getChildList(j);

            for (int i = 0; i < 1000; ++i) {

                ChildNumber childNumber = new ChildNumber(i, true);
                childList.set(j, childNumber);

                DeterministicKey key = hi.get(childList, true, true);
                String pubTemp = Tools.byteToString(key.getPubKey());
                if (pubTemp.equals(pubkey)) {
                    return key;
                }
            }
        }

        return null;
    }

}
