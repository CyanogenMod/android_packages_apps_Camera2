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

package com.android.gallery3d.ingest.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.mtp.MtpDevice;
import android.util.LruCache;

public class MtpBitmapCache extends LruCache<Integer, Bitmap> {
    private static final int PER_DEVICE_CACHE_MAX_BYTES = 4194304;
    private static MtpBitmapCache sInstance;

    public synchronized static MtpBitmapCache getInstanceForDevice(MtpDevice device) {
        if (sInstance == null || sInstance.mDevice != device) {
            sInstance = new MtpBitmapCache(PER_DEVICE_CACHE_MAX_BYTES, device);
        }
        return sInstance;
    }

    public synchronized static void onDeviceDisconnected(MtpDevice device) {
        if (sInstance != null && sInstance.mDevice == device) {
            synchronized (sInstance) {
                sInstance.mDevice = null;
            }
            sInstance = null;
        }
    }

    private MtpDevice mDevice;

    private MtpBitmapCache(int maxSize, MtpDevice device) {
        super(maxSize);
        mDevice = device;
    }

    @Override
    protected int sizeOf(Integer key, Bitmap value) {
        return value.getByteCount();
    }

    public Bitmap getOrCreate(Integer key) {
        Bitmap b = get(key);
        return b == null ? createAndInsert(key) : b;
    }

    private Bitmap createAndInsert(Integer key) {
        MtpDevice device;
        synchronized (this) {
            device = mDevice;
        }
        if (device == null) return null;
        byte[] imageBytes = device.getThumbnail(key);
        if (imageBytes == null) return null;
        Bitmap created = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        put(key, created);
        return created;
    }
}
