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

import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;

public class AlbumSetView implements SlotView.SlotRenderer {
    @SuppressWarnings("unused")
    private static final String TAG = "AlbumSetView";
    private static final int CACHE_SIZE = 32;

    private AlbumSetSlidingWindow mDataWindow;
    private final GalleryActivity mActivity;
    private final LabelSpec mLabelSpec;

    private SelectionDrawer mSelectionDrawer;
    private SlotView mSlotView;

    public static interface Model {
        public MediaItem[] getCoverItems(int index);
        public MediaSet getMediaSet(int index);
        public int size();
        public void setActiveWindow(int start, int end);
        public void setModelListener(ModelListener listener);
    }

    public static interface ModelListener {
        public void onWindowContentChanged(int index);
        public void onSizeChanged(int size);
    }

    public static class AlbumSetItem {
        public DisplayItem[] covers;
        public DisplayItem labelItem;
        public long setDataVersion;
    }

    public static class LabelSpec {
        public int labelBackgroundHeight;
        public int titleOffset;
        public int countOffset;
        public int titleFontSize;
        public int countFontSize;
        public int leftMargin;
        public int iconSize;
    }

    public AlbumSetView(GalleryActivity activity, SelectionDrawer drawer,
            SlotView slotView, LabelSpec labelSpec) {
        mActivity = activity;
        setSelectionDrawer(drawer);
        mLabelSpec = labelSpec;
        mSlotView = slotView;
    }

    public void setSelectionDrawer(SelectionDrawer drawer) {
        mSelectionDrawer = drawer;
        if (mDataWindow != null) {
            mDataWindow.setSelectionDrawer(drawer);
        }
    }

    public void setModel(AlbumSetView.Model model) {
        if (mDataWindow != null) {
            mDataWindow.setListener(null);
            mDataWindow = null;
            mSlotView.setSlotCount(0);
        }
        if (model != null) {
            mDataWindow = new AlbumSetSlidingWindow(mActivity, mLabelSpec,
                    mSelectionDrawer, model, CACHE_SIZE);
            mDataWindow.setListener(new MyCacheListener());
            mSlotView.setSlotCount(mDataWindow.size());
        }
    }

    @Override
    public int renderSlot(GLCanvas canvas, int index, int pass, int width, int height) {
        AlbumSetItem entry = mDataWindow.get(index);
        DisplayItem cover = entry.covers.length > 0 ? entry.covers[0] : null;
        DisplayItem label = entry.labelItem;

        // Put the cover items in reverse order, so that the first item is on
        // top of the rest.
        canvas.translate(width / 2, height / 2);
        int r = 0;
        if (cover != null) {
            cover.setBox(width, height);
            r |= cover.render(canvas, pass);
        }
        if (label != null) {
            label.setBox(width, height);
            r |= entry.labelItem.render(canvas, pass);
        }
        canvas.translate(-width / 2, -height / 2);
        return r;
    }

    @Override
    public void prepareDrawing() {
        mSelectionDrawer.prepareDrawing();
    }

    private class MyCacheListener implements AlbumSetSlidingWindow.Listener {

        @Override
        public void onSizeChanged(int size) {
            mSlotView.setSlotCount(size);
        }

        @Override
        public void onContentChanged() {
            mSlotView.invalidate();
        }
    }

    public void pause() {
        mDataWindow.pause();
    }

    public void resume() {
        mDataWindow.resume();
    }

    @Override
    public void onVisibleRangeChanged(int visibleStart, int visibleEnd) {
        if (mDataWindow != null) {
            mDataWindow.setActiveWindow(visibleStart, visibleEnd);
        }
    }
}
