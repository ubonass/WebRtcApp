/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtcpeer.util;

import android.os.Build;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;

/**
 * AppRTCUtils provides helper functions for managing thread safety.
 */
public final class AppRTCUtils {
    private AppRTCUtils() {
    }

    /**
     * Helper method which throws an exception  when an assertion has failed.
     */
    public static void assertIsTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected condition to be true");
        }
    }

    /**
     * Helper method for building a string of thread information.
     */
    public static String getThreadInfo() {
        return "@[name=" + Thread.currentThread().getName() + ", id=" + Thread.currentThread().getId()
                + "]";
    }

    /**
     * Information about the current build, taken from system properties.
     */
    public static void logDeviceInfo(String tag) {
        Log.d(tag, "Android SDK: " + Build.VERSION.SDK_INT + ", "
                + "Release: " + Build.VERSION.RELEASE + ", "
                + "Brand: " + Build.BRAND + ", "
                + "Device: " + Build.DEVICE + ", "
                + "Id: " + Build.ID + ", "
                + "Hardware: " + Build.HARDWARE + ", "
                + "Manufacturer: " + Build.MANUFACTURER + ", "
                + "Model: " + Build.MODEL + ", "
                + "Product: " + Build.PRODUCT);
    }

    public static String jsonToValue(org.json.JSONObject object, String key)
            throws JSONException {
        if (object == null || key == null) {
            //Log.e(TAG, "object or key is null");
            throw new NullPointerException("object or key is null");
        }
        return (String) object.get(key);
    }



    public static void jsonPut(JSONObject json,
                               String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
