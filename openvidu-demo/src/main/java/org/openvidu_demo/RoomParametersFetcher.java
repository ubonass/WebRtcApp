/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.openvidu_demo;

import android.util.Log;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtcpeer.AppRTCClient;
import org.webrtcpeer.AppRTCClient.SignalingParameters;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.webrtcpeer.util.AppRTCUtils.jsonToValue;

/**
 * AsyncTask that converts an AppRTC room URL into the set of signaling
 * parameters to use with that room.
 */
public class RoomParametersFetcher {
    private static final String AUTH_TOKEN = "Basic T1BFTlZJRFVBUFA6TVlfU0VDUkVU";
    public static final String TURN_ADDRESS = "turn:59.110.212.181:3478?transport=udp";
    public static final String TURN_USERNAME = "ubonass";
    public static final String TURN_PASSWORD = "openvidu";

    private static final String TAG = "RoomRTCClient";
    private static final int TURN_HTTP_TIMEOUT_MS = 5000;
    private final RoomParametersFetcherEvents events;
    private final String roomUrl;
    private final String urlParameters;
    private final String roomMessage;
    @Nullable
    private OkHttpClient client;
    private boolean initiator = true;

    /**
     * Room parameters fetcher callbacks.
     */
    public interface RoomParametersFetcherEvents {
        /**
         * Callback fired once the room's signaling parameters
         * SignalingParameters are extracted.
         */
        void onSignalingParametersReady(final SignalingParameters params);

        /**
         * Callback for room parameters extraction error.
         */
        void onSignalingParametersError(final String description);

        /**
         * @param response
         */
        //void onGetTokenSuccess(String response);

        //void onCreateSessionSuccess(String response);
    }

    private String getConnectionUrl() {
        String wurl = roomUrl + "/" + "openvidu";
        if (wurl.startsWith("https://")) {
            wurl = wurl.replace("https://", "wss://");
        } else if (wurl.startsWith("http://")) {
            wurl = wurl.replace("http://", "wss://");
        }
        Log.i(TAG, "wssurl:" + wurl);
        return wurl;
    }

