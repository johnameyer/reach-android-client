/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.reach.contacts.client;

import android.accounts.Account;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

/**
 * Provides utility methods for communicating with the server.
 */
final public class NetworkUtilities {
    /**
     * POST parameter name for the client's last-known sync state
     */
    public static final String PARAM_SYNC_STATE = "syncstate";
    /**
     * Timeout (in ms) we specify for each http request
     */
    public static final int HTTP_REQUEST_TIMEOUT_MS = 30 * 1000;
    /**
     * The tag used to log to adb console.
     */
    private static final String TAG = "NetworkUtilities";
    private static final String uid = "589755674b247469a37d1ad2";

    /**
     * Download the avatar image from the server.
     *
     * @param avatarUrl the URL pointing to the avatar image
     * @return a byte array with the raw JPEG avatar image
     */
    public static byte[] downloadAvatar(final String avatarUrl) {
        // If there is no avatar, we're done
        if (TextUtils.isEmpty(avatarUrl)) {
            return null;
        }

        try {
            Log.i(TAG, "Downloading avatar: " + avatarUrl);
            // Request the avatar image from the server, and create a bitmap
            // object from the stream we get back.
            URL url = new URL(avatarUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();
            try {
                final BitmapFactory.Options options = new BitmapFactory.Options();
                final Bitmap avatar = BitmapFactory.decodeStream(connection.getInputStream(),
                        null, options);

                // Take the image we received from the server, whatever format it
                // happens to be in, and convert it to a JPEG image. Note: we're
                // not resizing the avatar - we assume that the image we get from
                // the server is a reasonable size...
                Log.i(TAG, "Converting avatar to JPEG");
                ByteArrayOutputStream convertStream = new ByteArrayOutputStream(
                        avatar.getWidth() * avatar.getHeight() * 4);
                avatar.compress(Bitmap.CompressFormat.JPEG, 95, convertStream);
                convertStream.flush();
                convertStream.close();
                // On pre-Honeycomb systems, it's important to call recycle on bitmaps
                avatar.recycle();
                return convertStream.toByteArray();
            } finally {
                connection.disconnect();
            }
        } catch (MalformedURLException muex) {
            // A bad URL - nothing we can really do about it here...
            Log.e(TAG, "Malformed avatar URL: " + avatarUrl);
        } catch (IOException ioex) {
            // If we're unable to download the avatar, it's a bummer but not the
            // end of the world. We'll try to get it next time we sync.
            Log.e(TAG, "Failed to download user avatar: " + avatarUrl);
        }
        return null;
    }

    public static List<RawContact> syncContacts(Account account, long lastSyncMarker) throws MalformedURLException, JSONException {
        try {
            return new AsyncTask<Void, Void, List<RawContact>>() {
                @Override
                protected List<RawContact> doInBackground(Void... voids) {
                    try {
                        URL url = new URL("http://104.196.36.172/users/" + uid + "/contacts");
                        Log.d(TAG, url.toString());
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setReadTimeout(15000);
                        conn.setConnectTimeout(15000);
                        conn.setDoInput(true);
                        String str;

                        int responseCode = conn.getResponseCode();
                        Log.d(TAG, "Response code: " + responseCode);

                        StringBuilder result = new StringBuilder();
                        if (responseCode == HttpsURLConnection.HTTP_OK) {
                            try {
                                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                                Log.d(TAG, "Reading...");
                                String line;
                                while ((line = in.readLine()) != null) {
                                    result.append(line);
                                    result.append('\n');
                                }
                            } finally {
                                conn.disconnect();
                            }
                        }
                        Log.d(TAG, result.toString());
                        str = result.toString();
                        List<RawContact> rcs = new ArrayList<>();
                        JSONArray ja = new JSONArray(str.replace("u'","'"));
                        for (int i = 0; i < ja.length(); i++) {
                            rcs.add(RawContact.valueOf(ja.getJSONObject(i)));
                        }
                        return rcs;
                    } catch (Exception e) {
                        Log.e(TAG, "HTTP Error", e);
                        return null;
                    }
                }
            }.execute().get();
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
        return null;
    }

    public static String authenticate(String mUsername, String mPassword) {
        return "AuthorizationGranted";
    }
}