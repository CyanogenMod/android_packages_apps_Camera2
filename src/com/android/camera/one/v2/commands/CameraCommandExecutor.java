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
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;

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
                mCommand.run();
            } catch (InterruptedException e) {
                // If interrupted, just return.
            } catch (CameraAccessException e) {
                // If the camera was closed and the command failed, just return.
            } catch (CameraCaptureSessionClosedException e) {
                // If the session was closed and the command failed, just return.
            }
        }
    }

    private final ExecutorService mExecutor;

    public CameraCommandExecutor(ExecutorService threadPoolExecutor) {
        mExecutor = threadPoolExecutor;
    }

    public void execute(CameraCommand command) {
        try {
            mExecutor.submit(new CommandRunnable(command));
        } catch (RejectedExecutionException e) {
            // If the executor is shut down, the command will not be executed.
            // So, we can ignore this exception.
        }
    }

    @Override
    public void close() {
        mExecutor.shutdownNow();
    }
}
