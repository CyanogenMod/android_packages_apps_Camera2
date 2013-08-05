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
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.EditorFlip;
import com.android.gallery3d.filtershow.imageshow.GeometryMetadata.FLIP;

public class ImageFlip extends ImageGeometry {

    private static final Paint gPaint = new Paint();
    private static final float MIN_FLICK_DIST_FOR_FLIP = 0.1f;
    private static final String LOGTAG = "ImageFlip";
    private FLIP mNextFlip = FLIP.NONE;
    private EditorFlip mEditorFlip;

    public ImageFlip(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageFlip(Context context) {
        super(context);
    }

    @Override
    public String getName() {
        return getContext().getString(R.string.mirror);
    }

    @Override
    protected void setActionDown(float x, float y) {
        super.setActionDown(x, y);
    }

    boolean hasRotated90(){
        int rot = constrainedRotation(getLocalRotation());
        return (rot / 90) % 2 != 0;
    }

    public void flip() {
        FLIP flip = getLocalFlip();
        boolean next = true;
        // Picks next flip in order from enum FLIP (wrapping)
        for (FLIP f : FLIP.values()) {
            if (next) {
                mNextFlip = f;
                next = false;
            }
            if (f.equals(flip)) {
                next = true;
            }
        }
        setLocalFlip(mNextFlip);
    }

    @Override
    protected void setActionMove(float x, float y) {
        super.setActionMove(x, y);

        float diffx = mTouchCenterX - x;
        float diffy = mTouchCenterY - y;
        float flick = getScaledMinFlick();
        if(hasRotated90()){
            float temp = diffx;
            diffx = diffy;
            diffy = temp;
        }
        if (Math.abs(diffx) >= flick) {
            // flick moving left/right
            FLIP flip = getLocalFlip();
            switch (flip) {
                case NONE:
                    flip = FLIP.HORIZONTAL;
                    break;
                case HORIZONTAL:
                    flip = FLIP.NONE;
                    break;
                case VERTICAL:
                    flip = FLIP.BOTH;
                    break;
                case BOTH:
                    flip = FLIP.VERTICAL;
                    break;
                default:
                    flip = FLIP.NONE;
                    break;
            }
            mNextFlip = flip;
        }
        if (Math.abs(diffy) >= flick) {
            // flick moving up/down
            FLIP flip = getLocalFlip();
            switch (flip) {
                case NONE:
                    flip = FLIP.VERTICAL;
                    break;
                case VERTICAL:
                    flip = FLIP.NONE;
                    break;
                case HORIZONTAL:
                    flip = FLIP.BOTH;
                    break;
                case BOTH:
                    flip = FLIP.HORIZONTAL;
                    break;
                default:
                    flip = FLIP.NONE;
                    break;
            }
            mNextFlip = flip;
        }
    }

    @Override
    protected void setActionUp() {
        super.setActionUp();
        setLocalFlip(mNextFlip);
    }

    @Override
    public void resetParameter() {
        super.resetParameter();
        mNextFlip = FLIP.NONE;
    }

    private float getScaledMinFlick() {
        RectF disp = getLocalDisplayBounds();
        float scaled = Math.min(disp.width(), disp.height()) * MIN_FLICK_DIST_FOR_FLIP
                / getLocalScale();
        return scaled;
    }

    @Override
    protected void drawShape(Canvas canvas, Bitmap image) {
        gPaint.setAntiAlias(true);
        gPaint.setARGB(255, 255, 255, 255);
        drawTransformedCropped(canvas, image, gPaint);
    }

    public void setEditor(EditorFlip editorFlip) {
        mEditorFlip = editorFlip;
    }

}
