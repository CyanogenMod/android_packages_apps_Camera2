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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.cache.RenderingRequest;
import com.android.gallery3d.filtershow.cache.RenderingRequestCaller;
import com.android.gallery3d.filtershow.category.Action;
import com.android.gallery3d.filtershow.category.CategoryAdapter;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.imageshow.GeometryListener;
import com.android.gallery3d.filtershow.imageshow.GeometryMetadata;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.presets.ImagePreset;
import com.android.gallery3d.filtershow.tools.IconFactory;

// TODO: merge back IconButton and FilterIconButton?
public class FilterIconButton extends IconButton implements View.OnClickListener,
        RenderingRequestCaller, GeometryListener {
    private static final String LOGTAG = "FilterIconButton";
    private Bitmap mOverlayBitmap = null;
    private boolean mOverlayOnly = false;
    private FilterRepresentation mFilterRepresentation = null;
    private Bitmap mIconBitmap = null;
    private Action mAction;
    private Paint mSelectPaint;
    private int mSelectStroke;
    private CategoryAdapter mAdapter;
    public FilterIconButton(Context context) {
        super(context);
    }

    public FilterIconButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FilterIconButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setup(String text, LinearLayout parent, CategoryAdapter adapter) {
        mAdapter = adapter;
        setText(text);
        setContentDescription(text);
        super.setOnClickListener(this);
        Resources res = getContext().getResources();
        MasterImage.getImage().addGeometryListener(this);
        mSelectStroke = res.getDimensionPixelSize(R.dimen.thumbnail_margin);
        mSelectPaint = new Paint();
        mSelectPaint.setStyle(Paint.Style.FILL);
        mSelectPaint.setColor(res.getColor(R.color.filtershow_category_selection));
        invalidate();
    }

    @Override
    public void onClick(View v) {
        FilterShowActivity activity = (FilterShowActivity) getContext();
        activity.showRepresentation(mFilterRepresentation);
        mAdapter.setSelected(v);
    }

    public FilterRepresentation getFilterRepresentation() {
        return mFilterRepresentation;
    }

    public void setAction(Action action) {
        mAction = action;
        if (action == null) {
            return;
        }
        if (mAction.getPortraitImage() != null) {
            mIconBitmap = mAction.getPortraitImage();
            setIcon(mIconBitmap);
        }
        setFilterRepresentation(mAction.getRepresentation());
    }

    private void setFilterRepresentation(FilterRepresentation filterRepresentation) {
        mFilterRepresentation = filterRepresentation;
        if (mFilterRepresentation != null && mFilterRepresentation.getOverlayId() != 0) {
            if (mAction.getOverlayBitmap() == null) {
                mOverlayBitmap = BitmapFactory.decodeResource(getResources(),
                    mFilterRepresentation.getOverlayId());
                mAction.setOverlayBitmap(mOverlayBitmap);
            } else {
                mOverlayBitmap = mAction.getOverlayBitmap();
            }
        }
        mOverlayOnly = mFilterRepresentation.getOverlayOnly();
        if (mOverlayOnly) {
            assert(mOverlayBitmap != null);
            setIcon(mOverlayBitmap);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mIconBitmap == null && !mOverlayOnly) {
            postNewIconRenderRequest();
        } else {
            super.onDraw(canvas);
        }
        if (mAdapter.isSelected(this)) {
            Drawable iconDrawable = getCompoundDrawables()[1];
            if (iconDrawable != null) {
                canvas.save();
                int padding = getCompoundDrawablePadding();
                canvas.translate(getScrollX() + padding + getPaddingLeft() - mSelectStroke - 1,
                        getScrollY() + padding + getPaddingTop() - mSelectStroke - 1);
                Rect r = iconDrawable.getBounds();
                SelectionRenderer.drawSelection(canvas, r.left, r.top,
                        r.right + 2 * mSelectStroke + 2, r.bottom + 2 * mSelectStroke + 2,
                        mSelectStroke, mSelectPaint);
                canvas.restore();
            }
        }
    }

    @Override
    public void available(RenderingRequest request) {
        Bitmap bmap = request.getBitmap();
        if (bmap == null) {
            return;
        }
        if (mOverlayOnly) {
            setIcon(mOverlayBitmap);
        } else {
            mIconBitmap = bmap;
            if (mOverlayBitmap != null) {
                // Draw overlay bitmap over icon
                IconFactory.drawIcon(mIconBitmap, mOverlayBitmap, false);
            }
            setIcon(mIconBitmap);
            if (mAction != null) {
                mAction.setPortraitImage(mIconBitmap);
            }
        }
    }

    @Override
    public void geometryChanged() {
        if (mOverlayOnly) {
            return;
        }
        mIconBitmap = null;
        invalidate();
    }

    private void postNewIconRenderRequest() {
        Bitmap dst = MasterImage.getImage().getThumbnailBitmap();
        if (dst != null && mAction != null) {
            ImagePreset mPreset = new ImagePreset();
            mPreset.addFilter(mFilterRepresentation);

            GeometryMetadata geometry = mPreset.mGeoData;
            RectF bound = new RectF(0, 0, dst.getWidth(), dst.getHeight());
            geometry.setCropBounds(bound);
            geometry.setPhotoBounds(bound);

            RenderingRequest.post(dst.copy(Bitmap.Config.ARGB_8888, true),
                    mPreset, RenderingRequest.ICON_RENDERING, this);
        }
    }
}
