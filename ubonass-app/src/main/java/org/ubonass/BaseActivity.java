package org.ubonass;

import android.app.Activity;
import android.support.annotation.Nullable;
import org.webrtc.*;
import org.webrtcpeer.PeerConnectionClient;
import org.webrtcpeer.PeerConnectionEvents;
import org.ubonass.RoomParametersFetcher.RoomParametersFetcherEvents;
import org.webrtcpeer.AppRTCClient.SignalingParameters;

public class BaseActivity extends Activity
        implements PeerConnectionEvents {

    @Nullable
    private static RoomParametersFetcher roomParametersFetcher;
    @Nullable
    private PeerConnectionClient peerConnectionClient;

    protected void preparePeerConnection(EglBase eglBase) {
        if (peerConnectionClient != null)
            return;
        boolean loopback = false;
        // Create peer connection client.
        peerConnectionClient = new PeerConnectionClient(
                getApplicationContext(), eglBase,
                AppManager.getPeerConnectionParameters(loopback),
                this);
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        if (loopback) {
            options.networkIgnoreMask = 0;
        }
        peerConnectionClient.createPeerConnectionFactory(options);
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onPeerConnectionCreated(String connectionId) {

    }

    @Override
    public void onLocalDescription(SessionDescription sdp,
                                   String connectionId) {

    }

    @Override
    public void onIceCandidate(IceCandidate candidate,
                               String connectionId) {

    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates,
                                       String connectionId) {

    }

    @Override
    public void onIceConnected(String connectionId) {

    }

    @Override
    public void onIceDisconnected(String connectionId) {

    }

    @Override
    public void onPeerConnectionClosed() {

    }

    @Override
    public void onPeerConnectionStatsReady(StatsReport[] reports) {

    }

    @Override
    public void onRemoteStreamAdded(MediaStream stream,
                                    String connectionId) {

    }

    @Override
    public void onRemoteStreamRemoved(MediaStream stream,
                                      String connectionId) {

    }

    @Override
    public void onDataChannel(DataChannel dataChannel,
                              String connectionId) {

    }

    @Override
    public void onPeerConnectionError(String description) {

    }

    @Override
    public void onBufferedAmountChange(long l, DataChannel channel,
                                       String connectionId) {

    }

    @Override
    public void onStateChange(DataChannel channel,
                              String connectionId) {

    }

    @Override
    public void onMessage(DataChannel.Buffer buffer,
                          DataChannel channel,
                          String connectionId) {

    }
}
