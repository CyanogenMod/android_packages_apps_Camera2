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

package com.android.camera.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Drawable that takes a {@link RectF} as a screen, and draws around that
 * screen to fill margins between the screen and the edge of the {@link Canvas}
 * when drawing.
 */
public class MarginDrawable extends Drawable {

    private RectF mScreen = new RectF(0, 0, 0, 0);
    private final Paint mPaint;

    public MarginDrawable(int color) {
        super();

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(color);
    }

    /**
     * Set the screen around which will be drawn margins. If the screen rect
     * has no area (zero width or height), no margins will be drawn.
     *
     * @param screen A {@link RectF} describing the screen dimensions
     */
    public void setScreen(RectF screen) {
        mScreen.set(screen);
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        RectF s = mScreen;
        if (s.top < s.bottom && s.left < s.right) {
            Rect cb = canvas.getClipBounds();
            if (s.top > 0) {
                canvas.drawRect(0, 0, cb.right, s.top + 1, mPaint);
            }
            if (s.left > 0) {
                canvas.drawRect(0, s.top, s.left + 1, s.bottom, mPaint);
            }
            if (s.right < cb.right) {
                canvas.drawRect(s.right - 1, s.top, cb.right, s.bottom, mPaint);
            }
            if (s.bottom < cb.bottom) {
                canvas.drawRect(0, s.bottom - 1, cb.right, cb.bottom, mPaint);
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
