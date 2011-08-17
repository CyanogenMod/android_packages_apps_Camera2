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

import android.content.Context;

public class StaticBackground extends GLView {

    private Context mContext;
    private int mLandscapeResource;
    private int mPortraitResource;

    private BasicTexture mBackground;
    private boolean mIsLandscape = false;

    public StaticBackground(Context context) {
        mContext = context;
    }

    @Override
    protected void onLayout(boolean changeSize, int l, int t, int r, int b) {
        setOrientation(getWidth() >= getHeight());
    }

    private void setOrientation(boolean isLandscape) {
        if (mIsLandscape == isLandscape) return;
        mIsLandscape = isLandscape;
        if (mBackground != null) mBackground.recycle();
        mBackground = new ResourceTexture(
                mContext, mIsLandscape ? mLandscapeResource : mPortraitResource);
        invalidate();
    }

    public void setImage(int landscapeId, int portraitId) {
        mLandscapeResource = landscapeId;
        mPortraitResource = portraitId;
        if (mBackground != null) mBackground.recycle();
        mBackground = new ResourceTexture(
                mContext, mIsLandscape ? landscapeId : portraitId);
        invalidate();
    }

    @Override
    protected void render(GLCanvas canvas) {
        //mBackground.draw(canvas, 0, 0, getWidth(), getHeight());
        canvas.fillRect(0, 0, getWidth(), getHeight(), 0xFF000000);
    }
}
