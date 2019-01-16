/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.ubonass;


import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.ubonass.utils.AsyncHttpsConnection;
import org.webrtc.PeerConnection;
import org.webrtcpeer.AppRTCClient.SignalingParameters;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * AsyncTask that converts an AppRTC room URL into the set of signaling
 * parameters to use with that room.
 */
public class RoomParametersFetcher implements AsyncHttpsConnection.AsyncHttpEvents {
    private static final String HTTP_ORIGIN = "https://59.110.212.181:4443";
    private static final String AUTH_TOKEN = "Basic T1BFTlZJRFVBUFA6TVlfU0VDUkVU";

    private static final String TAG = "RoomRTCClient";
    @Nullable
    private final String roomUrl;
    @Nullable
    private AsyncHttpsConnection httpsConnection;

    private int REQUEST_GET_PUBLIC_URL = 0x0001;
    private int REQUEST_CREATE_SESSION = 0x0002;
    private int REQUEST_GET_SESSION = 0x0003;
    private int REQUEST_GET_ROOM_TOKEN = 0x0004;


    public RoomParametersFetcher() {
        this.roomUrl = HTTP_ORIGIN;
        httpsConnection =
                new AsyncHttpsConnection(this,roomUrl);
    }

    public void makeCreateSessionRequest(String sessionName) {
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
            httpsConnection.send(request,REQUEST_CREATE_SESSION);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void makeCreateSessionRequest() {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("customSessionId", "");
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
            httpsConnection.send(request,REQUEST_CREATE_SESSION);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void makeGetTokensRequest(String sessionId) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("session", sessionId);
            jsonObject.put("role", "MODERATOR");
            jsonObject.put("data", "Jeffrey");
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
            httpsConnection.send(request,REQUEST_GET_ROOM_TOKEN);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onHttpsError(String errorMessage,
                             int requestId) {
        //if (requestId)
    }

    @Override
    public void onHttpsComplete(String response,int requestId) {
        JSONObject jsonObject = null;
        try {
            if (requestId == REQUEST_CREATE_SESSION) {
                jsonObject = new JSONObject(response);
                String sessionId = jsonObject.getString("id");
                //makeGetTokensRequest(sessionId);
            } else if (requestId == REQUEST_GET_ROOM_TOKEN) {
                jsonObject = new JSONObject(response);
                String wssUrl = jsonObject.getString("wssUrl");
                String token = jsonObject.getString("token");
                String session = jsonObject.getString("session");
                List<PeerConnection.IceServer> iceServers =
                        iceServersFromPCConfigJSON(jsonObject.get("pcOptions")
                                .toString());
                SignalingParameters params = new SignalingParameters(
                        iceServers, true, session, wssUrl,
                        token, null, null);
                //events.onSignalingParametersReady(params);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

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

}
