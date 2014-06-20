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
import android.os.Process;
import android.os.SystemClock;

import com.android.camera.debug.Log;

import java.util.HashMap;

/**
 * Queries the current memory consumption of the app.
 */
public class MemoryQuery {
    private static final Log.Tag TAG = new Log.Tag("MemoryQuery");
    private final long BYTES_IN_KILOBYTE = 1024;
    private final long BYTES_IN_MEGABYTE = BYTES_IN_KILOBYTE * BYTES_IN_KILOBYTE;

    public static final String KEY_TIMESTAMP = "timestamp";
    public static final String KEY_MEMORY_AVAILABLE = "availMem";
    public static final String KEY_TOTAL_MEMORY = "totalMem";
    public static final String KEY_TOTAL_PSS = "totalPSS";
    public static final String KEY_NATIVE_PSS = "nativePSS";
    public static final String KEY_DALVIK_PSS = "dalvikPSS";
    public static final String KEY_OTHER_PSS = "otherPSS";
    public static final String KEY_THRESHOLD = "threshold";
    public static final String KEY_LOW_MEMORY = "lowMemory";
    public static final String KEY_LAST_TRIM_LEVEL = "lastTrimLevel";
    public static final String KEY_TOTAL_PRIVATE_DIRTY = "totalPrivateDirty";
    public static final String KEY_TOTAL_SHARED_DIRTY = "totalSharedDirty";
    public static final String KEY_MEMORY_CLASS = "memoryClass";
    public static final String KEY_LARGE_MEMORY_CLASS = "largeMemoryClass";

    public static final String REPORT_LABEL_LAUNCH = "launch";

    private ActivityManager mActivityManager;

    public MemoryQuery(ActivityManager activityManager) {
        mActivityManager = activityManager;
    }

    /**
     * Measures the current memory consumption and thresholds of the app, from
     * the ActivityManager and Debug.MemoryInfo,
     *
     * @return HashMap of memory metrics keyed by string labels.
     */
    public HashMap queryMemory() {
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

        // Retrieve a list of all running processes. Get the app PID.
        int appPID = Process.myPid();

        // Get ActivityManager.getProcessMemoryInfo for the app PID.
        long timestamp = SystemClock.elapsedRealtime();
        long totalPrivateDirty = 0L;
        long totalSharedDirty = 0L;
        long totalPSS = 0L;
        long nativePSS = 0L;
        long dalvikPSS = 0L;
        long otherPSS = 0L;

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

        HashMap outputData = new HashMap();
        outputData.put(KEY_TIMESTAMP, new Long(timestamp));
        outputData.put(KEY_MEMORY_AVAILABLE, new Long(availMem));
        outputData.put(KEY_TOTAL_MEMORY, new Long(totalMem));
        outputData.put(KEY_TOTAL_PSS, new Long(totalPSS));
        outputData.put(KEY_LAST_TRIM_LEVEL, new Integer(info.lastTrimLevel));
        outputData.put(KEY_TOTAL_PRIVATE_DIRTY, new Long(totalPrivateDirty));
        outputData.put(KEY_TOTAL_SHARED_DIRTY, new Long(totalSharedDirty));
        outputData.put(KEY_MEMORY_CLASS, new Long(memoryClass));
        outputData.put(KEY_LARGE_MEMORY_CLASS, new Long(largeMemoryClass));
        outputData.put(KEY_NATIVE_PSS, new Long(nativePSS));
        outputData.put(KEY_DALVIK_PSS, new Long(dalvikPSS));
        outputData.put(KEY_OTHER_PSS, new Long(otherPSS));
        outputData.put(KEY_THRESHOLD, new Long(threshold));
        outputData.put(KEY_LOW_MEMORY, new Boolean(lowMemory));

        Log.d(TAG, String.format("timestamp=%d, availMem=%d, totalMem=%d, totalPSS=%d, " +
                "lastTrimLevel=%d, largeMemoryClass=%d, nativePSS=%d, dalvikPSS=%d, otherPSS=%d," +
                "threshold=%d, lowMemory=%s", timestamp, availMem, totalMem, totalPSS,
                info.lastTrimLevel, largeMemoryClass, nativePSS, dalvikPSS, otherPSS,
                threshold, lowMemory));

        return outputData;
    }
}