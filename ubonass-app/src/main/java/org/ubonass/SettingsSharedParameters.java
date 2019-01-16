package org.ubonass;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;

public class SettingsSharedParameters {

    private static final String TAG = "SettingsSharedParameters";
    private SharedPreferences sharedPref;
    private String keyprefResolution;
    private String keyprefFps;
    private String keyprefVideoBitrateType;
    private String keyprefVideoBitrateValue;
    private String keyprefAudioBitrateType;
    private String keyprefAudioBitrateValue;
    private String keyprefRoomServerUrl;
    private String keyprefRoom;
    private String keyprefRoomList;
    @Nullable
    private Context appContext;

    public SettingsSharedParameters(Context context) {
        appContext = context;
        PreferenceManager.setDefaultValues(context, R.xml.preferences, false);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        keyprefResolution = context.getString(R.string.pref_resolution_key);
        keyprefFps = context.getString(R.string.pref_fps_key);
        keyprefVideoBitrateType = context.getString(R.string.pref_maxvideobitrate_key);
        keyprefVideoBitrateValue = context.getString(R.string.pref_maxvideobitratevalue_key);
        keyprefAudioBitrateType = context.getString(R.string.pref_startaudiobitrate_key);
        keyprefAudioBitrateValue = context.getString(R.string.pref_startaudiobitratevalue_key);
        keyprefRoomServerUrl = context.getString(R.string.pref_room_server_url_key);
        keyprefRoom = context.getString(R.string.pref_room_key);
        keyprefRoomList = context.getString(R.string.pref_room_list_key);
    }


    /**
     * Get a value from the shared preference or from the intent, if it does not
     * exist the default is used.
     */
    public String sharedPrefGetString(int attributeId, int defaultId) {
        String defaultValue = appContext.getString(defaultId);
        String attributeName = appContext.getString(attributeId);
        return sharedPref.getString(attributeName, defaultValue);
    }

    /**
     * Get a value from the shared preference or from the intent, if it does not
     * exist the default is used.
     */
    public boolean sharedPrefGetBoolean(
            int attributeId, int defaultId) {
        boolean defaultValue = Boolean.parseBoolean(appContext.getString(defaultId));
        String attributeName = appContext.getString(attributeId);
        return sharedPref.getBoolean(attributeName, defaultValue);
    }

    /**
     * Get a value from the shared preference or from the intent, if it does not
     * exist the default is used.
     */
    public int sharedPrefGetInteger(
            int attributeId, int defaultId) {
        String defaultString = appContext.getString(defaultId);
        int defaultValue = Integer.parseInt(defaultString);
        String attributeName = appContext.getString(attributeId);
        String value = sharedPref.getString(attributeName, defaultString);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            AppManager.logger(TAG,
                    "Wrong setting for: " + attributeName + ":" + value);
            return defaultValue;
        }

    }

}
