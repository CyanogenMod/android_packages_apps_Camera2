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
import com.android.gallery3d.filtershow.editors.EditorStraighten;

public class ImageStraighten extends ImageGeometry {

    private float mBaseAngle = 0;
    private float mAngle = 0;
    private EditorStraighten mEditorStraighten;

    private static final String LOGTAG = "ImageStraighten";
    private static final Paint gPaint = new Paint();
    public ImageStraighten(Context context) {
        super(context);
    }

    public ImageStraighten(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public String getName() {
        return getContext().getString(R.string.straighten);
    }

    @Override
    protected void setActionDown(float x, float y) {
        super.setActionDown(x, y);
        mBaseAngle = mAngle = getLocalStraighten();
    }

    private void setCropToStraighten(){
        setLocalCropBounds(getUntranslatedStraightenCropBounds(getLocalPhotoBounds(),
                getLocalStraighten()));
    }

    @Override
    protected void setActionMove(float x, float y) {
        super.setActionMove(x, y);
        computeValue();
        setLocalStraighten(mAngle);
        setCropToStraighten();
    }

    private void computeValue() {
        float angle = getCurrentTouchAngle();
        mAngle = (mBaseAngle - angle) % 360;
        mAngle = Math.max(MIN_STRAIGHTEN_ANGLE, mAngle);
        mAngle = Math.min(MAX_STRAIGHTEN_ANGLE, mAngle);
    }

    @Override
    protected void lostVisibility() {
        saveAndSetPreset();
    }

    @Override
    protected void gainedVisibility(){
        setCropToStraighten();
    }

    @Override
    protected void setActionUp() {
        super.setActionUp();
        setCropToStraighten();
    }

    @Override
    public void onNewValue(int value) {
        setLocalStraighten(GeometryMath.clamp(value, MIN_STRAIGHTEN_ANGLE, MAX_STRAIGHTEN_ANGLE));
        invalidate();
    }

    @Override
    protected int getLocalValue() {
        return (int) getLocalStraighten();
    }

    @Override
    protected void drawShape(Canvas canvas, Bitmap image) {
        float [] o = {0, 0};
        RectF bounds = drawTransformed(canvas, image, gPaint, o);

        // Draw the grid
        gPaint.setARGB(255, 255, 255, 255);
        gPaint.setStrokeWidth(3);
        gPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        RectF display = getLocalDisplayBounds();
        float dWidth = display.width();
        float dHeight = display.height();

        if (mMode == MODES.MOVE) {
            canvas.save();
            canvas.clipRect(bounds);

            int n = 16;
            float step = dWidth / n;
            float p = 0;
            for (int i = 1; i < n; i++) {
                p = i * step;
                gPaint.setARGB(60, 255, 255, 255);
                canvas.drawLine(p, 0, p, dHeight, gPaint);
                canvas.drawLine(0, p, dWidth, p, gPaint);
            }
            canvas.restore();
        }
    }

    public void setEditor(EditorStraighten editorStraighten) {
        mEditorStraighten = editorStraighten;
    }

}
