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

package com.android.camera.one.v2.initialization;

import android.content.Context;
import android.graphics.ImageFormat;

import com.android.camera.CaptureModuleUtil;
import com.android.camera.util.Size;

/**
 * Picks a preview size.
 */
class PreviewSizeSelector {
    private final int mImageFormat;
    private final Size[] mSupportedPreviewSizes;

    public PreviewSizeSelector(int imageFormat, Size[] supportedPreviewSizes) {
        mImageFormat = imageFormat;
        mSupportedPreviewSizes = supportedPreviewSizes;
    }

    public Size pickPreviewSize(Size pictureSize, Context context) {
        if (pictureSize == null) {
            // TODO The default should be selected by the caller, and
            // pictureSize should never be null.
            pictureSize = getDefaultPictureSize();
        }
        float pictureAspectRatio = pictureSize.getWidth() / (float) pictureSize.getHeight();

        // Since devices only have one raw resolution we need to be more
        // flexible for selecting a matching preview resolution.
        Double aspectRatioTolerance = mImageFormat == ImageFormat.RAW_SENSOR ? 10d : null;
        Size size = CaptureModuleUtil.getOptimalPreviewSize(context, mSupportedPreviewSizes,
                pictureAspectRatio, aspectRatioTolerance);
        return size;
    }

    /**
     * @return The largest supported picture size.
     */
    private Size getDefaultPictureSize() {
        Size[] supportedSizes = mSupportedPreviewSizes;

        // Find the largest supported size.
        Size largestSupportedSize = supportedSizes[0];
        long largestSupportedSizePixels =
                largestSupportedSize.getWidth() * largestSupportedSize.getHeight();
        for (int i = 1; i < supportedSizes.length; i++) {
            long numPixels = supportedSizes[i].getWidth() * supportedSizes[i].getHeight();
            if (numPixels > largestSupportedSizePixels) {
                largestSupportedSize = supportedSizes[i];
                largestSupportedSizePixels = numPixels;
            }
        }
        return new Size(largestSupportedSize.getWidth(), largestSupportedSize.getHeight());
    }
}
