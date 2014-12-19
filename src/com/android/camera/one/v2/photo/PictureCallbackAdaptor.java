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

package com.android.camera.one.v2.photo;

import java.util.concurrent.Executor;

import android.net.Uri;

import com.android.camera.async.ConcurrentState;
import com.android.camera.async.Updatable;
import com.android.camera.one.OneCamera;
import com.android.camera.session.CaptureSession;
import com.android.camera.util.Callback;

/**
 * Splits a {@link OneCamera.PictureCallback} into separate thread-safe
 * callbacks for each method.
 */
class PictureCallbackAdaptor {
    private final OneCamera.PictureCallback mPictureCallback;
    private final Executor mMainExecutor;

    public PictureCallbackAdaptor(OneCamera.PictureCallback pictureCallback,
            Executor mainExecutor) {
        mPictureCallback = pictureCallback;
        mMainExecutor = mainExecutor;
    }

    public Updatable<Void> provideQuickExposeUpdatable() {
        ConcurrentState<Void> exposeState = new ConcurrentState<>();
        exposeState.addCallback(new Callback<Long>() {
            @Override
            public void onCallback(Long timestamp) {
                mPictureCallback.onQuickExpose();
            }
        }, mMainExecutor);
        return exposeState;
    }

    public Updatable<byte[]> provideThumbnailUpdatable() {
        ConcurrentState<byte[]> thumbnailState = new ConcurrentState<>();
        thumbnailState.addCallback(new Callback<byte[]>() {
            @Override
            public void onCallback(byte[] jpegData) {
                mPictureCallback.onThumbnailResult(jpegData);
            }
        }, mMainExecutor);
        return thumbnailState;
    }

    public Updatable<CaptureSession> providePictureTakenUpdatable() {
        ConcurrentState<CaptureSession> state = new ConcurrentState<>();
        state.addCallback(new Callback<CaptureSession>() {
            @Override
            public void onCallback(CaptureSession session) {
                mPictureCallback.onPictureTaken(session);
            }
        }, mMainExecutor);
        return state;
    }

    public Updatable<Uri> providePictureSavedUpdatable() {
        ConcurrentState<Uri> state = new ConcurrentState<>();
        state.addCallback(new Callback<Uri>() {
            @Override
            public void onCallback(Uri uri) {
                mPictureCallback.onPictureSaved(uri);
            }
        }, mMainExecutor);
        return state;
    }

    public Updatable<Void> providePictureTakingFailedUpdatable() {
        ConcurrentState<Void> state = new ConcurrentState<>();
        state.addCallback(new Callback<Void>() {
            @Override
            public void onCallback(Void v) {
                mPictureCallback.onPictureTakingFailed();
            }
        }, mMainExecutor);
        return state;
    }

    public Updatable<Float> providePictureTakingProgressUpdatable() {
        ConcurrentState<Float> state = new ConcurrentState<>();
        state.addCallback(new Callback<Float>() {
            @Override
            public void onCallback(Float progress) {
                mPictureCallback.onTakePictureProgress(progress);
            }
        }, mMainExecutor);
        return state;
    }
}
