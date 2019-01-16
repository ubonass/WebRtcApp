package org.ubonass;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.PermissionChecker;
import android.util.Log;
import android.webkit.URLUtil;
import android.widget.Toast;
import org.apache.commons.lang3.RandomStringUtils;
import org.ubonass.database.CoreDataBase;
import org.ubonass.database.HistoryBean;
import org.ubonass.database.MessageBean;
import org.utilities_android.SecurityCertificatation;
import org.webrtcpeer.DataChannelParameters;
import org.webrtcpeer.PeerConnectionParameters;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.util.List;
import java.util.Random;

public class AppManager {

    private static final String TAG = "AppManager";
    private static Boolean debug = true;
    @Nullable
    private static int[] mHeadIconIds;
    @Nullable
    private static Toast logToast;
    @Nullable
    public static String userId = "";
    @Nullable
    public static Context appContext;
    @Nullable
    private static CoreDataBase coreDB;
    @Nullable
    private static SSLSocketFactory sslSocketFactory;
    @Nullable
    private RoomParametersFetcher roomParametersFetcher;
    @Nullable
    private static SettingsSharedParameters settingsSharedParameters;
    @Nullable
    private static MediaPlayer mediaPlayer;

    // List of mandatory application permissions.
    private static final String[] MANDATORY_PERMISSIONS =
            {"android.permission.MODIFY_AUDIO_SETTINGS",
                    "android.permission.RECORD_AUDIO",
                    "android.permission.INTERNET",
                    "android.permission.CAMERA"};

