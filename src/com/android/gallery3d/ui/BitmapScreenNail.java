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

package com.android.gallery3d.ui;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

// This is a ScreenNail wraps a Bitmap. It also includes the rotation
// information. The getWidth() and getHeight() methods return the width/height
// before rotation.
public class BitmapScreenNail implements ScreenNail {
    private static final String TAG = "BitmapScreenNail";
    private final int mWidth;
    private final int mHeight;
    private final int mRotation;
    private final Bitmap mBitmap;
    private BitmapTexture mTexture;

    public BitmapScreenNail(Bitmap bitmap, int rotation) {
        mWidth = bitmap.getWidth();
        mHeight = bitmap.getHeight();
        mRotation = rotation;
        mBitmap = bitmap;
        // We create mTexture lazily, so we don't incur the cost if we don't
        // actually need it.
    }

    @Override
    public int getWidth() {
        return mWidth;
    }

    @Override
    public int getHeight() {
        return mHeight;
    }

    @Override
    public int getRotation() {
        return mRotation;
    }

    @Override
    public void noDraw() {
    }

    @Override
    public void pauseDraw() {
        if (mTexture != null) {
            mTexture.recycle();
            mTexture = null;
        }
    }

    @Override
    public void draw(GLCanvas canvas, int x, int y, int width, int height) {
        if (mTexture == null) {
            mTexture = new BitmapTexture(mBitmap);
        }
        mTexture.draw(canvas, x, y, width, height);
    }

    @Override
    public void draw(GLCanvas canvas, RectF source, RectF dest) {
        if (mTexture == null) {
            mTexture = new BitmapTexture(mBitmap);
        }
        canvas.drawTexture(mTexture, source, dest);
    }
}
