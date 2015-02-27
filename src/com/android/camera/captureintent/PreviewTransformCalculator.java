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

package com.android.camera.captureintent;

import com.android.camera.app.OrientationManager;
import com.android.camera.debug.Log;
import com.android.camera.util.Size;

import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;

public class PreviewTransformCalculator {
    private static final Log.Tag TAG = new Log.Tag("PviewTransfmCal");

    private final OrientationManager mOrientationManager;

    public PreviewTransformCalculator(OrientationManager orientationManager) {
        mOrientationManager = orientationManager;
    }

    /**
     * Build a matrix which can be used when calling setTransform() on a
     * TextureView.
     * TODO: write unit test when roboletric is available.
     *
     * @param previewViewSize The TextureView current layout size.
     * @param previewStreamSize The selected preview video stream size.
     * @return The matrix to transform TextureView.
     */
    public Matrix toTransformMatrix(Size previewViewSize, Size previewStreamSize) {
        RectF previewViewRect =
                new RectF(0.0f, 0.0f, previewViewSize.width(), previewViewSize.height());
        PointF previewViewCenter = new PointF(previewViewRect.centerX(), previewViewRect.centerY());

        // If natural orientation is portrait, rotate the buffer dimensions.
        Size previewBufferSize = previewStreamSize;

        if (mOrientationManager.getDeviceNaturalOrientation() == OrientationManager.DeviceNaturalOrientation.PORTRAIT) {
            previewBufferSize = new Size(previewStreamSize.height(), previewStreamSize.width());
        }

        Matrix transformMatrix = new Matrix();

        // Adjust the effective preview rect to the center of the texture view.
        final RectF PreviewBufferRect =
                new RectF(0.0f, 0.0f, previewBufferSize.width(), previewBufferSize.height());
        final PointF previewBufferCenter =
                new PointF(PreviewBufferRect.centerX(), PreviewBufferRect.centerY());

        final RectF centeredEffectivePreviewRect = new RectF(PreviewBufferRect);
        centeredEffectivePreviewRect.offset(
                previewViewCenter.x - previewBufferCenter.x,
                previewViewCenter.y - previewBufferCenter.y);

        // Undo ScaleToFit.FILL done by the surface
        transformMatrix.setRectToRect(
                previewViewRect, centeredEffectivePreviewRect, Matrix.ScaleToFit.FILL);

        // Rotate buffer contents to proper orientation
        int rotateDegree = mOrientationManager.getDisplayRotation().getDegrees();
        transformMatrix.postRotate(
                rotateDegree,
                previewViewCenter.x, previewViewCenter.y);

        /**
         * Scale to fit view, cropping the longest dimension.
         *
         * surfaceTextureSize is changed with the device orientation. Since
         * previewStreamSize is always in landscape. To calculate the scale
         * factor, previewStreamSize needs to be rotated if in portrait.
         */
        Size rotatedPreviewSize = previewStreamSize;
        if (mOrientationManager.isInPortrait()) {
            rotatedPreviewSize = new Size(previewStreamSize.height(), previewStreamSize.width());
        }
        float scale = Math.min(
                (float) previewViewSize.width() / (float) rotatedPreviewSize.width(),
                (float) previewViewSize.height() / (float) rotatedPreviewSize.height());
        transformMatrix.postScale(scale, scale, previewViewCenter.x, previewViewCenter.y);

        RectF scaledPreviewStreamRect = new RectF(
                0.0f, 0.0f, previewStreamSize.width() * scale, previewStreamSize.height() * scale);
        PointF scaledPreviewStreamCenter =
                new PointF(scaledPreviewStreamRect.centerX(), scaledPreviewStreamRect.centerY());
        transformMatrix.postTranslate(
                scaledPreviewStreamCenter.x - previewViewCenter.x,
                scaledPreviewStreamCenter.y - previewViewCenter.y);

        return transformMatrix;
    }
}
