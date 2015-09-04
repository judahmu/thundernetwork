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
package network.thunder.client.communications;

import java.security.SignatureException;
import java.sql.Connection;

import org.bitcoinj.core.ECKey;

import com.google.gson.Gson;

import network.thunder.client.database.MySQLConnection;
import network.thunder.client.etc.Constants;
import network.thunder.client.etc.KeyDerivation;
import network.thunder.client.etc.Tools;

/** The Class Message. */
public class Message {

	/** The signature. */
	public String signature;

	/** The pubkey. */
	public String pubkey;

	/** The success. */
	public boolean success;

	/** The data. */
	public String data;

	/** The type. */
	public int type;

	/** The timestamp. */
	public int timestamp;

	/** Instantiates a new message. */
	public Message() {
	}

	/** Instantiates a new message.
	 *
	 * @param o the o
	 * @param type the type
	 * @param key the key */
	public Message(Object o, int type, ECKey key) {
		this.fill(o);
		this.success = true;
		this.type = type;
		this.sign(key);
	}

	/** Instantiates a new message.
	 * @param o the o
	 * @param type the type
	 * @param key the key
	 * @param timestamp the timestamp */
	public Message(Object o, int type, ECKey key, int timestamp) {
		this.fill(o);
		this.success = true;
		this.type = type;
		this.timestamp = timestamp;
		pubkey = Tools.byteToString(key.getPubKey());
		signature = key.signMessage(this.getSignatureMessage());
	}

	/** Instantiates a new message.
	 * @param response the response
	 * @param conn the conn
	 * @throws Exception the exception */
	public Message(String response, Connection conn) throws Exception {
		Message message = new Gson().fromJson(response, Message.class);
		this.signature = message.signature;
		this.pubkey = message.pubkey;
		this.success = message.success;
		this.data = message.data;
		this.type = message.type;
		this.timestamp = message.timestamp;
		prepare(conn);
	}

	/** Fill.
	 * @param o the o */
	public void fill(Object o) {
		if (o != null)
			data = new Gson().toJson(o);
	}

	/** Gets the signature message.
	 * @return the signature message */
	private String getSignatureMessage() {
		return type + pubkey + success + timestamp + data;
	}

	/** Sign.
	 * @param privateKey the private key */
	public void sign(byte[] privateKey) {
		timestamp = Tools.currentTime();
		ECKey key = ECKey.fromPrivate(privateKey);
		pubkey = Tools.byteToString(key.getPubKey());
		signature = key.signMessage(this.getSignatureMessage());
	}

	/** Sign.
	 * @param privateKey the private key */
	public void sign(ECKey privateKey) {
		timestamp = Tools.currentTime();
		pubkey = Tools.byteToString(privateKey.getPubKey());
		signature = privateKey.signMessage(this.getSignatureMessage());
	}

	/** Sign. */
	public void sign() {
		ECKey privateKey = KeyDerivation.getMasterKey(0);
		timestamp = Tools.currentTime();
		pubkey = Tools.byteToString(privateKey.getPubKey());
		signature = privateKey.signMessage(this.getSignatureMessage());
	}

	/** Validate.
	 * @return true, if successful
	 * @throws Exception the exception */
	public boolean validate() throws Exception {

		if (Math.abs(timestamp - Tools.currentTime()) > Constants.getTimeFrameForValidation()) {
			throw new Exception(
					"Timestamp does not match! Received: " + timestamp + " Current: " + Tools.currentTime());
		}

		ECKey key = ECKey.fromPublicOnly(Tools.stringToByte(pubkey));
		try {
			key.verifyMessage(this.getSignatureMessage(), signature);

		} catch (SignatureException e) {
			e.printStackTrace();
			throw new Exception("Signature does not match..");
		}
		return true;

	}

	/** Call this method right after receiving it.
	 * @param conn the conn
	 * @throws Exception the exception */
	public void prepare(Connection conn) throws Exception {
		if (!this.validate())
			throw new Exception("Validation failed..");
		if (!this.success)
			throw new Exception(this.data);
		if (conn != null)
			MySQLConnection.saveMessage(conn, this);
	}

	public String getDataString() {
		this.sign();
		return new Gson().toJson(this);
	}

	@Override
	public String toString() {
		return "Message\n\tsignature=" + signature + "\n\tpubkey=" + pubkey + "\n\tsuccess=" + success + "\n\tdata="
				+ data + "\n\ttype=" + type + "\n\ttimestamp=" + timestamp;
	}

}
