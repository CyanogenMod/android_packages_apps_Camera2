/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;

import com.android.camera.app.MediaSaver.QueueListener;
import com.android.camera.debug.Log;
import com.android.camera.util.AndroidServices;
import com.android.camera.util.GservicesHelper;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * Default implementation of the {@link MemoryManager}.
 * <p>
 * TODO: Add GCam signals.
 */
public class MemoryManagerImpl implements MemoryManager, QueueListener, ComponentCallbacks2 {
    private static final Log.Tag TAG = new Log.Tag("MemoryManagerImpl");
    /**
     * Let's signal only 70% of max memory is allowed to be used by native code
     * to allow a buffer for special captures.
     */
    private static final float MAX_MEM_ALLOWED = 0.70f;

    private static final int[] sCriticalStates = new int[] {
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
    };

    private final LinkedList<MemoryListener> mListeners = new LinkedList<MemoryListener>();

    /**
     * The maximum amount of memory allowed to be allocated in native code (in
     * megabytes)
     */
    private final int mMaxAllowedNativeMemory;

    /**
     * Used to query a breakdown of current memory consumption and memory
     * thresholds.
     */
    private final MemoryQuery mMemoryQuery;

    /**
     * Use this to create a wired-up memory manager.
     *
     * @param context this is used to register for system memory events.
     * @param mediaSaver this used to check if the saving queue is full.
     * @return A wired-up memory manager instance.
     */
    public static MemoryManagerImpl create(Context context, MediaSaver mediaSaver) {
        ActivityManager activityManager = AndroidServices.instance().provideActivityManager();
        int maxAllowedNativeMemory = getMaxAllowedNativeMemory(context);
        MemoryQuery mMemoryQuery = new MemoryQuery(activityManager);
        MemoryManagerImpl memoryManager = new MemoryManagerImpl(maxAllowedNativeMemory,
                mMemoryQuery);
        context.registerComponentCallbacks(memoryManager);
        mediaSaver.setQueueListener(memoryManager);
        return memoryManager;
    }

    /**
     * Use {@link #create(Context, MediaSaver)} to make sure it's wired up
     * correctly.
     */
    private MemoryManagerImpl(int maxAllowedNativeMemory, MemoryQuery memoryQuery) {
        mMaxAllowedNativeMemory = maxAllowedNativeMemory;
        mMemoryQuery = memoryQuery;
        Log.d(TAG, "Max native memory: " + mMaxAllowedNativeMemory + " MB");

    }

    @Override
    public void addListener(MemoryListener listener) {
        synchronized (mListeners) {
            if (!mListeners.contains(listener)) {
                mListeners.add(listener);
            } else {
                Log.w(TAG, "Listener already added.");
            }
        }
    }

    @Override
    public void removeListener(MemoryListener listener) {
        synchronized (mListeners) {
            if (mListeners.contains(listener)) {
                mListeners.remove(listener);
            } else {
                Log.w(TAG, "Cannot remove listener that was never added.");
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    }

    @Override
    public void onLowMemory() {
        notifyLowMemory();
    }

    @Override
    public void onTrimMemory(int level) {
        for (int i = 0; i < sCriticalStates.length; ++i) {
            if (level == sCriticalStates[i]) {
                notifyLowMemory();
                return;
            }
        }
    }

    @Override
    public void onQueueStatus(boolean full) {
        notifyCaptureStateUpdate(full ? STATE_LOW_MEMORY : STATE_OK);
    }

    @Override
    public int getMaxAllowedNativeMemoryAllocation() {
        return mMaxAllowedNativeMemory;
    }

    @Override
    public HashMap queryMemory() {
        return mMemoryQuery.queryMemory();
    }

    /** Helper to determine max allowed native memory allocation (in megabytes). */
    private static int getMaxAllowedNativeMemory(Context context) {
        // First check whether we have a system override.
        int maxAllowedOverrideMb = GservicesHelper.getMaxAllowedNativeMemoryMb(context
                .getContentResolver());
        if (maxAllowedOverrideMb > 0) {
            Log.d(TAG, "Max native memory overridden: " + maxAllowedOverrideMb);
            return maxAllowedOverrideMb;
        }

        ActivityManager activityManager = AndroidServices.instance().provideActivityManager();

        // Use the max of the regular memory class and the large memory class.
        // This is defined as the maximum memory allowed to be used by the
        // Dalvik heap, but it's safe to assume the app can use the same amount
        // once more in native code.
        return (int) (Math.max(activityManager.getMemoryClass(),
                activityManager.getLargeMemoryClass()) * MAX_MEM_ALLOWED);
    }

    /** Notify our listener that memory is running low. */
    private void notifyLowMemory() {
        synchronized (mListeners) {
            for (MemoryListener listener : mListeners) {
                listener.onLowMemory();
            }
        }
    }

    private void notifyCaptureStateUpdate(int captureState) {
        synchronized (mListeners) {
            for (MemoryListener listener : mListeners) {
                listener.onMemoryStateChanged(captureState);
            }
        }
    }
}
