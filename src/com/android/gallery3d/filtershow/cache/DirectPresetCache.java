
package com.android.gallery3d.filtershow.cache;

import java.util.Vector;

import android.graphics.Bitmap;
import android.util.Log;

import com.android.gallery3d.filtershow.imageshow.ImageShow;
import com.android.gallery3d.filtershow.presets.ImagePreset;

public class DirectPresetCache implements Cache {

    private static final String LOGTAG = "DirectPresetCache";
    private Bitmap mOriginalBitmap = null;
    private Vector<ImageShow> mObservers = new Vector<ImageShow>();
    private Vector<CachedPreset> mCache = new Vector<CachedPreset>();
    private int mCacheSize = 1;
    private Bitmap.Config mBitmapConfig = Bitmap.Config.ARGB_8888;
    private long mGlobalAge = 0;

    protected class CachedPreset {
        private Bitmap mBitmap = null;
        private ImagePreset mPreset = null;
        private long mAge = 0;
        private boolean mBusy = false;

        public void setBusy(boolean value) {
            mBusy = value;
        }

        public boolean busy() {
            return mBusy;
        }
    }

    public DirectPresetCache(int size) {
        mCacheSize = size;
    }

    public void setOriginalBitmap(Bitmap bitmap) {
        mOriginalBitmap = bitmap;
        notifyObservers();
    }

    public void notifyObservers() {
        for (int i = 0; i < mObservers.size(); i++) {
            ImageShow imageShow = mObservers.elementAt(i);
            imageShow.invalidate();
        }
    }

    public void addObserver(ImageShow observer) {
        if (!mObservers.contains(observer)) {
            mObservers.add(observer);
        }
    }

    private CachedPreset getCachedPreset(ImagePreset preset) {
        for (int i = 0; i < mCache.size(); i++) {
            CachedPreset cache = mCache.elementAt(i);
            if (cache.mPreset == preset && !cache.mBusy) {
                return cache;
            }
        }
        return null;
    }

    public Bitmap get(ImagePreset preset) {
        // Log.v(LOGTAG, "get preset " + preset.name() + " : " + preset);
        CachedPreset cache = getCachedPreset(preset);
        if (cache != null) {
            return cache.mBitmap;
        }
        // Log.v(LOGTAG, "didn't find preset " + preset.name() + " : " + preset
        // + " we have " + mCache.size() + " elts / " + mCacheSize);
        return null;
    }

    public void reset(ImagePreset preset) {
        CachedPreset cache = getCachedPreset(preset);
        if (cache != null && !cache.mBusy) {
            cache.mBitmap = null;
            willCompute(cache);
        }
    }

    private CachedPreset getOldestCachedPreset() {
        CachedPreset found = null;
        for (int i = 0; i < mCache.size(); i++) {
            CachedPreset cache = mCache.elementAt(i);
            if (cache.mBusy) {
                continue;
            }
            if (found == null) {
                found = cache;
            } else {
                if (found.mAge > cache.mAge) {
                    found = cache;
                }
            }
        }
        return found;
    }

    protected void willCompute(CachedPreset cache) {
        if (cache == null) {
            return;
        }
        cache.mBusy = true;
        compute(cache);
        didCompute(cache);
    }

    protected void didCompute(CachedPreset cache) {
        cache.mBusy = false;
        notifyObservers();
    }

    protected void compute(CachedPreset cache) {
        cache.mBitmap = null;
        cache.mBitmap = mOriginalBitmap.copy(mBitmapConfig, true);
        cache.mPreset.apply(cache.mBitmap);
        cache.mAge = mGlobalAge++;
    }

    public void prepare(ImagePreset preset) {
        // Log.v(LOGTAG, "prepare preset " + preset.name() + " : " + preset);
        CachedPreset cache = getCachedPreset(preset);
        if (cache == null) {
            if (mCache.size() < mCacheSize) {
                cache = new CachedPreset();
                mCache.add(cache);
            } else {
                cache = getOldestCachedPreset();
            }
            if (cache != null) {
                cache.mPreset = preset;
            }
        }
        willCompute(cache);
    }

}
