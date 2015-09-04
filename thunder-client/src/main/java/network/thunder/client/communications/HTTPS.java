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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

import com.google.gson.Gson;

import network.thunder.client.etc.Constants;

public class HTTPS {

	public HttpURLConnection con;

	HttpClient httpClient;
	public HttpResponse httpResponse;

	HttpPost httpPost;
	HttpGet httpGet;
	List<NameValuePair> nvps;

	boolean error = false;

	List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();

	public boolean connect(String URL) {
		try {

			RequestConfig.custom().setConnectTimeout(10 * 1000).build();
			httpClient = HttpClients.createDefault();

			httpGet = new HttpGet(URL);
			httpResponse = httpClient.execute(httpGet);

			return true;

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean connectPOST(String URL) {
		try {

			RequestConfig.custom().setConnectTimeout(10 * 1000).build();
			httpClient = HttpClients.createDefault();
			httpPost = new HttpPost(URL);
			nvps = new ArrayList<NameValuePair>();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public void addPOSTParameter(String parameter, String value) {
		nvps.add(new BasicNameValuePair(parameter, value));
	}

	public void submitPOST() throws ClientProtocolException, IOException {
		httpPost.setEntity(new UrlEncodedFormEntity(nvps, "UTF-8"));
		httpResponse = httpClient.execute(httpPost);
	}

	public static String postToApi(Object data) throws ClientProtocolException, IOException {
		HTTPS connectionOne = new HTTPS();
		connectionOne.connectPOST("http://" + Constants.SERVER_URL + "/api/");
		String d = new Gson().toJson(data);
		connectionOne.addPOSTParameter("data", d);
		connectionOne.submitPOST();
		return connectionOne.getContent();
	}

	public String getContent() throws UnsupportedOperationException, IOException {
		if (httpResponse != null && !error) {

			HttpEntity entity = httpResponse.getEntity();

			BufferedReader br = new BufferedReader(new InputStreamReader(entity.getContent()));

			String input;
			String ausgabe = "";

			while ((input = br.readLine()) != null) {
				ausgabe += input + "\n";
			}
			br.close();

			return ausgabe;
		}
		return null;

	}

	@SuppressWarnings("unused")
	private String getQuery(List<NameValuePair> params) throws UnsupportedEncodingException {
		StringBuilder result = new StringBuilder();
		boolean first = true;

		for (NameValuePair pair : params) {
			if (first) {
				first = false;
			} else {
				result.append("&");
			}

			result.append(URLEncoder.encode(pair.getName(), "UTF-8"));
			result.append("=");
			result.append(URLEncoder.encode(pair.getValue(), "UTF-8"));
		}

		return result.toString();
	}

}
