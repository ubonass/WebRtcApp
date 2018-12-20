package org.kurento_android.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.java_websocket.handshake.ServerHandshake;
import org.kurento_android.R;
import org.utilities_android.SecurityCertificatation;
import org.webrtc.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import org.webrtcpeer.*;
import org.webrtcpeer.PeerConnectionClient.PeerConnectionParameters;
import org.webrtcpeer.AppRTCClient.SignalingParameters;
import javax.net.ssl.SSLSocketFactory;
/**
 * Created by cxm on 8/3/16.
 */
public class CallFragment2 extends BaseFragment
        implements PeerConnectionEvents,
        WebSocketChannelClient.WebSocketChannelEvents{

    private static final String TAG = "CallFragment2";

    @InjectView(R.id.local_video_view)
    SurfaceViewRenderer localRender;

    @InjectView(R.id.remote_video_view)
    SurfaceViewRenderer remoteRender;

    @Nullable
    private PeerConnectionParameters peerConnectionParameters;
    @Nullable
    private PeerConnectionClient peerConnectionClient;

    @Nullable
    private AppRTCAudioManager audioManager;
    // video render
    @Nullable
    private EglBase rootEglBase;
    @Nullable
    private WebSocketChannelClient wsClient;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_call_buddy, container, false);
        ButterKnife.inject(this, view);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Create video renderers.
        rootEglBase = EglBase.create();
        localRender.init(rootEglBase.getEglBaseContext(), null);
        remoteRender.init(rootEglBase.getEglBaseContext(), null);
        localRender.setZOrderMediaOverlay(true);

        peerConnectionClient =
                new PeerConnectionClient(getContext(),
                        rootEglBase,peerConnectionParameters,this);
        PeerConnectionFactory.Options options =
                new PeerConnectionFactory.Options();
        if (peerConnectionParameters.loopback) {
            options.networkIgnoreMask = 0;
        }
        peerConnectionClient.createPeerConnectionFactory(options);
    }

    @Override
    public void onResume() {
        super.onResume();
    }


    @Override
    public void onStop() {

        if (peerConnectionClient != null) {
            peerConnectionClient.close();
            peerConnectionClient = null;
        }
        if (localRender != null) {
            localRender.release();
            // localRender = null;
        }
        if (remoteRender != null) {
            remoteRender.release();
            // remoteRender = null;
        }
        if (null != rootEglBase) {
            rootEglBase.release();
            rootEglBase = null;
        }

        if (null != audioManager) {
            //audioManager.close();
            audioManager = null;
        }

        super.onStop();
    }


    @Override
    public void onLocalDescription(SessionDescription sdp) {
        Log.i(TAG,"onLocalDescription: " + sdp.description);
        //ViewMessage viewMessage = new ViewMessage(sdp.description);
        //signalChannel.sendMessage(viewMessage);
    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {
        Log.i(TAG,"onIceCandidate: " + candidate);
        //signalChannel.sendMessage(new OnIceCandidateMessage(candidate));
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {
        Log.i(TAG,"onIceCandidateRemoved: " + candidates);
    }

    @Override
    public void onIceConnected() {
        Log.i(TAG,"onIceConnected");
    }

    @Override
    public void onIceDisconnected() {
        Log.i(TAG,"onIceDisconnected");
    }

    @Override
    public void onPeerConnectionClosed() {
        Log.i(TAG,"onPeerConnectionClosed");
    }

    @Override
    public void onPeerConnectionStatsReady(StatsReport[] reports) {
        Log.i(TAG,"onPeerConnectionStatsReady: " + reports);
    }

    @Override
    public void onRemoteStreamAdded(MediaStream stream) {
        
    }

    @Override
    public void onRemoteStreamRemoved(MediaStream stream) {

    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {

    }

    @Override
    public void onPeerConnectionError(String description) {
        Log.i(TAG,"onPeerConnectionError: " + description);
    }

    @Override
    public void onBufferedAmountChange(long l, DataChannel channel) {

    }

    @Override
    public void onStateChange(DataChannel channel) {

    }

    @Override
    public void onMessage(DataChannel.Buffer buffer, DataChannel channel) {

    }

    @Override
    public void onWebSocketOpen(ServerHandshake handshakedata) {
        Log.i(TAG, "onWebSocketOpen: " + handshakedata.getHttpStatusMessage());
    }

    @Override
    public void onWebSocketMessage(String message) {
        Log.i(TAG, "onWebSocketMessage: " + message);
    }

    @Override
    public void onWebSocketClose(int code, String reason, boolean remote) {
        Log.i(TAG, "onWebSocketClose: " + reason);
    }

    @Override
    public void onWebSocketError(Exception ex) {
        Log.i(TAG, "onWebSocketError: " + ex.getMessage());
    }
}
