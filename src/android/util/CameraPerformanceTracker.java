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

package android.util;

/**
 * This class tracks the timing of important state changes in camera app (e.g latency
 * of cold/warm start of the activity, mode switch duration, etc). We can then query
 * these values from the instrument tests, which will be helpful for tracking camera
 * app performance and regression tests.
 */
public class CameraPerformanceTracker {

    // Event types to track.
    public static final int ACTIVITY_START = 0;
    public static final int ACTIVITY_PAUSE = 1;
    public static final int ACTIVITY_RESUME = 2;
    public static final int MODE_SWITCH_START = 3;
    public static final int FIRST_PREVIEW_FRAME = 5;
    public static final int UNSET = -1;

    private static final String TAG = "CameraPerformanceTracker";
    private static final boolean DEBUG = false;
    private static CameraPerformanceTracker sInstance;

    // Internal tracking time.
    private long mAppStartTime = UNSET;
    private long mAppResumeTime = UNSET;
    private long mModeSwitchStartTime = UNSET;

    // Duration and/or latency or later querying.
    private long mFirstPreviewFrameLatencyColdStart = UNSET;
    private long mFirstPreviewFrameLatencyWarmStart = UNSET;
    // TODO: Need to how to best track the duration for each switch from/to pair.
    private long mModeSwitchDuration = UNSET;

    private CameraPerformanceTracker() {
        // Private constructor to ensure that it can only be created from within
        // the class.
    }

    /**
     * This gets called when an important state change happens. Based on the type
     * of the event/state change, either we will record the time of the event, or
     * calculate the duration/latency.
     *
     * @param eventType type of a event to track
     */
    public static void onEvent(int eventType) {
        if (sInstance == null) {
            sInstance = new CameraPerformanceTracker();
        }
        long currentTime = System.currentTimeMillis();
        switch (eventType) {
            case ACTIVITY_START:
                sInstance.mAppStartTime = currentTime;
                break;
            case ACTIVITY_PAUSE:
                sInstance.mFirstPreviewFrameLatencyWarmStart = UNSET;
                break;
            case ACTIVITY_RESUME:
                sInstance.mAppResumeTime = currentTime;
                break;
            case FIRST_PREVIEW_FRAME:
                Log.d(TAG, "First preview frame received");
                if (sInstance.mFirstPreviewFrameLatencyColdStart == UNSET) {
                    // Cold start.
                    sInstance.mFirstPreviewFrameLatencyColdStart =
                            currentTime - sInstance.mAppStartTime;
                } else {
                    // Warm Start.
                    sInstance.mFirstPreviewFrameLatencyWarmStart =
                            currentTime - sInstance.mAppResumeTime;
                }
                // If the new frame is triggered by the mode switch, track the duration.
                if (sInstance.mModeSwitchStartTime != UNSET) {
                    sInstance.mModeSwitchDuration = currentTime - sInstance.mModeSwitchStartTime;
                    sInstance.mModeSwitchStartTime = UNSET;
                }
                break;
            case MODE_SWITCH_START:
                sInstance.mModeSwitchStartTime = currentTime;
                break;
            default:
                break;
        }
        if (DEBUG && eventType == FIRST_PREVIEW_FRAME) {
            Log.d(TAG, "Mode switch duration: " + (sInstance.mModeSwitchDuration
                == UNSET ? "UNSET" : sInstance.mModeSwitchDuration));
            Log.d(TAG, "Cold start latency: " + (sInstance.mFirstPreviewFrameLatencyColdStart
                == UNSET ? "UNSET" : sInstance.mFirstPreviewFrameLatencyColdStart));
            Log.d(TAG, "Warm start latency: " + (sInstance.mFirstPreviewFrameLatencyWarmStart
                == UNSET ? "UNSET" : sInstance.mFirstPreviewFrameLatencyWarmStart));
        }
    }

    //TODO: Hook up these getters in the instrument tests.
    /**
     * Gets the latency of a cold start of the app, measured from the time onCreate
     * gets called to the time first preview frame gets received.
     *
     * @return latency of a cold start. If no instances have been created, return
     *         UNSET.
     */
    public static long getColdStartLatency() {
        if (sInstance == null) {
            return UNSET;
        }
        return sInstance.mFirstPreviewFrameLatencyColdStart;
    }

    /**
     * Gets the latency of a warm start of the app, measured from the time onResume
     * gets called to the time next preview frame gets received.
     *
     * @return latency of a warm start. If no instances have been created,
     *         return UNSET.
     */
    public static long getWarmStartLatency() {
        if (sInstance == null) {
            return UNSET;
        }
        return sInstance.mFirstPreviewFrameLatencyWarmStart;
    }

    /**
     * Gets the duration of the mode switch, measured from the start of a mode switch
     * to the time next preview frame gets received.
     *
     * @return duration of the mode switch. If no instances have been created,
     *         return UNSET.
     */
    public static long getModeSwitchDuration() {
        if (sInstance == null) {
            return UNSET;
        }
        return sInstance.mModeSwitchDuration;
    }
}
