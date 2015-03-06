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

package com.android.camera.processing.imagebackend;

import com.google.common.base.Optional;

import android.content.Context;
import android.location.Location;

import com.android.camera.app.CameraServices;
import com.android.camera.debug.Log;
import com.android.camera.processing.ProcessingTask;
import com.android.camera.session.CaptureSession;

import java.util.concurrent.locks.Condition;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Implements a placeholder task so that ImageBackend can communicate to the
 * ProcessingServiceManager, when it is running a set of task created by the
 * receiveImage call. The ImageShadow tasks also contains a Runnable which can
 * be executed when the set of TaskImageContainers associated with the
 * ImageShadow tasks completes. This implementation of the ProcessingTask will
 * block the ProcessingServiceManager from running any other jobs. However,
 * ProcessingServiceManager has no thread control over the ImageBackend. So
 * while ProcessingServiceManager may queue up this ImageShadowTask for later
 * execution, the ImageBackend will process the TaskImageContainer jobs without
 * regard to this ImageShadowTask being queued.
 */
@ParametersAreNonnullByDefault
class ImageShadowTask implements ProcessingTask {
    static final private Log.Tag TAG = new Log.Tag("ImageShadowTask");

    private final CaptureSession mCaptureSession;
    private final ImageBackend.BlockSignalProtocol mProtocol;
    private final Runnable mRunnableWhenDone;
    private ProcessingTaskDoneListener mDoneListener;
    private Condition mSignal;

    /**
     * Constructor
     *
     * @param protocol the blocking implementation that will keep this shadow
     *            task from completing before all of its associated subtasks are
     *            done
     * @param captureSession the capture session associated with this shadow
     *            task
     * @param runnableWhenDone optional runnable to be executed when all the
     *            associated sub-tasks of the ImageShadowTask are completed.
     *            This runnable will be executed on the Executor of the last
     *            subtask that completes (as specified in TaskImageContainer).
     *            This underlying runnable is a part of the ImageBackend
     *            infrastructure, and should NOT be associated with the
     *            ProcessingTask implementation.
     */
    ImageShadowTask(ImageBackend.BlockSignalProtocol protocol,
            CaptureSession captureSession, Optional<Runnable> runnableWhenDone) {
        mProtocol = protocol;
        mCaptureSession = captureSession;
        if(runnableWhenDone.isPresent()) {
            mRunnableWhenDone = runnableWhenDone.get();
        } else {
            mRunnableWhenDone = null;
        }
    }

    ImageBackend.BlockSignalProtocol getProtocol() {
        return mProtocol;
    }

    /**
     * Returns the Runnable to be executed when all the associated
     * TaskImageContainer of ImageShadowTask have been completed.
     */
    public Runnable getRunnableWhenDone() {
        return mRunnableWhenDone;
    }

    @Override
    public ProcessingResult process(Context context, CameraServices services, CaptureSession session) {
        try {
            mProtocol.block();
        } catch (InterruptedException e) {
            // Exit cleanly on Interrupt.
            Log.w(TAG, "Image Shadow task Interrupted.");
        }

        ProcessingResult finalResult = new ProcessingResult(true, mCaptureSession);
        // Always finishes alright.
        if (mDoneListener != null) {
            mDoneListener.onDone(finalResult);
        }
        return finalResult;
    }

    @Override
    public void suspend() {
        // Do nothing. We are unsuspendable.
    }

    @Override
    public void resume() {
        // Do nothing. We are unresumable.
    }

    @Override
    public String getName() {
        // Name is only required when Session is NULL. Session should never be
        // set to NULL.
        return null;
    }

    @Override
    public Location getLocation() {
        return null;
    }

    @Override
    public CaptureSession getSession() {
        return mCaptureSession;
    }

    @Override
    public void setDoneListener(ProcessingTaskDoneListener listener) {
        mDoneListener = listener;
    }

}
