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

package com.android.camera.drawable;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;


public class TextDrawable extends Drawable {

    private static final int DEFAULT_COLOR = Color.WHITE;
    private static final int DEFAULT_TEXTSIZE = 15;

    private Paint mPaint;
    private CharSequence mText;
    private int mIntrinsicWidth;
    private int mIntrinsicHeight;
    private boolean mUseDropShadow;

    public TextDrawable(Resources res) {
        this(res, "");
    }

    public TextDrawable(Resources res, CharSequence text) {
        mText = text;
        updatePaint();
        float textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                DEFAULT_TEXTSIZE, res.getDisplayMetrics());
        mPaint.setTextSize(textSize);
        mIntrinsicWidth = (int) (mPaint.measureText(mText, 0, mText.length()) + .5);
        mIntrinsicHeight = mPaint.getFontMetricsInt(null);
    }

    private void updatePaint() {
        if (mPaint == null) {
            mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
        mPaint.setColor(DEFAULT_COLOR);
        mPaint.setTextAlign(Align.CENTER);
        if (mUseDropShadow) {
            mPaint.setTypeface(Typeface.DEFAULT_BOLD);
            mPaint.setShadowLayer(10, 0, 0, 0xff000000);
        } else {
            mPaint.setTypeface(Typeface.DEFAULT);
            mPaint.setShadowLayer(0, 0, 0, 0);
        }
    }

    public void setText(CharSequence txt) {
        mText = txt;
        if (txt == null) {
            mIntrinsicWidth = 0;
            mIntrinsicHeight = 0;
        } else {
            mIntrinsicWidth = (int) (mPaint.measureText(mText, 0, mText.length()) + .5);
            mIntrinsicHeight = mPaint.getFontMetricsInt(null);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (mText != null) {
            Rect bounds = getBounds();
            canvas.drawText(mText, 0, mText.length(),
                    bounds.centerX(), bounds.centerY(), mPaint);
        }
    }

    public void setDropShadow(boolean shadow) {
        mUseDropShadow = shadow;
        updatePaint();
    }

    @Override
    public int getOpacity() {
        return mPaint.getAlpha();
    }

    @Override
    public int getIntrinsicWidth() {
        return mIntrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return mIntrinsicHeight;
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        mPaint.setColorFilter(filter);
    }

}
