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
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.Path;

import android.content.Context;

public class ManageCacheDrawer extends IconDrawer {
    private final ResourceTexture mCheckedItem;
    private final ResourceTexture mUnCheckedItem;
    private final SelectionManager mSelectionManager;

    private final ResourceTexture mLocalAlbumIcon;
    private final StringTexture mCachingText;

    private final int mCachePinSize;
    private final int mCachePinMargin;

    public ManageCacheDrawer(Context context, SelectionManager selectionManager,
            int cachePinSize, int cachePinMargin) {
        super(context);
        mCheckedItem = new ResourceTexture(context, R.drawable.btn_make_offline_normal_on_holo_dark);
        mUnCheckedItem = new ResourceTexture(context, R.drawable.btn_make_offline_normal_off_holo_dark);
        mLocalAlbumIcon = new ResourceTexture(context, R.drawable.btn_make_offline_disabled_on_holo_dark);
        String cachingLabel = context.getString(R.string.caching_label);
        mCachingText = StringTexture.newInstance(cachingLabel, 12, 0xffffffff);
        mSelectionManager = selectionManager;
        mCachePinSize = cachePinSize;
        mCachePinMargin = cachePinMargin;
    }

    @Override
    public void prepareDrawing() {
    }

    private static boolean isLocal(int dataSourceType) {
        return dataSourceType != DATASOURCE_TYPE_PICASA;
    }

    @Override
    public void draw(GLCanvas canvas, Texture content, int width,
            int height, int rotation, Path path,
            int dataSourceType, int mediaType, boolean isPanorama,
            int labelBackgroundHeight, boolean wantCache, boolean isCaching) {

        int x = -width / 2;
        int y = -height / 2;

        drawWithRotation(canvas, content, x, y, width, height, rotation);

        if (((rotation / 90) & 0x01) == 1) {
            int temp = width;
            width = height;
            height = temp;
            x = -width / 2;
            y = -height / 2;
        }

        drawMediaTypeOverlay(canvas, mediaType, isPanorama, x, y, width, height);
        drawLabelBackground(canvas, width, height, labelBackgroundHeight);
        drawIcon(canvas, width, height, dataSourceType);
        drawCachingPin(canvas, path, dataSourceType, isCaching, wantCache,
                width, height);

        if (mSelectionManager.isPressedPath(path)) {
            drawPressedFrame(canvas, x, y, width, height);
        }
    }

    private void drawCachingPin(GLCanvas canvas, Path path, int dataSourceType,
            boolean isCaching, boolean wantCache, int width, int height) {
        boolean selected = mSelectionManager.isItemSelected(path);
        boolean chooseToCache = wantCache ^ selected;

        ResourceTexture icon = null;
        if (isLocal(dataSourceType)) {
            icon = mLocalAlbumIcon;
        } else if (chooseToCache) {
            icon = mCheckedItem;
        } else {
            icon = mUnCheckedItem;
        }

        int w = mCachePinSize;
        int h = mCachePinSize;
        int right = (width + 1) / 2;
        int bottom = (height + 1) / 2;
        int x = right - w - mCachePinMargin;
        int y = bottom - h - mCachePinMargin;

        icon.draw(canvas, x, y, w, h);

        if (isCaching) {
            int textWidth = mCachingText.getWidth();
            int textHeight = mCachingText.getHeight();
            // Align the center of the text to the center of the pin icon
            x = right - mCachePinMargin - (textWidth + mCachePinSize) / 2;
            y = bottom - textHeight;
            mCachingText.draw(canvas, x, y);
        }
    }

    @Override
    public void drawFocus(GLCanvas canvas, int width, int height) {
    }
}
