/*
 * (C) Copyright 2016 VTT (http://www.vtt.fi)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.webrtcpeer;

import android.util.Log;
import org.webrtc.*;

import java.util.*;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nullable;

import android.util.Log;

import org.webrtc.*;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.webrtcpeer.PeerConnectionClient.PeerConnectionParameters;

/**
 * A peer connection wrapper which is used by PeerConnectionClient to support multiple connectivity.
 */
public class PeerConnectionObserver
        implements PeerConnection.Observer, SdpObserver {

    private static final String TAG = "PeerConnectionObserver";
    private static final String VIDEO_CODEC_PARAM_START_BITRATE =
            "x-google-start-bitrate";
    private static final String AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate";

    @Nullable
    private PeerConnection peerConnection;
    @Nullable
    private ExecutorService executor;
    @Nullable
    private SessionDescription localSdp; // either offer or answer SDP
    @Nullable
    private HashMap<String, ObservedDataChannel> observedDataChannels;
    @Nullable
    private ArrayList<IceCandidate> queuedRemoteCandidates;
    @Nullable
    private PeerConnectionEvents events;
    @Nullable
    private PeerConnectionParameters peerConnectionParameters;
    @Nullable
    private PeerConnectionClient client;
    private boolean preferIsac;

    //private boolean videoCallEnabled;
    //private boolean isInitiator;
    //private boolean isError;

    public PeerConnectionObserver(
            PeerConnectionClient client,
            ExecutorService executor,
            PeerConnectionParameters params,
            PeerConnectionEvents events) {
        this.client = client;
        this.events = events;
        this.preferIsac = params.audioCodec != null
                && params.audioCodec.equals(MediaConfiguration.AUDIO_CODEC_ISAC);
        /*this.videoCallEnabled =
                params.videoCallEnabled;*/
        this.executor = executor;
        //this.isInitiator = false;
        this.peerConnectionParameters = params;
        this.queuedRemoteCandidates = new ArrayList<>();
        this.observedDataChannels = new HashMap<>();
    }

    public void setPeerConnection(@Nullable PeerConnection peerConnection) {
        this.peerConnection = peerConnection;
    }


    /* This private class exists to receive per-channel events and forward them to upper layers
       with the channel instance
      */
    private class ObservedDataChannel implements DataChannel.Observer {
        private DataChannel channel;

        public ObservedDataChannel(String label, DataChannel.Init init) {
            channel = peerConnection.createDataChannel(label, init);
            if (channel != null) {
                channel.registerObserver(this);
                Log.i(TAG, "Created data channel with Id: " + label);
            } else {
                Log.e(TAG, "Failed to create data channel with Id: " + label);
            }
        }

        public DataChannel getChannel() {
            return channel;
        }

        @Override
        public void onBufferedAmountChange(long l) {
            Log.i(TAG, "[ObservedDataChannel] NBMPeerConnection onBufferedAmountChange");
            events.onBufferedAmountChange(l, channel);

        }

        @Override
        public void onStateChange() {
            events.onStateChange(channel);
        }

        @Override
        public void onMessage(DataChannel.Buffer buffer) {
            Log.i(TAG, "[ObservedDataChannel] NBMPeerConnection onMessage");
            events.onMessage(buffer, channel);
        }
    }

    @Override
    public void onIceCandidate(final IceCandidate candidate) {
        executor.execute(() -> events.onIceCandidate(candidate));
    }

    @Override
    public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
        executor.execute(() -> events.onIceCandidatesRemoved(candidates));
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState newState) {
        Log.d(TAG, "SignalingState: " + newState);
    }

    @Override
    public void onIceConnectionChange(final PeerConnection.IceConnectionState newState) {
        executor.execute(() -> {
            Log.d(TAG, "IceConnectionState: " + newState);
            if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                events.onIceConnected();
            } else if (newState == PeerConnection.IceConnectionState.DISCONNECTED) {
                events.onIceDisconnected();
            } else if (newState == PeerConnection.IceConnectionState.FAILED) {
                client.reportError("ICE connection failed.");
            }
        });
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
        Log.d(TAG, "IceGatheringState: " + newState);
    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {
        Log.d(TAG, "IceConnectionReceiving changed to " + receiving);
    }

    @Override
    public void onAddStream(final MediaStream stream) {
        executor.execute(() -> {
            if (peerConnection == null) {
                return;
            }
            if (stream.audioTracks.size() > 1
                    || stream.videoTracks.size() > 1) {
                client.reportError("Weird-looking stream:" + stream);
                return;
            }
            events.onRemoteStreamAdded(stream);
        });
    }

    @Override
    public void onRemoveStream(final MediaStream stream) {
        executor.execute(() -> {
            if (peerConnection == null) {
                return;
            }
            if (stream.audioTracks.size() > 1
                    || stream.videoTracks.size() > 1) {
                client.reportError("Weird-looking stream:" + stream);
                return;
            }
            events.onRemoteStreamRemoved(stream);
        });
    }

    @Override
    public void onDataChannel(final DataChannel dc) {
        Log.d(TAG, "New Data channel " + dc.label());
        /*if (!dataChannelEnabled)
            return;*/
        executor.execute(() -> events.onDataChannel(dc));
    }

    @Override
    public void onRenegotiationNeeded() {
        // No need to do anything; AppRTC follows a pre-agreed-upon
        // signaling/negotiation protocol.
    }

    @Override
    public void onAddTrack(final RtpReceiver receiver,
                           final MediaStream[] mediaStreams) {

    }

    @Override
    public void onCreateSuccess(final SessionDescription origSdp) {
        if (localSdp != null) {
            client.reportError("Multiple SDP create.");
            return;
        }
        String sdpDescription =
                origSdp.description;
        if (preferIsac) {
            sdpDescription = PeerConnectionClient.preferCodec(sdpDescription,
                    MediaConfiguration.AUDIO_CODEC_ISAC, true);
        }

        if (client.isVideoCallEnabled()) {
            sdpDescription =
                    PeerConnectionClient.preferCodec(sdpDescription,
                            PeerConnectionClient
                                    .getSdpVideoCodecName(peerConnectionParameters),
                            false);
        }
        final SessionDescription sdp =
                new SessionDescription(origSdp.type, sdpDescription);
        localSdp = sdp;
        executor.execute(() -> {
            if (peerConnection != null && !client.isError()) {
                Log.d(TAG, "Set local SDP from " + sdp.type);
                peerConnection.setLocalDescription(this, sdp);
            }
        });
    }

    @Override
    public void onSetSuccess() {
        executor.execute(() -> {
            if (peerConnection == null || client.isError()) {
                return;
            }
            if (client.isInitiator()) {
                // For offering peer connection we first create offer and set
                // local SDP, then after receiving answer set remote SDP.
                if (peerConnection.getRemoteDescription() == null) {
                    // We've just set our local SDP so time to send it.
                    Log.d(TAG, "Local SDP set succesfully");
                    events.onLocalDescription(localSdp);
                } else {
                    // We've just set remote description, so drain remote
                    // and send local ICE candidates.
                    Log.d(TAG, "Remote SDP set succesfully");
                    client.drainCandidates();
                }
            } else {
                // For answering peer connection we set remote SDP and then
                // create answer and set local SDP.
                if (peerConnection.getLocalDescription() != null) {
                    // We've just set our local SDP so time to send it, drain
                    // remote and send local ICE candidates.
                    Log.d(TAG, "Local SDP set succesfully");
                    events.onLocalDescription(localSdp);
                    client.drainCandidates();
                } else {
                    // We've just set remote SDP - do nothing for now -
                    // answer will be created soon.
                    Log.d(TAG, "Remote SDP set succesfully");
                }
            }
        });
    }

    @Override
    public void onCreateFailure(final String error) {
        client.reportError("createSDP error: " + error);
    }

    @Override
    public void onSetFailure(final String error) {
        client.reportError("setSDP error: " + error);
    }

    public DataChannel createDataChannel(String label,
                                         DataChannel.Init init) {
        ObservedDataChannel dataChannel =
                new ObservedDataChannel(label, init);
        observedDataChannels.put(label, dataChannel);
        return dataChannel.getChannel();
    }

    @SuppressWarnings("unused")
    public HashMap<String, DataChannel> getDataChannels() {
        HashMap<String, DataChannel> channels = new HashMap<>();
        for (HashMap.Entry<String, ObservedDataChannel> entry : observedDataChannels.entrySet()) {
            String key = entry.getKey();
            ObservedDataChannel value = entry.getValue();
            channels.put(key, value.getChannel());
        }
        return channels;
    }

    public DataChannel getDataChannel(String dataChannelId) {
        ObservedDataChannel channel =
                this.observedDataChannels.get(dataChannelId);
        if (channel == null) {
            return null;
        } else {
            return channel.getChannel();
        }
    }

}
