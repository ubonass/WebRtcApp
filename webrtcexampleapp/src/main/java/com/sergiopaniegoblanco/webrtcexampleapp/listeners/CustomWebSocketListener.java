package com.sergiopaniegoblanco.webrtcexampleapp.listeners;

import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sergiopaniegoblanco.webrtcexampleapp.VideoConferenceActivity;
import com.sergiopaniegoblanco.webrtcexampleapp.constants.JSONConstants;
import com.sergiopaniegoblanco.webrtcexampleapp.managers.PeersManager;
import com.sergiopaniegoblanco.webrtcexampleapp.R;
import com.sergiopaniegoblanco.webrtcexampleapp.RemoteParticipant;
import com.sergiopaniegoblanco.webrtcexampleapp.observers.CustomSdpObserver;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by sergiopaniegoblanco on 02/12/2017.
 */

public class CustomWebSocketListener extends WebSocketClient {

    private static final String TAG = "CustomWebSocketAdapter";
    private static final String JSON_RPCVERSION = "2.0";
    private static final int PING_MESSAGE_INTERVAL = 3;

    private VideoConferenceActivity videoConferenceActivity;
    private PeerConnection localPeer;
    private int id;
    private List<Map<String, String>> iceCandidatesParams;
    private Map<String, String> localOfferParams;
    private String userId;
    private String sessionName;
    private String participantName;
    private LinearLayout views_container;
    private Map<String, RemoteParticipant> participants;
    private String remoteParticipantId;
    private PeersManager peersManager;
    private String socketAddress;
    private String token;

    public CustomWebSocketListener(VideoConferenceActivity videoConferenceActivity,
                                   PeersManager peersManager,
                                   String sessionName,
                                   String participantName,
                                   LinearLayout views_container,
                                   String socketAddress,
                                   String token) throws URISyntaxException {
        super(new URI(socketAddress));
        this.videoConferenceActivity = videoConferenceActivity;
        this.peersManager = peersManager;
        this.localPeer = peersManager.getLocalPeer();
        this.id = 0;
        this.sessionName = sessionName;
        this.participantName = participantName;
        this.views_container = views_container;
        this.socketAddress = socketAddress;
        this.iceCandidatesParams = new ArrayList<>();
        this.participants = new HashMap<>();
        this.token = token;
    }

    public Map<String, RemoteParticipant> getParticipants() {
        return participants;
    }

    public String getUserId() {
        return userId;
    }

    public int getId() {
        return id;
    }

    private void updateId() {
        id++;
    }


