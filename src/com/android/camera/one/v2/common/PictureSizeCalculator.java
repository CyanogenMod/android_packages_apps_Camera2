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

package com.android.camera.one.v2.common;

import android.graphics.Rect;

import com.android.camera.one.OneCameraAccessException;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.util.AspectRatio;
import com.android.camera.util.Size;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Selects the optimal picture size and crop for a particular target size.
 * <p>
 * A particular camera2 device will support a finite set of output resolutions.
 * However, we may wish to take pictures with a size that is not directly
 * supported.
 * <p>
 * For example, we may wish to use a large 4:3 output size to capture
 * as-large-as-possible 16:9 images. This requires determining the smallest
 * output size which can contain the target size, and then computing the
 * appropriate crop region.
 */
@ParametersAreNonnullByDefault
public final class PictureSizeCalculator {
    private final OneCameraCharacteristics mCameraCharacteristics;

    public PictureSizeCalculator(OneCameraCharacteristics cameraCharacteristics) {
        mCameraCharacteristics = cameraCharacteristics;
    }

    public static final class Configuration {
        private final Size mSize;
        private final Rect mPostCrop;

        private Configuration(Size size, Rect postCrop) {
            mSize = size;
            mPostCrop = postCrop;
        }

        /**
         * @return The crop to be applied to Images returned from the camera
         *         device.
         */
        public Rect getPostCaptureCrop() {
            return mPostCrop;
        }

        /**
         * @return The best natively-supported size to use.
         */
        public Size getNativeOutputSize() {
            return mSize;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper("PictureSizeCalculator.Configuration")
                    .add("native size", mSize)
                    .add("crop", mPostCrop)
                    .toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (!(o instanceof Configuration)) {
                return false;
            }

            Configuration that = (Configuration) o;

            if (!mPostCrop.equals(that.mPostCrop)) {
                return false;
            } else if (!mSize.equals(that.mSize)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mSize, mPostCrop);
        }
    }

    @Nonnull
    private Size getSmallestSupportedSizeContainingTarget(List<Size> supported, Size target) {
        Preconditions.checkState(!supported.isEmpty());
        Size best = null;
        long bestArea = Long.MAX_VALUE;
        for (Size candidate : supported) {
            long pixels = candidate.area();
            if (candidate.getWidth() >= target.getWidth() &&
                    candidate.getHeight() >= target.getHeight() &&
                    pixels < bestArea) {
                best = candidate;
                bestArea = pixels;
            }
        }

        if (best == null) {
            // If no supported sizes contain the target size, then select the
            // largest one.
            best = getLargestSupportedSize(supported);
        }

        return best;
    }

    /**
     * A picture size Configuration consists of a device-supported size and a
     * crop-region to apply to images retrieved from the device. The combination
     * of these should achieve the desired image size specified in
     * {@link #computeConfiguration}.
     *
     * @return The optimal configuration of device-supported picture size and
     *         post-capture crop region to use.
     * @throws com.android.camera.one.OneCameraAccessException if a
     *             configuration could not be computed.
     */
    public Configuration computeConfiguration(Size targetSize, int imageFormat)
            throws OneCameraAccessException {
        List<Size> supportedPictureSizes = mCameraCharacteristics
                .getSupportedPictureSizes(imageFormat);
        if (supportedPictureSizes.isEmpty()) {
            throw new OneCameraAccessException("No picture sizes supported for format: "
                    + imageFormat);
        }
        Size size = getSmallestSupportedSizeContainingTarget(supportedPictureSizes, targetSize);
        Rect cropRegion = getPostCrop(AspectRatio.of(targetSize), size);
        return new Configuration(size, cropRegion);
    }

    @Nonnull
    private Size getLargestSupportedSize(List<Size> supported) {
        Preconditions.checkState(!supported.isEmpty());
        Size largestSize = supported.get(0);
        long largestArea = largestSize.area();
        for (Size candidate : supported) {
            long area = candidate.area();
            if (area > largestArea) {
                largestSize = candidate;
            }
        }
        return largestSize;
    }

    private Rect getPostCrop(AspectRatio targetAspect, Size actualSize) {
        return targetAspect.getLargestCenterCrop(actualSize);
    }
}
