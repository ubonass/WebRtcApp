/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
package org.webrtcpeer;

import android.content.Context;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;

import org.webrtc.*;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule.AudioRecordErrorCallback;
import org.webrtc.audio.JavaAudioDeviceModule.AudioTrackErrorCallback;
import org.webrtcpeer.AppRTCClient.SignalingParameters;

public class PeerConnectionClient {

    private static final String TAG = "PCRTCClient";
    private static final int HD_VIDEO_WIDTH = 1280;
    private static final int HD_VIDEO_HEIGHT = 720;
    private static final int BPS_IN_KBPS = 1000;
    private static final String RTCEVENTLOG_OUTPUT_DIR_NAME = "rtc_event_log";
    // Executor thread is started once in private ctor and is used for all
    // peer connection API calls to ensure new peer connection factory is
    // created on the same thread as previously destroyed factory.
    private static final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    //private final Timer statsTimer = new Timer();
    @Nullable
    protected final EglBase rootEglBase;
    @Nullable
    protected final Context appContext;
    @Nullable
    protected final PeerConnectionParameters peerConnectionParameters;
    @Nullable
    protected final PeerConnectionEvents events;
    @Nullable
    protected PeerConnectionFactory factory;

    @Nullable
    private AudioSource audioSource;
    @Nullable
    private SurfaceTextureHelper surfaceTextureHelper;
    @Nullable
    private VideoSource videoSource;

    private boolean videoCapturerStopped;
    @Nullable
    private VideoSink localRender;
    /*@Nullable
    private List<VideoSink> remoteSinks;*/
    @Nullable
    private SignalingParameters signalingParameters;
    private int videoWidth;
    private int videoHeight;
    private int videoFps;
    @Nullable
    private MediaConstraints audioConstraints;
    @Nullable
    private MediaConstraints sdpMediaConstraints;

    //@Nullable
    //private SessionDescription localSdp; // either offer or answer SDP
    @Nullable
    private VideoCapturer videoCapturer;
    // enableVideo is set to true if video should be rendered and sent.
    private boolean renderVideo = true;
    @Nullable
    private VideoTrack localVideoTrack;
    /*@Nullable
    private VideoTrack remoteVideoTrack;*/
    @Nullable
    private RtpSender localVideoSender;
    // enableAudio is set to true if audio should be sent.
    private boolean enableAudio = true;
    @Nullable
    private AudioTrack localAudioTrack;
    @Nullable
    private DataChannel dataChannel;
    private final boolean dataChannelEnabled;
    // Enable RtcEventLog.
    //@Nullable
    //private RtcEventLog rtcEventLog;
    // Implements the WebRtcAudioRecordSamplesReadyCallback interface and writes
    // recorded audio samples to an output file.
    @Nullable
    private RecordedAudioToFileController saveRecordedAudioToFile;
    @Nullable
    private Map<String, PeerConnectionProxy> connections =
            new HashMap<>();

    @Nullable
    private String localConnectionId;
    /**
     * peerConnection for local peer
     */
    @Nullable
    private PeerConnection localPeerConnection;