    public static void init(Context context) {
        appContext = context;
        coreDB = new CoreDataBase();
        userId = loadSharedData(context, "userId", userId);
        settingsSharedParameters =
                new SettingsSharedParameters(context);
        if (userId.equals("")) {
            userId = "" + (new Random().nextInt(900000) + 100000);
            //userId = generateRandomChain();
            saveUserId(userId);
        }
        try {
            sslSocketFactory = SecurityCertificatation
                    .getSslSocketFactory(
                            context.getResources().getAssets().open("openvidu-selfsigned.cer"),
                            null, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mediaPlayer = new MediaPlayer();
    }

    @Nullable
    public static SSLSocketFactory getSslSocketFactory() {
        return sslSocketFactory;
    }

    @Nullable
    public SettingsSharedParameters getSettingsSharedParameters() {
        return settingsSharedParameters;
    }

    public String generateRandomChain() {
        return RandomStringUtils.randomAlphanumeric(8).toLowerCase();
    }

    public static void requestPermission() {
        for (String permission : MANDATORY_PERMISSIONS) {
            if (PermissionChecker
                    .checkCallingOrSelfPermission(appContext, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                //logAndToast("Permission " + permission + " is not granted");
                ActivityCompat.requestPermissions((Activity) appContext,
                        new String[]{permission}, 1);
            }
        }
    }

    public static void startCallMedia(){
        Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        try {
            mediaPlayer.setDataSource(appContext, alert);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
            mediaPlayer.setLooping(true);    //循环播放开
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void stopCallMedia(){
        if (mediaPlayer.isPlaying())
            mediaPlayer.stop();
    }

    public static void saveUserId(String id) {
        userId = id;
        saveSharedData(appContext, "userId", userId);
    }

    public static void logAndToast(String str) {
        try {
            if (logToast != null) {
                logToast.setText(str);
                logToast.setDuration(Toast.LENGTH_SHORT);
            } else {
                logToast = Toast.makeText(appContext, str, Toast.LENGTH_SHORT);
            }
            logToast.show();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void logAndToast(Context context, String str) {
        try {
            if (logToast != null) {
                logToast.setText(str);
                logToast.setDuration(Toast.LENGTH_SHORT);
            } else {
                logToast = Toast.makeText(context, str, Toast.LENGTH_SHORT);
            }
            logToast.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean validateUrl(String url) {
        if (URLUtil.isHttpsUrl(url) || URLUtil.isHttpUrl(url)) {
            return true;
        }

        new AlertDialog.Builder(appContext)
                .setTitle(appContext.getText(R.string.invalid_url_title))
                .setMessage(appContext.getString(R.string.invalid_url_text, url))
                .setCancelable(false)
                .setNeutralButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        })
                .create()
                .show();
        return false;
    }

    public static int getLogoImage(Context context, String userID) {
        if (mHeadIconIds == null) {
            TypedArray ar = context.getResources().obtainTypedArray(R.array.head_images);
            int len = ar.length();
            mHeadIconIds = new int[len];
            for (int i = 0; i < len; i++) {
                mHeadIconIds[i] = ar.getResourceId(i, 0);
            }
            ar.recycle();
        }

        if (userID.isEmpty()) {
            return mHeadIconIds[70];
        } else {
            int intId = 0;
            char[] chars = userID.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                intId += (int) chars[i];
            }
            return mHeadIconIds[intId % 70];
        }
    }

    public static List<HistoryBean> getHistoryList(String type) {
        if (coreDB != null) {
            return coreDB.getHistory(type);
        } else {
            return null;
        }
    }

    public static void setHistory(HistoryBean history, Boolean hasRead) {
        if (coreDB != null) {
            coreDB.setHistory(history, hasRead);
        }
    }

    public static List<MessageBean> getMessageList(String conversationId) {
        if (coreDB != null) {
            return coreDB.getMessageList(conversationId);
        } else {
            return null;
        }
    }

    public static void saveMessage(MessageBean messageBean) {
        if (coreDB != null) {
            coreDB.setMessage(messageBean);
        }
    }

    public static void saveSharedData(Context context, String key, String value) {
        SharedPreferences sp = context.getSharedPreferences("ubonassDemo",
                Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(key, value);
        editor.commit();
    }

    public static String loadSharedData(Context context, String key) {
        SharedPreferences sp = context.getSharedPreferences("ubonassDemo",
                Activity.MODE_PRIVATE);
        return sp.getString(key, "");
    }

    public static String loadSharedData(Context context, String key, String defValue) {
        SharedPreferences sp = context.getSharedPreferences("ubonassDemo",
                Activity.MODE_PRIVATE);
        return sp.getString(key, defValue);
    }

    public static void saveVoipUserId(Context context, String uid) {
        String history = loadSharedData(context, "voipHistory");
        if (history.length() > 0) {
            String[] arr = history.split(",,");
            String newHistory = "";
            for (int i = 0; i < arr.length; i++) {
                if (i == 0) {
                    if (arr[i].equals(uid)) return;
                    newHistory += arr[i];
                } else {
                    if (arr[i].equals(uid)) continue;
                    newHistory += ",," + arr[i];
                }
            }
            if (newHistory.length() == 0) {
                newHistory = uid;
            } else {
                newHistory = uid + ",," + newHistory;
            }
            saveSharedData(context, "voipHistory", newHistory);
        } else {
            saveSharedData(context, "voipHistory", uid);
        }
    }

    public static void setDebug(Boolean b) {
        debug = b;
    }

    public static void logger(String tag, String msg) {
        if (debug) {
            Log.i("ubonass_" + tag, msg);
        }
    }

    @Nullable
    public static PeerConnectionParameters
    getPeerConnectionParameters(boolean loopback) {

        // Video call enabled flag.
        boolean videoCallEnabled =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_videocall_key,
                        R.string.pref_videocall_default);

        // Use screencapture option.
        boolean useScreencapture =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_screencapture_key,
                        R.string.pref_screencapture_default);

        // Use Camera2 option.
        boolean useCamera2 =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_camera2_key,
                        R.string.pref_camera2_default);

        // Get default codecs.
        String videoCodec =
                settingsSharedParameters.sharedPrefGetString(
                        R.string.pref_videocodec_key,
                        R.string.pref_videocodec_default);
        String audioCodec =
                settingsSharedParameters.sharedPrefGetString(
                        R.string.pref_audiocodec_key,
                        R.string.pref_audiocodec_default);

        // Check HW codec flag.
        boolean hwCodec =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_hwcodec_key,
                        R.string.pref_hwcodec_default);

        // Check Capture to texture.
        boolean captureToTexture =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_capturetotexture_key,
                        R.string.pref_capturetotexture_default);

        // Check FlexFEC.
        boolean videoFlexfecEnabled =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_flexfec_key,
                        R.string.pref_flexfec_default);

        // Check Disable Audio Processing flag.
        boolean noAudioProcessing =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_noaudioprocessing_key,
                        R.string.pref_noaudioprocessing_default);

        boolean aecDump =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_aecdump_key,
                        R.string.pref_aecdump_default);

