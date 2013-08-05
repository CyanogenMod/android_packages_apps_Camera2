/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.graphics.*;
import android.os.Handler;
import android.os.Message;

import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.HistoryAdapter;
import com.android.gallery3d.filtershow.cache.*;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.presets.ImagePreset;
import com.android.gallery3d.filtershow.state.StateAdapter;

import java.util.Vector;

public class MasterImage implements RenderingRequestCaller {

    private static final String LOGTAG = "MasterImage";
    private boolean DEBUG  = false;
    private static final boolean DISABLEZOOM = true;
    private static MasterImage sMasterImage = null;
    private static int sIconSeedSize = 128;
    private static float sHistoryPreviewSize = 128.0f;

    private boolean mSupportsHighRes = false;

    private ImageFilter mCurrentFilter = null;
    private ImagePreset mPreset = null;
    private ImagePreset mGeometryOnlyPreset = null;
    private ImagePreset mFiltersOnlyPreset = null;

    private TripleBufferBitmap mFilteredPreview = new TripleBufferBitmap();

    private Bitmap mGeometryOnlyBitmap = null;
    private Bitmap mFiltersOnlyBitmap = null;
    private Bitmap mPartialBitmap = null;
    private Bitmap mHighresBitmap = null;

    private ImageLoader mLoader = null;
    private HistoryAdapter mHistory = null;
    private StateAdapter mState = null;

    private FilterShowActivity mActivity = null;

    private Vector<ImageShow> mObservers = new Vector<ImageShow>();
    private FilterRepresentation mCurrentFilterRepresentation;
    private Vector<GeometryListener> mGeometryListeners = new Vector<GeometryListener>();

    private GeometryMetadata mPreviousGeometry = null;

    private float mScaleFactor = 1.0f;
    private float mMaxScaleFactor = 3.0f; // TODO: base this on the current view / image
    private Point mTranslation = new Point();
    private Point mOriginalTranslation = new Point();

    private Point mImageShowSize = new Point();

    private boolean mShowsOriginal;

    final private static int NEW_GEOMETRY = 1;

