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

package com.android.gallery3d.filtershow.cache;

import android.graphics.Bitmap;

import com.android.gallery3d.filtershow.imageshow.ImageShow;
import com.android.gallery3d.filtershow.presets.ImagePreset;

import java.util.Vector;

public class DirectPresetCache implements Cache {

    private static final String LOGTAG = "DirectPresetCache";
    private Bitmap mOriginalBitmap = null;
    private final Vector<ImageShow> mObservers = new Vector<ImageShow>();
    private final Vector<CachedPreset> mCache = new Vector<CachedPreset>();
    private int mCacheSize = 1;
    private final Bitmap.Config mBitmapConfig = Bitmap.Config.ARGB_8888;
    private long mGlobalAge = 0;
    private ImageLoader mLoader = null;

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

    public DirectPresetCache(ImageLoader loader, int size) {
        mLoader = loader;
        mCacheSize = size;
    }

    @Override
    public void setOriginalBitmap(Bitmap bitmap) {
        mOriginalBitmap = bitmap;
        notifyObservers();
    }

    public void notifyObservers() {
        mLoader.getActivity().runOnUiThread(mNotifyObserversRunnable);
    }

    private final Runnable mNotifyObserversRunnable = new Runnable() {
        @Override
        public void run() {
            for (int i = 0; i < mObservers.size(); i++) {
                ImageShow imageShow = mObservers.elementAt(i);
                imageShow.invalidate();
                imageShow.updateImage();
            }
        }
    };

    @Override
    public void addObserver(ImageShow observer) {
        if (!mObservers.contains(observer)) {
            mObservers.add(observer);
        }
    }

    private CachedPreset getCachedPreset(ImagePreset preset) {
        for (int i = 0; i < mCache.size(); i++) {
            CachedPreset cache = mCache.elementAt(i);
            if (cache.mPreset == preset) {
                return cache;
            }
        }
        return null;
    }

    @Override
    public Bitmap get(ImagePreset preset) {
        // Log.v(LOGTAG, "get preset " + preset.name() + " : " + preset);
        CachedPreset cache = getCachedPreset(preset);
        if (cache != null && !cache.mBusy) {
            return cache.mBitmap;
        }
        // Log.v(LOGTAG, "didn't find preset " + preset.name() + " : " + preset
        // + " we have " + mCache.size() + " elts / " + mCacheSize);
        return null;
    }

    @Override
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
        float scaleFactor = (float) cache.mBitmap.getWidth() / (float) mLoader.getOriginalBounds().width();
        if (scaleFactor < 1.0f) {
            cache.mPreset.setIsHighQuality(false);
        }
        cache.mPreset.setScaleFactor(scaleFactor);
        cache.mBitmap = cache.mPreset.apply(cache.mBitmap);
        cache.mAge = mGlobalAge++;
    }

    @Override
    public void prepare(ImagePreset preset) {
        // Log.v(LOGTAG, "prepare preset " + preset.name() + " : " + preset);
        CachedPreset cache = getCachedPreset(preset);
        if (cache == null || (cache.mBitmap == null && !cache.mBusy)) {
            if (cache == null) {
                if (mCache.size() < mCacheSize) {
                    cache = new CachedPreset();
                    mCache.add(cache);
                } else {
                    cache = getOldestCachedPreset();
                }
            }
            if (cache != null) {
                cache.mPreset = preset;
                willCompute(cache);
            }
        }

    }

}
