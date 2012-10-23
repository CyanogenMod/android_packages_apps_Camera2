/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.filtershow.imageshow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class ImageZoom extends ImageSlave {
    private boolean mTouchDown = false;
    private boolean mZoomedIn = false;
    private Rect mZoomBounds = null;

    public ImageZoom(Context context) {
        super(context);
    }

    public ImageZoom(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onTouchDown(float x, float y) {
        super.onTouchDown(x, y);
        if (mZoomedIn || mTouchDown) {
            return;
        }
        mTouchDown = true;
        Rect originalBounds = mImageLoader.getOriginalBounds();
        Rect imageBounds = getImageBounds();
        float touchX = x - imageBounds.left;
        float touchY = y - imageBounds.top;

        float w = originalBounds.width();
        float h = originalBounds.height();
        float ratio = w / h;
        int mw = getWidth() / 2;
        int mh = getHeight() / 2;
        int cx = (int) (w / 2);
        int cy = (int) (h / 2);
        cx = (int) (touchX / imageBounds.width() * w);
        cy = (int) (touchY / imageBounds.height() * h);
        int left = cx - mw;
        int top = cy - mh;
        mZoomBounds = new Rect(left, top, left + mw * 2, top + mh * 2);
    }

    @Override
    public void onTouchUp() {
        mTouchDown = false;
    }

    @Override
    public void onDraw(Canvas canvas) {
        drawBackground(canvas);
        Bitmap filteredImage = null;
        if ((mZoomedIn ||mTouchDown) && mImageLoader != null) {
            filteredImage = mImageLoader.getScaleOneImageForPreset(this, getImagePreset(), mZoomBounds, false);
        } else {
            requestFilteredImages();
            filteredImage = getFilteredImage();
        }
        drawImage(canvas, filteredImage);
        if (showControls()) {
            mSliderController.onDraw(canvas);
        }

        drawToast(canvas);
    }

    @Override
    public boolean onDoubleTap(MotionEvent event) {

        if (!mZoomedIn) {
            onTouchDown(event.getX(), event.getY());
        } else {
            onTouchUp();
        }
        mZoomedIn = !mZoomedIn;
        invalidate();
        return false;
    }
}