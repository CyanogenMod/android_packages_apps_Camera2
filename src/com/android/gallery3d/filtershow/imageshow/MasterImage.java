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

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;

import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.history.HistoryItem;
import com.android.gallery3d.filtershow.history.HistoryManager;
import com.android.gallery3d.filtershow.pipeline.Buffer;
import com.android.gallery3d.filtershow.pipeline.ImagePreset;
import com.android.gallery3d.filtershow.pipeline.RenderingRequest;
import com.android.gallery3d.filtershow.pipeline.RenderingRequestCaller;
import com.android.gallery3d.filtershow.pipeline.SharedBuffer;
import com.android.gallery3d.filtershow.pipeline.SharedPreset;
import com.android.gallery3d.filtershow.state.StateAdapter;

import java.util.Vector;

public class MasterImage implements RenderingRequestCaller {

    private static final String LOGTAG = "MasterImage";
    private boolean DEBUG  = false;
    private static final boolean DISABLEZOOM = false;
    public static final int SMALL_BITMAP_DIM = 160;
    public static final int MAX_BITMAP_DIM = 900;
    private static MasterImage sMasterImage = null;

    private boolean mSupportsHighRes = false;

    private ImageFilter mCurrentFilter = null;
    private ImagePreset mPreset = null;
    private ImagePreset mLoadedPreset = null;
    private ImagePreset mGeometryOnlyPreset = null;
    private ImagePreset mFiltersOnlyPreset = null;

    private SharedBuffer mPreviewBuffer = new SharedBuffer();
    private SharedPreset mPreviewPreset = new SharedPreset();

    private Bitmap mOriginalBitmapSmall = null;
    private Bitmap mOriginalBitmapLarge = null;
    private Bitmap mOriginalBitmapHighres = null;
    private int mOrientation;
    private Rect mOriginalBounds;
    private final Vector<ImageShow> mLoadListeners = new Vector<ImageShow>();
    private Uri mUri = null;
    private int mZoomOrientation = ImageLoader.ORI_NORMAL;

    private Bitmap mGeometryOnlyBitmap = null;
    private Bitmap mFiltersOnlyBitmap = null;
    private Bitmap mPartialBitmap = null;
    private Bitmap mHighresBitmap = null;

    private HistoryManager mHistory = null;
    private StateAdapter mState = null;

    private FilterShowActivity mActivity = null;

    private Vector<ImageShow> mObservers = new Vector<ImageShow>();
    private FilterRepresentation mCurrentFilterRepresentation;

    private float mScaleFactor = 1.0f;
    private float mMaxScaleFactor = 3.0f; // TODO: base this on the current view / image
    private Point mTranslation = new Point();
    private Point mOriginalTranslation = new Point();

    private Point mImageShowSize = new Point();

    private boolean mShowsOriginal;

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

    public Bitmap getOriginalBitmapSmall() {
        return mOriginalBitmapSmall;
    }

    public Bitmap getOriginalBitmapLarge() {
        return mOriginalBitmapLarge;
    }

    public Bitmap getOriginalBitmapHighres() {
        return mOriginalBitmapHighres;
    }

    public void setOriginalBitmapHighres(Bitmap mOriginalBitmapHighres) {
        this.mOriginalBitmapHighres = mOriginalBitmapHighres;
    }

    public int getOrientation() {
        return mOrientation;
    }

    public Rect getOriginalBounds() {
        return mOriginalBounds;
    }

    public void setOriginalBounds(Rect r) {
        mOriginalBounds = r;
    }

    public Uri getUri() {
        return mUri;
    }

    public void setUri(Uri uri) {
        mUri = uri;
    }

    public int getZoomOrientation() {
        return mZoomOrientation;
    }

    public void addListener(ImageShow imageShow) {
        if (!mLoadListeners.contains(imageShow)) {
            mLoadListeners.add(imageShow);
        }
    }

    public void warnListeners() {
        mActivity.runOnUiThread(mWarnListenersRunnable);
    }

    private Runnable mWarnListenersRunnable = new Runnable() {
        @Override
        public void run() {
            for (int i = 0; i < mLoadListeners.size(); i++) {
                ImageShow imageShow = mLoadListeners.elementAt(i);
                imageShow.imageLoaded();
            }
            invalidatePreview();
        }
    };

    public boolean loadBitmap(Uri uri, int size) {
        setUri(uri);
        mOrientation = ImageLoader.getMetadataOrientation(mActivity, uri);
        Rect originalBounds = new Rect();
        mOriginalBitmapLarge = ImageLoader.loadOrientedConstrainedBitmap(uri, mActivity,
                Math.min(MAX_BITMAP_DIM, size),
                mOrientation, originalBounds);
        setOriginalBounds(originalBounds);
        if (mOriginalBitmapLarge == null) {
            return false;
        }
        int sw = SMALL_BITMAP_DIM;
        int sh = (int) (sw * (float) mOriginalBitmapLarge.getHeight() / mOriginalBitmapLarge
                .getWidth());
        mOriginalBitmapSmall = Bitmap.createScaledBitmap(mOriginalBitmapLarge, sw, sh, true);
        mZoomOrientation = mOrientation;
        warnListeners();
        return true;
    }

