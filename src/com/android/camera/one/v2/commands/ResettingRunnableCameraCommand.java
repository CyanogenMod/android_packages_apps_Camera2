/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.google.common.util.concurrent.Futures;

import java.util.concurrent.Future;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Converts a {@link CameraCommand} into a {@link Runnable} which interrupts and
 * restarts the command if it was already running.
 */
@ThreadSafe
@ParametersAreNonnullByDefault
public final class ResettingRunnableCameraCommand implements Runnable {
    private final CameraCommandExecutor mExecutor;
    private final CameraCommand mCommand;
    private final Object mLock;

    /**
     * The future corresponding to any currently-executing command.
     */
    @Nonnull
    private Future<?> mInProgressCommand;

    public ResettingRunnableCameraCommand(CameraCommandExecutor executor, CameraCommand command) {
        mExecutor = executor;
        mCommand = command;
        mLock = new Object();
        mInProgressCommand = Futures.immediateFuture(new Object());
    }

    @Override
    public void run() {
        synchronized (mLock) {
            // Cancel, via interruption, the already-running command, one has
            // been started and has not yet completed.
            mInProgressCommand.cancel(true /* mayInterruptIfRunning */);
            mInProgressCommand = mExecutor.execute(mCommand);
        }
    }
}
