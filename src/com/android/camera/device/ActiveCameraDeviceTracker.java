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

package com.android.camera.device;

import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.google.common.annotations.VisibleForTesting;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.GuardedBy;

/**
 * Shared object for tracking the active camera device across multiple
 * implementations.
 */
@ParametersAreNonnullByDefault
public class ActiveCameraDeviceTracker {
    private static final Tag TAG = new Tag("ActvCamDevTrckr");

    /**
     * Singleton instance.
     */
    private static class Singleton {
        public static final ActiveCameraDeviceTracker INSTANCE = new ActiveCameraDeviceTracker();
    }

    public static ActiveCameraDeviceTracker instance() {
        return Singleton.INSTANCE;
    }

    private final Object mLock;

    @GuardedBy("mLock")
    private CameraId mActiveCamera;

    @GuardedBy("mLock")
    private CameraId mPreviousCamera;

    @VisibleForTesting
    ActiveCameraDeviceTracker() {
        mLock = new Object();
    }

    public CameraId getActiveCamera() {
        synchronized (mLock) {
            return mActiveCamera;
        }
    }

    public CameraId getActiveOrPreviousCamera() {
        synchronized (mLock) {
            if (mActiveCamera != null) {

                return mActiveCamera;
            }
            Log.v(TAG, "Returning previously active camera: " + mPreviousCamera);
            return mPreviousCamera;
        }
    }

    public void onCameraOpening(CameraId key) {
        synchronized (mLock) {
            if (mActiveCamera != null && !mActiveCamera.equals(key)) {
                mPreviousCamera = mActiveCamera;
            }

            Log.v(TAG, "Tracking active camera: " + mActiveCamera);
            mActiveCamera = key;
        }
    }

    public void onCameraClosed(CameraId key) {
        synchronized (mLock) {
            if (mActiveCamera != null && mActiveCamera.equals(key)) {
                mPreviousCamera = mActiveCamera;
                mActiveCamera = null;
            }
        }
    }
}
