/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.photoeditor.actions;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Full-screen tool view that gets photo display bounds and maps positions on photo display bounds
 * back to exact coordinates on photo.
 */
abstract class FullscreenToolView extends View {

    protected final RectF displayBounds = new RectF();
    private final Matrix photoMatrix = new Matrix();
    private RectF photoBounds;

    public FullscreenToolView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Photo bounds must be set before onSizeChanged() and all other instance methods are invoked.
     */
    public void setPhotoBounds(RectF photoBounds) {
        this.photoBounds = photoBounds;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        displayBounds.setEmpty();
        photoMatrix.reset();
        if (photoBounds.isEmpty()) {
            return;
        }

        // Assumes photo-view is also full-screen as this tool-view and centers/scales photo to fit.
        Matrix matrix = new Matrix();
        if (matrix.setRectToRect(photoBounds, new RectF(0, 0, w, h), Matrix.ScaleToFit.CENTER)) {
            matrix.mapRect(displayBounds, photoBounds);
        }

        matrix.invert(photoMatrix);
    }

    protected float getPhotoWidth() {
        return photoBounds.width();
    }

    protected float getPhotoHeight() {
        return photoBounds.height();
    }

    protected void mapPhotoPoint(float x, float y, PointF dst) {
        if (photoBounds.isEmpty()) {
            dst.set(0, 0);
        } else {
            float[] point = new float[] {x, y};
            photoMatrix.mapPoints(point);
            dst.set(point[0] / photoBounds.width(), point[1] / photoBounds.height());
        }
    }

    protected void mapPhotoRect(RectF src, RectF dst) {
        if (photoBounds.isEmpty()) {
            dst.setEmpty();
        } else {
            photoMatrix.mapRect(dst, src);
            dst.set(dst.left / photoBounds.width(), dst.top / photoBounds.height(),
                    dst.right / photoBounds.width(), dst.bottom / photoBounds.height());
        }
    }
}
