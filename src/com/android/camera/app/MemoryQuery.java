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

package com.android.camera.app;

import android.app.ActivityManager;
import android.os.Debug;
import android.os.Handler;
import android.os.Process;
import android.os.SystemClock;

import com.android.camera.app.MemoryManager.ReportType;
import com.android.camera.debug.Log;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Queries the current memory consumption of the app.
 */
public class MemoryQuery {
    private static final Log.Tag TAG = new Log.Tag("MemoryQuery");
    private final long BYTES_IN_KILOBYTE = 1024;
    private final long BYTES_IN_MEGABYTE = BYTES_IN_KILOBYTE * BYTES_IN_KILOBYTE;
    private ActivityManager mActivityManager;

    /** Memory measurement interval in milliseconds. */
    private int POLL_INTERVAL = 500;
    private Handler mHandler;
    private Runnable mMeasureMemoryTask = new Runnable() {
        public void run() {
            MemoryMeasurement memoryMetrics = queryMemory();
            mMemoryWindowMap.appendMeasurement(memoryMetrics);
            if (mMeasuringMemory) {
                mHandler.postDelayed(this, POLL_INTERVAL);
            }
        }
    };
    private boolean mMeasuringMemory = false;

    private MemoryWindowMap mMemoryWindowMap;

    /** Stores summary statistics of a memory analysis window. */
    public class WindowStats {
        public ReportType type;
        public float medianTotalPSS;
        public float maxTotalPSS;
        public float duration;
        public List<Long> warnings;
    }

    public MemoryQuery(ActivityManager activityManager) {
        mActivityManager = activityManager;
        mHandler = new Handler();
        mHandler.removeCallbacks(mMeasureMemoryTask);
        mMemoryWindowMap = new MemoryWindowMap();
        startMeasuringMemory();
    }

    /**
     * Begin storage of memory measurements, to be measured every
     * POLL_INTERVAL milliseconds.
     */
    public void startMeasuringMemory() {
        mMeasuringMemory = true;
        mHandler.postDelayed(mMeasureMemoryTask, POLL_INTERVAL);
    }

    /**
     * Stop storage of memory measurements.
     */
    public void stopMeasuringMemory() {
        mMeasuringMemory = false;
    }

    /**
     * Measures the current memory consumption and thresholds of the app, from
     * the ActivityManager and Debug.MemoryInfo,
     *
     * @return MemoryMeasurement containing memory metrics.
     */
    public MemoryMeasurement queryMemory() {
        // Get ActivityManager.MemoryInfo.
        int memoryClass = mActivityManager.getMemoryClass();
        int largeMemoryClass = mActivityManager.getLargeMemoryClass();
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        mActivityManager.getMemoryInfo(memoryInfo);
        long availMem = memoryInfo.availMem / BYTES_IN_MEGABYTE;
        long totalMem = memoryInfo.totalMem / BYTES_IN_MEGABYTE;
        long threshold = memoryInfo.threshold / BYTES_IN_MEGABYTE;
        boolean lowMemory = memoryInfo.lowMemory;

        // Get ActivityManager.RunningAppProcessInfo.
        ActivityManager.RunningAppProcessInfo info = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(info);

        long timestamp = SystemClock.elapsedRealtime();
        long totalPrivateDirty = 0L;
        long totalSharedDirty = 0L;
        long totalPSS = 0L;
        long nativePSS = 0L;
        long dalvikPSS = 0L;
        long otherPSS = 0L;

        int appPID = Process.myPid();

        if (appPID != 0) {
            int pids[] = new int[1];
            pids[0] = appPID;
            Debug.MemoryInfo[] memoryInfoArray = mActivityManager.getProcessMemoryInfo(pids);
            totalPrivateDirty = memoryInfoArray[0].getTotalPrivateDirty() / BYTES_IN_KILOBYTE;
            totalSharedDirty = memoryInfoArray[0].getTotalSharedDirty() / BYTES_IN_KILOBYTE;
            totalPSS = memoryInfoArray[0].getTotalPss() / BYTES_IN_KILOBYTE;
            nativePSS = memoryInfoArray[0].nativePss / BYTES_IN_KILOBYTE;
            dalvikPSS = memoryInfoArray[0].dalvikPss / BYTES_IN_KILOBYTE;
            otherPSS = memoryInfoArray[0].otherPss / BYTES_IN_KILOBYTE;
        }

        MemoryMeasurement measurement = new MemoryMeasurement();
        measurement.timestamp = timestamp;
        measurement.availMem = availMem;
        measurement.totalMem = totalMem;
        measurement.totalPSS = totalPSS;
        measurement.lastTrimLevel = info.lastTrimLevel;
        measurement.totalPrivateDirty = totalPrivateDirty;
        measurement.totalSharedDirty = totalSharedDirty;
        measurement.memoryClass = memoryClass;
        measurement.largeMemoryClass = largeMemoryClass;
        measurement.nativePSS = nativePSS;
        measurement.dalvikPSS = dalvikPSS;
        measurement.otherPSS = otherPSS;
        measurement.threshold = threshold;
        measurement.lowMemory = lowMemory;

        Log.v(TAG, String.format("queryMemory: timestamp=%d, availMem=%d, totalMem=%d, " +
                "totalPSS=%d, lastTrimLevel=%d, largeMemoryClass=%d, nativePSS=%d, " +
                "dalvikPSS=%d, otherPSS=%d, threshold=%d, lowMemory=%s", timestamp, availMem,
                totalMem, totalPSS, info.lastTrimLevel, largeMemoryClass, nativePSS, dalvikPSS,
                otherPSS, threshold, lowMemory));
        return measurement;
    }

