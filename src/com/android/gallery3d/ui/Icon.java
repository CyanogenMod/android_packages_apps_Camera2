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
import android.graphics.Rect;

public class Icon extends GLView {
    private final BasicTexture mIcon;

    // The width and height requested by the user.
    private int mReqWidth;
    private int mReqHeight;

    public Icon(Context context, int iconId, int width, int height) {
        this(context, new ResourceTexture(context, iconId), width, height);
    }

    public Icon(Context context, BasicTexture icon, int width, int height) {
        mIcon = icon;
        mReqWidth = width;
        mReqHeight = height;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        MeasureHelper.getInstance(this)
                .setPreferredContentSize(mReqWidth, mReqHeight)
                .measure(widthSpec, heightSpec);
    }

    @Override
    protected void render(GLCanvas canvas) {
        Rect p = mPaddings;

        int width = getWidth() - p.left - p.right;
        int height = getHeight() - p.top - p.bottom;

        // Draw the icon in the center of the space
        int xoffset = p.left + (width - mReqWidth) / 2;
        int yoffset = p.top + (height - mReqHeight) / 2;

        mIcon.draw(canvas, xoffset, yoffset, mReqWidth, mReqHeight);
    }
}
