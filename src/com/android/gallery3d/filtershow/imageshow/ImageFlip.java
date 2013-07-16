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
import com.android.gallery3d.filtershow.filters.FilterMirrorRepresentation.Mirror;

public class ImageFlip extends ImageGeometry {

    private static final Paint gPaint = new Paint();
    private static final float MIN_FLICK_DIST_FOR_FLIP= 0.1f;
    private static final String LOGTAG = "ImageFlip";
    private Mirror mNextFlip = Mirror.NONE;
    private EditorFlip mEditorFlip;

    public ImageFlip(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageFlip(Context context) {
        super(context);
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
        Mirror flip = getLocalMirror();
        boolean next = true;
        // Picks next flip in order from enum Mirror (wrapping)
        for (Mirror f : Mirror.values()) {
            if (next) {
                mNextFlip = f;
                next = false;
            }
            if (f.equals(flip)) {
                next = true;
            }
        }
        setLocalMirror(mNextFlip);
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
            Mirror flip = getLocalMirror();
            switch (flip) {
                case NONE:
                    flip = Mirror.HORIZONTAL;
                    break;
                case HORIZONTAL:
                    flip = Mirror.NONE;
                    break;
                case VERTICAL:
                    flip = Mirror.BOTH;
                    break;
                case BOTH:
                    flip = Mirror.VERTICAL;
                    break;
                default:
                    flip = Mirror.NONE;
                    break;
            }
            mNextFlip = flip;
        }
        if (Math.abs(diffy) >= flick) {
            // flick moving up/down
            Mirror flip = getLocalMirror();
            switch (flip) {
                case NONE:
                    flip = Mirror.VERTICAL;
                    break;
                case VERTICAL:
                    flip = Mirror.NONE;
                    break;
                case HORIZONTAL:
                    flip = Mirror.BOTH;
                    break;
                case BOTH:
                    flip = Mirror.HORIZONTAL;
                    break;
                default:
                    flip = Mirror.NONE;
                    break;
            }
            mNextFlip = flip;
        }
    }

    @Override
    protected void setActionUp() {
        super.setActionUp();
        setLocalMirror(mNextFlip);
    }

    @Override
    public void resetParameter() {
        super.resetParameter();
        mNextFlip = Mirror.NONE;
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
