
package com.android.gallery3d.filtershow.presets;

import java.util.Vector;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.filters.ImageFilterStraighten;
import com.android.gallery3d.filtershow.imageshow.ImageShow;

public class ImagePreset {

    private static final String LOGTAG = "ImagePreset";
    ImageShow mEndPoint = null;
    protected Vector<ImageFilter> mFilters = new Vector<ImageFilter>();
    protected String mName = "Original";
    protected String mHistoryName = "Original";
    protected boolean mIsFxPreset = false;

    enum FullRotate {
        ZERO, NINETY, HUNDRED_HEIGHTY, TWO_HUNDRED_SEVENTY
    }

    protected FullRotate mFullRotate = FullRotate.ZERO;
    protected float mStraightenRotate = 0;
    protected float mStraightenZoom = 0;
    protected boolean mHorizontalFlip = false;
    protected boolean mVerticalFlip = false;
    protected RectF mCrop = null;

    public ImagePreset() {
        setup();
    }

    public ImagePreset(ImagePreset source) {
        for (int i = 0; i < source.mFilters.size(); i++) {
            add(source.mFilters.elementAt(i).copy());
        }
        mName = source.name();
        mHistoryName = source.name();
        mIsFxPreset = source.isFx();

        mStraightenRotate = source.mStraightenRotate;
        mStraightenZoom = source.mStraightenZoom;
    }

    public void setStraightenRotation(float rotate, float zoom) {
        mStraightenRotate = rotate;
        mStraightenZoom = zoom;
    }

    private Bitmap applyGeometry(Bitmap original) {
        Bitmap bitmap = original;

        if (mFullRotate != FullRotate.ZERO) {
            // TODO
        }

//        Log.v(LOGTAG, "applyGeometry with rotate " + mStraightenRotate + " and zoom "
 //               + mStraightenZoom);

        if (mStraightenRotate != 0) {
            // TODO: keep the instances around
            ImageFilter straighten = new ImageFilterStraighten(mStraightenRotate, mStraightenZoom);
            straighten.apply(bitmap);
            straighten = null;
        }

        return bitmap;
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
        if (mStraightenRotate != preset.mStraightenRotate) {
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

    public void add(ImageFilter preset) {
        mFilters.add(preset);
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
            if (filter.name().equalsIgnoreCase(name)) {
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
        Bitmap bitmap = applyGeometry(original);

        // TODO -- apply borders separately
        ImageFilter borderFilter = null;
        for (int i = 0; i < mFilters.size(); i++) {
            ImageFilter filter = mFilters.elementAt(i);
            if (filter.name().equalsIgnoreCase("Border")) {
                // TODO don't use the name as an id
                borderFilter = filter;
            } else {
                filter.apply(bitmap);
            }
        }
        if (borderFilter != null) {
            borderFilter.apply(bitmap);
        }
        if (mEndPoint != null) {
            mEndPoint.updateFilteredImage(bitmap);
        }
        return bitmap;
    }

 }
