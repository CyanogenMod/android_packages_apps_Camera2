/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.ui;

import com.android.gallery3d.data.MediaItem;

import android.graphics.Bitmap;

public abstract class AbstractDisplayItem extends DisplayItem {

    private static final String TAG = "AbstractDisplayItem";

    private static final int STATE_INVALID = 0x01;
    private static final int STATE_VALID = 0x02;
    private static final int STATE_UPDATING = 0x04;
    private static final int STATE_CANCELING = 0x08;
    private static final int STATE_ERROR = 0x10;

    private int mState = STATE_INVALID;
    private boolean mImageRequested = false;
    private boolean mRecycling = false;
    private Bitmap mBitmap;

    protected final MediaItem mMediaItem;
    private int mRotation;

    public AbstractDisplayItem(MediaItem item) {
        mMediaItem = item;
        if (item == null) mState = STATE_ERROR;
        if (item != null) mRotation = mMediaItem.getRotation();
    }

    protected void updateImage(Bitmap bitmap, boolean isCancelled) {
        if (mRecycling) {
            return;
        }

        if (isCancelled && bitmap == null) {
            mState = STATE_INVALID;
            if (mImageRequested) {
                // request image again.
                requestImage();
            }
            return;
        }

        mBitmap = bitmap;
        mState = bitmap == null ? STATE_ERROR : STATE_VALID ;
        onBitmapAvailable(mBitmap);
    }

    @Override
    public int getRotation() {
        return mRotation;
    }

    @Override
    public long getIdentity() {
        return mMediaItem != null
                ? System.identityHashCode(mMediaItem.getPath())
                : System.identityHashCode(this);
    }

    public void requestImage() {
        mImageRequested = true;
        if (mState == STATE_INVALID) {
            mState = STATE_UPDATING;
            startLoadBitmap();
        }
    }

    public void cancelImageRequest() {
        mImageRequested = false;
        if (mState == STATE_UPDATING) {
            mState = STATE_CANCELING;
            cancelLoadBitmap();
        }
    }

    private boolean inState(int states) {
        return (mState & states) != 0;
    }

    public void recycle() {
        if (!inState(STATE_UPDATING | STATE_CANCELING)) {
            if (mBitmap != null) mBitmap = null;
        } else {
            mRecycling = true;
            cancelImageRequest();
        }
    }

    public boolean isRequestInProgress() {
        return mImageRequested && inState(STATE_UPDATING | STATE_CANCELING);
    }

    abstract protected void startLoadBitmap();
    abstract protected void cancelLoadBitmap();
    abstract protected void onBitmapAvailable(Bitmap bitmap);
}