    public boolean checkSession(String sessionName) {
        try {
            Request request = new Request.Builder()
                    .url(roomUrl + "/api/sessions/" + sessionName)
                    .addHeader("Authorization", AUTH_TOKEN)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .get()
                    .build();
            Response response = client.newCall(request).execute();
            //Log.i(TAG,"response:" + response.code());
            //Log.i(TAG,"response:" + response.message());
            if (response.code() != 200) {
                return false;
            } else {//房间加入者
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean getSessions(String sessionName) {
        Log.i(TAG, "roomUrl:" + roomUrl);
        try {
            //jsonObject.put("sessionId", sessionName);
            /*RequestBody body =
                    RequestBody.create(
                            MediaType.parse("application/json; charset=utf-8"),
                            jsonObject.toString());*/
            Request request = new Request.Builder()
                    .url(roomUrl + "/api/sessions")
                    .header("Authorization", AUTH_TOKEN)
                    //.addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .get()
                    .build();
            Response response = client.newCall(request).execute();
            String responseString = response.body().string();
            Log.i(TAG, "responseString:" + responseString);
            JSONObject jsonObject = new JSONObject(responseString);
            if (jsonObject
                    .getString("numberOfElements").equals("0"))
                return false;
            Log.i(TAG, "responseString:" + responseString);
            return true;
            //events.onCreateSessionSuccess(responseString);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void createSession(String sessionName) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("customSessionId", sessionName);
            RequestBody body =
                    RequestBody.create(
                            MediaType.parse("application/json; charset=utf-8"),
                            jsonObject.toString());
            Request request = new Request.Builder()
                    .url(roomUrl + "/api/sessions")
                    .addHeader("Authorization", AUTH_TOKEN)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();
            Response response = null;
            response = client.newCall(request).execute();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //return null;
    }

    public void createSessionAndGetToken(String sessionName) {
        Log.i(TAG, "roomUrl:" + roomUrl);
        //Log.i(TAG,"session :" + checkSession(sessionName));
        if (!checkSession(sessionName))
            createSession(sessionName);
        getToken(sessionName);
    }

    public void getToken(String sessionId) {
        Log.i(TAG, "roomUrl:" + roomUrl);
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("session", sessionId);
            jsonObject.put("role", "MODERATOR");
            jsonObject.put("data", "Jeffrey");
            Log.i(TAG, "request:" + jsonObject.toString());
            RequestBody body =
                    RequestBody.create(
                            MediaType.parse("application/json; charset=utf-8"),
                            jsonObject.toString());
            Request request = new Request.Builder()
                    .url(roomUrl + "/api/tokens")
                    .addHeader("Authorization", AUTH_TOKEN)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();
            Response response = null;
            response = client.newCall(request).execute();
            String responseString = response.body().string();
            Log.i(TAG,"responseString:" + responseString);
            jsonObject = new JSONObject(responseString);
            String wssUrl = jsonObject.getString("wssUrl");
            String token = jsonObject.getString("token");
            String session = jsonObject.getString("session");
            List<PeerConnection.IceServer> iceServers =
                    iceServersFromPCConfigJSON(jsonObject.get("pcOptions").toString());

            /*List<PeerConnection.IceServer> iceServers = new ArrayList<>();
            iceServers.add(new PeerConnection.IceServer(
                    TURN_ADDRESS, TURN_USERNAME, TURN_PASSWORD));*/
            //iceServers.add(new PeerConnection.IceServer(
            //        "turn:54.169.146.98:3478", "kurento", "kurento"));

            /*iceServers.add(new PeerConnection.IceServer("stun:stun.ekiga.net"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.ideasip.com"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.schlund.de"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.stunprotocol.org:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.voiparound.com"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.voipbuster.com"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.voipstunt.com"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.voxgratia.org"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.services.mozilla.com"));*/

            //IceCandidate iceCandidate = new IceCandidate();
            //iceServers.add(new PeerConnection.IceServer("stun:stun.59.110.212.181:3478"));
           /* iceServers.add(new PeerConnection.IceServer("stun:121.42.142.56:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.xten.com:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.voipbuster.com:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.voxgratia.org:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.sipgate.net:10000"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.ekiga.net:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.ideasip.com:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.schlund.de:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.voiparound.com:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.voipbuster.com:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.voipstunt.com:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:numb.viagenie.ca:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.counterpath.com:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.1und1.de:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.gmx.net:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.bcs2005.net:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.callwithus.com:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.counterpath.net:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.internetcalls.com:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.voip.aebc.com:3478"));*/
            SignalingParameters params = new SignalingParameters(
                    iceServers, true, session, wssUrl,
                    token, null, null);
            events.onSignalingParametersReady(params);//sendOffdp
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public RoomParametersFetcher(OkHttpClient client,
                                 AppRTCClient.RoomConnectionParameters roomConnectionParameters,
                                 String roomMessage,
                                 final RoomParametersFetcherEvents events) {
        this.roomUrl = roomConnectionParameters.roomUrl;
        this.urlParameters = roomConnectionParameters.urlParameters;
        this.roomMessage = roomMessage;
        this.events = events;
        this.client = client;
    }

    // Return the list of ICE servers described by a WebRTCPeerConnection
    // configuration string.
    private List<PeerConnection.IceServer> iceServersFromPCConfigJSON(String pcConfig)
            throws JSONException {
        JSONObject json = new JSONObject(pcConfig);
        JSONArray servers = json.getJSONArray("iceServers");
        List<PeerConnection.IceServer> ret = new ArrayList<>();
        for (int i = 0; i < servers.length(); ++i) {
            JSONObject server = servers.getJSONObject(i);
            String url = server.getString("url");
            String username = server.getString("username");
            String credential = server.getString("credential");
            //String credential = server.has("credential") ? server.getString("credential") : "";
            PeerConnection.IceServer turnServer =
                    PeerConnection.IceServer.builder(url)
                            .setUsername(username)
                            .setPassword(credential)
                            .createIceServer();
            ret.add(turnServer);
        }
        return ret;
    }


    /*iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
    iceServers.add(new PeerConnection.IceServer("stun:stun.xten.com:3478"));
    iceServers.add(new PeerConnection.IceServer("stun:stun.voipbuster.com:3478"));
    iceServers.add(new PeerConnection.IceServer("stun:stun.voxgratia.org:3478"));
    iceServers.add(new PeerConnection.IceServer("stun:stun.sipgate.net:10000"));
    iceServers.add(new PeerConnection.IceServer("stun:stun.ekiga.net:3478"));
    iceServers.add(new PeerConnection.IceServer("stun:stun.ideasip.com:3478"));
    iceServers.add(new PeerConnection.IceServer("stun:stun.schlund.de:3478"));
    iceServers.add(new PeerConnection.IceServer("stun:stun.voiparound.com:3478"));
    iceServers.add(new PeerConnection.IceServer("stun:stun.voipbuster.com:3478"));
    iceServers.add(new PeerConnection.IceServer("stun:stun.voipstunt.com:3478"));
    iceServers.add(new PeerConnection.IceServer("stun:numb.viagenie.ca:3478"));
    iceServers.add(new PeerConnection.IceServer("stun:stun.counterpath.com:3478"));
    iceServers.add(new PeerConnection.IceServer("stun:stun.1und1.de:3478"));
    iceServers.add(new PeerConnection.IceServer("stun:stun.gmx.net:3478"));
    iceServers.add(new PeerConnection.IceServer("stun:stun.bcs2005.net:3478"));
    iceServers.add(new PeerConnection.IceServer("stun:stun.callwithus.com:3478"));
    iceServers.add(new PeerConnection.IceServer("stun:stun.counterpath.net:3478"));
    iceServers.add(new PeerConnection.IceServer("stun:stun.internetcalls.com:3478"));
    iceServers.add(new PeerConnection.IceServer("stun:stun.voip.aebc.com:3478"));*/
}
