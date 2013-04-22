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

package com.android.gallery3d.filtershow.cache;

import android.graphics.Bitmap;

public class TripleBufferBitmap {

    private static String LOGTAG = "TripleBufferBitmap";

    private volatile Bitmap mBitmaps[] = new Bitmap[3];
    private volatile Bitmap mProducer = null;
    private volatile Bitmap mConsumer = null;
    private volatile Bitmap mIntermediate = null;
    private volatile boolean mNeedsSwap = false;

    private final Bitmap.Config mBitmapConfig = Bitmap.Config.ARGB_8888;
    private volatile boolean mNeedsRepaint = true;

    public TripleBufferBitmap() {

    }

    public synchronized void updateBitmaps(Bitmap bitmap) {
        mBitmaps[0] = bitmap.copy(mBitmapConfig, true);
        mBitmaps[1] = bitmap.copy(mBitmapConfig, true);
        mBitmaps[2] = bitmap.copy(mBitmapConfig, true);
        mProducer = mBitmaps[0];
        mConsumer = mBitmaps[1];
        mIntermediate = mBitmaps[2];
    }

    public synchronized void updateProducerBitmap(Bitmap bitmap) {
        mProducer = bitmap.copy(mBitmapConfig, true);
    }

    public synchronized void setProducer(Bitmap producer) {
        mProducer = producer;
    }

    public synchronized Bitmap getProducer() {
        return mProducer;
    }

    public synchronized Bitmap getConsumer() {
        return mConsumer;
    }

    public synchronized void swapProducer() {
        Bitmap intermediate = mIntermediate;
        mIntermediate = mProducer;
        mProducer = intermediate;
        mNeedsSwap = true;
    }

    public synchronized void swapConsumer() {
        if (!mNeedsSwap) {
            return;
        }
        Bitmap intermediate = mIntermediate;
        mIntermediate = mConsumer;
        mConsumer = intermediate;
        mNeedsSwap = false;
    }

    public synchronized void invalidate() {
        mNeedsRepaint = true;
    }

    public synchronized boolean checkRepaintNeeded() {
        if (mNeedsRepaint) {
            mNeedsRepaint = false;
            return true;
        }
        return false;
    }

}