        boolean saveInputAudioToFile =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_enable_save_input_audio_to_file_key,
                        R.string.pref_enable_save_input_audio_to_file_default);

        // Check OpenSL ES enabled flag.
        boolean useOpenSLES =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_opensles_key,
                        R.string.pref_opensles_default);

        // Check Disable built-in AEC flag.
        boolean disableBuiltInAEC =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_disable_built_in_aec_key,
                        R.string.pref_disable_built_in_aec_default
                );

        // Check Disable built-in AGC flag.
        boolean disableBuiltInAGC =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_disable_built_in_agc_key,
                        R.string.pref_disable_built_in_agc_default);

        // Check Disable built-in NS flag.
        boolean disableBuiltInNS =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_disable_built_in_ns_key,
                        R.string.pref_disable_built_in_ns_default);

        // Check Disable gain control
        boolean disableWebRtcAGCAndHPF =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_disable_webrtc_agc_and_hpf_key,
                        R.string.pref_disable_webrtc_agc_default);

        // Get video resolution from settings.
        int videoWidth = 0;
        int videoHeight = 0;

        String resolution =
                settingsSharedParameters.sharedPrefGetString(
                        R.string.pref_resolution_key,
                        R.string.pref_resolution_default);
        String[] dimensions = resolution.split("[ x]+");
        if (dimensions.length == 2) {
            try {
                videoWidth = Integer.parseInt(dimensions[0]);
                videoHeight = Integer.parseInt(dimensions[1]);
            } catch (NumberFormatException e) {
                videoWidth = 0;
                videoHeight = 0;
                Log.e(TAG, "Wrong video resolution setting: " + resolution);
            }
        }


        // Get camera fps from settings.
        int videoFps = 0;
        //
        String fps =
                settingsSharedParameters.sharedPrefGetString(
                        R.string.pref_fps_key, R.string.pref_fps_default);
        String[] fpsValues = fps.split("[ x]+");
        if (fpsValues.length == 2) {
            try {
                videoFps = Integer.parseInt(fpsValues[0]);
            } catch (NumberFormatException e) {
                videoFps = 0;
                Log.e(TAG, "Wrong camera fps setting: " + fps);
            }
        }

        // Check capture quality slider flag.
        boolean captureQualitySlider =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_capturequalityslider_key,
                        R.string.pref_capturequalityslider_default);
        // Get video and audio start bitrate.
        int videoStartBitrate = 0;
        String bitrateVideoTypeDefault =
                appContext.getString(
                        R.string.pref_maxvideobitrate_default);

        String bitrateVideoType =
                settingsSharedParameters.sharedPrefGetString(
                        R.string.pref_maxvideobitrate_key,
                        R.string.pref_maxvideobitrate_default);

        if (!bitrateVideoType.equals(bitrateVideoTypeDefault)) {
            String bitrateValue = settingsSharedParameters.sharedPrefGetString(
                    R.string.pref_maxvideobitratevalue_key,
                    R.string.pref_maxvideobitratevalue_default);
            videoStartBitrate = Integer.parseInt(bitrateValue);
        }

        int audioStartBitrate = 0;
        String bitrateAudioTypeDefault =
                appContext.getString(
                        R.string.pref_startaudiobitrate_default);
        String bitrateAudioType =
                settingsSharedParameters.sharedPrefGetString(
                        R.string.pref_startaudiobitrate_key,
                        R.string.pref_startaudiobitrate_default);

        if (!bitrateAudioType.equals(bitrateAudioTypeDefault)) {
            String bitrateValue = settingsSharedParameters.sharedPrefGetString(
                    R.string.pref_startaudiobitratevalue_key,
                    R.string.pref_startaudiobitratevalue_default);
            audioStartBitrate = Integer.parseInt(bitrateValue);
        }

        // Check statistics display option.
        boolean displayHud =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_displayhud_key,
                        R.string.pref_displayhud_default);

        boolean tracing = settingsSharedParameters.sharedPrefGetBoolean(
                R.string.pref_tracing_key,
                R.string.pref_tracing_default);

        // Check Enable RtcEventLog.
        boolean enableRtcEventLog =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_enable_rtceventlog_key,
                        R.string.pref_enable_rtceventlog_default);

        boolean useLegacyAudioDevice =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_use_legacy_audio_device_key,
                        R.string.pref_use_legacy_audio_device_default);

        // Get datachannel options
        boolean dataChannelEnabled =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_enable_datachannel_key,
                        R.string.pref_enable_datachannel_default);
        boolean ordered =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_ordered_key,
                        R.string.pref_ordered_default);
        boolean negotiated =
                settingsSharedParameters.sharedPrefGetBoolean(
                        R.string.pref_negotiated_key,
                        R.string.pref_negotiated_default);
        int maxRetransmitTimeMs =
                settingsSharedParameters.sharedPrefGetInteger(
                        R.string.pref_max_retransmit_time_ms_key,
                        R.string.pref_max_retransmit_time_ms_default);
        int maxRetransmits =
                settingsSharedParameters.sharedPrefGetInteger(
                        R.string.pref_max_retransmits_key,
                        R.string.pref_max_retransmits_default);
        int id = settingsSharedParameters.sharedPrefGetInteger(
                R.string.pref_data_id_key,
                R.string.pref_data_id_default);
        String protocol =
                settingsSharedParameters.sharedPrefGetString(
                        R.string.pref_data_protocol_key,
                        R.string.pref_data_protocol_default);
        DataChannelParameters dataChannelParameters = null;
        if (dataChannelEnabled) {
            dataChannelParameters =
                    new DataChannelParameters(
                            ordered,
                            maxRetransmitTimeMs,
                            maxRetransmits,
                            protocol,
                            negotiated, id);
        }

        PeerConnectionParameters peerConnectionParameters =
                new PeerConnectionParameters(
                        videoCallEnabled,
                        loopback,
                        tracing,
                        videoWidth,
                        videoHeight,
                        videoFps,
                        videoStartBitrate,
                        videoCodec,
                        hwCodec,
                        videoFlexfecEnabled,
                        audioStartBitrate,
                        audioCodec,
                        noAudioProcessing,
                        aecDump,
                        saveInputAudioToFile,
                        useOpenSLES,
                        disableBuiltInAEC,
                        disableBuiltInAGC,
                        disableBuiltInNS,
                        disableWebRtcAGCAndHPF,
                        enableRtcEventLog,
                        useLegacyAudioDevice,
                        dataChannelParameters);
        return peerConnectionParameters;
    }

}
