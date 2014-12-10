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

package com.android.camera.one.v2.common;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.android.camera.one.OneCamera;
import com.android.camera.session.CaptureSession;

/**
 * A {@link com.android.camera.one.v2.common.GenericOneCameraImpl.PictureTaker}
 * on which {@link #takePicture} may be called even if the underlying camera is
 * not yet ready.
 */
public class DeferredPictureTaker implements GenericOneCameraImpl.PictureTaker {
    private final Future<GenericOneCameraImpl.PictureTaker> mPictureTakerFuture;

    public DeferredPictureTaker(Future<GenericOneCameraImpl.PictureTaker> pictureTakerFuture) {
        mPictureTakerFuture = pictureTakerFuture;
    }

    public void takePicture(OneCamera.PhotoCaptureParameters params, CaptureSession session) {
        if (mPictureTakerFuture.isDone()) {
            try {
                GenericOneCameraImpl.PictureTaker taker = mPictureTakerFuture.get();
                taker.takePicture(params, session);
            } catch (InterruptedException | ExecutionException | CancellationException e) {
                return;
            }
        }
    }
}
