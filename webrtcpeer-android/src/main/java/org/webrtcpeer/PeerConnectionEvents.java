package org.webrtcpeer;

import org.webrtc.*;

public interface PeerConnectionEvents {

    /**
     * Callback fired once peer connection is created.
     */
    void onPeerConnectionCreated(String connectionId);

    /**
     * Callback fired once local SDP is created and set.
     */
    void onLocalDescription(final SessionDescription sdp,String connectionId);

    /**
     * Callback fired once local Ice candidate is generated.
     */
    void onIceCandidate(final IceCandidate candidate,String connectionId);

    /**
     * Callback fired once local ICE candidates are removed.
     */
    void onIceCandidatesRemoved(final IceCandidate[] candidates,String connectionId);

    /**
     * Callback fired once connection is established (IceConnectionState is
     * CONNECTED).
     */
    void onIceConnected(String connectionId);

    /**
     * Callback fired once connection is closed (IceConnectionState is
     * DISCONNECTED).
     */
    void onIceDisconnected(String connectionId);

    /**
     * Callback fired once peer connection is closed.
     */
    void onPeerConnectionClosed();

    /**
     * Callback fired once peer connection statistics is ready.
     */
    void onPeerConnectionStatsReady(final StatsReport[] reports);

    /**
     * WebRTC event which is triggered when A new remote stream is added to connection
     *
     * @param stream The new remote media stream
     */
    void onRemoteStreamAdded(MediaStream stream,String connectionId);

    /**
     * WebRTC event which is triggered when a remote media stream is terminated
     *
     * @param stream The removed remote media stream
     */
    void onRemoteStreamRemoved(MediaStream stream,String connectionId);

    /**
     * WebRTC event which is triggered when peer opens a data channel
     *
     * @param dataChannel The data channel
     */
    void onDataChannel(DataChannel dataChannel,String connectionId);

    /**
     * Callback fired once peer connection error happened.
     */
    void onPeerConnectionError(final String description);

    /**
     * WebRTC event which is triggered when a data channel buffer amount has changed
     * @param l The previous amount
     * @param channel The data channel which triggered the event
     */
    void onBufferedAmountChange(long l, DataChannel channel,String connectionId);

    /**
     * WebRTC event which is triggered when a data channel state has changed. Possible values:
     * DataChannel.State { CONNECTING, OPEN, CLOSING, CLOSED };
     * @param channel The data channel which triggered the event
     */
    void onStateChange(DataChannel channel,String connectionId);

    /**
     * WebRTC event which is triggered when a message is received from a data channel
     * @param buffer The message buffer
     * @param channel The data channel which triggered the event
     */
    void onMessage(DataChannel.Buffer buffer, DataChannel channel,String connectionId);
}
