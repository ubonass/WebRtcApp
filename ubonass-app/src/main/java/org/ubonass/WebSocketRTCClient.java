package org.ubonass;

import android.util.Log;
import org.openvidu_android.*;
import org.utilities_android.LooperExecutor;
import org.webrtcpeer.AppRTCClient;

import javax.annotation.Nullable;
import javax.net.ssl.SSLSocketFactory;

import org.webrtcpeer.AppRTCClient.SignalingEvents;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WebSocketRTCClient implements RoomListener {

    private static final String TAG = "WebSocketRTCClient";
    private static final int PING_MESSAGE_INTERVAL = 5;

    private enum ConnectionState {NEW, CONNECTED, PUBLISHED, CLOSED, ERROR}

    private ConnectionState roomState;
    @Nullable
    LooperExecutor executor;
    @Nullable
    private OpenViduApi openVidu;
    @Nullable
    private SSLSocketFactory sslSocketFactory;
    /*@Nullable
    private SignalingEvents events;*/
    /*@Nullable
    private RoomConnectionParameters connectionParameters;
    @Nullable
    private SignalingParameters signalingParameters;*/
    @Nullable
    private String localParticipantId;
    @Nullable
    private String remoteParticipantId;
    //@Nullable
    private String wssUrl;

    private int msgRequestId = 0;//msg for id

    public WebSocketRTCClient(/*SignalingEvents event,*/
                              SSLSocketFactory sslSocketFactory,
                              String wssUrl) {
        this.sslSocketFactory = sslSocketFactory;
        /*this.events = event;*/
        this.wssUrl = wssUrl;
        this.roomState = ConnectionState.CLOSED;
        this.executor = new LooperExecutor();
        this.executor.requestStart();

    }

    // Connects to room - function runs on a local looper thread.
    public void connectToRoomInternal() {
        roomState = ConnectionState.NEW;
        if (openVidu == null) {
            this.openVidu =
                    new OpenViduApi(executor, wssUrl, sslSocketFactory, this);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    openVidu.connectWebSocket();
                }
            });
        }
    }

    // Disconnect from room and send bye messages - runs on a local looper thread.
    public void disconnectFromRoomInternal() {
        Log.d(TAG, "Disconnect. Room state: " + roomState);
        if (roomState ==
                ConnectionState.PUBLISHED ||
                roomState == ConnectionState.CONNECTED) {
            Log.d(TAG, "Leave room.");
            openVidu.sendLeaveRoom(++msgRequestId);
        }
        roomState = ConnectionState.CLOSED;
        if (openVidu != null) {
            openVidu.disconnectWebSocket(true);
            openVidu = null;
        }
    }


    private void pingMessageHandler() {
        long initialDelay = 0L;
        ScheduledThreadPoolExecutor executor =
                new ScheduledThreadPoolExecutor(1);
        executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                Map<String, Object> pingParams = new HashMap<>();
                if (msgRequestId == 0) {
                    //interval*3 millis,服务器将interval*3的时间
                    //15 秒没收到ping就会关闭连接
                    pingParams.put("interval", "5000");
                }
                openVidu.sendCustomMessage("ping",
                        pingParams, msgRequestId++);
            }
        }, initialDelay, PING_MESSAGE_INTERVAL, TimeUnit.SECONDS);
    }


    // Helper functions.
    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.ERROR) {
                    roomState = ConnectionState.ERROR;
                    //events.onChannelError(errorMessage);
                }
            }
        });
    }

    /**
     * For RoomListener
     *
     * @param response The response object
     */
    @Override
    public void onRoomResponse(RoomResponse response) {
        Log.i(TAG, "onRoomResponse:" + response.toString());
    }

    @Override
    public void onRoomError(RoomError error) {
        Log.i(TAG, "onRoomError:" + error.toString());
    }

    @Override
    public void onRoomNotification(RoomNotification notification) {
        Log.i(TAG, "onRoomNotification:" + notification.toString());
    }

    @Override
    public void onRoomConnected() {
        Log.i(TAG, "onRoomConnected:");
        pingMessageHandler();
        roomState = ConnectionState.CONNECTED;
    }

    @Override
    public void onRoomDisconnected() {
        Log.i(TAG, "onRoomDisconnected:");
        roomState = ConnectionState.CLOSED;
        openVidu = null;
        executor.requestStop();
        //events.onChannelClose();
    }

}
