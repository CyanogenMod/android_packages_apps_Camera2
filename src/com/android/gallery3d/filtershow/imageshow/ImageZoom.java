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
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.android.gallery3d.filtershow.cache.ImageLoader;

public class ImageZoom extends ImageSlave {
    private static final String LOGTAG = "ImageZoom";
    private boolean mTouchDown = false;
    private boolean mZoomedIn = false;
    private Rect mZoomBounds = null;
    private static float mMaxSize = 512;

    public ImageZoom(Context context) {
        super(context);
    }

    public ImageZoom(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public static void setZoomedSize(float size) {
        mMaxSize = size;
    }

    @Override
    public void resetParameter() {
        super.resetParameter();
        mZoomedIn = false;
        mTouchDown = false;
    }

    @Override
    public void onTouchDown(float x, float y) {
        super.onTouchDown(x, y);
        if (mZoomedIn || mTouchDown) {
            return;
        }
        mTouchDown = true;
        GeometryMetadata geo = getImagePreset().mGeoData;
        Matrix originalToScreen = geo.getOriginalToScreen(true,
                mImageLoader.getOriginalBounds().width(),
                mImageLoader.getOriginalBounds().height(),
                getWidth(), getHeight());
        float[] point = new float[2];
        point[0] = x;
        point[1] = y;
        Matrix inverse = new Matrix();
        originalToScreen.invert(inverse);
        inverse.mapPoints(point);

        float ratio = (float) getWidth() / (float) getHeight();
        float mh = mMaxSize;
        float mw = ratio * mh;
        RectF zoomRect = new RectF(mTouchX - mw, mTouchY - mh, mTouchX + mw, mTouchY + mw);
        inverse.mapRect(zoomRect);
        zoomRect.set(zoomRect.centerX() - mw, zoomRect.centerY() - mh,
                zoomRect.centerX() + mw, zoomRect.centerY() + mh);
        mZoomBounds = new Rect((int) zoomRect.left, (int) zoomRect.top,
                (int) zoomRect.right, (int) zoomRect.bottom);
        invalidate();
    }

    @Override
    public void onTouchUp() {
        mTouchDown = false;
    }

    @Override
    public void onDraw(Canvas canvas) {
        drawBackground(canvas);

        Bitmap filteredImage = null;
        if ((mZoomedIn || mTouchDown) && mImageLoader != null) {
            filteredImage = mImageLoader.getScaleOneImageForPreset(this, getImagePreset(),
                    mZoomBounds, false);
        } else {
            requestFilteredImages();
            filteredImage = getFilteredImage();
        }
        canvas.save();
        if (mZoomedIn || mTouchDown) {
            int orientation = ImageLoader.getZoomOrientation();
            switch (orientation) {
                case ImageLoader.ORI_ROTATE_90: {
                    canvas.rotate(90, getWidth() / 2, getHeight() / 2);
                    break;
                }
                case ImageLoader.ORI_ROTATE_270: {
                    canvas.rotate(270, getWidth() / 2, getHeight() / 2);
                    break;
                }
                case ImageLoader.ORI_TRANSPOSE: {
                    canvas.rotate(90, getWidth() / 2, getHeight() / 2);
                    canvas.scale(1,  -1);
                    break;
                }
                case ImageLoader.ORI_TRANSVERSE: {
                    canvas.rotate(270, getWidth() / 2, getHeight() / 2);
                    canvas.scale(1,  -1);
                    break;
                }
            }
        }
        drawImage(canvas, filteredImage);
        canvas.restore();

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