    /**
     * Begins storage of the periodic memory consumption measurements for the
     * given key. Every POLL_INTERVAL milliseconds, memory consumption is
     * measured and appended to all active analysis windows.
     *
     * @param key Key to identify this list of memory consumption measurements.
     */
    public void startAnalysisWindow(String key, ReportType type) {
        mMemoryWindowMap.startWindow(key, type);
    }

    /**
     * Reports the collected memory consumption measurements for the given key.
     *
     * @param key Key of the memory consumption measurements to be
     *            reported.
     * @param terminate True if the analysis window should be terminated.
     */
    public WindowStats reportAnalysisWindow(String key, boolean terminate) {
        MemoryWindow window = mMemoryWindowMap.get(key);
        WindowStats stats = window.getStats();
        if (terminate) {
            mMemoryWindowMap.removeWindow(key);
        }

        Log.v(TAG, String.format("reportAnalysisWindow: type=%s, duration=%.1f, " +
                "medianTotalPSS=%.1f, maxTotalPSS=%.1f, warnings=%s",
                stats.type.name(), stats.duration, stats.medianTotalPSS, stats.maxTotalPSS,
                window.getWarnings().toString()));

        return stats;
    }

    /**
     * Append the given memory warning level to the current list of
     * memory warnings.
     *
     * @param level Integer memory warning level to append.
     */
    public void appendWarning(int level) {
        Log.v(TAG, String.format("appendWarning: %d", level));
        mMemoryWindowMap.appendWarning(level);
    }

    /**
     * Storage of instantaneous memory metrics.
     */
    public class MemoryMeasurement {
        public long timestamp;
        public long availMem;
        public long totalMem;
        public long totalPSS;
        public int lastTrimLevel;
        public long totalPrivateDirty;
        public long totalSharedDirty;
        public long memoryClass;
        public long largeMemoryClass;
        public long nativePSS;
        public long dalvikPSS;
        public long otherPSS;
        public long threshold;
        public boolean lowMemory;
    }

    /**
     * Storage of memory measurements and warnings.
     */
    private class MemoryWindow {
        /** The context in which we're capturing memory. */
        private ReportType mType;

        /** A list of memory measurements. */
        private List<MemoryMeasurement> mMeasurements;

        /** A list of warnings that have occurred during the memory window. */
        private List<Long> mWarnings;

        public MemoryWindow() {
            mMeasurements = new ArrayList<MemoryMeasurement>();
            mWarnings = new ArrayList<Long>();
        }

        /**
         * Sets the type of image capture performed for this analysis window.
         *
         * @param type Identifier of the type of image capture performed.
         */
        public void setType(ReportType type) {
            mType = type;
        }

        /**
         * Gets the type of image capture performed for this analysis window.
         *
         * @return Identifier of the type of image capture performed.
         */
        public ReportType getType() {
            return mType;
        }

        /**
         * Appends a memory measurement to this analysis window.
         *
         * @param measurement Memory measurement to append.
         */
        public void appendMeasurement(MemoryMeasurement measurement) {
            mMeasurements.add(measurement);
        }

        /**
         * Gets the list of memory measurements of this analysis window.
         *
         * @return List of memory measurements.
         */
        public List<MemoryMeasurement> getMeasurements() {
            return mMeasurements;
        }

        /**
         * Append the given memory warning level to the current list of
         * memory warnings.
         *
         * @param level Memory warning level code to append.
         */
        public void appendWarning(long level) {
            mWarnings.add(level);
        }

