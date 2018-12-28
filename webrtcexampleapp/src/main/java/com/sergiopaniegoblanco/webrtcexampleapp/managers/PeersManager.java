package com.sergiopaniegoblanco.webrtcexampleapp.managers;

import android.util.Log;
import android.widget.LinearLayout;


import com.sergiopaniegoblanco.webrtcexampleapp.VideoConferenceActivity;
import com.sergiopaniegoblanco.webrtcexampleapp.RemoteParticipant;
import com.sergiopaniegoblanco.webrtcexampleapp.listeners.CustomWebSocketListener;
import com.sergiopaniegoblanco.webrtcexampleapp.observers.CustomPeerConnectionObserver;
import com.sergiopaniegoblanco.webrtcexampleapp.observers.CustomSdpObserver;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by sergiopaniegoblanco on 18/02/2018.
 */

public class PeersManager {

    private PeerConnection localPeer;
    private PeerConnectionFactory peerConnectionFactory;
    private CustomWebSocketListener webSocketAdapter;
    private LinearLayout views_container;
    private AudioTrack localAudioTrack;
    private VideoTrack localVideoTrack;
    private VideoRenderer localRenderer;
    private SurfaceViewRenderer localVideoView;
    private VideoCapturer videoGrabberAndroid;
    private VideoConferenceActivity activity;
    public static final String TURN_ADDRESS = "turn:59.110.212.181:3478?transport=udp";
    public static final String TURN_USERNAME = "ubonass";
    public static final String TURN_PASSWORD = "openvidu";
    private static final String TAG = "PeersManager";
    public PeersManager(VideoConferenceActivity activity, LinearLayout views_container, SurfaceViewRenderer localVideoView) {
        this.views_container = views_container;
        this.localVideoView = localVideoView;
        this.activity = activity;
    }

    public PeerConnection getLocalPeer() {
        return localPeer;
    }

    public AudioTrack getLocalAudioTrack() {
        return localAudioTrack;
    }

    public VideoTrack getLocalVideoTrack() {
        return localVideoTrack;
    }

    public PeerConnectionFactory getPeerConnectionFactory() {
        return peerConnectionFactory;
    }


    public CustomWebSocketListener getWebSocketAdapter() {
        return webSocketAdapter;
    }

    public void setWebSocketAdapter(CustomWebSocketListener webSocketAdapter) {
        this.webSocketAdapter = webSocketAdapter;
    }

    public void start() {
        PeerConnectionFactory.initializeAndroidGlobals(activity, true);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = new PeerConnectionFactory(options);

        videoGrabberAndroid = createVideoGrabber();
        MediaConstraints constraints = new MediaConstraints();

        VideoSource videoSource = peerConnectionFactory.createVideoSource(videoGrabberAndroid);
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);

        AudioSource audioSource = peerConnectionFactory.createAudioSource(constraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);

        if (videoGrabberAndroid != null) {
            videoGrabberAndroid.startCapture(1000, 1000, 30);
        }

