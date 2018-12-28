package org.openvidu_demo;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import org.webrtcpeer.AppRTCClient.RoomConnectionParameters;
import org.webrtcpeer.DataChannelParameters;
import org.webrtcpeer.PeerConnectionParameters;

import java.util.ArrayList;

/**
 *
 */
public class ConnectionParameter {

    private static final String TAG = "PeerConnectionHelper";
    private static String keyprefVideoCall;
    private static String keyprefScreencapture;
    private static String keyprefCamera2;
    private static String keyprefResolution;
    private static String keyprefFps;
    private static String keyprefCaptureQualitySlider;
    private static String keyprefMaxVideoBitrateType;
    private static String keyprefMaxVideoBitrateValue;
    private static String keyPrefVideoCodec;
    private static String keyprefHwCodec;
    private static String keyprefCaptureToTexture;
    private static String keyprefFlexfec;

    private static String keyprefStartAudioBitrateType;
    private static String keyprefStartAudioBitrateValue;
    private static String keyPrefAudioCodec;
    private static String keyprefNoAudioProcessing;
    private static String keyprefAecDump;
    private static String keyprefEnableSaveInputAudioToFile;
    private static String keyprefOpenSLES;
    private static String keyprefDisableBuiltInAEC;
    private static String keyprefDisableBuiltInAGC;
    private static String keyprefDisableBuiltInNS;
    private static String keyprefDisableWebRtcAGCAndHPF;
    private static String keyprefSpeakerphone;

    private static String keyPrefRoomServerUrl;
    private static String keyPrefDisplayHud;
    private static String keyPrefTracing;
    private static String keyprefEnabledRtcEventLog;

    private static String keyprefEnableDataChannel;
    private static String keyprefOrdered;
    private static String keyprefMaxRetransmitTimeMs;
    private static String keyprefMaxRetransmits;
    private static String keyprefDataProtocol;
    private static String keyprefNegotiated;
    private static String keyprefDataId;
    private static String keyprefUseLegacyAudioDevice;


    private static String keyprefStartVideoBitrateType;
    private static String keyprefStartVideoBitrateValue;


    private static String keyprefRoomServerUrl;
    private static String keyprefRoom;
    private static String keyprefRoomList;
    private static ArrayList<String> roomList = new ArrayList<>();

    private static PeerConnectionParameters peerConnectionParameters;

    private static RoomConnectionParameters roomConnectionParameters;

    public static SharedPreferences getSharedPref() {
        return sharedPref;
    }

    private static SharedPreferences sharedPref;

    private static boolean loopback = false;

    public static PeerConnectionParameters

