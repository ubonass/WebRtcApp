package org.kurento_android.ui;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import org.kurento_android.R;
import org.utilities_android.SecurityCertificatation;
import org.webrtc.*;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import butterknife.ButterKnife;
import butterknife.InjectView;
import org.webrtcpeer.*;
import org.webrtcpeer.PeerConnectionClient.PeerConnectionParameters;
import org.webrtcpeer.AppRTCClient.SignalingParameters;
import org.webrtcpeer.AppRTCClient.RoomConnectionParameters;
import org.webrtcpeer.AppRTCAudioManager.AudioManagerEvents;
import org.webrtcpeer.AppRTCAudioManager.AudioDevice;

import javax.net.ssl.SSLSocketFactory;
/**
 * Created by cxm on 8/3/16.
 */
public class CallFragment extends BaseFragment
        implements PeerConnectionEvents, AppRTCClient.SignalingEvents {

    private static final String TAG = "CallFragment";

    @InjectView(R.id.local_video_view)
    SurfaceViewRenderer localRender;

    @InjectView(R.id.remote_video_view)
    SurfaceViewRenderer remoteRender;

    @Nullable
    private RoomConnectionParameters roomConnectionParameters;
    @Nullable
    private SignalingParameters signalingParameters;
    @Nullable
    private PeerConnectionParameters peerConnectionParameters;
    @Nullable
    private PeerConnectionClient peerConnectionClient;
    @Nullable
    private AppRTCAudioManager audioManager;
    @Nullable
    private SSLSocketFactory sslSocketFactory;
    // video render
    @Nullable
    private EglBase rootEglBase;
    @Nullable
    private AppRTCClient appRtcClient;

    private final ProxyVideoSink remoteProxyRenderer = new ProxyVideoSink();
    private final ProxyVideoSink localProxyVideoSink = new ProxyVideoSink();


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_call_buddy,
                container, false);
        ButterKnife.inject(this, view);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.e(TAG, "~~~~~~~~~~~~~~~~~~~");
        rootEglBase = EglBase.create();
        localRender.init(rootEglBase.getEglBaseContext(),
                null);
        remoteRender.init(rootEglBase.getEglBaseContext(),
                null);
        localRender.setZOrderMediaOverlay(true);

        localProxyVideoSink.setTarget(localRender);

        remoteProxyRenderer.setTarget(remoteRender);

        peerConnectionParameters =
                ConnectionParameter.getPeerConnectionParameters(getContext());

        roomConnectionParameters =
                ConnectionParameter.getRoomConnectionParameters(getContext());

        Log.d(TAG, "roomConnectionParameters:" + roomConnectionParameters.toString());

        peerConnectionClient =
                new PeerConnectionClient(getContext(),
                        rootEglBase, peerConnectionParameters, this);
        PeerConnectionFactory.Options options =
                new PeerConnectionFactory.Options();

        if (peerConnectionParameters.loopback) {
            options.networkIgnoreMask = 0;
        }
        peerConnectionClient.createPeerConnectionFactory(options);
        try {
            sslSocketFactory = SecurityCertificatation
                    .getSslSocketFactory(getResources()
                                    .getAssets()
                                    .open("kurento-app-server.cer"),
                            null, null);
            appRtcClient = new WebSocketRTCClient(this, sslSocketFactory);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        startCall();
    }

    @Override
    public void onStop() {
        disconnect();
        super.onStop();
    }

    // Disconnect from remote resources, dispose of local resources, and exit.
    private void disconnect() {
        remoteProxyRenderer.setTarget(null);
        localProxyVideoSink.setTarget(null);
        if (appRtcClient != null) {
            appRtcClient.disconnectFromRoom();
            appRtcClient = null;
        }
        if (localRender != null) {
            localRender.release();
            localRender = null;
        }

        if (remoteRender != null) {
            remoteRender.release();
            remoteRender = null;
        }
        if (peerConnectionClient != null) {
            peerConnectionClient.close();
            peerConnectionClient = null;
        }
        if (audioManager != null) {
            audioManager.stop();
            audioManager = null;
        }
        if (rootEglBase != null) {
            rootEglBase.release();
            rootEglBase = null;
        }

    }

    private void startCall() {
        if (appRtcClient == null) {
            Log.e(TAG, "AppRTC client is not allocated for a call.");
            return;
        }
        //callStartedTimeMs = System.currentTimeMillis();

        // Start room connection.
        Log.i(TAG, getString(R.string.connecting_to,
                roomConnectionParameters.roomUrl));
        //这里开始连接到room服务器,在WebSocketRTCClient中实现
        appRtcClient.connectToRoom(roomConnectionParameters);

        // Create and audio manager that will take care of audio routing,
        // audio modes, audio device enumeration etc.
        audioManager = AppRTCAudioManager.create(getContext());
        // Store existing audio settings and change audio mode to
        // MODE_IN_COMMUNICATION for best possible VoIP performance.
        Log.d(TAG, "Starting the audio manager...");
        audioManager.start(new AudioManagerEvents() {
            // This method will be called each time the number of available audio
            // devices has changed.
            @Override
            public void onAudioDeviceChanged(
                    AudioDevice audioDevice, Set<AudioDevice> availableAudioDevices) {
                onAudioManagerDevicesChanged(audioDevice, availableAudioDevices);
            }
        });
    }

    // This method is called when the audio manager reports audio device change,
    // e.g. from wired headset to speakerphone.
    private void onAudioManagerDevicesChanged(
            final AudioDevice device, final Set<AudioDevice> availableDevices) {
        Log.d(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
                + "selected: " + device);
        // TODO(henrika): add callback handler.
    }

    @Override
    public void onLocalDescription(SessionDescription sdp) {
        Log.i(TAG, "onLocalDescription: " + sdp.description);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (appRtcClient != null) {
                    if (signalingParameters.initiator) {
                        appRtcClient.sendOfferSdp(sdp);
                    } else {
                        appRtcClient.sendAnswerSdp(sdp);
                    }
                }
                if (peerConnectionParameters.videoMaxBitrate > 0) {
                    Log.d(TAG, "Set video maximum bitrate: "
                            + peerConnectionParameters.videoMaxBitrate);
                    peerConnectionClient.setVideoMaxBitrate(peerConnectionParameters.videoMaxBitrate);
                }
            }
        });
    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {
        Log.i(TAG, "onIceCandidate: " + candidate);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (appRtcClient != null) {
                    appRtcClient.sendLocalIceCandidate(candidate);
                }
            }
        });
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {
        Log.i(TAG, "onIceCandidateRemoved: " + candidates);
    }

    @Override
    public void onIceConnected() {
        Log.i(TAG, "onIceConnected");
    }

    @Override
    public void onIceDisconnected() {
        Log.i(TAG, "onIceDisconnected");
    }

    @Override
    public void onPeerConnectionClosed() {
        Log.i(TAG, "onPeerConnectionClosed");
    }

    @Override
    public void onPeerConnectionStatsReady(StatsReport[] reports) {
        Log.i(TAG, "onPeerConnectionStatsReady: " + reports);
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
        Log.i(TAG, "onPeerConnectionError: " + description);
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

    private @Nullable
    VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();
        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName,
                        null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }

    private @Nullable
    VideoCapturer createVideoCapturer() {
        final VideoCapturer videoCapturer;
        Logging.d(TAG, "Creating capturer using camera2 API.");
        videoCapturer = createCameraCapturer(new Camera2Enumerator(getContext()));

        if (videoCapturer == null) {
            Log.e(TAG, "Failed to open camera");
            return null;
        }
        return videoCapturer;
    }

    // -----Implementation of AppRTCClient.AppRTCSignalingEvents ---------------
    // All callbacks are invoked from websocket signaling looper thread and
    // are routed to UI thread.
    private void onConnectedToRoomInternal(final SignalingParameters params) {
        signalingParameters = params;
        if (signalingParameters == null) return;
        List<PeerConnection.IceServer> iceServers =
                signalingParameters.iceServers;
        if (iceServers != null) {
            iceServers.add(new PeerConnection.IceServer(
                    CallActivity.TURN_ADDRESS,
                    CallActivity.TURN_USERNAME,
                    CallActivity.TURN_PASSWORD));
            iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.xten.com:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.voipbuster.com:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.voxgratia.org:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.sipgate.net:10000"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.ekiga.net:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.ideasip.com:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.schlund.de:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.voiparound.com:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.voipbuster.com:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.voipstunt.com:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:numb.viagenie.ca:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.counterpath.com:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.1und1.de:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.gmx.net:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.bcs2005.net:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.callwithus.com:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.counterpath.net:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.internetcalls.com:3478"));
            iceServers.add(new PeerConnection.IceServer("stun:stun.voip.aebc.com:3478"));
        }
        VideoCapturer videoCapturer = null;
        if (peerConnectionParameters.videoCallEnabled) {
            videoCapturer = createVideoCapturer();
        }
        peerConnectionClient.createPeerConnection(
                localProxyVideoSink,
                remoteProxyRenderer,
                videoCapturer, signalingParameters);

        if (signalingParameters.initiator) {
            Log.i(TAG, "Creating OFFER...");
            // Create offer. Offer SDP will be sent to answering client in
            // PeerConnectionEvents.onLocalDescription event.
            peerConnectionClient.createOffer();
        } else {
            if (params.offerSdp != null) {
                peerConnectionClient.setRemoteDescription(params.offerSdp);
                Log.i(TAG, "Creating ANSWER...");
                // Create answer. Answer SDP will be sent to offering client in
                // PeerConnectionEvents.onLocalDescription event.
                peerConnectionClient.createAnswer();
            }
            if (params.iceCandidates != null) {
                // Add remote ICE candidates from room.
                for (IceCandidate iceCandidate
                        : params.iceCandidates) {
                    peerConnectionClient
                            .addRemoteIceCandidate(iceCandidate);
                }
            }
        }
    }

    @Override
    public void onConnectedToRoom(SignalingParameters params) {

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onConnectedToRoomInternal(params);
            }
        });
    }

    @Override
    public void onRemoteDescription(SessionDescription sdp) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG,
                            "Received remote SDP for non-initilized peer connection.");
                    return;
                }
                Log.i(TAG,"Received remote " + sdp.type);
                peerConnectionClient.setRemoteDescription(sdp);
                if (!signalingParameters.initiator) {
                    Log.i(TAG,"Creating ANSWER...");
                    // Create answer. Answer SDP will be sent to offering client in
                    // PeerConnectionEvents.onLocalDescription event.
                    peerConnectionClient.createAnswer();
                }
            }
        });
    }

    @Override
    public void onRemoteIceCandidate(IceCandidate candidate) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG,

                            "Received ICE candidate for a non-initialized peer connection.");
                    return;
                }
                peerConnectionClient.addRemoteIceCandidate(candidate);
            }
        });
    }

    @Override
    public void onRemoteIceCandidatesRemoved(IceCandidate[] candidates) {

    }

    @Override
    public void onChannelClose() {

    }

    @Override
    public void onChannelError(String description) {

    }

}
