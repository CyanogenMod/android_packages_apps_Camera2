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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.widget.Button;

import com.android.gallery3d.filtershow.tools.IconFactory;
import com.android.photos.data.GalleryBitmapPool;

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
     * and cropped into the largest bitmap with dimensions given by getGoodIconSideSize()
     * that will fit cleanly within the IconButton's layout.
     *
     * @param image image that icon will be set to before next draw.
     */
    public void setIcon(Bitmap image) {
        mImageMirror = image;
        stale_icon = true;
        invalidate();
    }

    /**
     * Finds a side lengths for the icon that fits within the button.
     * Only call after layout.  The default implementation returns the best
     * side lengths for a square icon.
     * <p>
     * Override this to make non-square icons or icons with different padding
     * constraints.
     *
     * @return  an array of ints representing the icon dimensions [ width, height ]
     */
    protected int[] getGoodIconSideSize() {
        Paint p = getPaint();
        Rect bounds = new Rect();
        // find text bounds
        String s = getText().toString();
        p.getTextBounds(s, 0, s.length(), bounds);

        int inner_padding = 2 * getCompoundDrawablePadding();

        // find total vertical space available for the icon
        int vert = getHeight() - getPaddingTop() - getPaddingBottom() - bounds.height()
                - inner_padding;

        // find total horizontal space available for the icon
        int horiz = getWidth() - getPaddingLeft() - getPaddingRight() - inner_padding;

        int defaultSize = Math.min(vert, horiz);
        return new int[] { defaultSize, defaultSize };
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

    // Internal methods

    /**
     * Creates and sets button icon. Only call after layout.
     *
     * @param image bitmap to use as icon
     */
    private boolean makeAndSetIcon(Bitmap image) {
        int[] sizes = getGoodIconSideSize();
        if (sizes != null && sizes.length >= 2 && sizes[0] > 0 && sizes[1] > 0) {
            return setImageIcon(makeImageIcon(image, sizes[0], sizes[1]));
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
        if(mIcon != null && mIcon.getConfig() == Bitmap.Config.ARGB_8888) {
            GalleryBitmapPool.getInstance().put(mIcon);
            mIcon = null;
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
        if (image == null || image.getHeight() < 1 || image.getWidth() < 1 ||
                width < 1 || height < 1) {
            throw new IllegalArgumentException("input is null, or has invalid dimensions");
        }
        Bitmap icon = null;
        icon = GalleryBitmapPool.getInstance().get(width, height);
        if (icon == null) {
            icon = IconFactory.createIcon(image, width, height, false);
        } else {
            assert(icon.getWidth() == width && icon.getHeight() == height);
            icon.eraseColor(Color.TRANSPARENT);
            IconFactory.drawIcon(icon, image, false);
        }
        return icon;
    }
}
