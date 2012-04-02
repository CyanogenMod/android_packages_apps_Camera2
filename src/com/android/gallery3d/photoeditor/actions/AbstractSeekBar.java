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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.SeekBar;

import com.android.gallery3d.R;

/**
 * Seek-bar base that implements a draggable thumb that fits seek-bar's track height.
 */
abstract class AbstractSeekBar extends SeekBar {

    public AbstractSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // Scale the thumb to fit seek-bar's track height.
        Resources res = getResources();
        Drawable thumb = res.getDrawable(R.drawable.photoeditor_seekbar_thumb);
        int height = h - getPaddingTop() - getPaddingBottom();
        int scaledWidth = thumb.getIntrinsicWidth() * height / thumb.getIntrinsicHeight();

        Bitmap bitmap = Bitmap.createBitmap(scaledWidth, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        thumb.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        thumb.draw(canvas);

        // The thumb should not extend out of the track per UX design.
        setThumb(new BitmapDrawable(res, bitmap));
        setThumbOffset(0);

        // The thumb position is updated here after the thumb is changed.
        super.onSizeChanged(w, h, oldw, oldh);
    }
}
