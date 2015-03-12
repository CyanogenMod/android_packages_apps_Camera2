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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Identifier for Camera1 and Camera2 camera devices.
 */
public final class CameraId {
    private final Integer mLegacyCameraId;
    private final String mCameraId;

    public static CameraId fromLegacyId(int camera1Id) {
        return new CameraId(computeCameraIdFromLegacyId(camera1Id), camera1Id);
    }

    public static CameraId from(@Nonnull String camera2Id) {
        return new CameraId(camera2Id, computeLegacyIdFromCamera2Id(camera2Id));
    }

    /**
     * This should compute a Legacy Api1 camera Id for the given camera2 device.
     * This class will return null if the camera2 identifier cannot be transformed
     * into an api1 id.
     */
    private static Integer computeLegacyIdFromCamera2Id(@Nonnull String camera2Id) {
        try {
            return Integer.parseInt(camera2Id);
        } catch (NumberFormatException ignored) {

        }

        return null;
    }

    /**
     * This should compute a Camera2 Id for the given legacy camera device.
     * This should never return a null value.
     */
    private static String computeCameraIdFromLegacyId(int camera1Id) {
        return String.valueOf(camera1Id);
    }

    private CameraId(@Nonnull String cameraId, @Nullable Integer legacyCameraId) {
        mCameraId = cameraId;
        mLegacyCameraId = legacyCameraId;
    }

    /**
     * Return the Camera Api2 String representation that this instance represents.
     */
    public String getValue() {
        return mCameraId;
    }

    /**
     * Return the Legacy Api1 Camera index. It will throw an exception if the value
     * does not exist, which should only happen if the device that is being opened
     * is not supported on Api1.
     */
    public int getLegacyValue() throws UnsupportedOperationException {
        if (mLegacyCameraId == null) {
            throw new UnsupportedOperationException("Attempted to access a camera id that is not"
                  + " supported on legacy camera API's: " + mCameraId);
        }

        return mLegacyCameraId;
    }

    /**
     * Return true if this instance has a valid Legacy Api camera index.
     */
    public boolean hasLeagcyValue() {
        return mLegacyCameraId != null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (! (other instanceof CameraId)) {
            return false;
        }

        CameraId otherCameraId = (CameraId) other;

        // Note: mLegacyCameraId is omitted and only mCameraId is used as the
        // canonical "equals" for these instances since a Camera2 id can be
        // created from any Camera1 id.
        return mCameraId.equals(otherCameraId.mCameraId);
    }

    @Override
    public int hashCode() {
        return mCameraId.hashCode();
    }

    @Override
    public String toString() {
        return "CameraId{" +
              "Api2='" + mCameraId + "\',Api1:"+mLegacyCameraId+"}";
    }
}
