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
import android.graphics.Color;

public class GridDrawer extends IconDrawer {
    private final NinePatchTexture mFrame;
    private final NinePatchTexture mFrameSelected;
    private final NinePatchTexture mFrameSelectedTop;
    private final NinePatchTexture mImportBackground;
    private Texture mImportLabel;
    private int mGridWidth;
    private final SelectionManager mSelectionManager;
    private final Context mContext;
    private final int FONT_SIZE = 14;
    private final int FONT_COLOR = Color.WHITE;
    private final int IMPORT_LABEL_PADDING = 10;
    private boolean mSelectionMode;

    public GridDrawer(Context context, SelectionManager selectionManager) {
        super(context);
        mContext = context;
        mFrame = new NinePatchTexture(context, R.drawable.album_frame);
        mFrameSelected = new NinePatchTexture(context, R.drawable.grid_selected);
        mFrameSelectedTop = new NinePatchTexture(context, R.drawable.grid_selected_top);
        mImportBackground = new NinePatchTexture(context, R.drawable.import_translucent);
        mSelectionManager = selectionManager;
    }

    @Override
    public void prepareDrawing() {
        mSelectionMode = mSelectionManager.inSelectionMode();
    }

    @Override
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
        if (mSelectionMode && mSelectionManager.isItemSelected(path)) {
            frame = topIndex == 0 ? mFrameSelectedTop : mFrameSelected;
        } else {
            frame = mFrame;
        }

        drawFrame(canvas, frame, x, y, width, height);

        if (topIndex == 0) {
            ResourceTexture icon = getIcon(dataSourceType);
            if (icon != null) {
                IconDimension id = getIconDimension(icon, width, height);
                if (dataSourceType == DATASOURCE_TYPE_MTP) {
                    if (mImportLabel == null || mGridWidth != width) {
                        mGridWidth = width;
                        mImportLabel = MultiLineTexture.newInstance(
                                mContext.getString(R.string.click_import),
                                width - id.width - IMPORT_LABEL_PADDING, FONT_SIZE, FONT_COLOR);
                    }
                    int bgHeight = Math.max(id.height, mImportLabel.getHeight());
                    mImportBackground.setSize(width, bgHeight);
                    mImportBackground.draw(canvas, x, -y - bgHeight);
                    mImportLabel.draw(canvas, x + id.width + IMPORT_LABEL_PADDING,
                            -y - bgHeight + Math.abs(bgHeight - mImportLabel.getHeight()) / 2);
                }
                icon.draw(canvas, id.x, id.y, id.width, id.height);
            }
        }
    }

    @Override
    public void drawFocus(GLCanvas canvas, int width, int height) {
    }
}