        localRenderer = new VideoRenderer(localVideoView);
        localVideoTrack.addRenderer(localRenderer);

        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));

        createLocalPeerConnection(sdpConstraints);
    }

    private VideoCapturer createVideoGrabber() {
        VideoCapturer videoCapturer;
        videoCapturer =
                createCameraGrabber(new Camera1Enumerator(false));
        return videoCapturer;
    }

    private VideoCapturer createCameraGrabber(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private void createLocalPeerConnection(MediaConstraints sdpConstraints) {
        final List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        PeerConnection.IceServer iceServer =
                new PeerConnection
                        .IceServer(TURN_ADDRESS,TURN_USERNAME,TURN_PASSWORD);
        iceServers.add(iceServer);
        localPeer =
                peerConnectionFactory.createPeerConnection(iceServers,
                        sdpConstraints, new CustomPeerConnectionObserver(
                                "localPeerCreation") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                Log.i(TAG,"onIceCandidate");
                Map<String, String> iceCandidateParams = new HashMap<>();
                iceCandidateParams.put("sdpMid", iceCandidate.sdpMid);
                iceCandidateParams.put("sdpMLineIndex",
                        Integer.toString(iceCandidate.sdpMLineIndex));
                iceCandidateParams.put("candidate", iceCandidate.sdp);
                Log.i(TAG,".....User id:" + webSocketAdapter.getUserId());
                if (webSocketAdapter.getUserId() != null) {
                    iceCandidateParams.put("endpointName",
                            webSocketAdapter.getUserId());
                    webSocketAdapter.sendJson("onIceCandidate",
                            iceCandidateParams);
                } else {
                    webSocketAdapter.addIceCandidate(iceCandidateParams);
                }
            }
        });
    }

    public void createLocalOffer(MediaConstraints sdpConstraints) {

        localPeer.createOffer(new CustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                Log.i(TAG,"onCreateSuccess");
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocalDesc"), sessionDescription);
                Map<String, String> localOfferParams = new HashMap<>();
                localOfferParams.put("audioActive", "true");
                localOfferParams.put("videoActive", "true");
                localOfferParams.put("hasAudio", "true");
                localOfferParams.put("hasVideo", "true");
                localOfferParams.put("doLoopback", "false");
                localOfferParams.put("frameRate", "30");
                localOfferParams.put("typeOfVideo", "CAMERA");
                localOfferParams.put("sdpOffer", sessionDescription.description);
                if (webSocketAdapter.getId() > 1) {
                    webSocketAdapter.sendJson("publishVideo", localOfferParams);
                } else {
                    webSocketAdapter.setLocalOfferParams(localOfferParams);
                }
            }
        }, sdpConstraints);
    }

    public void createRemotePeerConnection(RemoteParticipant remoteParticipant) {
        final List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        PeerConnection.IceServer iceServer =
                new PeerConnection.IceServer(TURN_ADDRESS,TURN_USERNAME,TURN_PASSWORD);
        iceServers.add(iceServer);

        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));

        PeerConnection remotePeer =
                peerConnectionFactory
                        .createPeerConnection(iceServers, sdpConstraints,
                                new CustomPeerConnectionObserver("remotePeerCreation",
                                        remoteParticipant) {

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                Log.i(TAG,"onIceCandidate for remote");
                Map<String, String> iceCandidateParams = new HashMap<>();
                iceCandidateParams.put("sdpMid", iceCandidate.sdpMid);
                iceCandidateParams.put("sdpMLineIndex",
                        Integer.toString(iceCandidate.sdpMLineIndex));
                iceCandidateParams.put("candidate", iceCandidate.sdp);
                iceCandidateParams.put("endpointName", getRemoteParticipant().getId());
                webSocketAdapter.sendJson("onIceCandidate", iceCandidateParams);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);
                activity.gotRemoteStream(mediaStream, getRemoteParticipant());
            }
        });
        MediaStream mediaStream =
                peerConnectionFactory.createLocalMediaStream("105");
        mediaStream.addTrack(localAudioTrack);
        mediaStream.addTrack(localVideoTrack);
        remotePeer.addStream(mediaStream);
        remoteParticipant.setPeerConnection(remotePeer);
    }


    public void hangup() {
        if (webSocketAdapter != null && localPeer != null) {
            webSocketAdapter.sendJson("leaveRoom", new HashMap<String, String>());
            webSocketAdapter.close();
            localPeer.close();
            Map<String, RemoteParticipant> participants = webSocketAdapter.getParticipants();
            for (RemoteParticipant remoteParticipant : participants.values()) {
                remoteParticipant.getPeerConnection().close();
                views_container.removeView(remoteParticipant.getView());

            }
        }
        if (localVideoTrack != null) {
            localVideoTrack.removeRenderer(localRenderer);
            localVideoView.clearImage();
            videoGrabberAndroid.dispose();
        }
    }
}
