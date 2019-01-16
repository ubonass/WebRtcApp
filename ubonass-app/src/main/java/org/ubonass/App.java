package org.ubonass;

import android.app.Application;
import android.support.annotation.Nullable;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.webrtcpeer.AppRTCClient.SignalingParameters;

public class App extends Application{

    private static final String TAG = "App";
    private static final String HTTP_ORIGIN = "https://59.110.212.181:4443";
    private static final String ROOM_URL = "wss://59.110.212.181:4443/openvidu";
    @Nullable
    private static RoomParametersFetcher roomParametersFetcher;
    @Nullable
    private static WebSocketRTCClient webSocketRTCClient;

    @Override
    public void onCreate() {
        super.onCreate();
        AppManager.init(this);
        AppManager.requestPermission();
        roomParametersFetcher =
                new RoomParametersFetcher();
        webSocketRTCClient = new WebSocketRTCClient(
                AppManager.getSslSocketFactory(), ROOM_URL);
        webSocketRTCClient.connectToRoomInternal();
    }
}
