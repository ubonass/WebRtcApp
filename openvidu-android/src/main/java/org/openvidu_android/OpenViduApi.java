package org.openvidu_android;

import android.support.annotation.Nullable;
import android.util.Log;
import net.minidev.json.JSONObject;
import org.java_websocket.handshake.ServerHandshake;
import org.jsonrpc_ws_android.JsonRpcNotification;
import org.jsonrpc_ws_android.JsonRpcRequest;
import org.jsonrpc_ws_android.JsonRpcResponse;
import org.utilities_android.LooperExecutor;

import javax.net.ssl.SSLSocketFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import static org.openvidu_android.ProtocolElements.*;

public class OpenViduApi extends OpenVidu {

    private static final String TAG = "OpenViduApi";
    @Nullable
    private Vector<RoomListener> listeners;

    public OpenViduApi(LooperExecutor executor,
                       String uri,
                       SSLSocketFactory sslSocketFactory,
                       RoomListener listener) {
        super(executor, uri, sslSocketFactory);
        this.listeners = new Vector<>();
        this.listeners.add(listener);
    }

    /**
     * @param session
     * @param token
     * @param metadata
     * @param secret
     * @param platform
     * @param recorder
     * @param id
     */
    public void sendJoinRoom(String user,
                             String secret,
                             String session,
                             String token,
                             boolean recorder,
                             String metadata,
                             String platform,
                             int id) {
        Map<String, Object> joinRoomParams = new HashMap<>();
        joinRoomParams.put("metadata",
                "{\"clientData\": \"" + metadata + "\"}");
        joinRoomParams.put("recorder", recorder);
        joinRoomParams.put("user", user);
        joinRoomParams.put("secret", secret);
        joinRoomParams.put("session", session);
        joinRoomParams.put("token", token);
        joinRoomParams.put("platform", platform);
        send(JOINROOM_METHOD, joinRoomParams, id);
    }

    /**
     * @param secret
     * @param session
     * @param token
     * @param recorder
     * @param metadata
     * @param platform
     * @param id
     */
    public void sendJoinRoom(String secret,
                             String session,
                             String token,
                             boolean recorder,
                             String metadata,
                             String platform,
                             int id) {
        Map<String, Object> joinRoomParams = new HashMap<>();
        joinRoomParams.put(JOINROOM_METADATA_PARAM,
                "{\"clientData\": \"" + metadata + "\"}");
        joinRoomParams.put(JOINROOM_RECORDER_PARAM, recorder);
        joinRoomParams.put(JOINROOM_SECRET_PARAM, secret);
        joinRoomParams.put(JOINROOM_ROOM_PARAM, session);
        joinRoomParams.put(JOINROOM_TOKEN_PARAM, token);
        joinRoomParams.put(JOINROOM_PLATFORM_PARAM, platform);
        send(JOINROOM_METHOD, joinRoomParams, id);
    }

    /**
     * Method will leave the current room.
     *
     * @param id is an index number to track the corresponding response message to this request.
     */
    public void sendLeaveRoom(int id) {
        send(LEAVEROOM_METHOD, null, id);
    }

    /**
     * @param hasAudio
     * @param hasVideo
     * @param audioActive
     * @param videoActive
     * @param doLoopback  is a boolean value enabling media loopback
     * @param frameRate
     * @param typeOfVideo
     * @param sdpOffer    is a string sent by the client
     * @param id          is an index number to track the corresponding response message to this request.
     */
    public void sendPublishVideo(boolean hasAudio,
                                 boolean hasVideo,
                                 boolean audioActive,
                                 boolean videoActive,
                                 boolean doLoopback,
                                 String videoDimensions,
                                 String frameRate,
                                 String typeOfVideo,
                                 String sdpOffer, int id) {
        Map<String, Object> sdpOfferParams = new HashMap<>();
        sdpOfferParams.put(PUBLISHVIDEO_AUDIOACTIVE_PARAM, audioActive);
        sdpOfferParams.put(PUBLISHVIDEO_VIDEOACTIVE_PARAM, videoActive);
        sdpOfferParams.put(PUBLISHVIDEO_HASAUDIO_PARAM, hasAudio);
        sdpOfferParams.put(PUBLISHVIDEO_HASVIDEO_PARAM, hasVideo);
        sdpOfferParams.put(PUBLISHVIDEO_DOLOOPBACK_PARAM, doLoopback);
        sdpOfferParams.put(PUBLISHVIDEO_FRAMERATE_PARAM, frameRate);
        sdpOfferParams.put(PUBLISHVIDEO_TYPEOFVIDEO_PARAM, typeOfVideo);
        sdpOfferParams.put(PUBLISHVIDEO_VIDEODIMENSIONS_PARAM,videoDimensions);
        sdpOfferParams.put(PUBLISHVIDEO_SDPOFFER_PARAM, sdpOffer);
        send(PUBLISHVIDEO_METHOD, sdpOfferParams, id);
    }

    /**
     * Method unpublishes a previously published video.
     *
     * @param id is an index number to track the corresponding response message to this request.
     */
    public void sendUnpublishVideo(int id) {
        send(UNPUBLISHVIDEO_METHOD, null, id);
    }

