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

import com.android.gallery3d.app.Log;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.PanelController;
import com.android.gallery3d.filtershow.cache.FilteringPipeline;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.cache.RenderingRequest;
import com.android.gallery3d.filtershow.cache.RenderingRequestCaller;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.FiltersManager;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.imageshow.GeometryListener;
import com.android.gallery3d.filtershow.imageshow.GeometryMetadata;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.presets.ImagePreset;

public class FilterIconButton extends IconButton implements View.OnClickListener,
        RenderingRequestCaller, GeometryListener {
    private static final String LOGTAG = "FilterIconButton";
    private Bitmap mOverlayBitmap = null;
    private PanelController mController = null;
    private FilterRepresentation mFilterRepresentation = null;
    private LinearLayout mParentContainer = null;
    private View.OnClickListener mListener = null;
    private Bitmap mIconBitmap = null;
    private ImagePreset mPreset = null;
    private Rect mDestination = null;

    public FilterIconButton(Context context) {
        super(context);
    }

    public FilterIconButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FilterIconButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setup(String text, PanelController controller, LinearLayout parent) {
        mController = controller;
        setText(text);
        mParentContainer = parent;
        super.setOnClickListener(this);
        MasterImage.getImage().addGeometryListener(this);
        invalidate();
    }

    @Override
    protected Bitmap drawImage(Bitmap dst, Bitmap image, Rect destination) {
        if (mIconBitmap == null && mPreset == null) {
            ImageLoader loader = MasterImage.getImage().getLoader();
            if (loader != null) {
                ImagePreset geoPreset = new ImagePreset(MasterImage.getImage().getGeometryPreset());
                image = geoPreset.applyGeometry(image);
                dst = super.drawImage(dst, image, destination);
                ImagePreset mPreset = new ImagePreset();
                mPreset.addFilter(mFilterRepresentation);
                mDestination = destination;
                RenderingRequest.post(dst.copy(Bitmap.Config.ARGB_8888, true), mPreset, RenderingRequest.ICON_RENDERING, this);
            }
            return dst;
        } else {
            return mIconBitmap;
        }
    }

    @Override
    public void setOnClickListener(View.OnClickListener listener) {
        mListener = listener;
    }

    @Override
    public void onClick(View v) {
        if (mController != null) {
            mController.useFilterRepresentation(mFilterRepresentation);
            mParentContainer.dispatchSetSelected(false);
            setSelected(true);
        }
        if (mListener != null && mListener != this) {
            mListener.onClick(v);
        }
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
        invalidate();
    }

    @Override
    public void available(RenderingRequest request) {
        if (request.getBitmap() == null) {
            return;
        }
        mIconBitmap = request.getBitmap();
        if (mOverlayBitmap != null) {
            mIconBitmap = super.drawImage(mIconBitmap, mOverlayBitmap, mDestination);
        }
        invalidate();
        stale_icon = true;
    }

    @Override
    public void geometryChanged() {
        stale_icon = true;
        mIconBitmap = null;
        mPreset = null;
        invalidate();
    }
}
