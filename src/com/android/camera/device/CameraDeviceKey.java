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

import java.util.Objects;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Internal representation of a single camera device for a given API. Only one
 * device key may be active at any given time and other devices must be closed
 * before opening a new one.
 *
 * A single instance is considered equal if both API and provided cameraIds
 * match and the device key is suitable for use in hash maps. All values are
 * immutable.
 */
@ThreadSafe
final class CameraDeviceKey {
    /**
     * Unified set of supported types.
     */
    public enum ApiType {
        CAMERA_API1,
        CAMERA_API2,
        CAMERA_API_PORTABILITY_AUTO,
        CAMERA_API_PORTABILITY_API1,
        CAMERA_API_PORTABILITY_API2
    }

    private final ApiType mApiType;
    private final CameraId mCameraId;

    /**
     * @return the api type for this instances.
     */
    public ApiType getApiType() {
        return mApiType;
    }

    /**
     * @return the typed cameraId for this instances.
     */
    public CameraId getCameraId() {
        return mCameraId;
    }

    /**
     * Create a camera device key with an explicit API version.
     */
    public CameraDeviceKey(ApiType apiType, CameraId cameraId) {
        mApiType = apiType;
        mCameraId = cameraId;
    }

    @Override
    public String toString() {
        return "CameraDeviceKey{" +
              "mApiType: " + mApiType +
              ", mCameraId: " + mCameraId + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CameraDeviceKey other = (CameraDeviceKey) o;

        if (mApiType != other.mApiType) {
            return false;
        }
        if (!mCameraId.equals(other.mCameraId)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mApiType, mCameraId);
    }
}
