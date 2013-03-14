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

package com.android.gallery3d.filtershow.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.widget.Button;

/**
 * Class of buttons with both an image icon and text.
 */
public class IconButton extends Button {

    private Bitmap mImageMirror = null;
    private Bitmap mIcon = null;

    private boolean stale_icon = true;

    public IconButton(Context context) {
        this(context, null);
    }

    public IconButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IconButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        BitmapDrawable ic = (BitmapDrawable) getCompoundDrawables()[1];

        if (ic != null) {
            mImageMirror = ic.getBitmap();
        }
    }

    /**
     * Set the image that the button icon will use.  The image bitmap will be scaled
     * and cropped into the largest square bitmap that will fit cleanly within the
     * IconButton's layout.
     *
     * @param image image that icon will be set to before next draw.
     */
    public void setIcon(Bitmap image) {
        mImageMirror = image;
        stale_icon = true;
        invalidate();
    }

    /**
     * Creates and sets button icon. Only call after layout.
     *
     * @param image bitmap to use as icon
     */
    private boolean makeAndSetIcon(Bitmap image) {
        int size = getGoodIconSideSize();
        if (size > 0) {
            return setImageIcon(makeImageIcon(image, size, size));
        }
        return false;
    }

    /**
     * Sets icon.
     *
     * @param image bitmap to set the icon to.
     */
    private boolean setImageIcon(Bitmap image) {
        if (image == null) {
            return false;
        }
        mIcon = image;
        this.setCompoundDrawablesWithIntrinsicBounds(null,
                new BitmapDrawable(getResources(), mIcon), null, null);
        return true;
    }

    /**
     * Generate an icon bitmap from a given bitmap.
     *
     * @param image bitmap to use as button icon
     * @param width icon width
     * @param height icon height
     * @return the scaled/cropped icon bitmap
     */
    private Bitmap makeImageIcon(Bitmap image, int width, int height) {
        Rect destination = new Rect(0, 0, width, height);
        Bitmap bmap = Bitmap.createBitmap(width, height,
                Bitmap.Config.ARGB_8888);
        drawImage(bmap, image, destination);
        return bmap;
    }

    /**
     * Finds a side length for the (square) icon that fits within the button.
     * Only call after layout.
     *
     * @return icon side length
     */
    private int getGoodIconSideSize() {
        Paint p = getPaint();
        Rect bounds = new Rect();
        String s = getText().toString();
        p.getTextBounds(s, 0, s.length(), bounds);
        int inner_padding = 2 * getCompoundDrawablePadding();
        int vert = getHeight() - getPaddingTop() - getPaddingBottom() - bounds.height()
                - inner_padding;
        int horiz = getWidth() - getPaddingLeft() - getPaddingRight() - inner_padding;
        return Math.min(vert, horiz);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) {
            stale_icon = true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (stale_icon && mImageMirror != null && mImageMirror.getHeight() > 0
                && mImageMirror.getWidth() > 0) {
            stale_icon = !makeAndSetIcon(mImageMirror);
        }
        super.onDraw(canvas);
    }

    /**
     * Draws the src image into the destination rectangle within the dst bitmap.
     * If src is a non-square image, clips to be a square before drawing into dst.
     *
     * @param dst  bitmap being drawn on.
     * @param src  bitmap to draw into dst.
     * @param destination  square in dst in which to draw src.
     */
    protected static void drawImage(Bitmap dst, Bitmap src, Rect destination) {
        if (src != null && dst != null && src.getWidth() > 0 && dst.getWidth() > 0
                && src.getHeight() > 0 && dst.getHeight() > 0) {
            Canvas canvas = new Canvas(dst);
            int iw = src.getWidth();
            int ih = src.getHeight();
            int x = 0;
            int y = 0;
            int size = 0;
            Rect source = null;
            if (iw > ih) {
                size = ih;
                x = (int) ((iw - size) / 2.0f);
                y = 0;
            } else {
                size = iw;
                x = 0;
                y = (int) ((ih - size) / 2.0f);
            }
            source = new Rect(x, y, x + size, y + size);
            canvas.drawBitmap(src, source, destination, new Paint());
        }
    }

}