    public void setSupportsHighRes(boolean value) {
        mSupportsHighRes = value;
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

    public FilterShowActivity getActivity() {
        return mActivity;
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

    public synchronized void setPreset(ImagePreset preset,
                                       FilterRepresentation change,
                                       boolean addToHistory) {
        if (DEBUG) {
            preset.showFilters();
        }
        mPreset = preset;
        mPreset.fillImageStateAdapter(mState);
        if (addToHistory) {
            HistoryItem historyItem = new HistoryItem(mPreset, change);
            mHistory.addHistoryItem(historyItem);
        }
        updatePresets(true);
        mActivity.updateCategories();
    }

    public void onHistoryItemClick(int position) {
        HistoryItem historyItem = mHistory.getItem(position);
        // We need a copy from the history
        ImagePreset newPreset = new ImagePreset(historyItem.getImagePreset());
        // don't need to add it to the history
        setPreset(newPreset, historyItem.getFilterRepresentation(), false);
        mHistory.setCurrentPreset(position);
    }

    public HistoryManager getHistory() {
        return mHistory;
    }

    public StateAdapter getState() {
        return mState;
    }

    public void setHistoryManager(HistoryManager adapter) {
        mHistory = adapter;
    }

    public void setStateAdapter(StateAdapter adapter) {
        mState = adapter;
    }

    public void setCurrentFilter(ImageFilter filter) {
        mCurrentFilter = filter;
    }

    public ImageFilter getCurrentFilter() {
        return mCurrentFilter;
    }

    public synchronized boolean hasModifications() {
        // TODO: We need to have a better same effects check to see if two
        // presets are functionally the same. Right now, we are relying on a
        // stricter check as equals().
        ImagePreset loadedPreset = getLoadedPreset();
        if (mPreset == null) {
            if (loadedPreset == null) {
                return false;
            } else {
                return loadedPreset.hasModifications();
            }
        } else {
            if (loadedPreset == null) {
                return mPreset.hasModifications();
            } else {
                return !mPreset.equals(loadedPreset);
            }
        }
    }

    public SharedBuffer getPreviewBuffer() {
        return mPreviewBuffer;
    }

    public SharedPreset getPreviewPreset() {
        return mPreviewPreset;
    }

    public Bitmap getFilteredImage() {
        mPreviewBuffer.swapConsumerIfNeeded(); // get latest bitmap
        Buffer consumer = mPreviewBuffer.getConsumer();
        if (consumer != null) {
            return consumer.getBitmap();
        }
        return null;
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
                RenderingRequest.post(mActivity, getOriginalBitmapLarge(),
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
                RenderingRequest.post(mActivity, MasterImage.getImage().getOriginalBitmapLarge(),
                        mFiltersOnlyPreset, RenderingRequest.FILTERS_RENDERING, this);
            }
        }
        invalidatePreview();
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
        mPreviewPreset.enqueuePreset(mPreset);
        mPreviewBuffer.invalidate();
        invalidatePartialPreview();
        invalidateHighresPreview();
        needsUpdatePartialPreview();
        needsUpdateHighResPreview();
        mActivity.getProcessingService().updatePreviewBuffer();
    }

    public void setImageShowSize(int w, int h) {
        if (mImageShowSize.x != w || mImageShowSize.y != h) {
            mImageShowSize.set(w, h);
            needsUpdatePartialPreview();
            needsUpdateHighResPreview();
        }
    }

    private Matrix getImageToScreenMatrix(boolean reflectRotation) {
        if (getOriginalBounds() == null || mImageShowSize.x == 0 || mImageShowSize.y == 0) {
            return new Matrix();
        }
        Matrix m = GeometryMathUtils.getImageToScreenMatrix(mPreset.getGeometryFilters(),
                reflectRotation, getOriginalBounds(), mImageShowSize.x, mImageShowSize.y);
        if (m == null) {
            m = new Matrix();
            m.reset();
            return m;
        }
        Point translate = getTranslation();
        float scaleFactor = getScaleFactor();
        m.postTranslate(translate.x, translate.y);
        m.postScale(scaleFactor, scaleFactor, mImageShowSize.x / 2.0f, mImageShowSize.y / 2.0f);
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
        if (mActivity.getProcessingService() == null) {
            return;
        }
        mActivity.getProcessingService().postHighresRenderingRequest(mPreset,
                getScaleFactor(), this);
        invalidateHighresPreview();
    }

    public void needsUpdatePartialPreview() {
        if (mPreset == null) {
            return;
        }
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
        RenderingRequest.post(mActivity, null, mPreset, RenderingRequest.PARTIAL_RENDERING,
                this, bounds, new Rect(0, 0, mImageShowSize.x, mImageShowSize.y));
        invalidatePartialPreview();
    }

    @Override
    public void available(RenderingRequest request) {
        if (request.getBitmap() == null) {
            return;
        }

        boolean needsCheckModification = false;
        if (request.getType() == RenderingRequest.GEOMETRY_RENDERING) {
            mGeometryOnlyBitmap = request.getBitmap();
            needsCheckModification = true;
        }
        if (request.getType() == RenderingRequest.FILTERS_RENDERING) {
            mFiltersOnlyBitmap = request.getBitmap();
            notifyObservers();
            needsCheckModification = true;
        }
        if (request.getType() == RenderingRequest.PARTIAL_RENDERING
                && request.getScaleFactor() == getScaleFactor()) {
            mPartialBitmap = request.getBitmap();
            notifyObservers();
            needsCheckModification = true;
        }
        if (request.getType() == RenderingRequest.HIGHRES_RENDERING) {
            mHighresBitmap = request.getBitmap();
            notifyObservers();
            needsCheckModification = true;
        }
        if (needsCheckModification) {
            mActivity.enableSave(hasModifications());
        }
    }

    public static void reset() {
        sMasterImage = null;
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
        return getOriginalBitmapSmall();
    }

    public Bitmap getLargeThumbnailBitmap() {
        return getOriginalBitmapLarge();
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

    public void setLoadedPreset(ImagePreset preset) {
        mLoadedPreset = preset;
    }

    public ImagePreset getLoadedPreset() {
        return mLoadedPreset;
    }

}