    private void pingMessageHandler() {
        long initialDelay = 0L;
        ScheduledThreadPoolExecutor executor =
                new ScheduledThreadPoolExecutor(1);
        executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                Map<String, String> pingParams = new HashMap<>();
                if (id == 0) {
                    pingParams.put("interval", "3000");
                }
                sendJson("ping", pingParams);
            }
        }, initialDelay, PING_MESSAGE_INTERVAL, TimeUnit.SECONDS);
    }


    private void handleResult(JSONObject json) throws JSONException {
        JSONObject result = new JSONObject(json.getString(JSONConstants.RESULT));
        if (result.has(JSONConstants.SDP_ANSWER)) {
            saveAnswer(result);
        } else if (result.has(JSONConstants.SESSION_ID)) {
            if (result.has(JSONConstants.VALUE)) {
                Log.i(TAG,"room has person");
                Log.i(TAG,"VALUE.length" +
                        result.getJSONArray(JSONConstants.VALUE).length());
                if (result.getJSONArray(JSONConstants.VALUE).length() > 0) {
                    addParticipantsAlreadyInRoom(result);
                }
                this.userId = result.getString(JSONConstants.ID);
                Log.i(TAG,"userId:" + userId);
                for (Map<String, String> iceCandidate : iceCandidatesParams) {
                    iceCandidate.put("endpointName", this.userId);
                    sendJson("onIceCandidate", iceCandidate);
                }
            }
        } else if (result.has(JSONConstants.VALUE)) {
            Log.i(TAG, "pong");
        } else {
            Log.e(TAG, "Unrecognized " + result);
        }
    }

    private void addParticipantsAlreadyInRoom(JSONObject result) throws JSONException {
        for (int i = 0; i < result.getJSONArray(JSONConstants.VALUE).length(); i++) {
            remoteParticipantId =
                    result.getJSONArray(JSONConstants.VALUE).getJSONObject(i)
                            .getString(JSONConstants.ID);
            final RemoteParticipant remoteParticipant = new RemoteParticipant();
            remoteParticipant.setId(remoteParticipantId);
            participants.put(remoteParticipantId, remoteParticipant);
            createVideoView(remoteParticipant);
            setRemoteParticipantName(new JSONObject(
                    result.getJSONArray(JSONConstants.VALUE)
                            .getJSONObject(i).getString(JSONConstants.METADATA))
                    .getString("clientData"), remoteParticipant);
            peersManager.createRemotePeerConnection(remoteParticipant);
            remoteParticipant.getPeerConnection().createOffer(
                    new CustomSdpObserver("remoteCreateOffer") {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    super.onCreateSuccess(sessionDescription);
                    remoteParticipant.getPeerConnection()
                            .setLocalDescription(new CustomSdpObserver(
                                    "remoteSetLocalDesc"), sessionDescription);
                    Map<String, String> remoteOfferParams = new HashMap<>();
                    remoteOfferParams.put("sdpOffer", sessionDescription.description);
                    remoteOfferParams.put("sender", remoteParticipantId + "_CAMERA");
                    sendJson("receiveVideoFrom", remoteOfferParams);
                }
            }, new MediaConstraints());
        }
    }

    private void handleMethod(JSONObject json) throws JSONException {
        if (!json.has(JSONConstants.PARAMS)) {
            Log.e(TAG, "No params");
        } else {
            final JSONObject params = new JSONObject(json.getString(JSONConstants.PARAMS));
            String method = json.getString(JSONConstants.METHOD);
            switch (method) {
                case JSONConstants.ICE_CANDIDATE:
                    Log.i(TAG,"ICE_CANDIDATE CALL");
                    iceCandidateMethod(params);
                    break;
                case JSONConstants.PARTICIPANT_JOINED:
                    Log.i(TAG,"PARTICIPANT_JOINED CALL");
                    participantJoinedMethod(params);
                    break;
                case JSONConstants.PARTICIPANT_PUBLISHED:
                    Log.i(TAG,"PARTICIPANT_PUBLISHED CALL");
                    participantPublishedMethod(params);
                    break;
                case JSONConstants.PARTICIPANT_LEFT:
                    Log.i(TAG,"PARTICIPANT_LEFT CALL");
                    participantLeftMethod(params);
                    break;
                default:
                    throw new JSONException("Can't understand method: " + method);
            }
        }
    }

    private void iceCandidateMethod(JSONObject params) throws JSONException {
        Log.i(TAG,"userId:" + userId + " endpointName:" +
                params.getString("endpointName"));
        if (params.getString("endpointName")
                .equals(userId)) {
            saveIceCandidate(params, null);
        } else {
            saveIceCandidate(params,
                    params.getString("endpointName"));
        }
    }

    private void participantJoinedMethod(JSONObject params) throws JSONException {
        final RemoteParticipant remoteParticipant = new RemoteParticipant();
        remoteParticipant.setId(params.getString(JSONConstants.ID));
        participants.put(params.getString(JSONConstants.ID), remoteParticipant);
        createVideoView(remoteParticipant);
        setRemoteParticipantName(
                new JSONObject(params.getString(JSONConstants.METADATA))
                        .getString("clientData"), remoteParticipant);
        peersManager
                .createRemotePeerConnection(remoteParticipant);
    }

    private void createVideoView(final RemoteParticipant remoteParticipant) {
        Handler mainHandler = new Handler(videoConferenceActivity.getMainLooper());

        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                View rowView = videoConferenceActivity.getLayoutInflater().inflate(R.layout.peer_video, null);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, 20);
                rowView.setLayoutParams(lp);
                int rowId = View.generateViewId();
                rowView.setId(rowId);
                views_container.addView(rowView);
                SurfaceViewRenderer videoView = (SurfaceViewRenderer) ((ViewGroup) rowView).getChildAt(0);
                remoteParticipant.setVideoView(videoView);
                videoView.setMirror(false);
                EglBase rootEglBase = EglBase.create();
                videoView.init(rootEglBase.getEglBaseContext(), null);
                videoView.setZOrderMediaOverlay(true);
                View textView = ((ViewGroup) rowView).getChildAt(1);
                remoteParticipant.setParticipantNameText((TextView) textView);
                remoteParticipant.setView(rowView);
            }
        };
        mainHandler.post(myRunnable);
    }

    private void participantPublishedMethod(JSONObject params) throws JSONException {
        remoteParticipantId = params.getString(JSONConstants.ID);
        RemoteParticipant remoteParticipantPublished = participants.get(remoteParticipantId);
        remoteParticipantPublished.getPeerConnection()
                .createOffer(new CustomSdpObserver("remoteCreateOffer",
                        remoteParticipantPublished) {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                getRemoteParticipant().getPeerConnection().setLocalDescription(new CustomSdpObserver("remoteSetLocalDesc"), sessionDescription);
                Map<String, String> remoteOfferParams = new HashMap<>();
                remoteOfferParams.put("sdpOffer", sessionDescription.description);
                remoteOfferParams.put("sender", getRemoteParticipant().getId() + "_webcam");
                sendJson("receiveVideoFrom", remoteOfferParams);
            }
        }, new MediaConstraints());
    }

    private void participantLeftMethod(JSONObject params) throws JSONException {
        final String participantId = params.getString("name");
        participants.get(participantId).getPeerConnection().close();
        Handler mainHandler = new Handler(videoConferenceActivity.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                views_container.removeView(participants.get(participantId).getView());
            }
        };
        mainHandler.post(myRunnable);
        RemoteParticipant remoteParticipantToDelete = participants.get(participantId);
        participants.remove(remoteParticipantToDelete);
    }

    private void setRemoteParticipantName(final String name, final RemoteParticipant participant) {
        Handler mainHandler = new Handler(videoConferenceActivity.getMainLooper());

        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                videoConferenceActivity.setRemoteParticipantName(name, participant);
            }
        };
        mainHandler.post(myRunnable);
    }


    private void saveIceCandidate(JSONObject json,
                                  String endPointName) throws JSONException {
        IceCandidate iceCandidate =
                new IceCandidate(json.getString("sdpMid"),
                        Integer.parseInt(json.getString("sdpMLineIndex")),
                        json.getString("candidate"));
        if (endPointName == null) {
            Log.i(TAG,"addIceCandidate Local");
            localPeer.addIceCandidate(iceCandidate);
        } else {
            Log.i(TAG,"addIceCandidate Remote");
            participants.get(endPointName).getPeerConnection().addIceCandidate(iceCandidate);
        }
    }

    private void saveAnswer(JSONObject json) throws JSONException {
        SessionDescription sessionDescription =
                new SessionDescription(SessionDescription.Type.ANSWER,
                        json.getString("sdpAnswer"));
        if (localPeer.getRemoteDescription() == null) {
            localPeer.setRemoteDescription(
                    new CustomSdpObserver("localSetRemoteDesc"),
                    sessionDescription);
        } else {
            participants.get(remoteParticipantId)
                    .getPeerConnection()
                    .setRemoteDescription(
                            new CustomSdpObserver("remoteSetRemoteDesc"),
                            sessionDescription);
        }
    }

    public void sendJson(String method, Map<String, String> params) {
        try {
            JSONObject paramsJson = new JSONObject();
            for (Map.Entry<String, String> param : params.entrySet()) {
                paramsJson.put(param.getKey(), param.getValue());
            }
            JSONObject jsonObject = new JSONObject();
            if (method.equals(JSONConstants.JOIN_ROOM)) {
                jsonObject.put(JSONConstants.ID, 1)
                        .put(JSONConstants.PARAMS, paramsJson);
            } else if (paramsJson.length() > 0) {
                jsonObject.put(JSONConstants.ID, getId())
                        .put(JSONConstants.PARAMS, paramsJson);
            } else {
                jsonObject.put(JSONConstants.ID, getId());
            }
            jsonObject.put("jsonrpc", JSON_RPCVERSION)
                    .put(JSONConstants.METHOD, method);
            String jsonString = jsonObject.toString();
            updateId();
            send(jsonString);
        } catch (JSONException e) {
            Log.i(TAG, "JSONException raised on sendJson");
        }
    }

    public void addIceCandidate(Map<String, String> iceCandidateParams) {
        Log.i(TAG,"addIceCandidate to  iceCandidatesParams");
        iceCandidatesParams.add(iceCandidateParams);
    }

    public void setLocalOfferParams(Map<String, String> offerParams) {
        this.localOfferParams = offerParams;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        pingMessageHandler();
        String regex = "(room)+";
        String baseAddress = socketAddress.split(regex)[0];
        Map<String, String> joinRoomParams = new HashMap<>();
        joinRoomParams.put(JSONConstants.METADATA,
                "{\"clientData\": \"" + participantName + "\"}");
        joinRoomParams.put("recorder", "false");
        joinRoomParams.put("secret", "MY_SECRET");
        joinRoomParams.put("session", sessionName);
        joinRoomParams.put("token", token);
        joinRoomParams.put("platform", "android");
        sendJson("joinRoom", joinRoomParams);

        if (localOfferParams != null) {
            sendJson("publishVideo", localOfferParams);
        }
    }

    @Override
    public void onMessage(String message) {
        //Log.i(TAG, "Text Message " + message);
        try {
            JSONObject json = new JSONObject(message);
            if (json.has(JSONConstants.RESULT)) {
                handleResult(json);
            } else {
                handleMethod(json);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {

    }

    @Override
    public void onError(Exception ex) {

    }
}