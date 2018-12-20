/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtcapp;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import org.webrtcpeer.AppRTCClient;
import org.webrtcpeer.WebSocketChannelClient;
import org.webrtcpeer.WebSocketChannelClient.WebSocketConnectionState;

import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class WebSocketRTCClient
        implements AppRTCClient,
        WebSocketChannelClient.WebSocketChannelEvents {
    public static final String TURN_ADDRESS = "turn:120.78.176.218:3478?transport=udp";
    public static final String TURN_USERNAME = "ubonass";
    public static final String TURN_PASSWORD = "openvidu";
    private static final String TAG = "WebSocketRTCClient";

    private enum ConnectionState {NEW, CONNECTED, CLOSED, ERROR}

    private enum MessageType {MESSAGE, LEAVE}

    private final Handler handler;
    private boolean initiator;
    private SignalingEvents events;
    private WebSocketChannelClient wsClient;
    private ConnectionState roomState;
    private RoomConnectionParameters connectionParameters;
    private SSLSocketFactory sslSocketFactory;
    private String messageUrl;
    private String leaveUrl;

    public WebSocketRTCClient(SignalingEvents events,
                              SSLSocketFactory sslSocketFactory) {
        this.events = events;
        this.sslSocketFactory = sslSocketFactory;
        this.roomState = ConnectionState.CLOSED;
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        this.handler = new Handler(handlerThread.getLooper());
    }

    // --------------------------------------------------------------------
    // AppRTCClient interface implementation.
    // Asynchronously connect to an AppRTC room URL using supplied connection
    // parameters, retrieves room parameters and connect to WebSocket server.
    @Override
    public void connectToRoom(RoomConnectionParameters connectionParameters) {
        this.connectionParameters = connectionParameters;
        handler.post(new Runnable() {
            @Override
            public void run() {
                connectToRoomInternal();
            }
        });
    }

    @Override
    public void disconnectFromRoom() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                disconnectFromRoomInternal();
                handler.getLooper().quit();
            }
        });
    }

    // Helper functions to get connection, post message and leave message URLs
    private String getConnectionUrl(RoomConnectionParameters
                                            connectionParameters) {
        String wssurl = connectionParameters.roomUrl + "/" + connectionParameters.roomId;
        if (wssurl.startsWith("https://")) {
            wssurl = wssurl.replace("https://", "wss://");
        } else if (wssurl.startsWith("http://")) {
            wssurl = wssurl.replace("http://", "wss://");
        }
        Log.i(TAG,"wssurl:" + wssurl);
        return wssurl;
    }

    /**
     * 1.创建session
     * 2.获取token
     * 3.开始连接
     */
    // Connects to room - function runs on a local looper thread.
    private void connectToRoomInternal() {
        String connectionUrl = getConnectionUrl(connectionParameters);
        Log.d(TAG, "Connect to room: " + connectionUrl);
        roomState = ConnectionState.NEW;
        try {
            wsClient =
                    new WebSocketChannelClient(new URI(connectionUrl),
                            handler, this);
            if (sslSocketFactory != null)
                wsClient.setSSLSocketFactory(sslSocketFactory);
            wsClient.startConnectWebsocket();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    // Disconnect from room and send bye messages - runs on a local looper thread.
    private void disconnectFromRoomInternal() {
        Log.d(TAG, "Disconnect. Room state: " + roomState);
        if (roomState == ConnectionState.CONNECTED) {
            Log.d(TAG, "Closing room.");
            //sendPostMessage(MessageType.LEAVE, leaveUrl, null);
        }
        roomState = ConnectionState.CLOSED;
        if (wsClient != null) {
            wsClient.disconnect(true);
        }
    }

    public void sendMessage(JSONObject message) {
        if (null == wsClient || null == message)
            throw new IllegalStateException("webSocket");
        wsClient.send(message.toString());
    }

    // Send local offer SDP to the other participant.
    @Override
    public void sendOfferSdp(final SessionDescription sdp) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending offer SDP in non connected state.");
                    return;
                }
                JSONObject jsonObject = new JSONObject();
                jsonPut(jsonObject,"id","start");
                jsonPut(jsonObject,"sdpOffer",sdp.description);
                sendMessage(jsonObject);
                if (connectionParameters.loopback) {
                    // In loopback mode rename this offer to answer and route it back.
                    SessionDescription sdpAnswer =
                            new SessionDescription(
                                    SessionDescription.Type.ANSWER,
                                    sdp.description);
                    events.onRemoteDescription(sdpAnswer);
                }
            }
        });
    }

    // Send local answer SDP to the other participant.
    @Override
    public void sendAnswerSdp(final SessionDescription sdp) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (connectionParameters.
                        loopback) {
                    Log.e(TAG,
                            "Sending answer in loopback mode.");
                    return;
                }
                JSONObject json = new JSONObject();
                //jsonPut(json, "id", sdp.description);
                jsonPut(json, "sdp", sdp.description);
                jsonPut(json, "type", "answer");
                sendMessage(json);
            }
        });
    }

    // Send Ice candidate to the other participant.
    @Override
    public void sendLocalIceCandidate(final IceCandidate candidate) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject jsonObject = toJsonCandidate(candidate);
                jsonPut(jsonObject, "id", "onIceCandidate");
                if (initiator) {
                    // Call initiator sends ice candidates to GAE server.
                    if (roomState !=
                            ConnectionState.CONNECTED) {
                        reportError(
                                "Sending ICE candidate in non connected state.");
                        return;
                    }
                    //sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
                    if (connectionParameters.loopback) {
                        events.onRemoteIceCandidate(candidate);
                    }
                } else {
                    // Call receiver sends ice candidates to websocket server.
                    sendMessage(jsonObject);
                }
            }
        });
    }

    // Send removed Ice candidates to the other participant.
    @Override
    public void sendLocalIceCandidateRemovals(final IceCandidate[] candidates) {
        handler.post(new Runnable() {
            @Override
            public void run() {

            }
        });
    }

    @Override
    public void onWebSocketOpen(ServerHandshake
                                            handshakedata) {
        if (roomState == ConnectionState.NEW) {
            roomState = ConnectionState.CONNECTED;
            List<PeerConnection.IceServer> iceServers = new ArrayList<>();
            SignalingParameters params = new SignalingParameters(
                    iceServers,
                    true,//是否是发起者
                    null,
                    getConnectionUrl(connectionParameters),
                    getConnectionUrl(connectionParameters),
                    null,
                    null);
            initiator = params.initiator;
            iceServers.add(new PeerConnection.IceServer(
                    TURN_ADDRESS,
                    TURN_USERNAME,
                    TURN_PASSWORD));
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
            iceServers.add(new PeerConnection.IceServer("stun:stun.voip.aebc.com:3478"));
            events.onConnectedToRoom(params);
        }
    }

    // --------------------------------------------------------------------
    // WebSocketChannelEvents interface implementation.
    // All events are called by WebSocketChannelClient on a local looper thread
    // (passed to WebSocket client constructor).
    @Override
    public void onWebSocketMessage(final String msg) {
        if (wsClient.getState() !=
                WebSocketConnectionState.CONNECTED) {
            Log.e(TAG,
                    "Got WebSocket message in non registered state.");
            return;
        }
        try {
            JSONObject json = new JSONObject(msg);
            String id = json.getString("id");
            Log.e(TAG,"msg:" + msg);
            switch (id) {
                case "iceCandidate" :
                    events.onRemoteIceCandidate(toJavaCandidate(
                            new JSONObject(json.getString("candidate"))));
                    break;

                case "viewerResponse":
                    break;

                case "error":
                    break;

                    default:
                        break;
            }
        } catch (JSONException e) {
            reportError("WebSocket message JSON parsing error: " + e.toString());
        }
    }

    @Override
    public void onWebSocketClose(int code, String reason, boolean remote) {
        events.onChannelClose();
    }

    @Override
    public void onWebSocketError(Exception ex) {
        reportError("WebSocket error: " + ex.getMessage());
    }

    // --------------------------------------------------------------------
    // Helper functions.
    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.ERROR) {
                    roomState = ConnectionState.ERROR;
                    events.onChannelError(errorMessage);
                }
            }
        });
    }

    // Put a |key|->|value| mapping in |json|.
    public static void jsonPut(JSONObject json,
                                String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    // Converts a Java candidate to a JSONObject.
    private JSONObject toJsonCandidate(final IceCandidate candidate) {
        JSONObject json = new JSONObject();
        jsonPut(json, "sdpMLineIndex", candidate.sdpMLineIndex);
        jsonPut(json, "sdpMid", candidate.sdpMid);
        jsonPut(json, "candidate", candidate.sdp);
        return json;
    }

    //String sdpMid, int sdpMLineIndex, String sdp
    // Converts a JSON candidate to a Java object.
    IceCandidate toJavaCandidate(JSONObject json) throws JSONException {
        return new IceCandidate(
                json.getString("sdpMid"),
                json.getInt("sdpMLineIndex"),
                json.getString("candidate"));
    }
}
