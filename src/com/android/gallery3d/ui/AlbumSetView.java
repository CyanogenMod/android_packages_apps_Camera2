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
import android.util.FloatMath;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.ui.AlbumSetSlidingWindow.AlbumSetEntry;

// TODO: rename to AlbumSetRenderer
public class AlbumSetView implements SlotView.SlotRenderer {
    @SuppressWarnings("unused")
    private static final String TAG = "AlbumSetView";
    private static final int CACHE_SIZE = 64;
    private static final int PLACEHOLDER_COLOR = 0xFF222222;

    private final ColorTexture mWaitLoadingTexture;
    private AlbumSetSlidingWindow mDataWindow;
    private final GalleryActivity mActivity;
    private final LabelSpec mLabelSpec;

    private SelectionDrawer mSelectionDrawer;
    private SlotView mSlotView;
    private NinePatchTexture mDarkStrip;

    public static interface Model {
        public MediaItem getCoverItem(int index);
        public MediaSet getMediaSet(int index);
        public int size();
        public void setActiveWindow(int start, int end);
        public void setModelListener(ModelListener listener);
    }

    public static interface ModelListener {
        public void onWindowContentChanged(int index);
        public void onSizeChanged(int size);
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
        mLabelSpec = labelSpec;
        mSlotView = slotView;

        mWaitLoadingTexture = new ColorTexture(PLACEHOLDER_COLOR);
        mWaitLoadingTexture.setSize(1, 1);

        Context context = activity.getAndroidContext();
        mDarkStrip = new NinePatchTexture(context, R.drawable.dark_strip);

        setSelectionDrawer(drawer);
    }

    public void setSelectionDrawer(SelectionDrawer drawer) {
        mSelectionDrawer = drawer;
        mSlotView.invalidate();
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

    private static Texture checkTexture(GLCanvas canvas, Texture texture) {
        return ((texture == null) || ((texture instanceof UploadedTexture)
                && !((UploadedTexture) texture).isContentValid(canvas)))
                ? null
                : texture;
    }

    @Override
    public int renderSlot(GLCanvas canvas, int index, int pass, int width, int height) {
        AlbumSetEntry entry = mDataWindow.get(index);
        return renderContent(canvas, pass, entry, width, height)
                | renderLabel(canvas, pass, entry, width, height);
    }

    private int renderContent(GLCanvas canvas,
            int pass, AlbumSetEntry entry, int width, int height) {
        Texture content = checkTexture(canvas, entry.content);

        if (content == null) {
            content = mWaitLoadingTexture;
            entry.isWaitLoadingDisplayed = true;
        } else if (entry.isWaitLoadingDisplayed) {
            entry.isWaitLoadingDisplayed = false;
            entry.content = new FadeInTexture(
                    PLACEHOLDER_COLOR, (BitmapTexture) content);
            content = entry.content;
        }

        // Fit the content into the box
        int w = content.getWidth();
        int h = content.getHeight();

        float scale = Math.min(width / (float) w, height / (float) h);

        w = (int) FloatMath.floor(w * scale);
        h = (int) FloatMath.floor(h * scale);

        canvas.translate(width / 2, height / 2);
        // Now draw it
        mSelectionDrawer.draw(canvas, content, width, height,
                entry.rotation, entry.setPath, entry.sourceType, entry.mediaType,
                entry.isPanorama, mLabelSpec.labelBackgroundHeight,
                entry.cacheFlag == MediaSet.CACHE_FLAG_FULL,
                (entry.cacheFlag == MediaSet.CACHE_FLAG_FULL)
                && (entry.cacheStatus != MediaSet.CACHE_STATUS_CACHED_FULL));
        canvas.translate(-width / 2, -height / 2);

        if ((content instanceof FadeInTexture) &&
                ((FadeInTexture) content).isAnimating()) {
            return SlotView.RENDER_MORE_FRAME;
        }
        return 0;
    }

    private int renderLabel(GLCanvas canvas,
            int pass, AlbumSetEntry entry, int width, int height) {

        // We show the loading message only when the album is still loading
        // (Not when we are still preparing the label)
        Texture content = checkTexture(canvas, entry.label);
        if (entry.album == null) {
            content = mDataWindow.getLoadingTexture();
        }
        if (content != null) {
            int h = mLabelSpec.labelBackgroundHeight;
            SelectionDrawer.drawFrame(canvas, mDarkStrip, 0, height - h, width, h);
            content.draw(canvas, 0, height - h, width, h);
        }
        return 0;
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

    @Override
    public void onSlotSizeChanged(int width, int height) {
        if (mDataWindow != null) {
            mDataWindow.onSlotSizeChanged(width, height);
        }
    }
}
