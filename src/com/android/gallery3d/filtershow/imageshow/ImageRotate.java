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
import android.util.AttributeSet;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.EditorRotate;

public class ImageRotate extends ImageGeometry {

    private float mBaseAngle = 0;
    private float mAngle = 0;

    private final boolean mSnapToNinety = true;
    private EditorRotate mEditorRotate;
    private static final String LOGTAG = "ImageRotate";

    public ImageRotate(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageRotate(Context context) {
        super(context);
    }

    @Override
    public String getName() {
        return getContext().getString(R.string.rotate);
    }

    private static final Paint gPaint = new Paint();

    private void computeValue() {
        float angle = getCurrentTouchAngle();
        mAngle = (mBaseAngle - angle) % 360;
    }

    public void rotate() {
        mAngle += 90;
        mAngle = snappedAngle(mAngle);
        mAngle %= 360;
        setLocalRotation(mAngle);
    }

    @Override
    protected void setActionDown(float x, float y) {
        super.setActionDown(x, y);
        mBaseAngle = mAngle = getLocalRotation();
    }

    @Override
    protected void setActionMove(float x, float y) {
        super.setActionMove(x, y);
        computeValue();
        setLocalRotation(mAngle % 360);
    }

    @Override
    protected void setActionUp() {
        super.setActionUp();
        if (mSnapToNinety) {
            setLocalRotation(snappedAngle(mAngle % 360));
        }
    }

    @Override
    public int getLocalValue() {
        return constrainedRotation(snappedAngle(getLocalRotation()));
    }

    @Override
    protected void drawShape(Canvas canvas, Bitmap image) {
        gPaint.setAntiAlias(true);
        gPaint.setARGB(255, 255, 255, 255);
        drawTransformedCropped(canvas, image, gPaint);
    }

    public void setEditor(EditorRotate editorRotate) {
        mEditorRotate = editorRotate;
    }
}
