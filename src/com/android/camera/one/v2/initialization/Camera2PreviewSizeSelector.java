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

import com.android.camera.CaptureModuleUtil;
import com.android.camera.one.PreviewSizeSelector;
import com.android.camera.util.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Picks a preview size. TODO Remove dependency on static CaptureModuleUtil
 * function and write tests.
 */
class Camera2PreviewSizeSelector implements PreviewSizeSelector {
    private final List<Size> mSupportedPreviewSizes;

    public Camera2PreviewSizeSelector(List<Size> supportedPreviewSizes) {
        mSupportedPreviewSizes = new ArrayList<>(supportedPreviewSizes);
    }

    public Size pickPreviewSize(Size pictureSize) {
        if (pictureSize == null) {
            // TODO The default should be selected by the caller, and
            // pictureSize should never be null.
            pictureSize = getLargestPictureSize();
        }
        float pictureAspectRatio = pictureSize.getWidth() / (float) pictureSize.getHeight();

        Size size = CaptureModuleUtil.getOptimalPreviewSize(
                (Size[]) mSupportedPreviewSizes.toArray(new Size[mSupportedPreviewSizes.size()]),
                pictureAspectRatio, null);
        return size;
    }

    /**
     * @return The largest supported picture size.
     */
    private Size getLargestPictureSize() {
        return Collections.max(mSupportedPreviewSizes, new Comparator<Size>() {
            @Override
            public int compare(Size size1, Size size2) {
                int area1 = size1.getWidth() * size1.getHeight();
                int area2 = size2.getWidth() * size2.getHeight();
                return Integer.compare(area1, area2);
            }
        });
    }
}
