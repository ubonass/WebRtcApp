package org.openvidu_demo;

import android.util.Log;
import okhttp3.OkHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openvidu_android.*;


import javax.annotation.Nullable;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.utilities_android.LooperExecutor;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.webrtcpeer.AppRTCClient.SignalingEvents;
import org.webrtcpeer.AppRTCClient;
import org.webrtcpeer.AppRTCClient.RoomConnectionParameters;
import org.webrtcpeer.AppRTCClient.SignalingParameters;
import org.openvidu_demo.RoomParametersFetcher.RoomParametersFetcherEvents;

import static org.openvidu_android.ProtocolElements.*;
import static org.webrtcpeer.util.AppRTCUtils.jsonPut;
import static org.webrtcpeer.util.AppRTCUtils.jsonToValue;

public class WebSocketRTCClient
        implements RoomListener,
        AppRTCClient, RoomParametersFetcherEvents {

    private static final String TAG = "WebSocketRTCClient";


    private enum ConnectionState {NEW, CONNECTED, PUBLISHED, CLOSED, ERROR}

    private ConnectionState roomState;
    @Nullable
    LooperExecutor executor;
    @Nullable
    private OpenViduApi openVidu;
    @Nullable
    private OkHttpClient httpClient;
    @Nullable
    private SSLSocketFactory sslSocketFactory;
    @Nullable
    private SignalingEvents events;
    @Nullable
    private String sessionToken;
    @Nullable
    private RoomConnectionParameters connectionParameters;
    @Nullable
    private RoomParametersFetcher fetcher;
    @Nullable
    private SignalingParameters signalingParameters;
    @Nullable
    private String userId;
    @Nullable
    private String remoteParticipantId;
    private int msgRequestId = 0;//msg for id


    public WebSocketRTCClient(SignalingEvents event,
                              SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
        this.events = event;
        this.roomState = ConnectionState.CLOSED;
        this.executor = new LooperExecutor();
        this.executor.requestStart();
        this.httpClient = new OkHttpClient()
                .newBuilder().connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .sslSocketFactory(sslSocketFactory)
                .hostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                }).build();
    }

    // Helper functions.
    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.ERROR) {
                    roomState = ConnectionState.ERROR;
                    events.onChannelError(errorMessage);
                }
            }
        });
    }
    // Connects to room - function runs on a local looper thread.
    private void connectToRoomInternal() {
        String connectionUrl =
                connectionParameters.roomUrl;
        Log.d(TAG, "Connect to room: " + connectionUrl);
        roomState = ConnectionState.NEW;
        fetcher =
                new RoomParametersFetcher(httpClient,
                        connectionUrl,
                        null,
                        this);
        fetcher.createSessionAndGetToken(connectionParameters.roomId);
    }

    // Disconnect from room and send bye messages - runs on a local looper thread.
    private void disconnectFromRoomInternal() {
        Log.d(TAG, "Disconnect. Room state: " + roomState);
        if (roomState == ConnectionState.CONNECTED) {
            Log.d(TAG, "Closing room.");
            openVidu.sendLeaveRoom(++msgRequestId);
        }
        roomState = ConnectionState.CLOSED;
        if (openVidu != null) {
            openVidu.disconnectWebSocket();
            openVidu = null;
        }
    }


    private void onConnectedToRoomInternal(final SignalingParameters params,
                                           String connectionId) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                events.onConnectedToRoom(params, connectionId);
            }
        });
    }

    private void processParticipantsAlreadyInRoom(
            List<HashMap<String, String>> remoteUsers) {
        if (remoteUsers == null) return;
        int numbers = remoteUsers.size();
        Log.i(TAG, numbers + " person has in Room");
        if (numbers == 0) return;
        executor.executeDelay(new Runnable() {
            @Override
            public void run() {
                for (HashMap<String, String> remoteUser
                        : remoteUsers) {
                    //远程用户是否已经在发布视频,如果是,则从那边接收视频
                    String remoteId = remoteUser.get("id");
                    events.onRemoteJoinedInRoom(signalingParameters, remoteId);
                    String streams = remoteUser.get("streams");
                    if (streams != null) {
                        events.onRemotePublishVideoInRoom(signalingParameters, remoteId);
                    }
                }
            }
        }, 1000);
    }

    private void processIceCandidate(Map<String, Object> params) {
        IceCandidate iceCandidate =
                toJavaCandidate(params);
        String endpointName = params.get("endpointName").toString();
        events.onRemoteIceCandidate(iceCandidate, endpointName);
    }

    private void processParticipantJoined(Map<String, Object> params) {
        String remoteId =
                params.get(PARTICIPANTJOINED_USER_PARAM).toString();
        events.onRemoteJoinedInRoom(signalingParameters, remoteId);
    }

    private void processParticipantPublishVideo(Map<String, Object> params) {
        String remoteId =
                params.get(PARTICIPANTJOINED_USER_PARAM).toString();
        events.onRemotePublishVideoInRoom(signalingParameters, remoteId);
    }


    @Override
    public void onRoomResponse(RoomResponse response) {
        Log.i(TAG, "response:" + response.toString());
        if (response.getMethod() == OpenVidu.Method.JOIN_ROOM) {
            if (roomState == ConnectionState.CONNECTED) {
                //1.首先创建localpeer
                //加入房间成功后创建peerConnection
                String userId = response.getUsertId();
                Log.i(TAG, "userId:" + userId);
                if (userId != null) {
                    this.userId = userId;
                    //创建localPeerConnection并创建sdp
                    onConnectedToRoomInternal(
                            signalingParameters, userId);
                }
            }
            //判断房间内是否已经有人,并且判断房间内的用户是否在发布视频
            List<HashMap<String, String>> remoteUsers =
                    response.getRemoteParticipants();
            if (remoteUsers != null) {//表示房间服务器中已经有人
                processParticipantsAlreadyInRoom(remoteUsers);
            }
        } else if (response.getSdpAnswer() != null) {//表示远程已经有应答
            SessionDescription sessionDescription =
                    new SessionDescription(SessionDescription.Type.ANSWER,
                            response.getSdpAnswer());
            if (roomState == ConnectionState.CONNECTED) {
                roomState = ConnectionState.PUBLISHED;
                events.onRemoteDescription(sessionDescription,
                        userId);
            } else if (roomState == ConnectionState.PUBLISHED) {
                //remote...
                events.onRemoteDescription(sessionDescription, remoteParticipantId);
            }
        }
    }

    @Override
    public void onRoomError(RoomError error) {
        Log.i(TAG, "code:" + error.getCode() + " data :" + error.getData());
        reportError(error.toString());
    }

    @Override
    public void onRoomNotification(RoomNotification notification) {
        Log.i(TAG, "notification:" + notification);
        String method = notification.getMethod();
        Map<String, Object> params = notification.getParams();
        switch (method) {
            case PARTICIPANTJOINED_METHOD:
                Log.i(TAG, "PARTICIPANTJOINED_METHOD");
                processParticipantJoined(params);
                break;

            case PARTICIPANTLEFT_METHOD:
                Log.i(TAG, "PARTICIPANTLEFT_METHOD");
                break;

            case PARTICIPANTEVICTED_METHOD:
                Log.i(TAG, "PARTICIPANTEVICTED_METHOD");
                break;

            case PARTICIPANTPUBLISHED_METHOD:
                Log.i(TAG, "PARTICIPANTPUBLISHED_METHOD");
                processParticipantPublishVideo(params);
                break;
            case PARTICIPANTUNPUBLISHED_METHOD:
                Log.i(TAG, "PARTICIPANTUNPUBLISHED_METHOD");

                break;
            case PARTICIPANTSENDMESSAGE_METHOD:
                Log.i(TAG, "PARTICIPANTSENDMESSAGE_METHOD");
                break;

            case ICECANDIDATE_METHOD:
                Log.i(TAG, "ICECANDIDATE_METHOD");
                processIceCandidate(params);
                break;

            default:
                Log.e(TAG, "Can't understand method: " + method);
        }
    }

    /**
     * websocket连接成功
     * 1.加入房间
     * 2.发送publishvideo到房间服务器
     */
    @Override
    public void onRoomConnected() {
        if (roomState != ConnectionState.CONNECTED) {
            roomState = ConnectionState.CONNECTED;
            //加入房间
            openVidu.sendJoinRoom(
                    "MY_SECRET",
                    connectionParameters.roomId,
                    sessionToken,
                    false,
                    connectionParameters.urlParameters,
                    "android",
                    ++msgRequestId);
        }
    }

    /**
     * 端口连接
     */
    @Override
    public void onRoomDisconnected() {
        Log.i(TAG, "onRoomDisconnected ");
        events.onChannelClose();
    }

    /**
     * 连接房间服务器入口,通过Http获取房间相关参数
     *
     * @param connectionParameters
     */
    @Override
    public void connectToRoom(
            RoomConnectionParameters connectionParameters) {
        this.connectionParameters = connectionParameters;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                connectToRoomInternal();
            }
        });
    }

    @Override
    public void sendOfferSdp(SessionDescription sdp,
                             String connectionId) {
        Log.i(TAG, "sendOfferSdp..." +
                "connectionId:" + connectionId + " roomstate:" + roomState);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (connectionId.equals(userId)) {
                    openVidu.sendPublishVideo(
                            true,
                            true,
                            true,
                            true,
                            false,
                            "{\"width\":1280,\"height\":720}",
                            "30",
                            "camera",
                            sdp.description, ++msgRequestId);

                } else {
                    remoteParticipantId = connectionId;
                    openVidu.sendReceiveVideoFrom(connectionId + "_" + "camera",
                            sdp.description, ++msgRequestId);
                }
            }
        });
    }

    @Override
    public void sendAnswerSdp(SessionDescription sdp, String connectionId) {

    }

    @Override
    public void sendLocalIceCandidate(IceCandidate candidate,
                                      String connectionId) {
        Log.i(TAG, "sendLocalIceCandidate..." +
                " connectionId:" + connectionId + " roomstate:" + roomState);
        if (roomState ==
                ConnectionState.CONNECTED || roomState ==
                ConnectionState.PUBLISHED) {
            openVidu.sendOnIceCandidate(
                    connectionId,
                    candidate.sdp,
                    candidate.sdpMid,
                    candidate.sdpMid,
                    ++msgRequestId);
        }
    }

    @Override
    public void
    sendLocalIceCandidateRemovals(IceCandidate[] candidates,
                                  String connectionId) {

    }

    @Override
    public void disconnectFromRoom() {
        disconnectFromRoomInternal();
        executor.requestStop();
    }

    @Override
    public void onSignalingParametersReady(SignalingParameters params) {
        this.signalingParameters = params;
        if (signalingParameters == null) return;
        openVidu = new OpenViduApi(executor,
                signalingParameters.wssUrl,
                sslSocketFactory,
                this);
        try {
            Log.i(TAG, "response:" + params.response);
            JSONObject jsonObject =
                    new JSONObject(params.response);
            sessionToken = jsonToValue(jsonObject, "token");
            //sessionName = jsonToValue(jsonObject, "session");
            Log.i(TAG, "sessionToken:" + sessionToken);
            openVidu.connectWebSocket();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSignalingParametersError(String description) {

    }

    //String sdpMid, int sdpMLineIndex, String sdp
    // Converts a JSON candidate to a Java object.
    private IceCandidate toJavaCandidate(Map<String, Object> params) {
        long sdpMLineIndex = (long)
                params.get(ICECANDIDATE_SDPMLINEINDEX_PARAM);
        return new IceCandidate(
                (String) params.get(ICECANDIDATE_SDPMID_PARAM),
                (int) sdpMLineIndex,
                (String) params.get(ICECANDIDATE_CANDIDATE_PARAM));
    }

    // Converts a Java candidate to a JSONObject.
    private JSONObject toJsonCandidate(final IceCandidate candidate) {
        org.json.JSONObject json = new org.json.JSONObject();
        jsonPut(json, ICECANDIDATE_SDPMLINEINDEX_PARAM, candidate.sdpMLineIndex);
        jsonPut(json, ICECANDIDATE_SDPMID_PARAM, candidate.sdpMid);
        jsonPut(json, ICECANDIDATE_CANDIDATE_PARAM, candidate.sdp);
        return json;
    }
}
