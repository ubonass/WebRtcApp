/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.ubonass.utils;

import android.annotation.SuppressLint;
import android.util.Log;
import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.ubonass.App;
import org.ubonass.AppManager;
import org.webrtc.PeerConnection;

import javax.annotation.Nullable;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Asynchronous http requests implementation.
 */
public class AsyncHttpsConnection {

    private static  String TAG = "AsyncHttpsURLConnectiond";
    private static final String HTTP_ORIGIN = "https://59.110.212.181:4443";
    private static final String AUTH_TOKEN = "Basic T1BFTlZJRFVBUFA6TVlfU0VDUkVU";
    @Nullable
    private final AsyncHttpEvents events;
    @Nullable
    private OkHttpClient client;
    @Nullable
    private String roomUrl;
    /**
     * Http requests callbacks.
     */
    public interface AsyncHttpEvents {
        void onHttpsError(String errorMessage,int requestId);

        void onHttpsComplete(String response,int requestId);
    }

    public AsyncHttpsConnection(AsyncHttpEvents events,
                                String roomUrl) {
        this.roomUrl = HTTP_ORIGIN;
        this.events = events;
        client = new OkHttpClient()
                .newBuilder().connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .sslSocketFactory(AppManager.getSslSocketFactory())
                .hostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                }).build();
    }

    public void send(Request request,int requestId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                sendHttpMessage(request,requestId);
            }
        }).start();
    }

    private void sendHttpMessage(Request request, int requestId) {
        try {
            Response response = client.newCall(request).execute();
            String responseString = response.body().string();
            Log.i(TAG,"response:" + responseString);
            events.onHttpsComplete(responseString,requestId);
        } catch (SocketTimeoutException e) {
            events.onHttpsError("HTTP " + " to " + roomUrl + " timeout",requestId);
        } catch (IOException e) {
            events.onHttpsError("HTTP " + " to " + roomUrl + " error: " + e.getMessage(),requestId);
        }
    }

    // Return the contents of an InputStream as a String.
    private static String drainStream(InputStream in) {
        Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}
