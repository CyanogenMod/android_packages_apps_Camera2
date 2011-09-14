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
    private static final int COLOR_CACHING_BACKGROUND = 0x7F000000;
    private static final int ICON_SIZE = 36;
    private final ResourceTexture mCheckedItem;
    private final ResourceTexture mUnCheckedItem;
    private final SelectionManager mSelectionManager;

    private final ResourceTexture mLocalAlbumIcon;
    private final StringTexture mCaching;

    public ManageCacheDrawer(Context context, SelectionManager selectionManager) {
        super(context);
        mCheckedItem = new ResourceTexture(context, R.drawable.btn_make_offline_normal_on_holo_dark);
        mUnCheckedItem = new ResourceTexture(context, R.drawable.btn_make_offline_normal_off_holo_dark);
        mLocalAlbumIcon = new ResourceTexture(context, R.drawable.btn_make_offline_disabled_on_holo_dark);
        String cachingLabel = context.getString(R.string.caching_label);
        mCaching = StringTexture.newInstance(cachingLabel, 12, 0xffffffff);
        mSelectionManager = selectionManager;
    }

    @Override
    public void prepareDrawing() {
    }

    private static boolean isLocal(int dataSourceType) {
        return dataSourceType != DATASOURCE_TYPE_PICASA;
    }

    @Override
    public void draw(GLCanvas canvas, Texture content, int width, int height,
            int rotation, Path path, int topIndex, int dataSourceType,
            int mediaType, int darkStripHeight, boolean wantCache,
            boolean isCaching) {

        boolean selected = mSelectionManager.isItemSelected(path);
        boolean chooseToCache = wantCache ^ selected;

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

        if (topIndex == 0) {
            drawDarkStrip(canvas, width, height, darkStripHeight);
            drawIcon(canvas, width, height, dataSourceType);
        }

        if (topIndex == 0) {
            ResourceTexture icon = null;
            if (isLocal(dataSourceType)) {
                icon = mLocalAlbumIcon;
            } else if (chooseToCache) {
                icon = mCheckedItem;
            } else {
                icon = mUnCheckedItem;
            }

            int w = ICON_SIZE;
            int h = ICON_SIZE;
            x = width / 2 - w;
            y = -height / 2;

            icon.draw(canvas, x, y, w, h);

            if (isCaching) {
                int textWidth = mCaching.getWidth();
                int textHeight = mCaching.getHeight();
                x = -textWidth / 2;
                y = height / 2 - textHeight;

                // Leave a few pixels of margin in the background rect.
                float sideMargin = Utils.clamp(textWidth * 0.1f, 2.0f,
                        6.0f);
                float clearance = Utils.clamp(textHeight * 0.1f, 2.0f,
                        6.0f);

                // Overlay the "Caching" wording at the bottom-center of the content.
                canvas.fillRect(x - sideMargin, y - clearance,
                        textWidth + sideMargin * 2, textHeight + clearance,
                        COLOR_CACHING_BACKGROUND);
                mCaching.draw(canvas, x, y);
            }
        }
    }

    @Override
    public void drawFocus(GLCanvas canvas, int width, int height) {
    }
}
