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

package com.android.camera.one.v2.commands;

import static com.google.common.base.Preconditions.checkNotNull;

import android.hardware.camera2.CameraAccessException;

import com.android.camera.async.SafeCloseable;
import com.android.camera.debug.Log;
import com.android.camera.debug.Logger;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;
import com.android.camera.util.Provider;
import com.google.common.util.concurrent.Futures;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * Executes camera commands on a thread pool.
 */
public class CameraCommandExecutor implements SafeCloseable {
    private class CommandRunnable implements Runnable {
        private final CameraCommand mCommand;

        public CommandRunnable(CameraCommand command) {
            mCommand = command;
        }

        @Override
        public void run() {
            try {
                mLog.d("Executing command: " + mCommand + " START");
                mCommand.run();
                mLog.d("Executing command: " + mCommand + " END");
            } catch (ResourceAcquisitionFailedException e) {
                // This may indicate that the command would have otherwise
                // deadlocked waiting for resources which can never be acquired,
                // or the command was aborted because the necessary resources
                // will never be available because the system is shutting down.
                e.printStackTrace();
            } catch (InterruptedException e) {
                // If interrupted, just return because the system is shutting
                // down.
                mLog.d("Interrupted while executing command: " + mCommand);
            } catch (CameraAccessException e) {
                // If the camera was closed and the command failed, just return.
                mLog.d("Unable to connect to camera while executing command: " + mCommand);
            } catch (CameraCaptureSessionClosedException e) {
                // If the session was closed and the command failed, just
                // return.
                mLog.d("Unable to connect to capture session while executing command: " +
                        mCommand);
            } catch (Exception e) {
                mLog.e("Exception when executing command: " + mCommand, e);
            }
        }
    }

    private final Logger mLog;
    private final Provider<ExecutorService> mExecutorProvider;
    private final Object mLock;
    @Nullable
    @GuardedBy("mLock")
    private ExecutorService mExecutor;
    @GuardedBy("mLock")
    private boolean mClosed;

    public CameraCommandExecutor(Logger.Factory loggerFactory,
            Provider<ExecutorService> threadPoolExecutor) {
        mLog = loggerFactory.create(new Log.Tag("CommandExecutor"));

        mLock = new Object();
        mExecutorProvider = threadPoolExecutor;
        mClosed = false;
    }

    /**
     * Executes the given command, returning a Future to indicate its status and
     * allow (interruptible) cancellation.
     */
    public Future<?> execute(CameraCommand command) {
        if (mClosed) {
            return Futures.immediateFuture(null);
        }
        synchronized (mLock) {
            if (mExecutor == null) {
                // Create a new executor, if necessary.
                mExecutor = mExecutorProvider.get();
            }
            checkNotNull(mExecutor);
            return mExecutor.submit(new CommandRunnable(command));
        }
    }

    /**
     * Cancels any pending, or currently-executing commands.
     * <p>
     * Like {@link #close} but allows reusing the object.
     */
    public void flush() {
        synchronized (mLock) {
            if (mExecutor != null) {
                mExecutor.shutdownNow();
            }

            mExecutor = null;
        }
    }

    @Override
    public void close() {
        // Shutdown immediately by interrupting all currently-executing
        // commands. Sending an interrupt is critical since commands may be
        // waiting for results from the camera device which will never arrive,
        // or for resources which may no longer be acquired.
        synchronized (mLock) {
            if (mExecutor != null) {
                mExecutor.shutdownNow();
            }

            mExecutor = null;
            mClosed = true;
        }
    }
}
