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

public class HighlightDrawer extends IconDrawer {
    private final NinePatchTexture mFrame;
    private final NinePatchTexture mFrameSelected;
    private final NinePatchTexture mFrameSelectedTop;
    private SelectionManager mSelectionManager;
    private Path mHighlightItem;

    public HighlightDrawer(Context context) {
        super(context);
        mFrame = new NinePatchTexture(context, R.drawable.album_frame);
        mFrameSelected = new NinePatchTexture(context, R.drawable.grid_selected);
        mFrameSelectedTop = new NinePatchTexture(context, R.drawable.grid_selected_top);
    }

    public void setHighlightItem(Path item) {
        mHighlightItem = item;
    }

    public void draw(GLCanvas canvas, Texture content, int width, int height,
            int rotation, Path path, int topIndex, int dataSourceType,
            int mediaType, boolean wantCache, boolean isCaching) {
        int x = -width / 2;
        int y = -height / 2;

        drawWithRotationAndGray(canvas, content, x, y, width, height, rotation,
                topIndex);

        if (((rotation / 90) & 0x01) == 1) {
            int temp = width;
            width = height;
            height = temp;
            x = -width / 2;
            y = -height / 2;
        }

        drawVideoOverlay(canvas, mediaType, x, y, width, height, topIndex);

        NinePatchTexture frame;
        if (path == mHighlightItem) {
            frame = topIndex == 0 ? mFrameSelectedTop : mFrameSelected;
        } else {
            frame = mFrame;
        }

        drawFrame(canvas, frame, x, y, width, height);

        if (topIndex == 0) {
            drawIcon(canvas, width, height, dataSourceType);
        }
    }
}
