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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Process;
import android.util.Log;

import com.android.camera.app.CameraApp;
import com.android.camera.app.CameraServices;
import com.android.camera.session.CaptureSession;
import com.android.camera.session.CaptureSessionManager;

/**
 * A service that processes a {@code ProcessingTask}. The service uses a fifo
 * queue so that only one {@code ProcessingTask} is processed at a time.
 * <p>
 * The service is meant to be called via {@code ProcessingService.addTask},
 * which takes care of starting the service and enqueueing the
 * {@code ProcessingTask} task:
 *
 * <pre>
 * {@code
 * ProcessingTask task = new MyProcessingTask(...);
 * ProcessingService.addTask(task);
 * }
 * </pre>
 */
public class ProcessingService extends Service {
    private static final String TAG = "ProcessingService";
    private static final int THREAD_PRIORITY = Process.THREAD_PRIORITY_DISPLAY;
    private WakeLock mWakeLock;

    /** Manages the capture session. */
    private CaptureSessionManager mSessionManager;

    private ProcessingServiceManager mProcessingServiceManager;
    private Thread mProcessingThread;

    @Override
    public void onCreate() {
        Log.d(TAG, "Starting up");

        mProcessingServiceManager = ProcessingServiceManager.getInstance();
        mSessionManager = getServices().getCaptureSessionManager();

        // Keep CPU awake while allowing screen and keyboard to switch off.
        PowerManager powerManager = (PowerManager) getSystemService(
                Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mWakeLock.acquire();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Shutting down");

        // Tell the manager that we're shutting down, so in case new tasks are
        // enqueued, we a new service needs to be started.
        mProcessingServiceManager.notifyStitchingFinished();

        // TODO: Cancel session in progress...

        // Unlock the power manager, i.e. let power management kick in if
        // needed.
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        asyncProcessAllTasksAndShutdown();

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null.
        return null;
    }

    /**
     * Starts a thread to process all tasks. When no more tasks are in the
     * queue, it exits the thread and shuts down the service.
     */
    private void asyncProcessAllTasksAndShutdown() {
        if (mProcessingThread != null) {
            return;
        }
        mProcessingThread = new Thread() {
            @Override
            public void run() {
                // Set the thread priority
                android.os.Process.setThreadPriority(THREAD_PRIORITY);

                ProcessingTask task;
                while ((task = mProcessingServiceManager.popNextSession()) != null) {
                    processAndNotify(task);
                }
                stopSelf();
            }
        };
        mProcessingThread.start();
    }

    /**
     * Processes a {@code ProcessingTask} and updates the notification bar.
     */
    void processAndNotify(ProcessingTask task) {
        if (task == null) {
            Log.e(TAG, "Reference to ProcessingTask is null");
            return;
        }
        CaptureSession session = task.getSession();
        if (session == null) {
            session = mSessionManager.createNewSession(task.getName(), task.getLocation());
        }
        task.process(this, getServices(), session);
    }

    /**
     * Returns the common camera services.
     */
    private CameraServices getServices() {
        return (CameraApp) this.getApplication();
    }
}
