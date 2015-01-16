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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import android.hardware.camera2.CameraAccessException;

import com.android.camera.async.SafeCloseable;
import com.android.camera.debug.Log;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;

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
                Log.d(TAG, "Executing command: " + mCommand + " START");
                mCommand.run();
                Log.d(TAG, "Executing command: " + mCommand + " END");
            } catch (ResourceAcquisitionFailedException e) {
                // This may indicate that the command would have otherwise
                // deadlocked waiting for resources which can never be acquired,
                // or the command was aborted because the necessary resources
                // will never be available because the system is shutting down.
                e.printStackTrace();
            } catch (InterruptedException e) {
                // If interrupted, just return because the system is shutting
                // down.
                Log.d(TAG, "Interrupted while executing command: " + mCommand);
            } catch (CameraAccessException e) {
                // If the camera was closed and the command failed, just return.
                Log.d(TAG, "Unable to connect to camera while executing command: " + mCommand);
            } catch (CameraCaptureSessionClosedException e) {
                // If the session was closed and the command failed, just
                // return.
                Log.d(TAG, "Unable to connect to capture session while executing command: " +
                        mCommand);
            } catch (Exception e) {
                Log.e(TAG, "Exception when executing command: " + mCommand, e);
            }
        }
    }

    private static final Log.Tag TAG = new Log.Tag("CameraCommandExecutor");

    private final ExecutorService mExecutor;

    public CameraCommandExecutor(ExecutorService threadPoolExecutor) {
        mExecutor = threadPoolExecutor;
    }

    public void execute(CameraCommand command) {
        try {
            mExecutor.execute(new CommandRunnable(command));
        } catch (RejectedExecutionException e) {
            // If the executor is shut down, the command will not be executed.
            // So, we can ignore this exception.
        }
    }

    @Override
    public void close() {
        // Shutdown immediately by interrupting all currently-executing
        // commands. Sending an interrupt is critical since commands may be
        // waiting for results from the camera device which will never arrive,
        // or for resources which may no longer be acquired.
        mExecutor.shutdownNow();
    }
}
