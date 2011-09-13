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
 * Seek-bar base that implements a draggable thumb that fits seek-bar height.
 */
abstract class AbstractSeekBar extends SeekBar {

    public AbstractSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Scale the thumb to fit seek-bar height.
        Resources res = getResources();
        Drawable thumb = res.getDrawable(R.drawable.photoeditor_seekbar_thumb);

        // Set the left/right padding to half width of the thumb drawn.
        int scaledWidth = thumb.getIntrinsicWidth() * h / thumb.getIntrinsicHeight();
        int padding = (scaledWidth + 1) / 2;
        setPadding(padding, 0, padding, 0);

        Bitmap bitmap = Bitmap.createBitmap(scaledWidth, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        thumb.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        thumb.draw(canvas);

        setThumb(new BitmapDrawable(res, bitmap));
    }
}
