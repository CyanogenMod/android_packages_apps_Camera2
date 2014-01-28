/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera.util;

import android.content.Context;
import android.hardware.Camera;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;


public class UsageStatistics {

    public static final String COMPONENT_GALLERY = "Gallery";
    public static final String COMPONENT_CAMERA = "Camera";
    public static final String COMPONENT_EDITOR = "Editor";
    public static final String COMPONENT_IMPORTER = "Importer";
    public static final String COMPONENT_LIGHTCYCLE = "Lightcycle";
    public static final String COMPONENT_PANORAMA = "Panorama";
    public static final String COMPONENT_GCAM = "GCam";

    public static final String TRANSITION_BACK_BUTTON = "BackButton";
    public static final String TRANSITION_UP_BUTTON = "UpButton";
    public static final String TRANSITION_PINCH_IN = "PinchIn";
    public static final String TRANSITION_PINCH_OUT = "PinchOut";
    public static final String TRANSITION_INTENT = "Intent";
    public static final String TRANSITION_ITEM_TAP = "ItemTap";
    public static final String TRANSITION_MENU_TAP = "MenuTap";
    public static final String TRANSITION_BUTTON_TAP = "ButtonTap";
    public static final String TRANSITION_SWIPE = "Swipe";

    public static final String ACTION_CAPTURE_START = "CaptureStart";
    public static final String ACTION_CAPTURE_FAIL = "CaptureFail";
    public static final String ACTION_CAPTURE_DONE = "CaptureDone";

    public static final String ACTION_STITCHING_START = "StitchingStart";
    public static final String ACTION_STITCHING_DONE = "StitchingDone";

    public static final String ACTION_FOREGROUNDED = "Foregrounded";
    public static final String ACTION_OPEN_FAIL = "OpenFailure";
    public static final String ACTION_SCREEN_CHANGED = "ScreenChanged";
    public static final String ACTION_FILMSTRIP = "Filmstrip";
    public static final String ACTION_TOUCH_FOCUS = "TouchFocus";
    public static final String ACTION_DELETE = "Delete";
    public static final String ACTION_GALLERY = "Gallery";
    public static final String ACTION_EDIT = "Edit";
    public static final String ACTION_CROP = "Crop";
    public static final String ACTION_PLAY_VIDEO = "PlayVideo";

    public static final String CATEGORY_LIFECYCLE = "AppLifecycle";
    public static final String CATEGORY_BUTTON_PRESS = "ButtonPress";

    public static final String LIFECYCLE_START = "Start";

    private static final String GOOGLER_LABEL = "Googler";
    private static final String TIMEZONE_LABEL = "Timezone";

    public static final String PLAY_LOG_TAG = "com.google.android.GoogleCamera";

    /**
     * The user has shared a photo or video.
     * <p/>
     * <p>This should be used only within the {@link #COMPONENT_GALLERY} category.
     * <p/>
     * <p>The label should be either Photo or Video, to identify the type of item being shared.
     * <p/>
     * <p>The optional value should be the time different between the time the photo or video was
     * taken, according to its metadata, and the time it was actually shared, or -1 if the time it
     * was taken is not available.
     */
    public static final String ACTION_SHARE = "Share";

    private static final String UNKNOWN_LABEL = "NULL";
    private static final String EVENT_TRANSITION = "ScreenTransition";
    private static final long SESSION_TIMEOUT_MS = 300000;
    private static final boolean DEV_MODE =
            !"user".equals(android.os.Build.TYPE);
    private static final String ANALYTICS_TAG_DEV = "UA-36276453-2";
    private static final String ANALYTICS_TAG_USER = "UA-36276453-1";
    private static final String TRANSITION_FORMAT_STRING = "%s -> %s";
    private static final int DEFAULT_SAMPLE_RATE_HUNDREDTHS = 10000;

    private static boolean sAnalyticsEnabled = false;
    private static String sAnalyticsTag;
    private static double sSampleRateHundredths = -1;
    private static String sLastHit = UNKNOWN_LABEL;
    private static String sPendingTransition = UNKNOWN_LABEL;
    private static long sLastUpdateTimestamp = 0;
    private static long sLastTimestampDifference = 0;
    private static MessageDigest sShaMessageDigest;
    private static String sTimeZone;
    private static Boolean sGoogler;
    private static int sCurrentMode;
    private static Map<String, Integer> sValidPreferences = new HashMap<String, Integer>();

    public static void initialize(Context context) {
    }

    public static void photoInteraction(String fileNameHash, int interactionType, int cause) {
    }

    public static void foregrounded(int source) {
    }

    public static void captureEvent(int mode, String fileNameHash, Camera.Parameters parameters) {
    }

    public static void captureEvent(int mode, String fileNameHash,
                                    Camera.Parameters parameters, Float duration) {
    }

    public static void changePreference(String preference, String newValue, String oldValue) {

    }

    public static void cameraFailure(int cause) {
    }

    public static void tapToFocus() {
    }

    public static void changeScreen(int newScreen, Integer interactionCause) {
    }


    /**
     * Given a filename such as image.jpg, it returns a hash used for logging.  The hash
     * identifies the file for later operations such as delete or share without leaking
     * the filename, or keeping extra book keeping on the device.
     *
     * @param fileName the filename with extension that will be hashed.
     * @return a deterministic one way hash of the fileName
     */
    public static String hashFileName(String fileName) {
        return toSHA1(fileName);
    }

    private static String toSHA1(String input) {
        byte[] inputBytes = input.getBytes();
        if (sShaMessageDigest == null) {
            try {
                sShaMessageDigest = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
        if (sShaMessageDigest != null && inputBytes != null) {
            return byteArrayToHexString(sShaMessageDigest.digest(inputBytes));
        } else {
            return "";
        }
    }

    private static String byteArrayToHexString(byte[] b) {
        StringBuilder resultBuilder = new StringBuilder();
        for (byte aB : b) {

            // This converts a byte to a 2 digit hex string by masking
            // first we get rid of any extraneous high bits
            // Then we set one high bit to guarantee the hex representation is long enough
            // lastly we only take the last 2 characters from the string (ignoring the high
            // order bit we set)
            resultBuilder.append(Integer.toString((aB & 0xff) + 0x100, 16).substring(1));
        }
        return resultBuilder.toString();
    }

}
