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

package com.android.camera.processing;

import android.content.Context;
import android.content.Intent;

import com.android.camera.debug.Log;
import com.android.camera.processing.imagebackend.ImageBackend;
import com.android.camera.util.AndroidContext;
import com.android.camera2.R;

import java.util.LinkedList;

/**
 * Manages a queue of processing tasks as well as the processing service
 * lifecycle.
 * <p>
 * Clients should only use this class and not the {@link ProcessingService}
 * directly.
 */
public class ProcessingServiceManager implements ProcessingTaskConsumer {
    private static final Log.Tag TAG = new Log.Tag("ProcessingSvcMgr");

    private static class Singleton {
        private static final ProcessingServiceManager INSTANCE = new ProcessingServiceManager(
              AndroidContext.instance().get());
    }

    public static ProcessingServiceManager instance() {
        return Singleton.INSTANCE;
    }

    /** The application context. */
    private final Context mAppContext;

    /** Queue of tasks to be processed. */
    private final LinkedList<ProcessingTask> mQueue = new LinkedList<ProcessingTask>();

    /** Whether a processing service is currently running. */
    private volatile boolean mServiceRunning = false;

    /** Can be set to prevent tasks from being processed until released.*/
    private boolean mHoldProcessing = false;

    private final ImageBackend mImageBackend;

    private ProcessingServiceManager(Context context) {
        mAppContext = context;

        // Read and set the round thumbnail diameter value from resources.
        int tinyThumbnailSize = context.getResources()
              .getDimensionPixelSize(R.dimen.rounded_thumbnail_diameter_max);
        mImageBackend = new ImageBackend(this, tinyThumbnailSize);
    }

    /**
     * Enqueues a new task. If the service is not already running, it will be
     * started.
     *
     * @param task The task to be enqueued.
     */
    @Override
    public synchronized void enqueueTask(ProcessingTask task) {
        mQueue.add(task);
        Log.d(TAG, "Task added. Queue size now: " + mQueue.size());

        if (!mServiceRunning && !mHoldProcessing) {
            startService();
        }
    }

    /**
     * Remove the next task from the queue and return it.
     *
     * @return The next Task or <code>null</code>, if no more tasks are in the
     *         queue or we have a processing hold. If null is returned the
     *         service is has to shut down as a new service is started if either
     *         new items enter the queue or the processing is resumed.
     */
    public synchronized ProcessingTask popNextSession() {
        if (!mQueue.isEmpty() && !mHoldProcessing) {
            Log.d(TAG, "Popping a session. Remaining: " + (mQueue.size() - 1));
            return mQueue.remove();
        } else {
            Log.d(TAG, "Popping null. On hold? " + mHoldProcessing);
            mServiceRunning = false;
            // Returning null will shut-down the service.
            return null;
        }
    }

    /**
     * @return Whether the service has queued items or is running.
     */
    public synchronized boolean isRunningOrHasItems() {
        return mServiceRunning || !mQueue.isEmpty();
    }

    /**
     * If the queue is currently empty, processing is suspended for new incoming
     * items until the hold is released.
     * <p>
     * If items are in the queue, processing cannot be suspended.
     *
     * @return Whether processing was suspended.
     */
    public synchronized boolean suspendProcessing() {
        if (!isRunningOrHasItems()) {
            Log.d(TAG, "Suspend processing");
            mHoldProcessing = true;
            return true;
        } else {
          Log.d(TAG, "Not able to suspend processing.");
          return false;
        }
    }

    /**
     * Releases an existing hold.
     */
    public synchronized void resumeProcessing() {
        Log.d(TAG, "Resume processing. Queue size: " + mQueue.size());
        if (mHoldProcessing) {
          mHoldProcessing = false;
            if (!mQueue.isEmpty()) {
                startService();
            }
        }
    }

    /**
     * @return the currently defined image backend for this service.
     */
    public ImageBackend getImageBackend() {
        return mImageBackend;
    }

    /**
     * Starts the service which will then work through the queue. Once the queue
     * is empty {@link #popNextSession()} returns null), the task will kill
     * itself automatically and call #stitchingFinished().
     */
    private void startService() {
        mAppContext.startService(new Intent(mAppContext, ProcessingService.class));
        mServiceRunning = true;
    }
}
