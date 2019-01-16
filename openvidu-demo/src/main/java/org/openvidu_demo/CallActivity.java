/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.openvidu_demo;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;
import org.utilities_android.SecurityCertificatation;
import org.webrtc.*;
import org.webrtc.RendererCommon.ScalingType;
import org.webrtcpeer.*;
import org.webrtcpeer.AppRTCAudioManager.AudioDevice;
import org.webrtcpeer.AppRTCAudioManager.AudioManagerEvents;
import org.webrtcpeer.AppRTCClient.RoomConnectionParameters;
import org.webrtcpeer.AppRTCClient.SignalingParameters;
import org.webrtcpeer.DataChannelParameters;
import org.webrtcpeer.PeerConnectionParameters;

import javax.annotation.Nullable;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Activity for peer connection call setup, call waiting
 * and call view.
 */
public class CallActivity extends Activity
        implements AppRTCClient.SignalingEvents,
        PeerConnectionEvents,
        CallFragment.OnCallEvents {
    private static final String TAG = "CallRTCClient";

    private static final int CAPTURE_PERMISSION_REQUEST_CODE = 1;

    // List of mandatory application permissions.
    private static final String[] MANDATORY_PERMISSIONS = {"android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.RECORD_AUDIO", "android.permission.INTERNET"};

    // Peer connection statistics callback period in ms.
    private static final int STAT_CALLBACK_PERIOD = 1000;

    private final ProxyVideoSink remoteProxyRenderer = new ProxyVideoSink();
    private final ProxyVideoSink localProxyVideoSink = new ProxyVideoSink();
    @Nullable
    private PeerConnectionClient peerConnectionClient;
    @Nullable
    private AppRTCClient appRtcClient;
    @Nullable
    private SignalingParameters signalingParameters;
    @Nullable
    private AppRTCAudioManager audioManager;
    @Nullable
    private SurfaceViewRenderer pipRenderer;
    @Nullable
    private SurfaceViewRenderer fullscreenRenderer;
    @Nullable
    private VideoFileRenderer videoFileRenderer;
    private final List<VideoSink> remoteSinks = new ArrayList<>();
    private Toast logToast;
    private boolean commandLineRun;
    private boolean activityRunning;
    private RoomConnectionParameters roomConnectionParameters;
    @Nullable
    private PeerConnectionParameters peerConnectionParameters;
    private boolean iceConnected;
    private boolean isError;
    private boolean callControlFragmentVisible = true;
    private long callStartedTimeMs;
    private boolean micEnabled = true;
    private boolean screencaptureEnabled;
    private static Intent mediaProjectionPermissionResultData;
    private static int mediaProjectionPermissionResultCode;
    // True if local view is in the fullscreen renderer.
    private boolean isSwappedFeeds;

    // Controls
    private CallFragment callFragment;
    private HudFragment hudFragment;
    private CpuMonitor cpuMonitor;

    @Override
    // TODO(bugs.webrtc.org/8580): LayoutParams.FLAG_TURN_SCREEN_ON and
    // LayoutParams.FLAG_SHOW_WHEN_LOCKED are deprecated.
    @SuppressWarnings("deprecation")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new UnhandledExceptionHandler(this));

        // Set window styles for fullscreen-window size. Needs to be done before
        // adding content.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(LayoutParams.FLAG_FULLSCREEN | LayoutParams.FLAG_KEEP_SCREEN_ON
                | LayoutParams.FLAG_SHOW_WHEN_LOCKED | LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility());
        setContentView(R.layout.activity_call);

        iceConnected = false;
        signalingParameters = null;

        // Create UI controls.
        pipRenderer = findViewById(R.id.pip_video_view);
        fullscreenRenderer = findViewById(R.id.fullscreen_video_view);
        callFragment = new CallFragment();
        hudFragment = new HudFragment();

        // Show/hide call control fragment on view click.
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleCallControlFragmentVisibility();
            }
        };

        // Swap feeds on pip view click.
        pipRenderer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setSwappedFeeds(!isSwappedFeeds);
            }
        });

        fullscreenRenderer.setOnClickListener(listener);
        remoteSinks.add(remoteProxyRenderer);

        final Intent intent = getIntent();
        final EglBase eglBase = EglBase.create();

        // Create video renderers.
        pipRenderer.init(eglBase.getEglBaseContext(), null);
        pipRenderer.setScalingType(ScalingType.SCALE_ASPECT_FIT);

        String saveRemoteVideoToFile =
                intent.getStringExtra(ExtraInfo.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE);

        // When saveRemoteVideoToFile is set we save the video from the remote to a file.
        if (saveRemoteVideoToFile != null) {
            int videoOutWidth = intent.getIntExtra(ExtraInfo.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH, 0);
            int videoOutHeight = intent.getIntExtra(ExtraInfo.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT, 0);
            try {
                videoFileRenderer = new VideoFileRenderer(
                        saveRemoteVideoToFile, videoOutWidth, videoOutHeight, eglBase.getEglBaseContext());
                remoteSinks.add(videoFileRenderer);
            } catch (IOException e) {
                throw new RuntimeException(
                        "Failed to open video file for output: " + saveRemoteVideoToFile, e);
            }
        }
        fullscreenRenderer.init(eglBase.getEglBaseContext(), null);
        fullscreenRenderer.setScalingType(ScalingType.SCALE_ASPECT_FILL);

        pipRenderer.setZOrderMediaOverlay(true);
        pipRenderer.setEnableHardwareScaler(true /* enabled */);
        fullscreenRenderer.setEnableHardwareScaler(false /* enabled */);
        // Start with local feed in fullscreen and swap
        // it to the pip when the call is connected.
        setSwappedFeeds(true /* isSwappedFeeds */);

        // Check for mandatory permissions.
        for (String permission : MANDATORY_PERMISSIONS) {
            if (checkCallingOrSelfPermission(permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                logAndToast("Permission " + permission + " is not granted");
                setResult(RESULT_CANCELED);
                finish();
                return;
            }
        }

        Uri roomUri = intent.getData();
        if (roomUri == null) {
            logAndToast(getString(R.string.missing_url));
            Log.e(TAG, "Didn't get any URL in intent!");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        Log.d(TAG, "roomUri:" + roomUri);
        // Get Intent parameters.
        String roomId = intent.getStringExtra(ExtraInfo.EXTRA_ROOMID);
        Log.d(TAG, "Room ID: " + roomId);
        if (roomId == null || roomId.length() == 0) {
            logAndToast(getString(R.string.missing_url));
            Log.e(TAG, "Incorrect room ID in intent!");
            setResult(RESULT_CANCELED);
            finish();
            return;
            //roomId = "helloworld";
            //intent.putExtra(ExtraInfo.EXTRA_ROOMID,roomId);
        }

        boolean loopback = intent.getBooleanExtra(ExtraInfo.EXTRA_LOOPBACK, false);
        boolean tracing = intent.getBooleanExtra(ExtraInfo.EXTRA_TRACING, false);

        //Log.i(TAG,"~~~~~~~~loopback:" + loopback + "--------");

        int videoWidth = intent.getIntExtra(ExtraInfo.EXTRA_VIDEO_WIDTH, 0);
        int videoHeight = intent.getIntExtra(ExtraInfo.EXTRA_VIDEO_HEIGHT, 0);

        screencaptureEnabled = intent.getBooleanExtra(ExtraInfo.EXTRA_SCREENCAPTURE, false);
        // If capturing format is not specified for screencapture, use screen resolution.
        if (screencaptureEnabled && videoWidth == 0 && videoHeight == 0) {
            DisplayMetrics displayMetrics = getDisplayMetrics();
            videoWidth = displayMetrics.widthPixels;
            videoHeight = displayMetrics.heightPixels;
        }
        DataChannelParameters dataChannelParameters = null;
        if (intent.getBooleanExtra(ExtraInfo.EXTRA_DATA_CHANNEL_ENABLED, false)) {
            dataChannelParameters = new DataChannelParameters(intent.getBooleanExtra(ExtraInfo.EXTRA_ORDERED, true),
                    intent.getIntExtra(ExtraInfo.EXTRA_MAX_RETRANSMITS_MS, -1),
                    intent.getIntExtra(ExtraInfo.EXTRA_MAX_RETRANSMITS, -1), intent.getStringExtra(ExtraInfo.EXTRA_PROTOCOL),
                    intent.getBooleanExtra(ExtraInfo.EXTRA_NEGOTIATED, false), intent.getIntExtra(ExtraInfo.EXTRA_ID, -1));
        }
        peerConnectionParameters =
                new PeerConnectionParameters(intent.getBooleanExtra(ExtraInfo.EXTRA_VIDEO_CALL, true), loopback,
                        tracing, videoWidth, videoHeight, intent.getIntExtra(ExtraInfo.EXTRA_VIDEO_FPS, 0),
                        intent.getIntExtra(ExtraInfo.EXTRA_VIDEO_BITRATE, 0), intent.getStringExtra(ExtraInfo.EXTRA_VIDEOCODEC),
                        intent.getBooleanExtra(ExtraInfo.EXTRA_HWCODEC_ENABLED, true),
                        intent.getBooleanExtra(ExtraInfo.EXTRA_FLEXFEC_ENABLED, false),
                        intent.getIntExtra(ExtraInfo.EXTRA_AUDIO_BITRATE, 0), intent.getStringExtra(ExtraInfo.EXTRA_AUDIOCODEC),
                        intent.getBooleanExtra(ExtraInfo.EXTRA_NOAUDIOPROCESSING_ENABLED, false),
                        intent.getBooleanExtra(ExtraInfo.EXTRA_AECDUMP_ENABLED, false),
                        intent.getBooleanExtra(ExtraInfo.EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED, false),
                        intent.getBooleanExtra(ExtraInfo.EXTRA_OPENSLES_ENABLED, false),
                        intent.getBooleanExtra(ExtraInfo.EXTRA_DISABLE_BUILT_IN_AEC, false),
                        intent.getBooleanExtra(ExtraInfo.EXTRA_DISABLE_BUILT_IN_AGC, false),
                        intent.getBooleanExtra(ExtraInfo.EXTRA_DISABLE_BUILT_IN_NS, false),
                        intent.getBooleanExtra(ExtraInfo.EXTRA_DISABLE_WEBRTC_AGC_AND_HPF, false),
                        intent.getBooleanExtra(ExtraInfo.EXTRA_ENABLE_RTCEVENTLOG, false),
                        intent.getBooleanExtra(ExtraInfo.EXTRA_USE_LEGACY_AUDIO_DEVICE, false), dataChannelParameters);
        commandLineRun = intent.getBooleanExtra(ExtraInfo.EXTRA_CMDLINE, false);
        int runTimeMs = intent.getIntExtra(ExtraInfo.EXTRA_RUNTIME, 0);
        Log.d(TAG, "peerConnectionParameters:" + peerConnectionParameters.toString());
        Log.d(TAG, "VIDEO_FILE: '" + intent.getStringExtra(ExtraInfo.EXTRA_VIDEO_FILE_AS_CAMERA) + "'");

        // Create connection client. Use DirectRTCClient if room name is an IP otherwise use the
        // standard WebSocketRTCClient.
        try {
            SSLSocketFactory sslSocketFactory = SecurityCertificatation
                    .getSslSocketFactory(getResources()
                                    .getAssets()
                                    .open("openvidu-selfsigned.cer"),
                            null, null);
            appRtcClient = new WebSocketRTCClient(this, sslSocketFactory);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //appRtcClient = new WebSocketRTCClient(this，);

        // Create connection parameters.
        String urlParameters =
                intent.getStringExtra(ExtraInfo.EXTRA_URLPARAMETERS);
        roomConnectionParameters =
                new RoomConnectionParameters(roomUri.toString(),
                        roomId, loopback, urlParameters);
        Log.d(TAG, "roomConnectionParameters:" + roomConnectionParameters.toString());
        // Create CPU monitor
        if (CpuMonitor.isSupported()) {
            cpuMonitor = new CpuMonitor(this);
            hudFragment.setCpuMonitor(cpuMonitor);
        }

        // Send intent arguments to fragments.
        callFragment.setArguments(intent.getExtras());
        hudFragment.setArguments(intent.getExtras());
        // Activate call and HUD fragments and start the call.
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.add(R.id.call_fragment_container, callFragment);
        ft.add(R.id.hud_fragment_container, hudFragment);
        ft.commit();

        // For command line execution run connection for <runTimeMs> and exit.
        if (commandLineRun && runTimeMs > 0) {
            (new Handler()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    disconnect();
                }
            }, runTimeMs);
        }

        // Create peer connection client.
        peerConnectionClient = new PeerConnectionClient(
                getApplicationContext(), eglBase,
                peerConnectionParameters, CallActivity.this);
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        if (loopback) {
            options.networkIgnoreMask = 0;
        }
        peerConnectionClient.createPeerConnectionFactory(options);

        if (screencaptureEnabled) {
            startScreenCapture();
        } else {
            startCall();//这里开始
        }
    }

    @TargetApi(17)
    private DisplayMetrics getDisplayMetrics() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager =
                (WindowManager) getApplication().getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        return displayMetrics;
    }

    @TargetApi(19)
    private static int getSystemUiVisibility() {
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        return flags;
    }

    @TargetApi(21)
    private void startScreenCapture() {
        MediaProjectionManager mediaProjectionManager =
                (MediaProjectionManager) getApplication().getSystemService(
                        Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(), CAPTURE_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != CAPTURE_PERMISSION_REQUEST_CODE)
            return;
        mediaProjectionPermissionResultCode = resultCode;
        mediaProjectionPermissionResultData = data;
        startCall();
    }

    private boolean useCamera2() {
        return Camera2Enumerator.isSupported(this) && getIntent().getBooleanExtra(ExtraInfo.EXTRA_CAMERA2, true);
    }

    private boolean captureToTexture() {
        return getIntent().getBooleanExtra(ExtraInfo.EXTRA_CAPTURETOTEXTURE_ENABLED, false);
    }

    private @Nullable
    VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

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

    @TargetApi(21)
    private @Nullable
    VideoCapturer createScreenCapturer() {
        if (mediaProjectionPermissionResultCode != Activity.RESULT_OK) {
            reportError("User didn't give permission to capture the screen.");
            return null;
        }
        return new ScreenCapturerAndroid(
                mediaProjectionPermissionResultData, new MediaProjection.Callback() {
            @Override
            public void onStop() {
                reportError("User revoked permission to capture the screen.");
            }
        });
    }

    // Activity interfaces
    @Override
    public void onStop() {
        super.onStop();
        activityRunning = false;
        // Don't stop the video when using screencapture to allow user to show other apps to the remote
        // end.
        if (peerConnectionClient != null && !screencaptureEnabled) {
            peerConnectionClient.stopVideoSource();
        }
        if (cpuMonitor != null) {
            cpuMonitor.pause();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        activityRunning = true;
        // Video is not paused for screencapture. See onPause.
        if (peerConnectionClient != null && !screencaptureEnabled) {
            peerConnectionClient.startVideoSource();
        }
        if (cpuMonitor != null) {
            cpuMonitor.resume();
        }
    }

    @Override
    protected void onDestroy() {
        Thread.setDefaultUncaughtExceptionHandler(null);
        disconnect();
        if (logToast != null) {
            logToast.cancel();
        }
        activityRunning = false;
        super.onDestroy();
    }

    // CallFragment.OnCallEvents interface implementation.
    @Override
    public void onCallHangUp() {
        disconnect();
    }

    @Override
    public void onCameraSwitch() {
        if (peerConnectionClient != null) {
            peerConnectionClient.switchCamera();
        }
    }

    @Override
    public void onVideoScalingSwitch(ScalingType scalingType) {
        fullscreenRenderer.setScalingType(scalingType);
    }

    @Override
    public void onCaptureFormatChange(int width, int height, int framerate) {
        if (peerConnectionClient != null) {
            peerConnectionClient.changeCaptureFormat(width, height, framerate);
        }
    }

    @Override
    public boolean onToggleMic() {
        if (peerConnectionClient != null) {
            micEnabled = !micEnabled;
            peerConnectionClient.setAudioEnabled(micEnabled);
        }
        return micEnabled;
    }

    // Helper functions.
    private void toggleCallControlFragmentVisibility() {
        if (!iceConnected || !callFragment.isAdded()) {
            return;
        }
        // Show/hide call control fragment
        callControlFragmentVisible = !callControlFragmentVisible;
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        if (callControlFragmentVisible) {
            ft.show(callFragment);
            ft.show(hudFragment);
        } else {
            ft.hide(callFragment);
            ft.hide(hudFragment);
        }
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.commit();
    }

    private void startCall() {
        if (appRtcClient == null) {
            Log.e(TAG, "AppRTC client is not allocated for a call.");
            return;
        }
        callStartedTimeMs = System.currentTimeMillis();
        // Start room connection.
        logAndToast(getString(R.string.connecting_to, roomConnectionParameters.roomUrl));
        //这里开始连接到room服务器,在WebSocketRTCClient中实现
        appRtcClient.connectToRoom(roomConnectionParameters);

        // Create and audio manager that will take care of audio routing,
        // audio modes, audio device enumeration etc.
        audioManager = AppRTCAudioManager.create(getApplicationContext());
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

    // Should be called from UI thread
    private void callConnected() {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        Log.i(TAG, "Call connected: delay=" + delta + "ms");
        if (peerConnectionClient == null || isError) {
            Log.w(TAG, "Call is connected in closed or error state");
            return;
        }
        // Enable statistics callback.
        /*peerConnectionClient
                .enableStatsEvents(true, STAT_CALLBACK_PERIOD);*/
        setSwappedFeeds(false /* isSwappedFeeds */);
    }

    // This method is called when the audio manager reports audio device change,
    // e.g. from wired headset to speakerphone.
    private void onAudioManagerDevicesChanged(
            final AudioDevice device, final Set<AudioDevice> availableDevices) {
        Log.d(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
                + "selected: " + device);
        // TODO(henrika): add callback handler.
    }

    // Disconnect from remote resources, dispose of local resources, and exit.
    private void disconnect() {
        activityRunning = false;
        remoteProxyRenderer.setTarget(null);
        localProxyVideoSink.setTarget(null);
        if (appRtcClient != null) {
            appRtcClient.disconnectFromRoom();
            appRtcClient = null;
        }
        if (pipRenderer != null) {
            pipRenderer.release();
            pipRenderer = null;
        }
        if (videoFileRenderer != null) {
            videoFileRenderer.release();
            videoFileRenderer = null;
        }
        if (fullscreenRenderer != null) {
            fullscreenRenderer.release();
            fullscreenRenderer = null;
        }
        if (peerConnectionClient != null) {
            peerConnectionClient.close();
            peerConnectionClient = null;
        }
        if (audioManager != null) {
            audioManager.stop();
            audioManager = null;
        }
        if (iceConnected && !isError) {
            setResult(RESULT_OK);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    private void disconnectWithErrorMessage(final String errorMessage) {
        if (commandLineRun || !activityRunning) {
            Log.e(TAG, "Critical error: " + errorMessage);
            disconnect();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(getText(R.string.channel_error_title))
                    .setMessage(errorMessage)
                    .setCancelable(false)
                    .setNeutralButton(R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                    disconnect();
                                }
                            })
                    .create()
                    .show();
        }
    }

    // Log |msg| and Toast about it.
    private void logAndToast(String msg) {
        Log.d(TAG, "++++++++" + msg + "---------------------");
        if (logToast != null) {
            logToast.cancel();
        }
        logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        logToast.show();
    }

    private void reportError(final String description) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isError) {
                    isError = true;
                    disconnectWithErrorMessage(description);
                }
            }
        });
    }

    private @Nullable
    VideoCapturer createVideoCapturer() {
        final VideoCapturer videoCapturer;
        String videoFileAsCamera = getIntent().getStringExtra(ExtraInfo.EXTRA_VIDEO_FILE_AS_CAMERA);
        if (videoFileAsCamera != null) {
            try {
                videoCapturer = new FileVideoCapturer(videoFileAsCamera);
            } catch (IOException e) {
                reportError("Failed to open video file for emulated camera");
                return null;
            }
        } else if (screencaptureEnabled) {
            return createScreenCapturer();
        } else if (useCamera2()) {
            if (!captureToTexture()) {
                reportError(getString(R.string.camera2_texture_only_error));
                return null;
            }

            Logging.d(TAG, "Creating capturer using camera2 API.");
            videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
        } else {
            Logging.d(TAG, "Creating capturer using camera1 API.");
            videoCapturer = createCameraCapturer(new Camera1Enumerator(captureToTexture()));
        }
        if (videoCapturer == null) {
            reportError("Failed to open camera");
            return null;
        }
        return videoCapturer;
    }

    private void setSwappedFeeds(boolean isSwappedFeeds) {
        Logging.d(TAG, "setSwappedFeeds: " + isSwappedFeeds);
        this.isSwappedFeeds = isSwappedFeeds;
        localProxyVideoSink.setTarget(isSwappedFeeds ? fullscreenRenderer : pipRenderer);
        remoteProxyRenderer.setTarget(isSwappedFeeds ? pipRenderer : fullscreenRenderer);
        fullscreenRenderer.setMirror(isSwappedFeeds);
        pipRenderer.setMirror(!isSwappedFeeds);
    }

    // -----Implementation of AppRTCClient.AppRTCSignalingEvents ---------------
    // All callbacks are invoked from websocket signaling looper thread and
    // are routed to UI thread.
    private void onConnectedToRoomInternal(final SignalingParameters params,
                                           final String connectionId) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        signalingParameters = params;
        logAndToast("Creating peer connection, delay=" + delta + "ms");
        VideoCapturer videoCapturer = null;
        if (peerConnectionParameters.videoCallEnabled) {
            videoCapturer = createVideoCapturer();
        }
        peerConnectionClient.createPeerConnection(
                localProxyVideoSink,
                videoCapturer,
                signalingParameters, connectionId);
    }

    @Override
    public void onConnectedToRoom(final SignalingParameters params,
                                  String connectionId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onConnectedToRoomInternal(params,
                        connectionId);
            }
        });
    }

    /*@Override
    public void onRegistedInRoom(SignalingParameters params,
                                 String connectionId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //开始准备offsdp参数
                peerConnectionClient.createOffer(connectionId);
                *//*if (signalingParameters.initiator) {
                    logAndToast("Creating OFFER...");
                    // Create offer. Offer SDP will be sent to answering client in
                    // PeerConnectionEvents.onLocalDescription event.
                    peerConnectionClient.createOffer();
                }*//*
            }
        });
    }*/

    @Override
    public void onRemoteJoinedInRoom(SignalingParameters params,
                                     String connectionId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                peerConnectionClient.createRemotePeerConnection(
                        signalingParameters, connectionId);
            }
        });
    }

    @Override
    public void onRemotePublishVideoInRoom(SignalingParameters params,
                                           String connectionId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                peerConnectionClient.createOffer(connectionId);
            }
        });
    }

    @Override
    public void onRemoteDescription(final SessionDescription sdp,
                                    String connectionId) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG,
                            "Received remote SDP for non-initilized peer connection.");
                    return;
                }
                logAndToast("Received remote "
                        + sdp.type + ", delay=" + delta + "ms");
                /**
                 * 当设置成功后
                 * 在PeerConnectionObserver中的onSetSuccess方法会被回调
                 */
                peerConnectionClient.setRemoteDescription(sdp, connectionId);
            }
        });
    }

    @Override
    public void onRemoteIceCandidate(final IceCandidate candidate,
                                     String connectionId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG,

                            "Received ICE candidate for a non-initialized peer connection.");
                    return;
                }
                peerConnectionClient.addRemoteIceCandidate(candidate,
                        connectionId);
            }
        });
    }

    @Override
    public void onRemoteIceCandidatesRemoved(final IceCandidate[] candidates,
                                             String connectionId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG, "Received ICE candidate removals for a non-initialized peer connection.");
                    return;
                }
                peerConnectionClient
                        .removeRemoteIceCandidates(candidates,
                                connectionId);
            }
        });
    }

    @Override
    public void onChannelClose() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast("Remote end hung up; dropping PeerConnection");
                disconnect();
            }
        });
    }

    @Override
    public void onChannelError(final String description) {
        reportError(description);
    }

    @Override
    public void onPeerConnectionCreated(
            String connectionId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                peerConnectionClient.createOffer(connectionId);
            }
        });
    }

    // -----Implementation of PeerConnectionClient.PeerConnectionEvents.---------
    // Send local peer connection SDP and ICE candidates to remote party.
    // All callbacks are invoked from peer connection client looper thread and
    // are routed to UI thread.
    @Override
    public void onLocalDescription(
            final SessionDescription sdp,
                                   String connectionId) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        Log.i(TAG,"............onLocalDescription...........");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (appRtcClient != null) {
                    logAndToast("Sending " + sdp.type + ", delay=" + delta + "ms");
                    if (signalingParameters.initiator) {
                        appRtcClient.sendOfferSdp(sdp,
                                connectionId);
                    } else {
                        appRtcClient.sendAnswerSdp(sdp,
                                connectionId);
                    }
                }
                if (peerConnectionParameters.videoMaxBitrate > 0) {
                    Log.d(TAG, "Set video maximum bitrate: "
                            + peerConnectionParameters.videoMaxBitrate);
                    peerConnectionClient.setVideoMaxBitrate(
                            peerConnectionParameters.videoMaxBitrate);
                }
            }
        });
    }

    @Override
    public void onIceCandidate(final IceCandidate candidate,
                               String connectionId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (appRtcClient != null) {
                    appRtcClient.sendLocalIceCandidate(candidate,
                            connectionId);
                }
            }
        });
    }

    @Override
    public void onIceCandidatesRemoved(final IceCandidate[] candidates,
                                       String connectionId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (appRtcClient != null) {
                    appRtcClient.sendLocalIceCandidateRemovals(candidates,
                            connectionId);
                }
            }
        });
    }

    @Override
    public void onIceConnected(String connectionId) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast("ICE connected, delay=" + delta + "ms");
                iceConnected = true;
                callConnected();
            }
        });
    }

    @Override
    public void onIceDisconnected(String connectionId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast("ICE disconnected");
                iceConnected = false;
                disconnect();
            }
        });
    }

    @Override
    public void onPeerConnectionClosed() {
    }

    @Override
    public void onPeerConnectionStatsReady(final StatsReport[] reports) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isError && iceConnected) {
                    hudFragment.updateEncoderStatistics(reports);
                }
            }
        });
    }

    @Override
    public void onRemoteStreamAdded(MediaStream stream,
                                    String connectionId) {
        Log.i(TAG,"onRemoteStreamAdded:" + stream.getId());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //peerConnectionClient
                //        .addRemoteMediaStream(connectionId);
                stream.videoTracks.get(0)
                        .addSink(remoteProxyRenderer);
            }
        });
    }

    @Override
    public void onRemoteStreamRemoved(MediaStream stream,String connectionId) {

    }

    @Override
    public void onDataChannel(DataChannel dataChannel,String connectionId) {

    }

    @Override
    public void onPeerConnectionError(final String description) {
        reportError(description);
    }

    @Override
    public void onBufferedAmountChange(long l, DataChannel channel,String connectionId) {

    }

    @Override
    public void onStateChange(DataChannel channel,String connectionId) {

    }

    @Override
    public void onMessage(DataChannel.Buffer buffer, DataChannel channel,String connectionId) {

    }
}
