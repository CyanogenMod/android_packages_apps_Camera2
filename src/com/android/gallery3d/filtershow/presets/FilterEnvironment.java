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

package com.android.gallery3d.filtershow.presets;

import android.graphics.Bitmap;
import android.support.v8.renderscript.Allocation;
import android.util.Log;

import com.android.gallery3d.filtershow.cache.CachingPipeline;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.FiltersManager;
import com.android.gallery3d.filtershow.filters.ImageFilter;

import java.lang.ref.WeakReference;
import java.util.HashMap;

public class FilterEnvironment {
    private static final String LOGTAG = "FilterEnvironment";
    private ImagePreset mImagePreset;
    private float mScaleFactor;
    private int mQuality;
    private FiltersManager mFiltersManager;
    private CachingPipeline mCachingPipeline;
    private volatile boolean mStop = false;

    public synchronized boolean needsStop() {
        return mStop;
    }

    public synchronized void setStop(boolean stop) {
        this.mStop = stop;
    }

    private HashMap<Long, WeakReference<Bitmap>>
            bitmapCach = new HashMap<Long, WeakReference<Bitmap>>();

    public void cache(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        Long key = calcKey(bitmap.getWidth(), bitmap.getHeight());
        bitmapCach.put(key, new WeakReference<Bitmap>(bitmap));
    }

    public Bitmap getBitmap(int w, int h) {
        Long key = calcKey(w, h);
        WeakReference<Bitmap> ref = bitmapCach.remove(key);
        Bitmap bitmap = null;
        if (ref != null) {
            bitmap = ref.get();
        }
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(
                    w, h, Bitmap.Config.ARGB_8888);
        }
        return bitmap;
    }

    private Long calcKey(long w, long h) {
        return (w << 32) | (h << 32);
    }

    public void setImagePreset(ImagePreset imagePreset) {
        mImagePreset = imagePreset;
    }

    public ImagePreset getImagePreset() {
        return mImagePreset;
    }

    public void setScaleFactor(float scaleFactor) {
        mScaleFactor = scaleFactor;
    }

    public float getScaleFactor() {
        return mScaleFactor;
    }

    public void setQuality(int quality) {
        mQuality = quality;
    }

    public int getQuality() {
        return mQuality;
    }

    public void setFiltersManager(FiltersManager filtersManager) {
        mFiltersManager = filtersManager;
    }

    public FiltersManager getFiltersManager() {
        return mFiltersManager;
    }

    public void applyRepresentation(FilterRepresentation representation,
                                    Allocation in, Allocation out) {
        ImageFilter filter = mFiltersManager.getFilterForRepresentation(representation);
        filter.useRepresentation(representation);
        filter.setEnvironment(this);
        if (filter.supportsAllocationInput()) {
            filter.apply(in, out);
        }
        filter.setEnvironment(null);
    }

    public Bitmap applyRepresentation(FilterRepresentation representation, Bitmap bitmap) {
        ImageFilter filter = mFiltersManager.getFilterForRepresentation(representation);
        filter.useRepresentation(representation);
        filter.setEnvironment(this);
        Bitmap ret = filter.apply(bitmap, mScaleFactor, mQuality);
        filter.setEnvironment(null);
        return ret;
    }

    public CachingPipeline getCachingPipeline() {
        return mCachingPipeline;
    }

    public void setCachingPipeline(CachingPipeline cachingPipeline) {
        mCachingPipeline = cachingPipeline;
    }

}