    private final Handler mHandler = new Handler() {
            @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NEW_GEOMETRY: {
                hasNewGeometry();
                break;
            }
            }
        }
    };

    private MasterImage() {
    }

    // TODO: remove singleton
    public static void setMaster(MasterImage master) {
        sMasterImage = master;
    }

    public static MasterImage getImage() {
        if (sMasterImage == null) {
            sMasterImage = new MasterImage();
        }
        return sMasterImage;
    }

    public void setSupportsHighRes(boolean value) {
        mSupportsHighRes = value;
    }

    public static void setIconSeedSize(int iconSeedSize) {
        sIconSeedSize = iconSeedSize;
    }

    public void addObserver(ImageShow observer) {
        if (mObservers.contains(observer)) {
            return;
        }
        mObservers.add(observer);
    }

    public void setActivity(FilterShowActivity activity) {
        mActivity = activity;
    }

    public ImageLoader getLoader() {
        return mLoader;
    }

    public synchronized ImagePreset getPreset() {
        return mPreset;
    }

    public synchronized ImagePreset getGeometryPreset() {
        return mGeometryOnlyPreset;
    }

    public synchronized ImagePreset getFiltersOnlyPreset() {
        return mFiltersOnlyPreset;
    }

    public synchronized void setPreset(ImagePreset preset, boolean addToHistory) {
        mPreset = preset;
        mPreset.setImageLoader(mLoader);
        setGeometry();
        mPreset.fillImageStateAdapter(mState);
        if (addToHistory) {
            mHistory.addHistoryItem(mPreset);
        }
        updatePresets(true);
        GeometryMetadata geo = mPreset.mGeoData;
        if (!geo.equals(mPreviousGeometry)) {
            notifyGeometryChange();
        }
        mPreviousGeometry = new GeometryMetadata(geo);
    }

    private void renderHistoryPreview() {
        ImagePreset historyPreset = mPreset;
        if (historyPreset != null) {
            Bitmap preview = mLoader.getOriginalBitmapSmall();
            if (preview != null) {
                float s = Math.min(preview.getWidth(), preview.getHeight());
                float f = sHistoryPreviewSize / s;
                int w = (int) (preview.getWidth() * f);
                int h = (int) (preview.getHeight() * f);
                Bitmap historyPreview = Bitmap.createScaledBitmap(preview, w, h, true);
                historyPreset.setPreviewImage(historyPreview);
                RenderingRequest.post(historyPreview,
                        historyPreset, RenderingRequest.ICON_RENDERING, this);
            }
        }
    }

    private void setGeometry() {
        Bitmap image = mLoader.getOriginalBitmapLarge();
        if (image == null) {
            return;
        }
        float w = image.getWidth();
        float h = image.getHeight();
        GeometryMetadata geo = mPreset.mGeoData;
        RectF pb = geo.getPhotoBounds();
        if (w == pb.width() && h == pb.height()) {
            return;
        }
        RectF r = new RectF(0, 0, w, h);
        geo.setPhotoBounds(r);
        geo.setCropBounds(r);
    }

    public void onHistoryItemClick(int position) {
        setPreset(new ImagePreset(mHistory.getItem(position)), false);
        // We need a copy from the history
        mHistory.setCurrentPreset(position);
    }

    public HistoryAdapter getHistory() {
        return mHistory;
    }

    public StateAdapter getState() {
        return mState;
    }

    public void setHistoryAdapter(HistoryAdapter adapter) {
        mHistory = adapter;
    }

    public void setStateAdapter(StateAdapter adapter) {
        mState = adapter;
    }

    public void setImageLoader(ImageLoader loader) {
        mLoader = loader;
    }

    public ImageLoader getImageLoader() {
        return mLoader;
    }

    public void setCurrentFilter(ImageFilter filter) {
        mCurrentFilter = filter;
    }

    public ImageFilter getCurrentFilter() {
        return mCurrentFilter;
    }

    public synchronized boolean hasModifications() {
        if (mPreset == null) {
            return false;
        }
        return mPreset.hasModifications();
    }

    public TripleBufferBitmap getDoubleBuffer() {
        return mFilteredPreview;
    }

    public void setOriginalGeometry(Bitmap originalBitmapLarge) {
        GeometryMetadata geo = getPreset().mGeoData;
        float w = originalBitmapLarge.getWidth();
        float h = originalBitmapLarge.getHeight();
        RectF r = new RectF(0, 0, w, h);
        geo.setPhotoBounds(r);
        geo.setCropBounds(r);
        getPreset().setGeometry(geo);
    }

    public Bitmap getFilteredImage() {
        return mFilteredPreview.getConsumer();
    }

    public Bitmap getFiltersOnlyImage() {
        return mFiltersOnlyBitmap;
    }

    public Bitmap getGeometryOnlyImage() {
        return mGeometryOnlyBitmap;
    }

    public Bitmap getPartialImage() {
        return mPartialBitmap;
    }

    public Bitmap getHighresImage() {
        return mHighresBitmap;
    }

    public void notifyObservers() {
        for (ImageShow observer : mObservers) {
            observer.invalidate();
        }
    }

    public void updatePresets(boolean force) {
        if (force || mGeometryOnlyPreset == null) {
            ImagePreset newPreset = new ImagePreset(mPreset);
            newPreset.setDoApplyFilters(false);
            newPreset.setDoApplyGeometry(true);
            if (force || mGeometryOnlyPreset == null
                    || !newPreset.same(mGeometryOnlyPreset)) {
                mGeometryOnlyPreset = newPreset;
                RenderingRequest.post(mLoader.getOriginalBitmapLarge(),
                        mGeometryOnlyPreset, RenderingRequest.GEOMETRY_RENDERING, this);
            }
        }
        if (force || mFiltersOnlyPreset == null) {
            ImagePreset newPreset = new ImagePreset(mPreset);
            newPreset.setDoApplyFilters(true);
            newPreset.setDoApplyGeometry(false);
            if (force || mFiltersOnlyPreset == null
                    || !newPreset.same(mFiltersOnlyPreset)) {
                mFiltersOnlyPreset = newPreset;
                RenderingRequest.post(mLoader.getOriginalBitmapLarge(),
                        mFiltersOnlyPreset, RenderingRequest.FILTERS_RENDERING, this);
            }
        }
        invalidatePreview();
        mActivity.enableSave(hasModifications());
    }

    public FilterRepresentation getCurrentFilterRepresentation() {
        return mCurrentFilterRepresentation;
    }

    public void setCurrentFilterRepresentation(FilterRepresentation currentFilterRepresentation) {
        mCurrentFilterRepresentation = currentFilterRepresentation;
    }

    public void invalidateFiltersOnly() {
        mFiltersOnlyPreset = null;
        updatePresets(false);
    }

    public void invalidatePartialPreview() {
        if (mPartialBitmap != null) {
            mPartialBitmap = null;
            notifyObservers();
        }
    }

    public void invalidateHighresPreview() {
        if (mHighresBitmap != null) {
            mHighresBitmap = null;
            notifyObservers();
        }
    }

    public void invalidatePreview() {
        mFilteredPreview.invalidate();
        invalidatePartialPreview();
        invalidateHighresPreview();
        needsUpdatePartialPreview();
        needsUpdateHighResPreview();
        FilteringPipeline.getPipeline().updatePreviewBuffer();
    }

    public void setImageShowSize(int w, int h) {
        if (mImageShowSize.x != w || mImageShowSize.y != h) {
            mImageShowSize.set(w, h);
            needsUpdatePartialPreview();
            needsUpdateHighResPreview();
        }
    }

    private Matrix getImageToScreenMatrix(boolean reflectRotation) {
        GeometryMetadata geo = mPreset.mGeoData;
        if (geo == null || mLoader == null
                || mLoader.getOriginalBounds() == null
                || mImageShowSize.x == 0) {
            return new Matrix();
        }
        Matrix m = geo.getOriginalToScreen(reflectRotation,
                mLoader.getOriginalBounds().width(),
                mLoader.getOriginalBounds().height(), mImageShowSize.x, mImageShowSize.y);
        Point translate = getTranslation();
        float scaleFactor = getScaleFactor();
        m.postTranslate(translate.x, translate.y);
        m.postScale(scaleFactor, scaleFactor, mImageShowSize.x/2.0f, mImageShowSize.y/2.0f);
        return m;
    }

    private Matrix getScreenToImageMatrix(boolean reflectRotation) {
        Matrix m = getImageToScreenMatrix(reflectRotation);
        Matrix invert = new Matrix();
        m.invert(invert);
        return invert;
    }

    public void needsUpdateHighResPreview() {
        if (!mSupportsHighRes) {
            return;
        }
        RenderingRequest.post(null, mPreset, RenderingRequest.HIGHRES_RENDERING, this);
        invalidateHighresPreview();
    }

    public void needsUpdatePartialPreview() {
        if (!mPreset.canDoPartialRendering()) {
            invalidatePartialPreview();
            return;
        }
        Matrix m = getScreenToImageMatrix(true);
        RectF r = new RectF(0, 0, mImageShowSize.x, mImageShowSize.y);
        RectF dest = new RectF();
        m.mapRect(dest, r);
        Rect bounds = new Rect();
        dest.roundOut(bounds);
        RenderingRequest.post(null, mPreset, RenderingRequest.PARTIAL_RENDERING,
                this, bounds, new Rect(0, 0, mImageShowSize.x, mImageShowSize.y));
        invalidatePartialPreview();
    }

    @Override
    public void available(RenderingRequest request) {
        if (request.getBitmap() == null) {
            return;
        }
        if (request.getType() == RenderingRequest.GEOMETRY_RENDERING) {
            mGeometryOnlyBitmap = request.getBitmap();
        }
        if (request.getType() == RenderingRequest.FILTERS_RENDERING) {
            mFiltersOnlyBitmap = request.getBitmap();
        }
        if (request.getType() == RenderingRequest.PARTIAL_RENDERING
                && request.getScaleFactor() == getScaleFactor()) {
            mPartialBitmap = request.getBitmap();
            notifyObservers();
        }
        if (request.getType() == RenderingRequest.HIGHRES_RENDERING) {
            mHighresBitmap = request.getBitmap();
            notifyObservers();
        }

        if (request.getType() == RenderingRequest.ICON_RENDERING) {
            // History preview images
            ImagePreset preset = request.getOriginalImagePreset();
            preset.setPreviewImage(request.getBitmap());
            mHistory.notifyDataSetChanged();
        }
    }

    public static void reset() {
        sMasterImage = null;
    }

    public void addGeometryListener(GeometryListener listener) {
        mGeometryListeners.add(listener);
    }

    public void notifyGeometryChange() {
        if (mHandler.hasMessages(NEW_GEOMETRY)) {
            return;
        }
        mHandler.sendEmptyMessage(NEW_GEOMETRY);
    }

    public void hasNewGeometry() {
        updatePresets(true);
        for (GeometryListener listener : mGeometryListeners) {
            listener.geometryChanged();
        }
    }


    public float getScaleFactor() {
        return mScaleFactor;
    }

    public void setScaleFactor(float scaleFactor) {
        if (DISABLEZOOM) {
            return;
        }
        if (scaleFactor == mScaleFactor) {
            return;
        }
        mScaleFactor = scaleFactor;
        invalidatePartialPreview();
    }

    public Point getTranslation() {
        return mTranslation;
    }

    public void setTranslation(Point translation) {
        if (DISABLEZOOM) {
            mTranslation.x = 0;
            mTranslation.y = 0;
            return;
        }
        mTranslation.x = translation.x;
        mTranslation.y = translation.y;
        needsUpdatePartialPreview();
    }

    public Point getOriginalTranslation() {
        return mOriginalTranslation;
    }

    public void setOriginalTranslation(Point originalTranslation) {
        if (DISABLEZOOM) {
            return;
        }
        mOriginalTranslation.x = originalTranslation.x;
        mOriginalTranslation.y = originalTranslation.y;
    }

    public void resetTranslation() {
        mTranslation.x = 0;
        mTranslation.y = 0;
        needsUpdatePartialPreview();
    }

    public Bitmap getThumbnailBitmap() {
        return mLoader.getOriginalBitmapSmall();
    }

    public Bitmap getLargeThumbnailBitmap() {
        return mLoader.getOriginalBitmapLarge();
    }

    public float getMaxScaleFactor() {
        if (DISABLEZOOM) {
            return 1;
        }
        return mMaxScaleFactor;
    }

    public void setMaxScaleFactor(float maxScaleFactor) {
        mMaxScaleFactor = maxScaleFactor;
    }

    public boolean supportsHighRes() {
        return mSupportsHighRes;
    }

    public void setShowsOriginal(boolean value) {
        mShowsOriginal = value;
        notifyObservers();
    }

    public boolean showsOriginal() {
        return mShowsOriginal;
    }
}
