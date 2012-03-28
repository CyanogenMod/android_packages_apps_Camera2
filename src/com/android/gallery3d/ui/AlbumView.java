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

public class AlbumView implements SlotView.SlotRenderer {
    @SuppressWarnings("unused")
    private static final String TAG = "AlbumView";
    private static final int CACHE_SIZE = 64;

    private AlbumSlidingWindow mDataWindow;
    private final GalleryActivity mActivity;
    private SelectionDrawer mSelectionDrawer;
    private int mCacheThumbSize;

    private final SlotView mSlotView;

    public static interface Model {
        public int size();
        public MediaItem get(int index);
        public void setActiveWindow(int start, int end);
        public void setModelListener(ModelListener listener);
    }

    public static interface ModelListener {
        public void onWindowContentChanged(int index);
        public void onSizeChanged(int size);
    }

    public AlbumView(GalleryActivity activity, SlotView  slotView,
            int cacheThumbSize) {
        mCacheThumbSize = cacheThumbSize;
        mSlotView = slotView;
        mActivity = activity;
    }

    public void setSelectionDrawer(SelectionDrawer drawer) {
        mSelectionDrawer = drawer;
        if (mDataWindow != null) mDataWindow.setSelectionDrawer(drawer);
    }

    public void setModel(Model model) {
        if (mDataWindow != null) {
            mDataWindow.setListener(null);
            mSlotView.setSlotCount(0);
            mDataWindow = null;
        }
        if (model != null) {
            mDataWindow = new AlbumSlidingWindow(
                    mActivity, model, CACHE_SIZE,
                    mCacheThumbSize);
            mDataWindow.setSelectionDrawer(mSelectionDrawer);
            mDataWindow.setListener(new MyDataModelListener());
            mSlotView.setSlotCount(model.size());
        }
    }

    public void setFocusIndex(int slotIndex) {
        if (mDataWindow != null) {
            mDataWindow.setFocusIndex(slotIndex);
        }
    }

    @Override
    public int renderSlot(GLCanvas canvas, int index, int pass, int width, int height) {
        DisplayItem item = mDataWindow.get(index);
        if (item != null) {
            canvas.translate(width / 2, height / 2);
            item.setBox(width, height);
            int r = item.render(canvas, pass);
            canvas.translate(-width / 2, -height / 2);
            return r;
        }
        return 0;
    }

    private class MyDataModelListener implements AlbumSlidingWindow.Listener {
        @Override
        public void onContentChanged() {
            mSlotView.invalidate();
        }

        @Override
        public void onSizeChanged(int size) {
            mSlotView.setSlotCount(size);
        }
    }

    public void resume() {
        mDataWindow.resume();
    }

    public void pause() {
        mDataWindow.pause();
    }

    @Override
    public void prepareDrawing() {
        mSelectionDrawer.prepareDrawing();
    }

    @Override
    public void onVisibleRangeChanged(int visibleStart, int visibleEnd) {
        if (mDataWindow != null) {
            mDataWindow.setActiveWindow(visibleStart, visibleEnd);
        }
    }
}
