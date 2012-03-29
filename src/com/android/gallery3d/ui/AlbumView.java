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

import android.util.FloatMath;

import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.data.MediaItem;

public class AlbumView implements SlotView.SlotRenderer {
    private static final int PLACEHOLDER_COLOR = 0xFF222222;

    @SuppressWarnings("unused")
    private static final String TAG = "AlbumView";
    private static final int CACHE_SIZE = 64;

    private AlbumSlidingWindow mDataWindow;
    private final GalleryActivity mActivity;
    private final ColorTexture mWaitLoadingTexture;
    private final SlotView mSlotView;

    private SelectionDrawer mSelectionDrawer;
    private int mFocusIndex = -1;

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

    public AlbumView(GalleryActivity activity, SlotView slotView) {
        mSlotView = slotView;
        mActivity = activity;

        mWaitLoadingTexture = new ColorTexture(PLACEHOLDER_COLOR);
        mWaitLoadingTexture.setSize(1, 1);
    }

    public void setSelectionDrawer(SelectionDrawer drawer) {
        mSelectionDrawer = drawer;
    }

    public void setModel(Model model) {
        if (mDataWindow != null) {
            mDataWindow.setListener(null);
            mSlotView.setSlotCount(0);
            mDataWindow = null;
        }
        if (model != null) {
            mDataWindow = new AlbumSlidingWindow(mActivity, model, CACHE_SIZE);
            mDataWindow.setListener(new MyDataModelListener());
            mSlotView.setSlotCount(model.size());
        }
    }

    public void setFocusIndex(int slotIndex) {
        if (mFocusIndex == slotIndex) return;
        mFocusIndex = slotIndex;
        mSlotView.invalidate();
    }

    private static Texture checkTexture(GLCanvas canvas, Texture texture) {
        return ((texture == null) || ((texture instanceof UploadedTexture)
                && !((UploadedTexture) texture).isContentValid(canvas)))
                ? null
                : texture;
    }

    @Override
    public int renderSlot(GLCanvas canvas, int index, int pass, int width, int height) {
        AlbumSlidingWindow.AlbumEntry entry = mDataWindow.get(index);
        Texture content = checkTexture(canvas, entry.content);

        if (content == null) {
            content = mWaitLoadingTexture;
            entry.isWaitDisplayed = true;
        } else if (entry.isWaitDisplayed) {
            entry.isWaitDisplayed = false;
            entry.content = new FadeInTexture(
                    PLACEHOLDER_COLOR, (BitmapTexture) entry.content);
            content = entry.content;
        }

        // Fit the content into the box
        int w = content.getWidth();
        int h = content.getHeight();

        float scale = Math.min((float) width / w, (float) height / h);

        w = (int) FloatMath.floor(w * scale);
        h = (int) FloatMath.floor(h * scale);

        // Now draw it
        if (pass == 0) {
            canvas.translate(width / 2, height / 2);
            mSelectionDrawer.draw(canvas, content, w, h,
                    entry.rotation, entry.path, entry.mediaType, entry.isPanorama);
            canvas.translate(-width / 2, -height / 2);
            int result = 0;
            if (mFocusIndex == index) {
                result |= SlotView.RENDER_MORE_PASS;
            }
            if ((content instanceof FadeInTexture) &&
                    ((FadeInTexture) content).isAnimating()) {
                result |= SlotView.RENDER_MORE_FRAME;
            }
            return result;
        } else if (pass == 1) {
            canvas.translate(width / 2, height / 2);
            mSelectionDrawer.drawFocus(canvas, width, height);
            canvas.translate(-width / 2, -height / 2);
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

    @Override
    public void onSlotSizeChanged(int width, int height) {
        // Do nothing
    }
}
