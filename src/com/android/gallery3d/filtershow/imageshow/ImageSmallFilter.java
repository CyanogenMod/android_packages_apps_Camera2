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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.presets.ImagePreset;

public class ImageSmallFilter extends ImageShow implements View.OnClickListener {

    private static final String LOGTAG = "ImageSmallFilter";
    private FilterShowActivity mController = null;
    protected ImageFilter mImageFilter = null;
    private boolean mShowTitle = true;
    private boolean mSetBorder = false;
    protected final Paint mPaint = new Paint();
    protected boolean mIsSelected = false;

    // TODO: move this to xml.
    protected static int mMargin = 12;
    protected static int mTextMargin = 8;
    protected static int mBackgroundColor = Color.BLUE;
    protected final int mSelectedBackgroundColor = Color.WHITE;
    protected final int mTextColor = Color.WHITE;
    private ImageSmallFilter mNullFilter;

    public static void setMargin(int value) {
        mMargin = value;
    }

    public static void setTextMargin(int value) {
        mTextMargin = value;
    }

    public static void setDefaultBackgroundColor(int value) {
        mBackgroundColor = value;
    }

    public ImageSmallFilter(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnClickListener(this);
    }

    public ImageSmallFilter(Context context) {
        super(context);
        setOnClickListener(this);
    }

    public void setImageFilter(ImageFilter filter) {
        mImageFilter = filter;
        mImagePreset = new ImagePreset();
        mImagePreset.setName(filter.getName());
        filter.setImagePreset(mImagePreset);
        mImagePreset.add(mImageFilter);
    }

    @Override
    public void setSelected(boolean value) {
        if (mIsSelected != value) {
            invalidate();
        }
        mIsSelected = value;
    }

    public void setBorder(boolean value) {
        mSetBorder = value;
    }

    public void setController(FilterShowActivity activity) {
        mController = activity;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        int h = mTextSize + mTextPadding;
        setMeasuredDimension(parentHeight - h, parentHeight);
    }

    /**
     * Setting the nullFilter implies that the behavior of the button is toggle
     *
     * @param nullFilter
     */
    public void setNulfilter(ImageSmallFilter nullFilter) {
        mNullFilter = nullFilter;
    }

    @Override
    public void onClick(View v) {
        if (mController != null) {
            if (mImageFilter != null) {
                if (mIsSelected && mNullFilter != null) {
                    mNullFilter.onClick(v);
                }
                else {
                    mController.useImageFilter(this, mImageFilter, mSetBorder);
                }
            } else if (mImagePreset != null) {
                if (mIsSelected && mNullFilter != null) {
                    mNullFilter.onClick(v);
                }
                else {
                    mController.useImagePreset(this, mImagePreset);
                }
            }
        }
    }

    @Override
    public boolean updateGeometryFlags() {
        // We don't want to warn listeners here that the image size has changed, because
        // we'll be working with the small image...
        return false;
    }

    public void setShowTitle(boolean value) {
        mShowTitle = value;
        invalidate();
    }

    @Override
    public boolean showTitle() {
        return mShowTitle;
    }

    @Override
    public boolean showControls() {
        return false;
    }

    @Override
    public boolean showHires() {
        return false;
    }

    @Override
    public ImagePreset getImagePreset() {
        return mImagePreset;
    }

    @Override
    public void updateImagePresets(boolean force) {
        ImagePreset preset = getImagePreset();
        if (preset == null) {
            return;
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        requestFilteredImages();
        canvas.drawColor(mBackgroundColor);
        float textWidth = mPaint.measureText(mImageFilter.getName());
        int h = mTextSize + 2 * mTextPadding;
        int x = (int) ((getWidth() - textWidth) / 2);
        int y = getHeight();
        if (mIsSelected) {
            mPaint.setColor(mSelectedBackgroundColor);
            canvas.drawRect(0, mMargin, getWidth(), getWidth() + mMargin, mPaint);
        }
        Rect destination = new Rect(mMargin, 2*mMargin, getWidth() - mMargin, getWidth());
        drawImage(canvas, getFilteredImage(), destination);
        mPaint.setTextSize(mTextSize);
        mPaint.setColor(mTextColor);
        canvas.drawText(mImageFilter.getName(), x, y - mTextMargin, mPaint);
    }

    public void drawImage(Canvas canvas, Bitmap image, Rect destination) {
        if (image != null) {
            int iw = image.getWidth();
            int ih = image.getHeight();
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
            canvas.drawBitmap(image, source, destination, mPaint);
        }
    }

}
