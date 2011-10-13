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
import com.android.gallery3d.anim.AlphaAnimation;
import com.android.gallery3d.app.AlbumDataAdapter;
import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View.MeasureSpec;

public class FilmStripView extends GLView implements ScrollBarView.Listener,
        UserInteractionListener {
    @SuppressWarnings("unused")
    private static final String TAG = "FilmStripView";

    private static final int HIDE_ANIMATION_DURATION = 300;  // 0.3 sec

    public interface Listener {
        // Returns false if it cannot jump to the specified index at this time.
        boolean onSlotSelected(int slotIndex);
    }

    private int mTopMargin, mMidMargin, mBottomMargin;
    private int mContentSize, mBarSize, mGripSize;
    private AlbumView mAlbumView;
    private ScrollBarView mScrollBarView;
    private AlbumDataAdapter mAlbumDataAdapter;
    private StripDrawer mStripDrawer;
    private Listener mListener;
    private UserInteractionListener mUIListener;
    private NinePatchTexture mBackgroundTexture;

    // The layout of FileStripView is
    // topMargin
    //             ----+----+
    //            /    +----+--\
    // contentSize     |    |   thumbSize
    //            \    +----+--/
    //             ----+----+
    // midMargin
    //             ----+----+
    //            /    +----+--\
    //     barSize     |    |   gripSize
    //            \    +----+--/
    //             ----+----+
    // bottomMargin
    public FilmStripView(GalleryActivity activity, MediaSet mediaSet,
            int topMargin, int midMargin, int bottomMargin, int contentSize,
            int thumbSize, int barSize, int gripSize, int gripWidth) {
        mTopMargin = topMargin;
        mMidMargin = midMargin;
        mBottomMargin = bottomMargin;
        mContentSize = contentSize;
        mBarSize = barSize;
        mGripSize = gripSize;

        mStripDrawer = new StripDrawer((Context) activity);
        SlotView.Spec spec = new SlotView.Spec();
        spec.slotWidth = thumbSize;
        spec.slotHeight = thumbSize;
        mAlbumView = new AlbumView(activity, spec, thumbSize);
        mAlbumView.setOverscrollEffect(SlotView.OVERSCROLL_NONE);
        mAlbumView.setSelectionDrawer(mStripDrawer);
        mAlbumView.setListener(new SlotView.SimpleListener() {
            @Override
            public void onDown(int index) {
                FilmStripView.this.onDown(index);
            }
            @Override
            public void onUp() {
                FilmStripView.this.onUp();
            }
            @Override
            public void onSingleTapUp(int slotIndex) {
                FilmStripView.this.onSingleTapUp(slotIndex);
            }
            @Override
            public void onLongTap(int slotIndex) {
                FilmStripView.this.onLongTap(slotIndex);
            }
            @Override
            public void onScrollPositionChanged(int position, int total) {
                FilmStripView.this.onScrollPositionChanged(position, total);
            }
        });
        mAlbumView.setUserInteractionListener(this);
        mAlbumDataAdapter = new AlbumDataAdapter(activity, mediaSet);
        addComponent(mAlbumView);
        mScrollBarView = new ScrollBarView(activity.getAndroidContext(),
                mGripSize, gripWidth);
        mScrollBarView.setListener(this);
        addComponent(mScrollBarView);

        mAlbumView.setModel(mAlbumDataAdapter);
        mBackgroundTexture = new NinePatchTexture(activity.getAndroidContext(),
                R.drawable.navstrip_translucent);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setUserInteractionListener(UserInteractionListener listener) {
        mUIListener = listener;
    }

    public void show() {
        if (getVisibility() == GLView.VISIBLE) return;
        startAnimation(null);
        setVisibility(GLView.VISIBLE);
    }

    public void hide() {
        if (getVisibility() == GLView.INVISIBLE) return;
        AlphaAnimation animation = new AlphaAnimation(1, 0);
        animation.setDuration(HIDE_ANIMATION_DURATION);
        startAnimation(animation);
        setVisibility(GLView.INVISIBLE);
    }

    @Override
    protected void onVisibilityChanged(int visibility) {
        super.onVisibilityChanged(visibility);
        if (visibility == GLView.VISIBLE) {
            onUserInteraction();
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int height = mTopMargin + mContentSize + mMidMargin + mBarSize + mBottomMargin;
        MeasureHelper.getInstance(this)
                .setPreferredContentSize(MeasureSpec.getSize(widthSpec), height)
                .measure(widthSpec, heightSpec);
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        if (!changed) return;
        mAlbumView.layout(0, mTopMargin, right - left, mTopMargin + mContentSize);
        int barStart = mTopMargin + mContentSize + mMidMargin;
        mScrollBarView.layout(0, barStart, right - left, barStart + mBarSize);
        int width = right - left;
        int height = bottom - top;
    }

    @Override
    protected boolean onTouch(MotionEvent event) {
        // consume all touch events on the "gray area", so they don't go to
        // the photo view below. (otherwise you can scroll the picture through
        // it).
        return true;
    }

    @Override
    protected boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                onUserInteractionBegin();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                onUserInteractionEnd();
                break;
        }

        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void render(GLCanvas canvas) {
        mBackgroundTexture.draw(canvas, 0, 0, getWidth(), getHeight());
        super.render(canvas);
    }

    private void onDown(int index) {
        MediaItem item = mAlbumDataAdapter.get(index);
        Path path = (item == null) ? null : item.getPath();
        mStripDrawer.setPressedPath(path);
        mAlbumView.invalidate();
    }

    private void onUp() {
        mStripDrawer.setPressedPath(null);
        mAlbumView.invalidate();
    }

    private void onSingleTapUp(int slotIndex) {
        if (mListener.onSlotSelected(slotIndex)) {
            mAlbumView.setFocusIndex(slotIndex);
        }
    }

    private void onLongTap(int slotIndex) {
        onSingleTapUp(slotIndex);
    }

    private void onScrollPositionChanged(int position, int total) {
        mScrollBarView.setContentPosition(position, total);
    }

    // Called by AlbumView
    @Override
    public void onUserInteractionBegin() {
        mUIListener.onUserInteractionBegin();
    }

    // Called by AlbumView
    @Override
    public void onUserInteractionEnd() {
        mUIListener.onUserInteractionEnd();
    }

    // Called by AlbumView
    @Override
    public void onUserInteraction() {
        mUIListener.onUserInteraction();
    }

    // Called by ScrollBarView
    @Override
    public void onScrollBarPositionChanged(int position) {
        mAlbumView.setScrollPosition(position);
    }

    public void setFocusIndex(int slotIndex) {
        mAlbumView.setFocusIndex(slotIndex);
        mAlbumView.makeSlotVisible(slotIndex);
    }

    public void setStartIndex(int slotIndex) {
        mAlbumView.setStartIndex(slotIndex);
    }

    public void pause() {
        mAlbumView.pause();
        mAlbumDataAdapter.pause();
    }

    public void resume() {
        mAlbumView.resume();
        mAlbumDataAdapter.resume();
    }
}
