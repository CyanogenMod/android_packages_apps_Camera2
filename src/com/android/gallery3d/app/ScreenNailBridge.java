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

package com.android.gallery3d.app;

import android.graphics.RectF;
import android.os.Handler;
import android.util.Log;

import com.android.gallery3d.ui.GLCanvas;
import com.android.gallery3d.ui.ScreenNail;

// This is a ScreenNail whose actually display is done by an foreign component.
// The foreign component tells the ScreenNail its size by setSize(). The
// ScreenNail tells the foreign component the position to display by
// updateView().
class ScreenNailBridge implements ScreenNail {
    private static final String TAG = "ScreenNailBridge";
    private int mWidth, mHeight;
    private boolean mVisible = false;
    private int mDrawX, mDrawY, mDrawWidth, mDrawHeight;
    private Listener mListener;
    private Handler mMainHandler;

    public interface Listener {
        // This is called from the main thread.
        void updateView(boolean visible, int x, int y, int width, int height);
    };

    // The constructor should be called from the main thread.
    public ScreenNailBridge(Listener listener) {
        mListener = listener;
        mMainHandler = new Handler();
    }

    // This can be called from any thread.  (We expect it to be called from the
    // main thread).
    public synchronized void setSize(int w, int h) {
        mWidth = w;
        mHeight = h;
    }

    // This can be called from any thread. (We expect it to be called from GL
    // thread)
    @Override
    public synchronized int getWidth() {
        return mWidth;
    }

    // This can be called from any thread. (We expect it to be called from GL
    // thread)
    @Override
    public synchronized int getHeight() {
        return mHeight;
    }

    @Override
    public int getRotation() {
        return 0;
    }

    // This is run in the main thread.
    private Runnable mUpdateViewRunnable = new Runnable() {
            public void run() {
                boolean v;
                int x, y, width, height;
                synchronized (ScreenNailBridge.this) {
                    v = mVisible;
                    x = mDrawX;
                    y = mDrawY;
                    width = mDrawWidth;
                    height = mDrawHeight;
                }
                mListener.updateView(v, x, y, width, height);
            }
        };

    @Override
    public synchronized void draw(GLCanvas canvas, int x, int y, int width, int height) {
        if (mVisible && mDrawX == x && mDrawY == y && mDrawWidth == width &&
                mDrawHeight == height) {
            return;
        }
        mVisible = true;
        mDrawX = x;
        mDrawY = y;
        mDrawWidth = width;
        mDrawHeight = height;
        mMainHandler.post(mUpdateViewRunnable);
    }

    @Override
    public synchronized void disableDraw() {
        if (!mVisible) return;
        mVisible = false;
        mMainHandler.post(mUpdateViewRunnable);
    }

    @Override
    public void recycle() {
        // Make sure we will not draw anymore.
        disableDraw();
    }

    @Override
    public void draw(GLCanvas canvas, RectF source, RectF dest) {
        throw new UnsupportedOperationException();
    }
}
