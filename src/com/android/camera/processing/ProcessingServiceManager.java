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
import android.util.Log;

import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 * Manages a queue of processing tasks as well as the processing service
 * lifecycle.
 * <p>
 * Clients should only use this class and not the {@link ProcessingService}
 * directly.
 */
public class ProcessingServiceManager {
    private static final String TAG = "ProcessingServiceManager";

    /** The singleton instance of this manager. */
    private static ProcessingServiceManager sInstance;

    /** The application context. */
    private final Context mAppContext;

    /** Queue of tasks to be processed. */
    private final LinkedList<ProcessingTask> mQueue = new LinkedList<ProcessingTask>();

    /** Whether a processing service is currently running. */
    private volatile boolean mServiceRunning = false;

    /**
     * Initializes the singleton instance.
     *
     * @param context the application context.
     */
    public static void initSingleton(Context appContext) {
        sInstance = new ProcessingServiceManager(appContext);
    }

    /**
     * Note: Make sure to call {@link #initSingleton(Context)} first.
     *
     * @return the singleton instance of the processing service manager.
     */
    public static ProcessingServiceManager getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("initSingleton() not yet called.");
        }
        return sInstance;
    }

    private ProcessingServiceManager(Context context) {
        mAppContext = context;
    }

    /**
     * Enqueues a new task. If the service is not already running, it will be
     * started.
     *
     * @param task The task to be enqueued.
     */
    public synchronized void enqueueTask(ProcessingTask task) {
        mQueue.add(task);
        Log.d(TAG, "Task added. Queue size now: " + mQueue.size());

        if (!mServiceRunning) {
            // Starts the service which will then work through the queue. Once
            // the queue is empty (#popNextSession() returns null), the task
            // will kill itself automatically and call #stitchingFinished().
            mAppContext.startService(new Intent(mAppContext, ProcessingService.class));
        }
        mServiceRunning = true;
    }

    /**
     * Remove the next task from the queue and return it.
     *
     * @return The next Task or <code>null</code>, of no more tasks are in the
     *         queue.
     */
    public synchronized ProcessingTask popNextSession() {
        try {
            return mQueue.remove();
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    /**
     * Called by the processing service, notifying us that it has finished.
     */
    public synchronized void notifyStitchingFinished() {
        this.mServiceRunning = false;
    }
}