        /**
         * Get the list of memory warning codes accumulated for the duration
         * of this analysis window.
         *
         * @return List of memory warning codes accumulated for the duration
         *         of this analysis window.
         */
        public List<Long> getWarnings() {
            return mWarnings;
        }

        /**
         * Compute statistics for the memory measurements taken.
         *
         *  @return WindowStats object containing:
         *           - duration in milliseconds
         *           - median total PSS
         *           - max total PSS
         */
        public WindowStats getStats() {
            long[] PSSs = new long[mMeasurements.size()];
            for(int i = 0; i < mMeasurements.size(); i++) {
                PSSs[i] = mMeasurements.get(i).totalPSS;
            }
            Arrays.sort(PSSs);
            Log.v(TAG, String.format("PSSs: %s", Arrays.toString(PSSs)));
            float medianPSS = getMedianFromSorted(PSSs);
            float maxPSS = getMaxFromSorted(PSSs);

            WindowStats stats = new WindowStats();
            stats.type = mType;
            stats.duration = getDuration();
            stats.medianTotalPSS = medianPSS;
            stats.maxTotalPSS = maxPSS;
            stats.warnings = getWarnings();
            return stats;
        }

        /**
         * Returns the duration of the analysis window in milliseconds.
         *
         * @return duration of the analysis window in milliseconds.
         *         Returns zero if there are < 2 measurements.
         */
        public float getDuration() {
            if (mMeasurements.size() < 2) {
                return 0L;
            }
            else {
                MemoryMeasurement firstSample = mMeasurements.get(0);
                MemoryMeasurement lastSample = mMeasurements.get(mMeasurements.size() - 1);
                return (lastSample.timestamp - firstSample.timestamp / 1000f);
            }
        }

        /**
         * Returns the median from a sorted array of Longs.
         *
         * @param data The sorted array of Longs.
         * @return The median of the given sorted array. -1 if an empty array.
         */
        private float getMedianFromSorted(long[] data) {
            if (data.length == 0) {
                return -1;
            }
            float median;
            if (data.length % 2 == 0) {
                median = ((float) data[data.length / 2 - 1]
                        + (float) data[data.length / 2]) / 2;
            } else {
                median = (float) data[data.length / 2];
            }
            return median;
        }


        /**
         * Returns the maximum value of a sorted array of Longs.
         *
         * @param data The sorted array of Longs.
         *
         * @return The maximum value of the given sorted array.
         *         -1 if the array size is zero.
         */
        private long getMaxFromSorted(long[] data) {
            if (data.length == 0) {
                return -1;
            }
            return data[data.length - 1];
        }

    }

    /**
     * Manages the multiple and simultaneous recording of MemoryWindows via a HashMap,
     * analogous to Python's defaultdict(list).
     */
    private class MemoryWindowMap {
        private HashMap<String, MemoryWindow> windows;

        public MemoryWindowMap() {
            windows = new HashMap<String, MemoryWindow>();
        }

        /**
         * Retrieve the actively recording MemoryWindows.
         *
         * @return The set of actively recording MemoryWindows.
         */
        public Set<String> keySet() {
            return windows.keySet();
        }

        /**
         * Creates a new MemoryWindow at the given key.
         * New measurements will be appended to this window.
         *
         * @param key String key of this MemoryWindow.
         * @param type The type of image capture for which we're capturing
         #             memory, e.g., ReportType.LENS_BLUR
         */
        public void startWindow(String key, ReportType type) {
            if (keySet().contains(key)) {
                // Window is already active.
                return;
            }
            MemoryWindow newWindow = new MemoryWindow();
            newWindow.setType(type);
            windows.put(key, newWindow);
        }

        public MemoryWindow get(String key) {
            return windows.get(key);
        }

        /**
         * Append this memory measurement to every MemoryWindow in windows.
         *
         * @param measurement A measurement of instantaneous memory consumption
         *                    as returned from queryMemory().
         */
        public void appendMeasurement(MemoryMeasurement measurement) {
            for (String key : keySet()) {
                get(key).appendMeasurement(measurement);
            }
        }

        /**
         * Append this warning to every MemoryWindow in windows.
         *
         * @param level Warning level from onTrimMemory(int level).
         */
        public void appendWarning(int level) {
            for (String key : keySet()) {
                get(key).appendWarning(level);
            }
        }

        /**
         * Terminate the recording of this MemoryWindow and remove it from windows.
         *
         * @param key Key of the MemoryWindow for which recording should be
         *            terminated.
         */
        public void removeWindow(String key) {
            windows.remove(key);
        }
    }
}