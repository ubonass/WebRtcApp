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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A peer connection wrapper which is used by PeerConnectionClient to support multiple connectivity.
 */
public class PeerConnectionProxy
        implements PeerConnection.Observer, SdpObserver {

    private static final String TAG = "PeerConnectionProxy";
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
    private PeerConnectionEvents events;
    @Nullable
    private PeerConnectionParameters peerConnectionParameters;
    @Nullable
    private PeerConnectionClient client;
    @Nullable
    private MediaConstraints sdpMediaConstraints;
    // Queued remote ICE candidates are consumed only after both local and
    // remote descriptions are set. Similarly local ICE candidates are sent to
    // remote peer after both local and remote description are set.
    @Nullable
    private List<IceCandidate> queuedRemoteCandidates;
    @Nullable
    private String connectionId;
    //private boolean videoCallEnabled;
    private boolean isInitiator;
    private boolean isError;
    private boolean preferIsac;
    private boolean videoCallEnabled;
    public PeerConnectionProxy(
            PeerConnectionClient client,
            ExecutorService executor,
            PeerConnectionParameters params,
            PeerConnectionEvents events,String id) {
        this.client = client;
        this.events = events;
        this.preferIsac = params.audioCodec != null
                && params.audioCodec.equals(MediaConfiguration.AUDIO_CODEC_ISAC);
        this.executor = executor;
        this.isError = false;
        this.isInitiator = false;
        this.connectionId = id;
        this.videoCallEnabled =
                params.videoCallEnabled;
        this.peerConnectionParameters = params;
        this.observedDataChannels = new HashMap<>();
        this.queuedRemoteCandidates = new ArrayList<>();
    }

    public void setPeerConnection(@Nullable PeerConnection peerConnection) {
        this.peerConnection = peerConnection;
    }

    @Nullable
    public PeerConnection getPeerConnection() {
        return peerConnection;
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
            events.onBufferedAmountChange(l, channel,connectionId);

        }

        @Override
        public void onStateChange() {
            events.onStateChange(channel,connectionId);
        }

        @Override
        public void onMessage(DataChannel.Buffer buffer) {
            Log.i(TAG, "[ObservedDataChannel] NBMPeerConnection onMessage");
            events.onMessage(buffer, channel,connectionId);
        }
    }

    @Override
    public void onIceCandidate(final IceCandidate candidate) {
        Log.e(TAG,"...onIceCandidate...");
        executor.execute(() -> events.onIceCandidate(candidate,connectionId));
    }

    @Override
    public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
        Log.e(TAG,"...onIceCandidatesRemoved...");
        executor.execute(() -> events.onIceCandidatesRemoved(candidates,connectionId));
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
                events.onIceConnected(connectionId);
            } else if (newState == PeerConnection.IceConnectionState.DISCONNECTED) {
                events.onIceDisconnected(connectionId);
            } else if (newState == PeerConnection.IceConnectionState.FAILED) {
                reportError("ICE connection failed.");
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
        Log.i(TAG,"onAddStream  and stream id :" + stream.getId());
        executor.execute(() -> {
            if (peerConnection == null) {
                return;
            }
            if (stream.audioTracks.size() > 1
                    || stream.videoTracks.size() > 1) {
                reportError("Weird-looking stream:"
                        + stream);
                return;
            }
            events.onRemoteStreamAdded(stream,connectionId);
        });
    }

    @Override
    public void onRemoveStream(final MediaStream stream) {
        Log.i(TAG,"onRemoveStream....");
        executor.execute(() -> {
            if (peerConnection == null) {
                return;
            }
            if (stream.audioTracks.size() > 1
                    || stream.videoTracks.size() > 1) {
                reportError("Weird-looking stream:" + stream);
                return;
            }
            events.onRemoteStreamRemoved(stream,connectionId);
        });
    }

    @Override
    public void onDataChannel(final DataChannel dc) {
        Log.d(TAG, "New Data channel " + dc.label());
        /*if (!dataChannelEnabled)
            return;*/
        executor.execute(() -> events.onDataChannel(dc,connectionId));
    }

    @Override
    public void onRenegotiationNeeded() {
        // No need to do anything; AppRTC follows a pre-agreed-upon
        // signaling/negotiation protocol.
    }

    @Override
    public void onAddTrack(final RtpReceiver receiver,
                           final MediaStream[] mediaStreams) {
        Log.i(TAG,"onAddTrack  and stream ");
    }

    @Override
    public void onCreateSuccess(final SessionDescription origSdp) {
        Log.i(TAG,"onCreateSuccess.....");
        if (localSdp != null) {
            reportError("Multiple SDP create.");
            return;
        }
        String sdpDescription =
                origSdp.description;
        if (preferIsac) {
            sdpDescription = preferCodec(sdpDescription,
                    MediaConfiguration.AUDIO_CODEC_ISAC, true);
        }

        if (client.isVideoCallEnabled()) {
            sdpDescription =
                    preferCodec(sdpDescription,
                            PeerConnectionClient
                                    .getSdpVideoCodecName(peerConnectionParameters),
                            false);
        }
        final SessionDescription sdp =
                new SessionDescription(origSdp.type, sdpDescription);
        localSdp = sdp;
        executor.execute(() -> {
            if (peerConnection != null && !isError) {
                Log.d(TAG, "Set local SDP from " + sdp.type);
                peerConnection.setLocalDescription(this, sdp);
            }
        });
    }

    @Override
    public void onSetSuccess() {
        Log.i(TAG,"onSetSuccess...");
        executor.execute(() -> {
            if (peerConnection == null || isError) {
                Log.e(TAG,"onSetSuccess peerConnection is null");
                return;
            }
            if (isInitiator) {
                // For offering peer connection we first create offer and set
                // local SDP, then after receiving answer set remote SDP.
                if (peerConnection.getRemoteDescription() == null) {
                    // We've just set our local SDP so time to send it.
                    Log.i(TAG, "Local SDP set succesfully...");
                    events.onLocalDescription(localSdp,connectionId);
                    //下一个动作需要发送offerSdp到远程
                    //远程收到后会应答,同时会发送远程的iceCandidate到本地来
                } else {
                    // We've just set remote description, so drain remote
                    // and send local ICE candidates.
                    //Log.i(TAG,"Description:" + peerConnection.getRemoteDescription().description);
                    Log.i(TAG, "...Remote SDP set succesfully");
                    drainCandidates();
                    //events.
                }
            } else {
                // For answering peer connection we set remote SDP and then
                // create answer and set local SDP.
                if (peerConnection.getLocalDescription() != null) {
                    // We've just set our local SDP so time to send it, drain
                    // remote and send local ICE candidates.
                    Log.i(TAG, "...Local SDP set succesfully");
                    events.onLocalDescription(localSdp,connectionId);
                    drainCandidates();
                } else {
                    // We've just set remote SDP - do nothing for now -
                    // answer will be created soon.
                    Log.i(TAG, "Remote SDP set succesfully");
                }
            }
        });
    }

    @Override
    public void onCreateFailure(final String error) {
        reportError("createSDP error: " + error);
    }

    @Override
    public void onSetFailure(final String error) {
        reportError("setSDP error: " + error);
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
        for (HashMap.Entry<String, ObservedDataChannel> entry
                : observedDataChannels.entrySet()) {
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


    public void createOffer(MediaConstraints
                                    sdpMediaConstraints) {
        Log.i(TAG,"start createOffer..");
        executor.execute(() -> {
            if (peerConnection != null && !isError) {
                Log.i(TAG, "PC Create OFFER");
                isInitiator = true;
                peerConnection.createOffer(this,
                        sdpMediaConstraints);
            }
        });
    }

    public void createAnswer(MediaConstraints sdpMediaConstraints) {
        Log.i(TAG,"start createAnswer..");
        this.sdpMediaConstraints = sdpMediaConstraints;
        executor.execute(() -> {
            if (peerConnection != null && !isError) {
                Log.d(TAG, "PC create ANSWER");
                isInitiator = false;
                peerConnection.createAnswer(this, sdpMediaConstraints);
            }
        });
    }

    public void setRemoteDescription(final SessionDescription sdp) {
        executor.execute(() -> {
            if (peerConnection == null || isError) {
                return;
            }
            String sdpDescription = sdp.description;
            if (preferIsac) {
                sdpDescription = preferCodec(sdpDescription,
                        MediaConfiguration.AUDIO_CODEC_ISAC, true);
            }
            if (videoCallEnabled) {
                sdpDescription =
                        preferCodec(sdpDescription,
                                PeerConnectionClient.
                                        getSdpVideoCodecName(peerConnectionParameters),
                                false);
            }
            if (peerConnectionParameters.audioStartBitrate > 0) {
                sdpDescription = setStartBitrate(
                        MediaConfiguration.AUDIO_CODEC_OPUS,
                        false, sdpDescription,
                        peerConnectionParameters.audioStartBitrate);
            }
            //Log.d(TAG, "Set remote SDP.");
            SessionDescription sdpRemote =
                    new SessionDescription(sdp.type, sdpDescription);
            peerConnection.setRemoteDescription(this, sdpRemote);
        });
    }

    /**
     * 先清理干净？
     */
    public void drainCandidates() {
        //Log.i(TAG,"drainCandidates was been called....");
        if (queuedRemoteCandidates != null) {
            Log.d(TAG, "Add "
                    + queuedRemoteCandidates.size() + " remote candidates");
            for (IceCandidate candidate : queuedRemoteCandidates) {
                Log.i(TAG,".........addIceCandidate.....");
                peerConnection.addIceCandidate(candidate);
            }
            queuedRemoteCandidates = null;
        }
    }

    public void addRemoteIceCandidate(final IceCandidate candidate) {
        executor.execute(() -> {
            if (peerConnection != null && !isError) {
                if (queuedRemoteCandidates != null) {
                    Log.i(TAG,
                            "addRemoteIceCandidate to queuedRemoteCandidates");
                    queuedRemoteCandidates.add(candidate);
                } else {
                    Log.i(TAG,"addIceCandidate to peerConnection");
                    peerConnection.addIceCandidate(candidate);
                }
            }
        });
    }

    public void removeRemoteIceCandidates(final IceCandidate[] candidates) {
        executor.execute(() -> {
            if (peerConnection == null || isError) {
                return;
            }
            // Drain the queued remote candidates if there is any so that
            // they are processed in the proper order.
            drainCandidates();
            peerConnection.removeIceCandidates(candidates);
        });
    }

    public void reportError(final String errorMessage) {
        Log.e(TAG, "Peerconnection error: " + errorMessage);
        executor.execute(() -> {
            if (!isError) {
                events.onPeerConnectionError(errorMessage);
                isError = true;
            }
        });
    }

    public void close() {
        Log.d(TAG, "Closing peer connection.");
        if (peerConnection != null) {
            peerConnection.dispose();
            peerConnection = null;
        }
        Log.d(TAG, "Closing peer connection done.");
    }
    /**
     * Returns the line number containing "m=audio|video", or -1 if no such line exists.
     */
    private static int findMediaDescriptionLine(boolean isAudio, String[] sdpLines) {
        final String mediaDescription = isAudio ? "m=audio " : "m=video ";
        for (int i = 0; i < sdpLines.length; ++i) {
            if (sdpLines[i].startsWith(mediaDescription)) {
                return i;
            }
        }
        return -1;
    }

    private static String joinString(
            Iterable<? extends CharSequence> s, String delimiter, boolean delimiterAtEnd) {
        Iterator<? extends CharSequence> iter = s.iterator();
        if (!iter.hasNext()) {
            return "";
        }
        StringBuilder buffer = new StringBuilder(iter.next());
        while (iter.hasNext()) {
            buffer.append(delimiter).append(iter.next());
        }
        if (delimiterAtEnd) {
            buffer.append(delimiter);
        }
        return buffer.toString();
    }

    public static @Nullable
    String movePayloadTypesToFront(
            List<String> preferredPayloadTypes, String mLine) {
        // The format of the media description line should be: m=<media> <port> <proto> <fmt> ...
        final List<String> origLineParts = Arrays.asList(mLine.split(" "));
        if (origLineParts.size() <= 3) {
            Log.e(TAG, "Wrong SDP media description format: " + mLine);
            return null;
        }
        final List<String> header = origLineParts.subList(0, 3);
        final List<String> unpreferredPayloadTypes =
                new ArrayList<>(origLineParts.subList(3, origLineParts.size()));
        unpreferredPayloadTypes.removeAll(preferredPayloadTypes);
        // Reconstruct the line with |preferredPayloadTypes| moved to the beginning of the payload
        // types.
        final List<String> newLineParts = new ArrayList<>();
        newLineParts.addAll(header);
        newLineParts.addAll(preferredPayloadTypes);
        newLineParts.addAll(unpreferredPayloadTypes);
        return joinString(newLineParts, " ", false /* delimiterAtEnd */);
    }

    public static String preferCodec(String sdpDescription, String codec, boolean isAudio) {
        final String[] lines = sdpDescription.split("\r\n");
        final int mLineIndex = findMediaDescriptionLine(isAudio, lines);
        if (mLineIndex == -1) {
            Log.w(TAG, "No mediaDescription line, so can't prefer " + codec);
            return sdpDescription;
        }
        // A list with all the payload types with name |codec|. The payload types are integers in the
        // range 96-127, but they are stored as strings here.
        final List<String> codecPayloadTypes = new ArrayList<>();
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        final Pattern codecPattern = Pattern.compile("^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$");
        for (String line : lines) {
            Matcher codecMatcher = codecPattern.matcher(line);
            if (codecMatcher.matches()) {
                codecPayloadTypes.add(codecMatcher.group(1));
            }
        }
        if (codecPayloadTypes.isEmpty()) {
            Log.w(TAG, "No payload types with name " + codec);
            return sdpDescription;
        }

        final String newMLine = movePayloadTypesToFront(codecPayloadTypes, lines[mLineIndex]);
        if (newMLine == null) {
            return sdpDescription;
        }
        Log.d(TAG, "Change media description from: " + lines[mLineIndex] + " to " + newMLine);
        lines[mLineIndex] = newMLine;
        return joinString(Arrays.asList(lines), "\r\n", true /* delimiterAtEnd */);
    }

    @SuppressWarnings("StringSplitter")
    public static String setStartBitrate(
            String codec, boolean isVideoCodec, String sdpDescription, int bitrateKbps) {
        String[] lines = sdpDescription.split("\r\n");
        int rtpmapLineIndex = -1;
        boolean sdpFormatUpdated = false;
        String codecRtpMap = null;
        // Search for codec rtpmap in format
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
        Pattern codecPattern = Pattern.compile(regex);
        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                codecRtpMap = codecMatcher.group(1);
                rtpmapLineIndex = i;
                break;
            }
        }
        if (codecRtpMap == null) {
            Log.w(TAG, "No rtpmap for " + codec + " codec");
            return sdpDescription;
        }
        Log.d(TAG, "Found " + codec + " rtpmap " + codecRtpMap + " at " + lines[rtpmapLineIndex]);

        // Check if a=fmtp string already exist in remote SDP for this codec and
        // update it with new bitrate parameter.
        regex = "^a=fmtp:" + codecRtpMap + " \\w+=\\d+.*[\r]?$";
        codecPattern = Pattern.compile(regex);
        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                Log.d(TAG, "Found " + codec + " " + lines[i]);
                if (isVideoCodec) {
                    lines[i] += "; " + MediaConfiguration.VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
                } else {
                    lines[i] += "; " + MediaConfiguration.AUDIO_CODEC_PARAM_BITRATE + "=" + (bitrateKbps * 1000);
                }
                Log.d(TAG, "Update remote SDP line: " + lines[i]);
                sdpFormatUpdated = true;
                break;
            }
        }

        StringBuilder newSdpDescription = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            newSdpDescription.append(lines[i]).append("\r\n");
            // Append new a=fmtp line if no such line exist for a codec.
            if (!sdpFormatUpdated && i == rtpmapLineIndex) {
                String bitrateSet;
                if (isVideoCodec) {
                    bitrateSet =
                            "a=fmtp:" + codecRtpMap + " " + MediaConfiguration.VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
                } else {
                    bitrateSet = "a=fmtp:" + codecRtpMap + " " + MediaConfiguration.AUDIO_CODEC_PARAM_BITRATE + "="
                            + (bitrateKbps * 1000);
                }
                Log.d(TAG, "Add remote SDP line: " + bitrateSet);
                newSdpDescription.append(bitrateSet).append("\r\n");
            }
        }
        return newSdpDescription.toString();
    }
}
