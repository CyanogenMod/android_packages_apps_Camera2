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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Process;
import android.support.v4.content.LocalBroadcastManager;

import com.android.camera.app.CameraServices;
import com.android.camera.app.CameraServicesImpl;
import com.android.camera.debug.Log;
import com.android.camera.session.CaptureSession;
import com.android.camera.session.CaptureSession.ProgressListener;
import com.android.camera.session.CaptureSessionManager;
import com.android.camera.util.AndroidServices;
import com.android.camera2.R;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
public class ProcessingService extends Service implements ProgressListener {
    /**
     * Class used to receive broadcast and control the service accordingly.
     */
    public class ServiceController extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == ACTION_PAUSE_PROCESSING_SERVICE) {
                ProcessingService.this.pause();
            } else if (intent.getAction() == ACTION_RESUME_PROCESSING_SERVICE) {
                ProcessingService.this.resume();
            }
        }
    }

    private static final Log.Tag TAG = new Log.Tag("ProcessingService");
    private static final int THREAD_PRIORITY = Process.THREAD_PRIORITY_BACKGROUND;
    private static final int CAMERA_NOTIFICATION_ID = 2;
    private Notification.Builder mNotificationBuilder;
    private NotificationManager mNotificationManager;

    /** Sending this broadcast intent will cause the processing to pause. */
    public static final String ACTION_PAUSE_PROCESSING_SERVICE =
            "com.android.camera.processing.PAUSE";
    /**
     * Sending this broadcast intent will cause the processing to resume after
     * it has been paused.
     */
    public static final String ACTION_RESUME_PROCESSING_SERVICE =
            "com.android.camera.processing.RESUME";

    private WakeLock mWakeLock;
    private final ServiceController mServiceController = new ServiceController();

    /** Manages the capture session. */
    private CaptureSessionManager mSessionManager;

    private ProcessingServiceManager mProcessingServiceManager;
    private Thread mProcessingThread;
    private volatile boolean mPaused = false;
    private ProcessingTask mCurrentTask;
    private final Lock mSuspendStatusLock = new ReentrantLock();

    @Override
    public void onCreate() {
        mProcessingServiceManager = ProcessingServiceManager.instance();
        mSessionManager = getServices().getCaptureSessionManager();

        // Keep CPU awake while allowing screen and keyboard to switch off.
        PowerManager powerManager = AndroidServices.instance().providePowerManager();
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG.toString());
        mWakeLock.acquire();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_PAUSE_PROCESSING_SERVICE);
        intentFilter.addAction(ACTION_RESUME_PROCESSING_SERVICE);
        LocalBroadcastManager.getInstance(this).registerReceiver(mServiceController, intentFilter);
        mNotificationBuilder = createInProgressNotificationBuilder();
        mNotificationManager = AndroidServices.instance().provideNotificationManager();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Shutting down");
        // TODO: Cancel session in progress...

        // Unlock the power manager, i.e. let power management kick in if
        // needed.
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mServiceController);
        stopForeground(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Starting in foreground.");

        // We need to start this service in foreground so that it's not getting
        // killed easily when memory pressure is building up.
        startForeground(CAMERA_NOTIFICATION_ID, mNotificationBuilder.build());

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

    private void pause() {
        Log.d(TAG, "Pausing");
        try {
            mSuspendStatusLock.lock();
            mPaused = true;
            if (mCurrentTask != null) {
                mCurrentTask.suspend();
            }
        } finally {
            mSuspendStatusLock.unlock();
        }
    }

    private void resume() {
        Log.d(TAG, "Resuming");
        try {
            mSuspendStatusLock.lock();
            mPaused = false;
            if (mCurrentTask != null) {
                mCurrentTask.resume();
            }
        } finally {
            mSuspendStatusLock.unlock();
        }
    }

    /**
     * Starts a thread to process all tasks. When no more tasks are in the
     * queue, it exits the thread and shuts down the service.
     */
    private void asyncProcessAllTasksAndShutdown() {
        if (mProcessingThread != null) {
            return;
        }
        mProcessingThread = new Thread("CameraProcessingThread") {
            @Override
            public void run() {
                // Set the thread priority
                android.os.Process.setThreadPriority(THREAD_PRIORITY);

                ProcessingTask task;
                while ((task = mProcessingServiceManager.popNextSession()) != null) {
                    mCurrentTask = task;
                    try {
                        mSuspendStatusLock.lock();
                        if (mPaused) {
                            mCurrentTask.suspend();
                        }
                    } finally {
                        mSuspendStatusLock.unlock();
                    }
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

        // TODO: Get rid of this null check. There should not be a task without
        // a session.
        if (session == null) {
            // TODO: Timestamp is not required right now, refactor this to make it clearer.
            session = mSessionManager.createNewSession(task.getName(), 0, task.getLocation());
        }
        resetNotification();

        // Adding the listener also causes it to get called for the session's
        // current status message and percent completed.
        session.addProgressListener(this);

        System.gc();
        Log.d(TAG, "Processing start");
        task.process(this, getServices(), session);
        Log.d(TAG, "Processing done");
    }

    private void resetNotification() {
        mNotificationBuilder.setContentText("…").setProgress(100, 0, false);
        postNotification();
    }

    /**
     * Returns the common camera services.
     */
    private CameraServices getServices() {
        return CameraServicesImpl.instance();
    }

    private void postNotification() {
        mNotificationManager.notify(CAMERA_NOTIFICATION_ID, mNotificationBuilder.build());
    }

    /**
     * Creates a notification to indicate that a computation is in progress.
     */
    private Notification.Builder createInProgressNotificationBuilder() {
        return new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_notification)
                .setWhen(System.currentTimeMillis())
                .setOngoing(true)
                .setContentTitle(this.getText(R.string.app_name));
    }

    @Override
    public void onProgressChanged(int progress) {
        mNotificationBuilder.setProgress(100, progress, false);
        postNotification();
    }

    @Override
    public void onStatusMessageChanged(int messageId) {
        mNotificationBuilder.setContentText(messageId > 0 ? getString(messageId) : "");
        postNotification();
    }
}
