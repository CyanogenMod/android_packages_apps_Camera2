/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import com.android.gallery3d.filtershow.editors.EditorVignette;
import com.android.gallery3d.filtershow.filters.FilterVignetteRepresentation;

public class ImageVignette extends ImageShow {
    private static final String LOGTAG = "ImageVignette";

    private FilterVignetteRepresentation mVignetteRep;
    private EditorVignette mEditorVignette;

    private int mActiveHandle = -1;

    EclipseControl mElipse;

    public ImageVignette(Context context) {
        super(context);
        mElipse = new EclipseControl(context);
    }

    public ImageVignette(Context context, AttributeSet attrs) {
        super(context, attrs);
        mElipse = new EclipseControl(context);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int mask = event.getActionMasked();
        if (mActiveHandle == -1) {
            if (MotionEvent.ACTION_DOWN != mask) {
                return super.onTouchEvent(event);
            }
            if (event.getPointerCount() == 1) {
                mActiveHandle = mElipse.getCloseHandle(event.getX(), event.getY());
            }
            if (mActiveHandle == -1) {
                return super.onTouchEvent(event);
            }
        } else {
            switch (mask) {
                case MotionEvent.ACTION_UP:
                    mActiveHandle = -1;
                    break;
                case MotionEvent.ACTION_DOWN:
                    break;
            }
        }
        float x = event.getX();
        float y = event.getY();

        mElipse.setScrToImageMatrix(getScreenToImageMatrix(true));

        boolean didComputeEllipses = false;
        switch (mask) {
            case (MotionEvent.ACTION_DOWN):
                mElipse.actionDown(x, y, mVignetteRep);
                break;
            case (MotionEvent.ACTION_UP):
            case (MotionEvent.ACTION_MOVE):
                mElipse.actionMove(mActiveHandle, x, y, mVignetteRep);
                setRepresentation(mVignetteRep);
                didComputeEllipses = true;
                break;
        }
        if (!didComputeEllipses) {
            computeEllipses();
        }
        invalidate();
        return true;
    }

    public void setRepresentation(FilterVignetteRepresentation vignetteRep) {
        mVignetteRep = vignetteRep;
        computeEllipses();
    }

    public void computeEllipses() {
        if (mVignetteRep == null) {
            return;
        }
        Matrix toImg = getScreenToImageMatrix(false);
        Matrix toScr = new Matrix();
        toImg.invert(toScr);

        float[] c = new float[] {
                mVignetteRep.getCenterX(), mVignetteRep.getCenterY() };
        if (Float.isNaN(c[0])) {
            float cx = mImageLoader.getOriginalBounds().width() / 2;
            float cy = mImageLoader.getOriginalBounds().height() / 2;
            float rx = Math.min(cx, cy) * .8f;
            float ry = rx;
            mVignetteRep.setCenter(cx, cy);
            mVignetteRep.setRadius(rx, ry);

            c[0] = cx;
            c[1] = cy;
            toScr.mapPoints(c);
            if (getWidth() != 0) {
                mElipse.setCenter(c[0], c[1]);
                mElipse.setRadius(c[0] * 0.8f, c[1] * 0.8f);
            }
        } else {

            toScr.mapPoints(c);

            mElipse.setCenter(c[0], c[1]);
            mElipse.setRadius(toScr.mapRadius(mVignetteRep.getRadiusX()),
                    toScr.mapRadius(mVignetteRep.getRadiusY()));
        }
        mEditorVignette.commitLocalRepresentation();
    }

    public void setEditor(EditorVignette editorVignette) {
        mEditorVignette = editorVignette;
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w,  h, oldw, oldh);
        computeEllipses();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mVignetteRep == null) {
            return;
        }
        Matrix toImg = getScreenToImageMatrix(false);
        Matrix toScr = new Matrix();
        toImg.invert(toScr);
        float[] c = new float[] {
                mVignetteRep.getCenterX(), mVignetteRep.getCenterY() };
        toScr.mapPoints(c);
        mElipse.setCenter(c[0], c[1]);
        mElipse.setRadius(toScr.mapRadius(mVignetteRep.getRadiusX()),
                toScr.mapRadius(mVignetteRep.getRadiusY()));

        mElipse.draw(canvas);
    }

}
