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
        outputData.put("timestamp", new Long(timestamp));
        outputData.put("availMem", new Long(availMem));
        outputData.put("totalMem", new Long(totalMem));
        outputData.put("totalPSS", new Long(totalPSS));
        outputData.put("lastTrimLevel", new Integer(info.lastTrimLevel));
        outputData.put("totalPrivateDirty", new Long(totalPrivateDirty));
        outputData.put("totalSharedDirty", new Long(totalSharedDirty));
        outputData.put("memoryClass", new Long(memoryClass));
        outputData.put("largeMemoryClass", new Long(largeMemoryClass));
        outputData.put("nativePSS", new Long(nativePSS));
        outputData.put("dalvikPSS", new Long(dalvikPSS));
        outputData.put("otherPSS", new Long(otherPSS));
        outputData.put("threshold", new Long(threshold));
        outputData.put("lowMemory", new Boolean(lowMemory));

        Log.d(TAG, String.format("timestamp=%d, availMem=%d, totalMem=%d, totalPSS=%d, " +
                "lastTrimLevel=%d, largeMemoryClass=%d, nativePSS=%d, dalvikPSS=%d, otherPSS=%d," +
                "threshold=%d, lowMemory=%s", timestamp, availMem, totalMem, totalPSS,
                info.lastTrimLevel, largeMemoryClass, nativePSS, dalvikPSS, otherPSS,
                threshold, lowMemory));

        return outputData;
    }
}