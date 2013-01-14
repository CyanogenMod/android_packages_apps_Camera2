package com.android.gallery3d.filtershow.imageshow;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;
import android.widget.ArrayAdapter;

import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.HistoryAdapter;
import com.android.gallery3d.filtershow.ImageStateAdapter;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.presets.ImagePreset;

import java.util.Vector;

public class MasterImage {

    private static final String LOGTAG = "MasterImage";

    private static MasterImage sMasterImage = new MasterImage();

    private ImageFilter mCurrentFilter = null;
    private ImagePreset mPreset = null;
    private ImagePreset mGeometryOnlyPreset = null;
    private ImagePreset mFiltersOnlyPreset = null;

    private Bitmap mGeometryOnlyImage = null;
    private Bitmap mFiltersOnlyImage = null;
    private Bitmap mFilteredImage = null;

    private ImageLoader mLoader = null;
    private HistoryAdapter mHistory = null;
    private ImageStateAdapter mState = null;

    private FilterShowActivity mActivity = null;

    private Vector<ImageShow> mObservers = new Vector<ImageShow>();

    private MasterImage() {
    }

    public static MasterImage getImage() {
        return sMasterImage;
    }

    public void addObserver(ImageShow observer) {
        mObservers.add(observer);
    }

    public void setActivity(FilterShowActivity activity) {
        mActivity = activity;
    }

    public ImagePreset getPreset() {
        return mPreset;
    }

    public void setPreset(ImagePreset preset, boolean addToHistory) {
        mPreset = preset;
        mPreset.setImageLoader(mLoader);
        setGeometry();
        mPreset.fillImageStateAdapter(mState);
        if (addToHistory) {
            mHistory.addHistoryItem(mPreset);
        }
        updatePresets(true);
        requestImages();
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

    public ImageStateAdapter getState() {
        return mState;
    }

    public void setHistoryAdapter(HistoryAdapter adapter) {
        mHistory = adapter;
    }

    public void setStateAdapter(ImageStateAdapter adapter) {
        mState = adapter;
    }

    public void setImageLoader(ImageLoader loader) {
        mLoader = loader;
    }

    public void setCurrentFilter(ImageFilter filter) {
        mCurrentFilter = filter;
    }

    public ImageFilter getCurrentFilter() {
        return mCurrentFilter;
    }

    public boolean hasModifications() {
        if (mPreset == null) {
            return false;
        }
        return mPreset.hasModifications();
    }

    public Bitmap getFilteredImage() {
        requestImages();
        return mFilteredImage;
    }

    public Bitmap getFiltersOnlyImage() {
        requestImages();
        return mFiltersOnlyImage;
    }

    public Bitmap getGeometryOnlyImage() {
        requestImages();
        return mGeometryOnlyImage;
    }

    private void notifyObservers() {
        for (ImageShow observer : mObservers) {
            observer.invalidate();
        }
    }

    public void updatedCache() {
        requestImages();
        notifyObservers();
    }

    public void updatePresets(boolean force) {
        if (force) {
            mLoader.resetImageForPreset(mPreset, null);
        }
        if (force || mGeometryOnlyPreset == null) {
            ImagePreset newPreset = new ImagePreset(mPreset);
            newPreset.setDoApplyFilters(false);
            if (mGeometryOnlyPreset == null
                    || !newPreset.same(mGeometryOnlyPreset)) {
                mGeometryOnlyPreset = newPreset;
                mGeometryOnlyImage = null;
            }
        }
        if (force || mFiltersOnlyPreset == null) {
            ImagePreset newPreset = new ImagePreset(mPreset);
            newPreset.setDoApplyGeometry(false);
            if (mFiltersOnlyPreset == null
                    || !newPreset.same(mFiltersOnlyPreset)) {
                mFiltersOnlyPreset = newPreset;
                mFiltersOnlyImage = null;
            }
        }
        mActivity.enableSave(hasModifications());
    }

    public void requestImages() {
        if (mLoader == null) {
            return;
        }

        // FIXME getImageForPreset caller
        Bitmap bitmap = mLoader.getImageForPreset(null, mPreset, true);

        if (bitmap != null) {
            mFilteredImage = bitmap;
            notifyObservers();
        }
        updatePresets(false);
        if (mGeometryOnlyPreset != null) {
            bitmap = mLoader.getImageForPreset(null, mGeometryOnlyPreset,
                    true);
            if (bitmap != null) {
                mGeometryOnlyImage = bitmap;
            }
        }
        if (mFiltersOnlyPreset != null) {
            bitmap = mLoader.getImageForPreset(null, mFiltersOnlyPreset,
                    true);
            if (bitmap != null) {
                mFiltersOnlyImage = bitmap;
            }
        }
    }
}