    /**
     * Create a PeerConnectionClient with the specified parameters. PeerConnectionClient takes
     * ownership of |eglBase|.
     */
    public PeerConnectionClient(Context appContext,
                                EglBase eglBase,
                                PeerConnectionParameters peerConnectionParameters,
                                PeerConnectionEvents events) {
        this.rootEglBase = eglBase;
        this.appContext = appContext;
        this.events = events;
        this.peerConnectionParameters =
                peerConnectionParameters;
        this.dataChannelEnabled =
                peerConnectionParameters.dataChannelParameters != null;
        //this.executor.requestStart();
        Log.d(TAG, "Preferred video codec: "
                + getSdpVideoCodecName(peerConnectionParameters));
        final String fieldTrials = getFieldTrials(peerConnectionParameters);
        executor.execute(() -> {
            Log.d(TAG, "Initialize WebRTC. Field trials: " + fieldTrials);
            PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(appContext)
                            .setFieldTrials(fieldTrials)
                            .setEnableInternalTracer(true)
                            .createInitializationOptions());
        });
    }

    /**
     * This function should only be called once.
     */
    public void createPeerConnectionFactory(PeerConnectionFactory.Options options) {
        if (factory != null) {
            throw new IllegalStateException("PeerConnectionFactory has already been constructed");
        }
        executor.execute(() -> createPeerConnectionFactoryInternal(options));
    }

    /*public void createPeerConnection(final VideoSink localRender,
                                     final VideoSink remoteSink,
                                     final VideoCapturer videoCapturer,
                                     final SignalingParameters signalingParameters,
                                     final String connectionId) {
        if (peerConnectionParameters.videoCallEnabled && videoCapturer == null) {
            Log.w(TAG, "Video call enabled but no video capturer provided.");
        }
        createPeerConnection(
                localRender, Collections.singletonList(remoteSink),
                videoCapturer, signalingParameters,connectionId);
    }*/

    public void createRemotePeerConnection(final SignalingParameters signalingParameters,
                                     final String connectionId) {
        if (peerConnectionParameters.videoCallEnabled
                && videoCapturer == null
                || localRender == null ) {
            Log.e(TAG, "must create local Peerconnection first");
            return;
        }
        createPeerConnectionInternal(false,
                connectionId);
        /*executor.execute(() -> {
            try {
                createPeerConnectionInternal(false,
                        connectionId);
            } catch (Exception e) {
                events.onPeerConnectionError(
                        "Failed to create peer connection: " + e.getMessage());
                throw e;
            }
        });*/
    }

    /**
     * for Local PeerConnection create......
     * @param localRender
     * @param videoCapturer
     * @param signalingParameters
     * @param connectionId
     */
    public void createPeerConnection(final VideoSink localRender,
                                     /*final List<VideoSink> remoteSinks,*/
                                     final VideoCapturer videoCapturer,
                                     final SignalingParameters signalingParameters,
                                     final String connectionId) {
        if (peerConnectionParameters == null) {
            Log.e(TAG, "Creating peer connection without initializing factory.");
            return;
        }
        this.localRender = localRender;
        //this.remoteSinks = remoteSinks;
        this.videoCapturer = videoCapturer;
        this.signalingParameters = signalingParameters;
        this.localConnectionId = connectionId;
        //createMediaConstraintsInternal();
        //createPeerConnectionInternal(true, connectionId);
        executor.execute(() -> {
            try {
                createMediaConstraintsInternal();
                createPeerConnectionInternal(true,
                        connectionId);
                //maybeCreateAndStartRtcEventLog();
            } catch (Exception e) {
                events.onPeerConnectionError(
                        "Failed to create peer connection: " + e.getMessage());
                throw e;
            }
        });
    }


    public void close() {
        executor.execute(this::closeInternal);
    }

    public boolean isVideoCallEnabled() {
        return peerConnectionParameters.videoCallEnabled
                && videoCapturer != null;
    }

    private void createPeerConnectionFactoryInternal(
            PeerConnectionFactory.Options options) {

        if (peerConnectionParameters.tracing) {
            PeerConnectionFactory.startInternalTracingCapture(
                    Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator
                            + "webrtc-trace.txt");
        }

        // It is possible to save a copy in raw PCM format on a file by checking
        // the "Save input audio to file" checkbox in the Settings UI. A callback
        // interface is set when this flag is enabled. As a result, a copy of recorded
        // audio samples are provided to this client directly from the native audio
        // layer in Java.
        if (peerConnectionParameters.saveInputAudioToFile) {
            if (!peerConnectionParameters.useOpenSLES) {
                Log.d(TAG, "Enable recording of microphone input audio to file");
                saveRecordedAudioToFile =
                        new RecordedAudioToFileController(executor);
            } else {
                // TODO(henrika): ensure that the UI reflects that if OpenSL ES is selected,
                // then the "Save inut audio to file" option shall be grayed out.
                Log.e(TAG, "Recording of input audio is not supported for OpenSL ES");
            }
        }

        final AudioDeviceModule adm = createJavaAudioDevice();

        // Create peer connection factory.
        if (options != null) {
            Log.d(TAG, "Factory networkIgnoreMask option: " + options.networkIgnoreMask);
        }
        final boolean enableH264HighProfile =
                MediaConfiguration.VIDEO_CODEC_H264_HIGH
                        .equals(peerConnectionParameters.videoCodec);
        final VideoEncoderFactory encoderFactory;
        final VideoDecoderFactory decoderFactory;

        if (peerConnectionParameters.videoCodecHwAcceleration) {
            encoderFactory = new DefaultVideoEncoderFactory(
                    rootEglBase.getEglBaseContext(), true /* enableIntelVp8Encoder */, enableH264HighProfile);
            decoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
        } else {
            encoderFactory = new SoftwareVideoEncoderFactory();
            decoderFactory = new SoftwareVideoDecoderFactory();
        }

        factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
        Log.d(TAG, "Peer connection factory created.");
        adm.release();
    }

    AudioDeviceModule createJavaAudioDevice() {
        // Enable/disable OpenSL ES playback.
        if (!peerConnectionParameters.useOpenSLES) {
            Log.w(TAG, "External OpenSLES ADM not implemented yet.");
            // TODO(magjed): Add support for external OpenSLES ADM.
        }

        // Set audio record error callbacks.
        AudioRecordErrorCallback audioRecordErrorCallback = new AudioRecordErrorCallback() {
            @Override
            public void onWebRtcAudioRecordInitError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioRecordInitError: " + errorMessage);
                events.onPeerConnectionError(errorMessage);
            }

            @Override
            public void onWebRtcAudioRecordStartError(
                    JavaAudioDeviceModule.AudioRecordStartErrorCode errorCode, String errorMessage) {
                Log.e(TAG, "onWebRtcAudioRecordStartError: " + errorCode + ". " + errorMessage);
                events.onPeerConnectionError(errorMessage);
            }

            @Override
            public void onWebRtcAudioRecordError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioRecordError: " + errorMessage);
                events.onPeerConnectionError(errorMessage);
            }
        };

        AudioTrackErrorCallback audioTrackErrorCallback = new AudioTrackErrorCallback() {
            @Override
            public void onWebRtcAudioTrackInitError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackInitError: " + errorMessage);
                events.onPeerConnectionError(errorMessage);
            }

            @Override
            public void onWebRtcAudioTrackStartError(
                    JavaAudioDeviceModule.AudioTrackStartErrorCode errorCode, String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackStartError: " + errorCode + ". " + errorMessage);
                events.onPeerConnectionError(errorMessage);
            }

            @Override
            public void onWebRtcAudioTrackError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackError: " + errorMessage);
                events.onPeerConnectionError(errorMessage);
            }
        };

        return JavaAudioDeviceModule.builder(appContext)
                .setSamplesReadyCallback(saveRecordedAudioToFile)
                .setUseHardwareAcousticEchoCanceler(!peerConnectionParameters.disableBuiltInAEC)
                .setUseHardwareNoiseSuppressor(!peerConnectionParameters.disableBuiltInNS)
                .setAudioRecordErrorCallback(audioRecordErrorCallback)
                .setAudioTrackErrorCallback(audioTrackErrorCallback)
                .createAudioDeviceModule();
    }

    private void createMediaConstraintsInternal() {
        // Create video constraints if video call is enabled.
        if (isVideoCallEnabled()) {
            videoWidth = peerConnectionParameters.videoWidth;
            videoHeight = peerConnectionParameters.videoHeight;
            videoFps = peerConnectionParameters.videoFps;

            // If video resolution is not specified, default to HD.
            if (videoWidth == 0 || videoHeight == 0) {
                videoWidth = HD_VIDEO_WIDTH;
                videoHeight = HD_VIDEO_HEIGHT;
            }

            // If fps is not specified, default to 30.
            if (videoFps == 0) {
                videoFps = 30;
            }
            Logging.d(TAG, "Capturing format: " +
                    videoWidth + "x" + videoHeight + "@" + videoFps);
        }

        // Create audio constraints.
        audioConstraints = new MediaConstraints();
        // added for audio performance measurements
        if (peerConnectionParameters.noAudioProcessing) {
            Log.d(TAG, "Disabling audio processing");
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(
                            MediaConfiguration.AUDIO_ECHO_CANCELLATION_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(
                            MediaConfiguration.AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(
                            MediaConfiguration.AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(
                            MediaConfiguration.AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "false"));
        }
        // Create SDP constraints.
        sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo", Boolean.toString(isVideoCallEnabled())));
    }

    private void createPeerConnectionInternal(boolean localPeer,
                                              String connectionId) {
        if (factory == null) {
            Log.e(TAG, "Peerconnection factory is not created");
            return;
        }
        Log.d(TAG, "Create peer connection.");
        PeerConnectionProxy peerConnectionProxy = connections.get(connectionId);
        if(peerConnectionProxy != null) return;

        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(signalingParameters.iceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.RELAY;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy =
                PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        // Enable DTLS for normal calls and disable for loopback calls.
        rtcConfig.enableDtlsSrtp = !peerConnectionParameters.loopback;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        peerConnectionProxy =
                new PeerConnectionProxy(this,
                        executor, peerConnectionParameters, events,connectionId);
        PeerConnection peerConnection =
                factory.createPeerConnection(rtcConfig, peerConnectionProxy);
        peerConnectionProxy.setPeerConnection(peerConnection);
        connections.put(connectionId, peerConnectionProxy);

        if (dataChannelEnabled) {
            DataChannel.Init init = new DataChannel.Init();
            init.ordered = peerConnectionParameters.dataChannelParameters.ordered;
            init.negotiated = peerConnectionParameters.dataChannelParameters.negotiated;
            init.maxRetransmits = peerConnectionParameters.dataChannelParameters.maxRetransmits;
            init.maxRetransmitTimeMs = peerConnectionParameters.dataChannelParameters.maxRetransmitTimeMs;
            init.id = peerConnectionParameters.dataChannelParameters.id;
            init.protocol = peerConnectionParameters.dataChannelParameters.protocol;
            dataChannel = peerConnectionProxy.createDataChannel("ApprtcDemo data", init);
            //peerConnection.createDataChannel("ApprtcDemo data", init);
        }
        if (localPeer) {
            localPeerConnection = peerConnection;
            createMediaStream(peerConnectionProxy);
            events.onPeerConnectionCreated(connectionId);
        }
        /*peerConnectionProxy
                .createOffer(sdpMediaConstraints);*/
        Log.d(TAG, "Peer connection created.");
        //events.onPeerConnectionCreated(connectionId);
    }


    private void createMediaStream(PeerConnectionProxy proxy) {
        if (proxy == null)
            return;
        PeerConnection peerConnection =
                proxy.getPeerConnection();
        if (peerConnection == null)
            return;
        // Set INFO libjingle logging.
        // NOTE: this _must_ happen while |factory| is alive!
        Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO);
        List<String> mediaStreamLabels = Collections.singletonList("ARDAMS");
        if (isVideoCallEnabled()) {
            peerConnection.addTrack(createVideoTrack(videoCapturer), mediaStreamLabels);
            // We can add the renderers right away because we don't need to wait for an
            // answer to get the remote track.
            /*remoteVideoTrack = getRemoteVideoTrack();
            if (remoteVideoTrack != null) {
                remoteVideoTrack.setEnabled(renderVideo);
                for (VideoSink remoteSink : remoteSinks) {
                    remoteVideoTrack.addSink(remoteSink);
                }
            }*/
        }
        peerConnection.addTrack(createAudioTrack(), mediaStreamLabels);
        if (isVideoCallEnabled()) {
            findVideoSender(peerConnection);
        }

        if (peerConnectionParameters.aecDump) {
            try {
                ParcelFileDescriptor aecDumpFileDescriptor =
                        ParcelFileDescriptor.open(new File(Environment.getExternalStorageDirectory().getPath()
                                        + File.separator + "Download/audio.aecdump"),
                                ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE
                                        | ParcelFileDescriptor.MODE_TRUNCATE);
                factory.startAecDump(aecDumpFileDescriptor.detachFd(), -1);
            } catch (IOException e) {
                Log.e(TAG, "Can not open aecdump file", e);
            }
        }

        if (saveRecordedAudioToFile != null) {
            if (saveRecordedAudioToFile.start()) {
                Log.d(TAG, "Recording input audio to file is activated");
            }
        }
    }

    private void closeInternal() {
        if (factory != null && peerConnectionParameters.aecDump) {
            factory.stopAecDump();
        }
        Log.d(TAG, "Closing peer connection.");
        //statsTimer.cancel();
        if (dataChannel != null) {
            dataChannel.dispose();
            dataChannel = null;
        }
        closeAllConnections();//modify by jeffrey...

        Log.d(TAG, "Closing audio source.");
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }
        Log.d(TAG, "Stopping capture.");
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            videoCapturerStopped = true;
            videoCapturer.dispose();
            videoCapturer = null;
        }
        Log.d(TAG, "Closing video source.");
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
        if (saveRecordedAudioToFile != null) {
            Log.d(TAG, "Closing audio file for recorded input audio.");
            saveRecordedAudioToFile.stop();
            saveRecordedAudioToFile = null;
        }
        localRender = null;
        //remoteSinks = null;
        Log.d(TAG, "Closing peer connection factory.");
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
        rootEglBase.release();
        Log.d(TAG, "Closing peer connection done.");
        events.onPeerConnectionClosed();
        PeerConnectionFactory.stopInternalTracingCapture();
        PeerConnectionFactory.shutdownInternalTracer();
    }

    public boolean isHDVideo() {
        return isVideoCallEnabled() && videoWidth * videoHeight >= 1280 * 720;
    }

    public void setAudioEnabled(final boolean enable) {
        executor.execute(() -> {
            enableAudio = enable;
            if (localAudioTrack != null) {
                localAudioTrack.setEnabled(enableAudio);
            }
        });
    }

    public void setVideoEnabled(final boolean enable) {
        executor.execute(() -> {
            renderVideo = enable;
            if (localVideoTrack != null) {
                localVideoTrack.setEnabled(renderVideo);
            }
            /*if (remoteVideoTrack != null) {
                remoteVideoTrack.setEnabled(renderVideo);
            }*/
        });
    }

    public void stopVideoSource() {
        executor.execute(() -> {
            if (videoCapturer != null && !videoCapturerStopped) {
                Log.d(TAG, "Stop video source.");
                try {
                    videoCapturer.stopCapture();
                } catch (InterruptedException e) {
                }
                videoCapturerStopped = true;
            }
        });
    }

    public void startVideoSource() {
        executor.execute(() -> {
            if (videoCapturer != null && videoCapturerStopped) {
                Log.d(TAG, "Restart video source.");
                videoCapturer.startCapture(videoWidth, videoHeight, videoFps);
                videoCapturerStopped = false;
            }
        });
    }

    public void setVideoMaxBitrate(@Nullable final Integer maxBitrateKbps) {
        executor.execute(() -> {
            if (localPeerConnection == null || localVideoSender == null) {
                return;
            }
            Log.d(TAG, "Requested max video bitrate: " + maxBitrateKbps);
            if (localVideoSender == null) {
                Log.w(TAG, "Sender is not ready.");
                return;
            }

            RtpParameters parameters = localVideoSender.getParameters();
            if (parameters.encodings.size() == 0) {
                Log.w(TAG, "RtpParameters are not ready.");
                return;
            }

            for (RtpParameters.Encoding encoding
                    : parameters.encodings) {
                // Null value means no limit.
                encoding.maxBitrateBps = maxBitrateKbps ==
                        null ? null : maxBitrateKbps * BPS_IN_KBPS;
            }
            if (!localVideoSender.setParameters(parameters)) {
                Log.e(TAG, "RtpSender.setParameters failed.");
            }
            Log.d(TAG, "Configured max video bitrate to: " + maxBitrateKbps);
        });
    }

    @Nullable
    private AudioTrack createAudioTrack() {
        audioSource = factory.createAudioSource(audioConstraints);
        localAudioTrack = factory.createAudioTrack(
                MediaConfiguration.AUDIO_TRACK_ID, audioSource);
        localAudioTrack.setEnabled(enableAudio);
        return localAudioTrack;
    }

    @Nullable
    private VideoTrack createVideoTrack(VideoCapturer capturer) {
        surfaceTextureHelper =
                SurfaceTextureHelper.create("CaptureThread",
                        rootEglBase.getEglBaseContext());
        videoSource = factory.createVideoSource(capturer.isScreencast());
        capturer.initialize(surfaceTextureHelper, appContext, videoSource.getCapturerObserver());
        capturer.startCapture(videoWidth, videoHeight, videoFps);

        localVideoTrack = factory.createVideoTrack(MediaConfiguration.VIDEO_TRACK_ID, videoSource);
        localVideoTrack.setEnabled(renderVideo);
        localVideoTrack.addSink(localRender);
        return localVideoTrack;
    }

    private void findVideoSender(PeerConnection peerConnection) {
        for (RtpSender sender : peerConnection.getSenders()) {
            if (sender.track() != null) {
                String trackType = sender.track().kind();
                if (trackType.equals(MediaConfiguration.VIDEO_TRACK_TYPE)) {
                    Log.d(TAG, "Found video sender.");
                    localVideoSender = sender;
                }
            }
        }
    }

    // Returns the remote VideoTrack, assuming there is only one.
    public  @Nullable
    VideoTrack getRemoteVideoTrack(
            String connectionId) {
        PeerConnectionProxy proxy =
                getConnection(connectionId);
        if (proxy == null) return null;
        PeerConnection peerConnection =
                proxy.getPeerConnection();
        for (RtpTransceiver transceiver :
                peerConnection.getTransceivers()) {
            MediaStreamTrack track =
                    transceiver.getReceiver().track();
            if (track instanceof VideoTrack) {
                //Log.i(TAG,"found VideoTrack.....");
                return (VideoTrack) track;
            }
        }
        return null;
    }

    public static String getSdpVideoCodecName(
            PeerConnectionParameters parameters) {
        switch (parameters.videoCodec) {
            case MediaConfiguration.VIDEO_CODEC_VP8:
                return MediaConfiguration.VIDEO_CODEC_VP8;
            case MediaConfiguration.VIDEO_CODEC_VP9:
                return MediaConfiguration.VIDEO_CODEC_VP9;
            case MediaConfiguration.VIDEO_CODEC_H264_HIGH:
            case MediaConfiguration.VIDEO_CODEC_H264_BASELINE:
                return MediaConfiguration.VIDEO_CODEC_H264;
            default:
                return MediaConfiguration.VIDEO_CODEC_VP8;
        }
    }

    private static String getFieldTrials(PeerConnectionParameters peerConnectionParameters) {
        String fieldTrials = "";
        if (peerConnectionParameters.videoFlexfecEnabled) {
            fieldTrials += MediaConfiguration.VIDEO_FLEXFEC_FIELDTRIAL;
            Log.d(TAG, "Enable FlexFEC field trial.");
        }
        fieldTrials += MediaConfiguration.VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL;
        if (peerConnectionParameters.disableWebRtcAGCAndHPF) {
            fieldTrials += MediaConfiguration.DISABLE_WEBRTC_AGC_FIELDTRIAL;
            Log.d(TAG, "Disable WebRTC AGC field trial.");
        }
        return fieldTrials;
    }

    private void switchCameraInternal() {
        if (videoCapturer instanceof CameraVideoCapturer) {
            if (!isVideoCallEnabled()) {
                Log.e(TAG,
                        "Failed to switch camera. Video: " + isVideoCallEnabled() );
                return; // No video is sent or only one camera is available or error happened.
            }
            Log.d(TAG, "Switch camera");
            CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;
            cameraVideoCapturer.switchCamera(null);
        } else {
            Log.d(TAG, "Will not switch camera, video caputurer is not a camera");
        }
    }

    public void switchCamera() {
        executor.execute(this::switchCameraInternal);
    }

    public void changeCaptureFormat(final int width, final int height, final int framerate) {
        executor.execute(() -> changeCaptureFormatInternal(width, height, framerate));
    }

    private void changeCaptureFormatInternal(int width, int height, int framerate) {
        if (!isVideoCallEnabled() || videoCapturer == null) {
            Log.e(TAG,
                    "Failed to change capture format. Video: " + isVideoCallEnabled());
            return;
        }
        Log.d(TAG, "changeCaptureFormat: " + width + "x" + height + "@" + framerate);
        videoSource.adaptOutputFormat(width, height, framerate);
    }


    public PeerConnectionProxy getConnection(String connectionId) {
        return connections.get(connectionId);
    }

    public Collection<PeerConnectionProxy> getConnections() {
        return connections.values();
    }

    public void closeConnection(String connectionId) {
        PeerConnectionProxy connection =
                connections.remove(connectionId);
        connection.close();
    }

    public void closeAllConnections() {
        for (PeerConnectionProxy connection
                : connections.values()) {
            connection.close();
        }
        connections.clear();
    }

    @Nullable
    public String getLocalConnectionId() {
        return localConnectionId;
    }

    public void createOffer(String connectionId) {
        PeerConnectionProxy proxy =
                getConnection(connectionId);
        if (proxy != null) {
            proxy.createOffer(sdpMediaConstraints);
        }
    }

    public void createAnswer(MediaConstraints sdpMediaConstraints,
                             String connectionId) {
        PeerConnectionProxy proxy =
                getConnection(connectionId);
        if (proxy != null) {
            proxy.createAnswer(sdpMediaConstraints);
        }
    }

    public void setRemoteDescription(final SessionDescription sdp,
                                     String connectionId) {
        PeerConnectionProxy proxy =
                getConnection(connectionId);
        if (proxy != null) {
            proxy.setRemoteDescription(sdp);
        }
    }

    public void addRemoteIceCandidate(final IceCandidate candidate,
                                      String connectionId) {
        PeerConnectionProxy proxy =
                getConnection(connectionId);
        if (proxy != null) {
            proxy.addRemoteIceCandidate(candidate);
        }
    }

    public void removeRemoteIceCandidates(final IceCandidate[] candidates,
                                          String connectionId) {
        PeerConnectionProxy proxy =
                getConnection(connectionId);
        if (proxy != null) {
            proxy.removeRemoteIceCandidates(candidates);
        }
    }
}