    /**
     * Method represents the client's request to receive media from participants in
     * the room that published their media. The response will contain the sdpAnswer attribute.
     *
     * @param sender   is a combination of publisher's name and his currently opened stream
     *                 (usually webcam) separated by underscore. For example: userid_webcam
     * @param sdpOffer is the SDP offer sent by this client.
     * @param id       is an index number to track the corresponding response message to this request.
     */
    public void sendReceiveVideoFrom(String sender,
                                     String sdpOffer,
                                     int id) {
        HashMap<String, Object> namedParameters = new HashMap<>();
        namedParameters.put(RECEIVEVIDEO_SDPOFFER_PARAM, sdpOffer);
        namedParameters.put(RECEIVEVIDEO_SENDER_PARAM, sender);
        send(RECEIVEVIDEO_METHOD, namedParameters, id);
    }

    /**
     * Method represents a client's request to stop receiving media from a given publisher.
     * Response will contain the sdpAnswer attribute.
     *
     * @param userId   is the publisher's username.
     * @param streamId is the name of the stream (typically webcam)
     * @param id       is an index number to track the corresponding response message to this request.
     */
    public void sendUnsubscribeFromVideo(String userId, String streamId, int id) {
        String sender = userId + "_" + streamId;
        HashMap<String, Object> namedParameters = new HashMap<>();
        namedParameters.put("sender", sender);
        send(UNSUBSCRIBEFROMVIDEO_METHOD, namedParameters, id);
    }

    /**
     * Method carries the information about the ICE candidate gathered on the client side.
     * This information is required to implement the trickle ICE mechanism.
     *
     * @param endpointName  is the username of the peer whose ICE candidate was found
     * @param candidate     contains the candidate attribute information
     * @param sdpMid        is the media stream identification, "audio" or "video", for the m-line,
     *                      this candidate is associated with.
     * @param sdpMLineIndex is the index (starting at 0) of the m-line in the SDP,
     *                      this candidate is associated with.
     */
    public void sendOnIceCandidate(String endpointName, String candidate, String sdpMid, String sdpMLineIndex, int id) {
        HashMap<String, Object> namedParameters = new HashMap<>();
        namedParameters.put("endpointName", endpointName);
        namedParameters.put("candidate", candidate);
        namedParameters.put("sdpMid", sdpMid);
        namedParameters.put("sdpMLineIndex", sdpMLineIndex);
        send(ONICECANDIDATE_METHOD, namedParameters, id);
    }

    /**
     * Method sends a message from the user to all other participants in the room.
     *
     * @param roomId  is the name of the room.
     * @param userId  is the username of the user sending the message.
     * @param message is the text message sent to the room.
     * @param id      is an index number to track the corresponding response message to this request.
     */
    public void sendMessage(String roomId, String userId, String message, int id) {
        HashMap<String, Object> namedParameters = new HashMap<>();
        namedParameters.put("message", message);
        namedParameters.put("userMessage", userId);
        namedParameters.put("roomMessage", roomId);
        send(SENDMESSAGE_ROOM_METHOD, namedParameters, id);
    }

    /**
     * Method to send any custom requests that are not directly implemented by the Room server.
     *
     * @param names  is an array of parameter names.
     * @param values is an array of parameter values where the index is corresponding with
     *               the applicable name value in the names array.
     * @param id     is an index number to track the corresponding response message to this request.
     */
    public void sendCustomRequest(String[] names, String[] values, int id) {
        if (names == null || values == null || names.length != values.length) {
            return;  // mismatching name-value pairs
        }
        HashMap<String, Object> namedParameters = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            namedParameters.put(names[i], values[i]);
        }
        send(CUSTOMREQUEST_METHOD, namedParameters, id);

    }

    /**
     * send message from custom..
     *
     * @param method
     * @param message
     * @param id
     */
    public void sendCustomMessage(String method,
                                  Map<String, Object> message, int id) {
        send(method, message, id);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        super.onOpen(handshakedata);
        synchronized (listeners) {
            for (RoomListener rl : listeners) {
                rl.onRoomConnected();
            }
        }
    }

    @Override
    public void onRequest(JsonRpcRequest request) {
        super.onRequest(request);
    }

    @Override
    public void onResponse(JsonRpcResponse response) {
        super.onResponse(response);
        if (response.isSuccessful()) {
            JSONObject jsonObject = (JSONObject) response.getResult();
            RoomResponse roomResponse =
                    new RoomResponse(response.getId().toString(), jsonObject);

            synchronized (listeners) {
                for (RoomListener rl : listeners) {
                    rl.onRoomResponse(roomResponse);
                }
            }
        } else {
            RoomError roomError = new RoomError(response.getError());

            synchronized (listeners) {
                for (RoomListener rl : listeners) {
                    rl.onRoomError(roomError);
                }
            }
        }
    }

    @Override
    public void onNotification(JsonRpcNotification notification) {
        super.onNotification(notification);
        RoomNotification roomNotification =
                new RoomNotification(notification);

        synchronized (listeners) {
            for (RoomListener rl : listeners) {
                rl.onRoomNotification(roomNotification);
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        super.onClose(code, reason, remote);
        synchronized (listeners) {
            for (RoomListener rl : listeners) {
                rl.onRoomDisconnected();
            }
        }
    }

    @Override
    public void onError(Exception e) {
        super.onError(e);
        Log.e(TAG, "onError: " + e.getMessage(), e);
    }

    public void addObserver(RoomListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeObserver(RoomListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }
}