        getPeerConnectionParameters(Context context) {
        if (sharedPref == null)
            sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        keyprefVideoCall = context.getString(R.string.pref_videocall_key);
        keyprefScreencapture = context.getString(R.string.pref_screencapture_key);
        keyprefCamera2 = context.getString(R.string.pref_camera2_key);
        keyprefResolution = context.getString(R.string.pref_resolution_key);
        keyprefFps = context.getString(R.string.pref_fps_key);
        keyprefCaptureQualitySlider = context.getString(R.string.pref_capturequalityslider_key);
        keyprefMaxVideoBitrateType = context.getString(R.string.pref_maxvideobitrate_key);
        keyprefMaxVideoBitrateValue = context.getString(R.string.pref_maxvideobitratevalue_key);
        keyPrefVideoCodec = context.getString(R.string.pref_videocodec_key);
        keyprefHwCodec = context.getString(R.string.pref_hwcodec_key);
        keyprefCaptureToTexture = context.getString(R.string.pref_capturetotexture_key);
        keyprefFlexfec = context.getString(R.string.pref_flexfec_key);

        keyprefStartVideoBitrateType = context.getString(R.string.pref_startvideobitrate_key);
        keyprefStartVideoBitrateValue = context.getString(R.string.pref_startvideobitratevalue_key);

        keyprefStartAudioBitrateType = context.getString(R.string.pref_startaudiobitrate_key);
        keyprefStartAudioBitrateValue = context.getString(R.string.pref_startaudiobitratevalue_key);

        keyPrefAudioCodec = context.getString(R.string.pref_audiocodec_key);
        keyprefNoAudioProcessing = context.getString(R.string.pref_noaudioprocessing_key);
        keyprefAecDump = context.getString(R.string.pref_aecdump_key);
        keyprefEnableSaveInputAudioToFile =
                context.getString(R.string.pref_enable_save_input_audio_to_file_key);
        keyprefOpenSLES = context.getString(R.string.pref_opensles_key);
        keyprefDisableBuiltInAEC = context.getString(R.string.pref_disable_built_in_aec_key);
        keyprefDisableBuiltInAGC = context.getString(R.string.pref_disable_built_in_agc_key);
        keyprefDisableBuiltInNS = context.getString(R.string.pref_disable_built_in_ns_key);
        keyprefDisableWebRtcAGCAndHPF = context.getString(R.string.pref_disable_webrtc_agc_and_hpf_key);
        keyprefSpeakerphone = context.getString(R.string.pref_speakerphone_key);

        keyprefEnableDataChannel = context.getString(R.string.pref_enable_datachannel_key);
        keyprefOrdered = context.getString(R.string.pref_ordered_key);
        keyprefMaxRetransmitTimeMs = context.getString(R.string.pref_max_retransmit_time_ms_key);
        keyprefMaxRetransmits = context.getString(R.string.pref_max_retransmits_key);
        keyprefDataProtocol = context.getString(R.string.pref_data_protocol_key);
        keyprefNegotiated = context.getString(R.string.pref_negotiated_key);
        keyprefDataId = context.getString(R.string.pref_data_id_key);

        keyPrefRoomServerUrl = context.getString(R.string.pref_room_server_url_key);
        keyPrefDisplayHud = context.getString(R.string.pref_displayhud_key);
        keyPrefTracing = context.getString(R.string.pref_tracing_key);
        keyprefEnabledRtcEventLog = context.getString(R.string.pref_enable_rtceventlog_key);
        keyprefUseLegacyAudioDevice = context.getString(R.string.pref_use_legacy_audio_device_key);


        loopback = false;

        boolean videoCallEnabled = sharedPref.getBoolean(keyprefVideoCall,
                Boolean.valueOf(context.getString(R.string.pref_videocall_default)));

        // Use Camera2 option.
        boolean useCamera2 = sharedPref.getBoolean(keyprefCamera2,
                Boolean.valueOf(context.getString(R.string.pref_camera2_default)));

        // Get default codecs.
        String videoCodec = sharedPref.getString(keyPrefVideoCodec,
                context.getString(R.string.pref_videocodec_default));

        String audioCodec = sharedPref.getString(keyPrefAudioCodec,
                context.getString(R.string.pref_audiocodec_default));

        // Check HW codec flag.
        boolean hwCodec = sharedPref.getBoolean(keyprefHwCodec,
                Boolean.valueOf(context.getString(R.string.pref_hwcodec_default)));

        // Check Capture to texture.
        boolean captureToTexture = sharedPref.getBoolean(keyprefCaptureToTexture,
                Boolean.valueOf(context.getString(R.string.pref_capturetotexture_default)));
        // Check FlexFEC.
        boolean videoFlexfecEnabled = sharedPref.getBoolean(keyprefFlexfec,
                Boolean.valueOf(context.getString(R.string.pref_flexfec_default)));

        // Check Disable Audio Processing flag.
        boolean noAudioProcessing = sharedPref.getBoolean(
                keyprefNoAudioProcessing,
                Boolean.valueOf(context.getString(R.string.pref_noaudioprocessing_default)));

        // Check Disable Audio Processing flag.
        boolean aecDump = sharedPref.getBoolean(
                keyprefAecDump,
                Boolean.valueOf(context.getString(R.string.pref_aecdump_default)));

        boolean saveInputAudioToFile = sharedPref.getBoolean(
                keyprefEnableSaveInputAudioToFile,
                Boolean.valueOf(context.getString(R.string.pref_enable_save_input_audio_to_file_default)));

        // Check OpenSL ES enabled flag.
        boolean useOpenSLES = sharedPref.getBoolean(
                keyprefOpenSLES,
                Boolean.valueOf(context.getString(R.string.pref_opensles_default)));

        // Check Disable built-in AEC flag.
        boolean disableBuiltInAEC = sharedPref.getBoolean(
                keyprefDisableBuiltInAEC,
                Boolean.valueOf(context.getString(R.string.pref_disable_built_in_aec_default)));

        // Check Disable built-in AGC flag.
        boolean disableBuiltInAGC = sharedPref.getBoolean(
                keyprefDisableBuiltInAGC,
                Boolean.valueOf(context.getString(R.string.pref_disable_built_in_agc_default)));


        // Check Disable built-in NS flag.
        boolean disableBuiltInNS = sharedPref.getBoolean(
                keyprefDisableBuiltInNS,
                Boolean.valueOf(context.getString(R.string.pref_disable_built_in_ns_default)));

        // Check Disable gain control
        boolean disableWebRtcAGCAndHPF = sharedPref.getBoolean(
                keyprefDisableWebRtcAGCAndHPF,
                Boolean.valueOf(context.getString(R.string.pref_disable_webrtc_agc_default)));

        // Get video resolution from settings.
        int videoWidth = 0;
        int videoHeight = 0;
        String resolution = sharedPref.getString(keyprefResolution,
                context.getString(R.string.pref_resolution_default));
        String[] dimensions = resolution.split("[ x]+");
        if (dimensions.length == 2) {
            try {
                videoWidth = Integer.parseInt(dimensions[0]);
                videoHeight = Integer.parseInt(dimensions[1]);
            } catch (NumberFormatException e) {
                videoWidth = 0;
                videoHeight = 0;
                Log.e(TAG,"Wrong video resolution setting: " + resolution);
            }
        }

        // Get camera fps from settings.
        int videoFps = 0;
        String fps = sharedPref.getString(keyprefFps,
                context.getString(R.string.pref_fps_default));
        String[] fpsValues = fps.split("[ x]+");
        if (fpsValues.length == 2) {
            try {
                videoFps = Integer.parseInt(fpsValues[0]);
            } catch (NumberFormatException e) {
                Log.e(TAG,"Wrong camera fps setting: " + fps);
            }
        }
        // Check capture quality slider flag.
        boolean captureQualitySlider = sharedPref.getBoolean(keyprefCaptureQualitySlider,
                Boolean.valueOf(context.getString(R.string.pref_capturequalityslider_default)));

        // Get video and audio start bitrate.
        int videoStartBitrateValue = 0;
        String bitrateTypeDefault = context.getString(
                R.string.pref_startvideobitrate_default);
        String bitrateType = sharedPref.getString(
                keyprefStartVideoBitrateType, "");
        if (!bitrateType.equals(bitrateTypeDefault)) {
            String bitrateValue = sharedPref.getString(keyprefStartVideoBitrateValue,
                    context.getString(R.string.pref_startvideobitratevalue_default));
            videoStartBitrateValue = Integer.parseInt(bitrateValue);
        }

        int videoMaxBitrateVaule = 0;
        String videobitrateMaxTypeDefault = context.getString(
                R.string.pref_maxvideobitrate_default);
        String videobitrateMaxType = sharedPref.getString(
                keyprefMaxVideoBitrateType, "");
        if (!videobitrateMaxType.equals(videobitrateMaxTypeDefault)) {
            String videoMaxbitrateValue = sharedPref.getString(keyprefStartVideoBitrateValue,
                    context.getString(R.string.pref_maxvideobitratevalue_default));
            videoMaxBitrateVaule = Integer.parseInt(videoMaxbitrateValue);
        }

        int audioStartBitrate = 0;
        bitrateTypeDefault = context.getString(R.string.pref_startaudiobitrate_default);
        bitrateType = sharedPref.getString(
                keyprefStartAudioBitrateType, bitrateTypeDefault);
        if (!bitrateType.equals(bitrateTypeDefault)) {
            String bitrateValue = sharedPref.getString(keyprefStartAudioBitrateValue,
                    context.getString(R.string.pref_startaudiobitratevalue_default));
            audioStartBitrate = Integer.parseInt(bitrateValue);
        }

        // Check statistics display option.
        boolean displayHud = sharedPref.getBoolean(keyPrefDisplayHud,
                Boolean.valueOf(context.getString(R.string.pref_displayhud_default)));

        boolean tracing = sharedPref.getBoolean(
                keyPrefTracing, Boolean.valueOf(context.getString(R.string.pref_tracing_default)));


        // Get datachannel options
        boolean dataChannelEnabled = sharedPref.getBoolean(
                keyprefEnableDataChannel, Boolean.valueOf(context.getString(R.string.pref_enable_datachannel_default)));

        boolean ordered = sharedPref.getBoolean(
                keyprefOrdered, Boolean.valueOf(context.getString(R.string.pref_ordered_default)));

        boolean negotiated = sharedPref.getBoolean(
                keyprefNegotiated, Boolean.valueOf(context.getString(R.string.pref_negotiated_default)));

        int maxRetrMs = sharedPref.getInt(
                keyprefMaxRetransmitTimeMs,
                Integer.valueOf(context.getString(R.string.pref_max_retransmit_time_ms_default)));
        int maxRetr = sharedPref.getInt(
                keyprefMaxRetransmits,
                Integer.valueOf(context.getString(R.string.pref_max_retransmits_default)));

        int id = sharedPref.getInt(
                keyprefDataId,
                Integer.valueOf(context.getString(R.string.pref_data_id_default)));

        String protocol = sharedPref.getString(
                keyprefDataProtocol,
                String.valueOf(context.getString(R.string.pref_data_protocol_default)));

        DataChannelParameters dataChannelParameters = null;
        if (dataChannelEnabled) {
            dataChannelParameters =
                    new DataChannelParameters(ordered,maxRetrMs,maxRetr,protocol,negotiated,id);
        }

        peerConnectionParameters =
                new PeerConnectionParameters(videoCallEnabled,
                        loopback,
                        tracing,
                        videoWidth,
                        videoHeight,
                        videoFps,
                        videoMaxBitrateVaule,
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
                        true,
                        false,
                        dataChannelParameters);

        return peerConnectionParameters;
    }

    public static RoomConnectionParameters
    getRoomConnectionParameters(Context context) {
        if (sharedPref == null)
            sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

        keyprefRoomServerUrl = context.getString(R.string.pref_room_server_url_key);
        keyprefRoom = context.getString(R.string.pref_room_key);
        keyprefRoomList = context.getString(R.string.pref_room_list_key);

        String roomUrl = sharedPref.getString(keyprefRoomServerUrl,
                context.getString(R.string.pref_room_server_url_default));
        String roomId = sharedPref.getString(keyprefRoom, "");

        loopback = false;
        String urlParameters = null;

        roomConnectionParameters =
                new RoomConnectionParameters(roomUrl,roomId,loopback,urlParameters);
        return roomConnectionParameters;
    }
}
