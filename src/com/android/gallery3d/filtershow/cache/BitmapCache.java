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

import com.android.gallery3d.filtershow.presets.ImagePreset;

import java.nio.ByteBuffer;

public class BitmapCache {

    private static final String LOGTAG = "BitmapCache";
    static int mNbItems = 20;
    private final Bitmap[] mBitmaps = new Bitmap[mNbItems];
    private final Object[] mKeys = new Object[mNbItems];
    private final long[] mIndices = new long[mNbItems];
    private final boolean[] mBusyStatus = new boolean[mNbItems];

    private Bitmap mOriginalBitmap = null;
    private ByteBuffer mBuffer = null;
    private final Bitmap.Config mConfig = Bitmap.Config.ARGB_8888;
    private long mIndex = 0;

    public void setOriginalBitmap(Bitmap original) {
        if (original == null) {
            return;
        }
        mOriginalBitmap = original.copy(mConfig, true);
        int size = 4 * original.getWidth() * original.getHeight();
        mBuffer = ByteBuffer.allocate(size);
        mOriginalBitmap.copyPixelsToBuffer(mBuffer);
        mBuffer.rewind();
        mOriginalBitmap.copyPixelsFromBuffer(mBuffer);
        for (int i = 0; i < mNbItems; i++) {
            mBitmaps[i] = mOriginalBitmap.copy(mConfig, true);
        }
    }

    private int getOldestPosition() {
        long minIndex = mIndices[0];
        int current = 0;
        for (int i = 1; i < mNbItems; i++) {
            if (!mBusyStatus[i] && minIndex > mIndices[i]) {
                minIndex = mIndices[i];
                current = i;
            }
        }
        return current;
    }

    public Bitmap put(ImagePreset preset) {
        int pos = getOldestPosition();
        return put(preset, pos);
    }

    public Bitmap put(ImagePreset preset, int pos) {
        mBitmaps[pos] = mOriginalBitmap.copy(mConfig, true);
        Bitmap bitmap = mBitmaps[pos];
        bitmap = preset.apply(bitmap);
        mKeys[pos] = preset;
        mIndices[pos] = mIndex++;
        return bitmap;
    }

    public int reservePosition(ImagePreset preset) {
        for (int i = 1; i < mNbItems; i++) {
            if (mKeys[i] == preset && mBusyStatus[i]) {
                return -1;
            }
        }
        int pos = getOldestPosition();
        mBusyStatus[pos] = true;
        mKeys[pos] = preset;
        return pos;
    }

    public void processPosition(int pos) {
        ImagePreset preset = (ImagePreset) mKeys[pos];
        mBitmaps[pos] = mOriginalBitmap.copy(mConfig, true);
        Bitmap bitmap = mBitmaps[pos];
        bitmap = preset.apply(bitmap);
        mIndices[pos] = mIndex++;
    }

    public void unlockPosition(int pos) {
        mBusyStatus[pos] = false;
    }

    public Bitmap get(ImagePreset preset) {
        int foundPosition = -1;
        int currentIndice = 0;
        for (int i = 0; i < mNbItems; i++) {
            if (mKeys[i] == preset && mBitmaps[i] != null) {
                if (mIndices[i] > currentIndice) {
                    foundPosition = i;
                }
            }
        }
        if (foundPosition != -1) {
            mIndices[foundPosition] = mIndex++;
            return mBitmaps[foundPosition];
        }
        return null;
    }

    public void reset(ImagePreset preset) {
        for (int i = 0; i < mNbItems; i++) {
            if (mKeys[i] == preset && !mBusyStatus[i]) {
                mBitmaps[i] = null;
            }
        }
    }
}
