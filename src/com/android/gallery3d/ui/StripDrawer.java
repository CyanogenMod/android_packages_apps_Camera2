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

import com.android.gallery3d.R;
import com.android.gallery3d.data.Path;

import android.content.Context;
import android.graphics.Rect;

public class StripDrawer extends SelectionDrawer {
    private NinePatchTexture mFocusBox;
    private Rect mFocusBoxPadding;

    public StripDrawer(Context context) {
        mFocusBox = new NinePatchTexture(context, R.drawable.focus_box);
        mFocusBoxPadding = mFocusBox.getPaddings();
    }

    @Override
    public void prepareDrawing() {
    }

    @Override
    public void draw(GLCanvas canvas, Texture content, int width, int height,
            int rotation, Path path, int topIndex, int dataSourceType,
            int mediaType, int darkStripHeight, boolean wantCache,
            boolean isCaching) {

        int x = -width / 2;
        int y = -height / 2;

        drawWithRotation(canvas, content, x, y, width, height, rotation);
    }

    @Override
    public void drawFocus(GLCanvas canvas, int width, int height) {
        int x = -width / 2;
        int y = -height / 2;
        Rect p = mFocusBoxPadding;
        mFocusBox.draw(canvas, x - p.left, y - p.top,
                width + p.left + p.right, height + p.top + p.bottom);
    }
}
