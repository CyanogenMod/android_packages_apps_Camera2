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
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Region.Op;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.widget.SeekBar;

import com.android.gallery3d.R;

/**
 * Seek-bar that has a draggable thumb to set and get the color from predefined color set.
 */
class ColorSeekBar extends AbstractSeekBar {

    /**
     * Listens to color changes.
     */
    public interface OnColorChangeListener {

        void onColorChanged(int color, boolean fromUser);
    }

    private final int[] colors;
    private Bitmap progressDrawable;

    public ColorSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Set up the predefined colors that could be indexed in the seek-bar.
        TypedArray a = getResources().obtainTypedArray(R.array.color_picker_colors);
        colors = new int[a.length()];
        for (int i = 0; i < a.length(); i++) {
            colors[i] = a.getColor(i, 0x000000);
        }
        a.recycle();
        setMax(colors.length - 1);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (progressDrawable != null) {
            progressDrawable.recycle();
        }
        int width = w - getPaddingLeft() - getPaddingRight();
        int height = h - getPaddingTop() - getPaddingBottom();
        progressDrawable = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(progressDrawable);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);

        // Draw two half circles in the first and last colors at the left/right ends.
        int radius = height / 2;
        float left = radius;
        float right = width - radius;

        canvas.save();
        canvas.clipRect(left, 0, right, height, Op.DIFFERENCE);
        paint.setColor(colors[0]);
        canvas.drawCircle(left, radius, radius, paint);
        paint.setColor(colors[colors.length - 1]);
        canvas.drawCircle(right, radius, radius, paint);
        canvas.restore();

        // Draw color strips that make the thumb stop at every strip's center during seeking.
        float strip = (right - left) / (colors.length - 1);
        right = left + strip / 2;
        paint.setColor(colors[0]);
        canvas.drawRect(left, 0, right, height, paint);
        left = right;
        for (int i = 1; i < colors.length - 1; i++) {
            right = left + strip;
            paint.setColor(colors[i]);
            canvas.drawRect(left, 0, right, height, paint);
            left = right;
        }
        right = left + strip / 2;
        paint.setColor(colors[colors.length - 1]);
        canvas.drawRect(left, 0, right, height, paint);

        setProgressDrawable(new BitmapDrawable(getResources(), progressDrawable));
    }

    public void setOnColorChangeListener(final OnColorChangeListener listener) {
        setOnSeekBarChangeListener((listener == null) ? null : new OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                listener.onColorChanged(colors[progress], fromUser);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    public void setColorIndex(int colorIndex) {
        setProgress(colorIndex);
    }

    public int getColor() {
        return colors[getProgress()];
    }
}
