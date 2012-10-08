
package com.android.gallery3d.filtershow.presets;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import com.android.gallery3d.filtershow.ImageStateAdapter;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.filters.ImageFilterStraighten;
import com.android.gallery3d.filtershow.imageshow.GeometryMetadata;
import com.android.gallery3d.filtershow.imageshow.ImageShow;

import java.util.Vector;

public class ImagePreset {

    private static final String LOGTAG = "ImagePreset";
    ImageShow mEndPoint = null;
    protected Vector<ImageFilter> mFilters = new Vector<ImageFilter>();
    protected String mName = "Original";
    protected String mHistoryName = "Original";
    protected boolean mIsFxPreset = false;

    enum FullRotate {
        ZERO, NINETY, HUNDRED_EIGHTY, TWO_HUNDRED_SEVENTY
    }

    // This is where the geometry metadata lives now.
    public final GeometryMetadata mGeoData = new GeometryMetadata();

    public void setGeometry(GeometryMetadata m) {
        mGeoData.set(m);
    }

    private float mScaleFactor = 1.0f;
    private boolean mIsHighQuality = false;

    public ImagePreset() {
        setup();
    }

    public ImagePreset(ImagePreset source) {
        try {
            for (int i = 0; i < source.mFilters.size(); i++) {
                add(source.mFilters.elementAt(i).clone());
            }
        } catch (java.lang.CloneNotSupportedException e) {
            Log.v(LOGTAG, "Exception trying to clone: " + e);
        }
        mName = source.name();
        mHistoryName = source.name();
        mIsFxPreset = source.isFx();

        mGeoData.set(source.mGeoData);
    }

    public boolean isFx() {
        return mIsFxPreset;
    }

    public void setIsFx(boolean value) {
        mIsFxPreset = value;
    }

    public void setName(String name) {
        mName = name;
        mHistoryName = name;
    }

    public void setHistoryName(String name) {
        mHistoryName = name;
    }

    public boolean same(ImagePreset preset) {
        if (preset.mFilters.size() != mFilters.size()) {
            return false;
        }
        if (!mName.equalsIgnoreCase(preset.name())) {
            return false;
        }

        if (!mGeoData.equals(preset.mGeoData)) {
            return false;
        }
        for (int i = 0; i < preset.mFilters.size(); i++) {
            ImageFilter a = preset.mFilters.elementAt(i);
            ImageFilter b = mFilters.elementAt(i);
            if (!a.same(b)) {
                return false;
            }
        }
        return true;
    }

    public String name() {
        return mName;
    }

    public String historyName() {
        return mHistoryName;
    }

    public void add(ImageFilter filter) {
        mFilters.add(filter);
    }

    public void remove(String filterName) {
        ImageFilter filter = getFilter(filterName);
        if (filter != null) {
            mFilters.remove(filter);
        }
    }

    public int getCount() {
        return mFilters.size();
    }

    public ImageFilter getFilter(String name) {
        for (int i = 0; i < mFilters.size(); i++) {
            ImageFilter filter = mFilters.elementAt(i);
            if (filter.getName().equalsIgnoreCase(name)) {
                return filter;
            }
        }
        return null;
    }

    public void setup() {
        // do nothing here
    }

    public void setEndpoint(ImageShow image) {
        mEndPoint = image;
    }

    public Bitmap apply(Bitmap original) {
        // First we apply any transform -- 90 rotate, flip, straighten, crop
        Bitmap bitmap = mGeoData.apply(original, mScaleFactor, mIsHighQuality);

        // TODO -- apply borders separately
        ImageFilter borderFilter = null;
        for (int i = 0; i < mFilters.size(); i++) {
            ImageFilter filter = mFilters.elementAt(i);
            if (filter.getName().equalsIgnoreCase("Border")) {
                // TODO don't use the name as an id
                borderFilter = filter;
            } else {
                bitmap = filter.apply(bitmap, mScaleFactor, mIsHighQuality);
            }
        }
        if (borderFilter != null) {
            bitmap = borderFilter.apply(bitmap, mScaleFactor, mIsHighQuality);
        }
        if (mEndPoint != null) {
            mEndPoint.updateFilteredImage(bitmap);
        }
        return bitmap;
    }

    public void fillImageStateAdapter(ImageStateAdapter imageStateAdapter) {
        if (imageStateAdapter == null) {
            return;
        }
        imageStateAdapter.clear();
        imageStateAdapter.addAll(mFilters);
        imageStateAdapter.notifyDataSetChanged();
    }

    public float getScaleFactor() {
        return mScaleFactor;
    }

    public boolean isHighQuality() {
        return mIsHighQuality;
    }

    public void setIsHighQuality(boolean value) {
        mIsHighQuality = value;
    }

    public void setScaleFactor(float value) {
        mScaleFactor = value;
    }
}
