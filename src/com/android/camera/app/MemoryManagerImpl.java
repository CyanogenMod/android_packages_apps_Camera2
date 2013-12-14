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

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;

import com.android.camera.app.MediaSaver.QueueListener;

import java.util.LinkedList;

/**
 * Default implementation of the {@link MemoryManager}.
 * <p>
 * TODO: Add GCam signals.
 */
public class MemoryManagerImpl implements MemoryManager, QueueListener, ComponentCallbacks2 {
    private static final String TAG = "MemoryManagerImpl";

    private static final int[] sCriticalStates = new int[] {
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
    };

    private final LinkedList<MemoryListener> mListeners = new LinkedList<MemoryListener>();

    /**
     * Use this to create a wired-up memory manager.
     *
     * @param context this is used to register for system memory events.
     * @param mediaSaver this used to check if the saving queue is full.
     * @return A wired-up memory manager instance.
     */
    public static MemoryManagerImpl create(Context context, MediaSaver mediaSaver) {
        MemoryManagerImpl memoryManager = new MemoryManagerImpl();
        context.registerComponentCallbacks(memoryManager);
        mediaSaver.setQueueListener(memoryManager);
        return memoryManager;
    }

    /**
     * Use {@link #create(Context, MediaSaver)} to make sure it's wired up
     * correctly.
     */
    private MemoryManagerImpl() {
    }

    @Override
    public void addListener(MemoryListener listener) {
        synchronized (mListeners) {
            if (mListeners.contains(listener)) {
                throw new IllegalStateException("Listener already added.");
            }
            mListeners.add(listener);
        }
    }

    @Override
    public void removeListener(MemoryListener listener) {
        synchronized (mListeners) {
            if (!mListeners.contains(listener)) {
                Log.w(TAG, "Cannot remove listener that was never added.");
                return;
            }
            mListeners.remove(listener);
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
