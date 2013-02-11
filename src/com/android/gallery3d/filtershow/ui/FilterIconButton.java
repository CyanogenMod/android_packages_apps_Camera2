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
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.cache.FilteringPipeline;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.FiltersManager;
import com.android.gallery3d.filtershow.filters.ImageFilter;

public class FilterIconButton extends IconButton implements View.OnClickListener {
    private Bitmap mOverlayBitmap = null;
    private FilterShowActivity mController = null;
    private ImageFilter mImageFilter = null;
    private FilterRepresentation mFilterRepresentation = null;
    private LinearLayout mParentContainer = null;
    private View.OnClickListener mListener = null;

    public FilterIconButton(Context context) {
        super(context);
    }

    public FilterIconButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FilterIconButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setup(String text, FilterShowActivity controller, LinearLayout parent) {
        mController = controller;
        setText(text);
        mParentContainer = parent;
        super.setOnClickListener(this);
        invalidate();
    }

    public void setup(String text, ImageFilter filter, FilterShowActivity controller,
            LinearLayout parent) {
        setup(text, controller, parent);
        mImageFilter = filter;
    }

    @Override
    protected Bitmap drawImage(Bitmap dst, Bitmap image, Rect destination) {
        dst = super.drawImage(dst, image, destination);
        if (mImageFilter == null && mFilterRepresentation != null) {
            mImageFilter = FiltersManager.getManager().getFilterForRepresentation(mFilterRepresentation);
        }
        if (mFilterRepresentation != null && mImageFilter != null) {
            mImageFilter.useRepresentation(mFilterRepresentation);
        }
        if (mImageFilter != null) {
            dst =  mImageFilter.iconApply(dst, 1.0f, false);
        }
        if (mOverlayBitmap != null) {
            dst = super.drawImage(dst, mOverlayBitmap, destination);
        }
        return dst;
    }

    @Override
    public void setOnClickListener(View.OnClickListener listener) {
        mListener = listener;
    }

    @Override
    public void onClick(View v) {
        if (mController != null && mImageFilter != null) {
            mController.useFilterRepresentation(mFilterRepresentation);
            mParentContainer.dispatchSetSelected(false);
            setSelected(true);
        }
        if (mListener != null && mListener != this) {
            mListener.onClick(v);
        }
    }

    public ImageFilter getImageFilter() {
        return mImageFilter;
    }

    public FilterRepresentation getFilterRepresentation() {
        return mFilterRepresentation;
    }

    public void setFilterRepresentation(FilterRepresentation filterRepresentation) {
        mFilterRepresentation = filterRepresentation;
        if (mFilterRepresentation != null && mFilterRepresentation.getOverlayId() != 0) {
            mOverlayBitmap = BitmapFactory.decodeResource(getResources(),
                    mFilterRepresentation.getOverlayId());
        }
    }
}
