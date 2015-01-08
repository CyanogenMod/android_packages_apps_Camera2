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

import android.net.Uri;

import com.android.camera.async.Updatable;
import com.android.camera.one.OneCamera;
import com.android.camera.session.CaptureSession;

import java.util.concurrent.Executor;

import javax.annotation.Nonnull;

/**
 * Splits a {@link OneCamera.PictureCallback} into separate thread-safe
 * callbacks for each method.
 */
class PictureCallbackAdapter {
    private final OneCamera.PictureCallback mPictureCallback;
    private final Executor mMainExecutor;

    public PictureCallbackAdapter(OneCamera.PictureCallback pictureCallback,
          Executor mainExecutor) {
        mPictureCallback = pictureCallback;
        mMainExecutor = mainExecutor;
    }

    public Updatable<Void> provideQuickExposeUpdatable() {
        return new Updatable<Void>() {
            @Override
            public void update(@Nonnull Void v) {
                mMainExecutor.execute(new Runnable() {
                    public void run() {
                        mPictureCallback.onQuickExpose();
                    }
                });
            }
        };
    }

    public Updatable<byte[]> provideThumbnailUpdatable() {
        return new Updatable<byte[]>() {
            @Override
            public void update(@Nonnull final byte[] jpegData) {
                mMainExecutor.execute(new Runnable() {
                    public void run() {
                        mPictureCallback.onThumbnailResult(jpegData);
                    }
                });
            }
        };
    }

    public Updatable<CaptureSession> providePictureTakenUpdatable() {
        return new Updatable<CaptureSession>() {
            @Override
            public void update(@Nonnull final CaptureSession session) {
                mMainExecutor.execute(new Runnable() {
                    public void run() {
                        mPictureCallback.onPictureTaken(session);
                    }
                });
            }
        };
    }

    public Updatable<Uri> providePictureSavedUpdatable() {
        return new Updatable<Uri>() {
            @Override
            public void update(@Nonnull final Uri uri) {
                mMainExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        mPictureCallback.onPictureSaved(uri);
                    }
                });
            }
        };
    }

    public Updatable<Void> providePictureTakingFailedUpdatable() {
        return new Updatable<Void>() {
            @Override
            public void update(@Nonnull Void v) {
                mMainExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        mPictureCallback.onPictureTakingFailed();
                    }
                });
            }
        };
    }

    public Updatable<Float> providePictureTakingProgressUpdatable() {
        return new Updatable<Float>() {
            @Override
            public void update(@Nonnull final Float progress) {
                mMainExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        mPictureCallback.onTakePictureProgress(progress);
                    }
                });
            }
        };
    }
}
