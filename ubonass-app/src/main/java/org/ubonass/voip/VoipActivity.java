package org.ubonass.voip;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.ubonass.*;
import org.ubonass.R;
import org.ubonass.ui.CircularCoverView;
import org.ubonass.utils.ColorUtils;
import org.ubonass.utils.DensityUtils;
import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.RendererCommon.ScalingType;
import org.webrtcpeer.*;
import org.webrtcpeer.AppRTCAudioManager.AudioManagerEvents;
import org.webrtcpeer.AppRTCAudioManager.AudioDevice;

import java.io.IOException;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import org.webrtcpeer.AppRTCClient.SignalingParameters;

public class VoipActivity extends BaseActivity
        implements View.OnClickListener {
    private static String TAG = "VoipActivity";
    public static String ACTION = "ACTION";
    public static String RING = "RING";
    public static String CALLING = "CALLING";

    @Nullable
    private SurfaceViewRenderer targetRenderer;
    @Nullable
    private SurfaceViewRenderer selfRenderer;
    @Nullable
    private AppRTCAudioManager audioManager;
    @Nullable
    private Chronometer timer;
    @Nullable
    private String action;
    @Nullable
    private String targetId;

    private Boolean isTalking = false;

    private long callStartedTimeMs;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_voip);
        targetId = getIntent().getStringExtra("targetId");
        action = getIntent().getStringExtra(ACTION);
        targetRenderer = findViewById(R.id.voip_surface_target);
        selfRenderer = findViewById(R.id.voip_surface_self);
        selfRenderer.setZOrderMediaOverlay(true);
        timer = (Chronometer) findViewById(R.id.timer);
        targetRenderer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isTalking) {
                    findViewById(R.id.talking_view)
                            .setVisibility(findViewById(R.id.talking_view)
                                    .getVisibility() == View.VISIBLE
                                    ? View.INVISIBLE : View.VISIBLE);
                }
            }
        });

        ((TextView) findViewById(R.id.targetid_text)).setText(targetId);
        findViewById(R.id.head_bg).setBackgroundColor(ColorUtils.getColor(VoipActivity.this, targetId));
        ((CircularCoverView) findViewById(R.id.head_cover)).setCoverColor(Color.parseColor("#000000"));
        int cint = DensityUtils.dip2px(VoipActivity.this, 45);
        ((CircularCoverView) findViewById(R.id.head_cover)).setRadians(cint, cint, cint, cint, 0);

        findViewById(R.id.calling_hangup).setOnClickListener(this);
        findViewById(R.id.talking_hangup).setOnClickListener(this);
        findViewById(R.id.switch_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //voipManager.switchCamera();
            }
        });
        findViewById(R.id.screen_btn).setOnClickListener(this);

        if (action.equals(CALLING)) {
            showCallingView();
        }
        /**
         * 开始打开摄像头和响起呼叫铃声
         */
        final EglBase eglBase = EglBase.create();
        // Create video renderers.
        targetRenderer.init(eglBase.getEglBaseContext(), null);
        targetRenderer.setScalingType(ScalingType.SCALE_ASPECT_FIT);
        targetRenderer.setZOrderMediaOverlay(true);
        targetRenderer.setEnableHardwareScaler(true /* enabled */);
        selfRenderer.init(eglBase.getEglBaseContext(), null);
        selfRenderer.setScalingType(ScalingType.SCALE_ASPECT_FILL);
        selfRenderer.setEnableHardwareScaler(false /* enabled */);
        preparePeerConnection(eglBase);
        startCall();
    }


    private void showCallingView() {
        findViewById(R.id.calling_view).setVisibility(View.VISIBLE);
        findViewById(R.id.talking_view).setVisibility(View.INVISIBLE);
    }

    private void showTalkingView() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                isTalking = true;
                findViewById(R.id.calling_view).setVisibility(View.INVISIBLE);
                findViewById(R.id.talking_view).setVisibility(View.VISIBLE);
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) findViewById(R.id.talking_view).getLayoutParams();
                flp.width = findViewById(R.id.calling_view).getWidth();
                findViewById(R.id.talking_view).setLayoutParams(flp);
                timer.setBase(SystemClock.elapsedRealtime());
                timer.start();
            }
        });
    }

    private void startCall() {
        //callStartedTimeMs = System.currentTimeMillis();
        // Create and audio manager that will take care of audio routing,
        // audio modes, audio device enumeration etc.
        audioManager = AppRTCAudioManager.create(getApplicationContext());
        // Store existing audio settings and change audio mode to
        // MODE_IN_COMMUNICATION for best possible VoIP performance.
        AppManager.logger(TAG, "Starting the audio manager...");
        audioManager.start(new AudioManagerEvents() {
            // This method will be called each time the number of available audio
            // devices has changed.
            @Override
            public void onAudioDeviceChanged(
                    AudioDevice audioDevice, Set<AudioDevice> availableAudioDevices) {
                onAudioManagerDevicesChanged(audioDevice, availableAudioDevices);
            }
        });
        //开始创建1V1的房间,并且获取Token
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                callStartedTimeMs++;
                if (callStartedTimeMs >20
                        && !isTalking) {
                    //如果30秒内无人接听则挂断
                    callStartedTimeMs = 0;
                    timer.cancel();
                    audioManager.stopMeida();
                    handupWithNobodyIsAnswering();
                }
            }
        };
        timer.schedule(task,0,1500);
    }

    // This method is called when the audio manager reports audio device change,
    // e.g. from wired headset to speakerphone.
    private void onAudioManagerDevicesChanged(
            final AudioDevice device, final Set<AudioDevice> availableDevices) {
        AppManager.logger(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
                + "selected: " + device);
        // TODO(henrika): add callback handler.
        //audioManager.selectAudioDevice(AudioDevice.SPEAKER_PHONE);
        audioManager.playAssetsMedia("ring.mp3");
    }

    private void handupWithNobodyIsAnswering() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AppManager.logAndToast("对方无法接听...");
                finish();
            }
        });
    }


    @Override
    public void onResume() {
        super.onResume();

    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onRestart() {
        super.onRestart();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.calling_hangup:

                break;
            case R.id.talking_hangup:

                break;
            case R.id.screen_btn:

                break;
        }
    }

    private static final int REQUEST_CODE = 1;
    private MediaProjectionManager mMediaProjectionManager;
    //private ScreenRecorder mRecorder;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        MediaProjection mediaProjection =
                mMediaProjectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            Log.e("@@", "media projection is null");
            return;
        }
        findViewById(R.id.screen_btn).setSelected(true);

        /*// video size
        final int width = StarRtcCore.bigVideoW;
        final int height = StarRtcCore.bigVideoH;
        final int bitrate = StarRtcCore.bitRateBig * 1000;

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        final int dpi = metrics.densityDpi;
        mRecorder = new ScreenRecorder(width, height, bitrate, dpi, mediaProjection);
        StarRtcCore.getInstance().voipShareScreen(mRecorder);*/
    }

    private void stopAndFinish() {
        /*if (starRTCAudioManager != null) {
            starRTCAudioManager.stop();
        }*/
        VoipActivity.this.finish();
    }

}
