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

import com.android.gallery3d.anim.Animation;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.ui.PositionRepository.Position;
import com.android.gallery3d.util.LinkedNode;

import android.content.Context;
import android.graphics.Rect;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.HashMap;

public class SlotView extends GLView {
    @SuppressWarnings("unused")
    private static final String TAG = "SlotView";

    private static final boolean WIDE = true;

    private static final int INDEX_NONE = -1;

    public interface Listener {
        public void onSingleTapUp(int index);
        public void onLongTap(int index);
        public void onScrollPositionChanged(int position, int total);
    }

    public static class SimpleListener implements Listener {
        public void onSingleTapUp(int index) {}
        public void onLongTap(int index) {}
        public void onScrollPositionChanged(int position, int total) {}
    }

    private final GestureDetector mGestureDetector;
    private final ScrollerHelper mScroller;
    private final Paper mPaper = new Paper();

    private Listener mListener;
    private UserInteractionListener mUIListener;

    // Use linked hash map to keep the rendering order
    private HashMap<DisplayItem, ItemEntry> mItems =
            new HashMap<DisplayItem, ItemEntry>();

    public LinkedNode.List<ItemEntry> mItemList = LinkedNode.newList();

    // This is used for multipass rendering
    private ArrayList<ItemEntry> mCurrentItems = new ArrayList<ItemEntry>();
    private ArrayList<ItemEntry> mNextItems = new ArrayList<ItemEntry>();

    private boolean mMoreAnimation = false;
    private MyAnimation mAnimation = null;
    private final Position mTempPosition = new Position();
    private final Layout mLayout = new Layout();
    private PositionProvider mPositions;
    private int mStartIndex = INDEX_NONE;

    // whether the down action happened while the view is scrolling.
    private boolean mDownInScrolling;
    private int mOverscrollEffect = OVERSCROLL_3D;

    public static final int OVERSCROLL_3D = 0;
    public static final int OVERSCROLL_SYSTEM = 1;
    public static final int OVERSCROLL_NONE = 2;

    public SlotView(Context context) {
        mGestureDetector =
                new GestureDetector(context, new MyGestureListener());
        mScroller = new ScrollerHelper(context);
    }

    public void setCenterIndex(int index) {
        int slotCount = mLayout.mSlotCount;
        if (index < 0 || index >= slotCount) {
            return;
        }
        Rect rect = mLayout.getSlotRect(index);
        int position = WIDE
                ? (rect.left + rect.right - getWidth()) / 2
                : (rect.top + rect.bottom - getHeight()) / 2;
        setScrollPosition(position);
    }

    public void makeSlotVisible(int index) {
        Rect rect = mLayout.getSlotRect(index);
        int visibleBegin = WIDE ? mScrollX : mScrollY;
        int visibleLength = WIDE ? getWidth() : getHeight();
        int visibleEnd = visibleBegin + visibleLength;
        int slotBegin = WIDE ? rect.left : rect.top;
        int slotEnd = WIDE ? rect.right : rect.bottom;

        int position = visibleBegin;
        if (visibleLength < slotEnd - slotBegin) {
            position = visibleBegin;
        } else if (slotBegin < visibleBegin) {
            position = slotBegin;
        } else if (slotEnd > visibleEnd) {
            position = slotEnd - visibleLength;
        }

        setScrollPosition(position);
    }

    public void setScrollPosition(int position) {
        position = Utils.clamp(position, 0, mLayout.getScrollLimit());
        mScroller.setPosition(position);
        updateScrollPosition(position, false);
    }

    public void setSlotSize(int slotWidth, int slotHeight) {
        mLayout.setSlotSize(slotWidth, slotHeight);
    }

    @Override
    public void addComponent(GLView view) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeComponent(GLView view) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void onLayout(boolean changeSize, int l, int t, int r, int b) {
        if (!changeSize) return;
        mLayout.setSize(r - l, b - t);
        onLayoutChanged(r - l, b - t);
        if (mOverscrollEffect == OVERSCROLL_3D) {
            mPaper.setSize(r - l, b - t);
        }
    }

    protected void onLayoutChanged(int width, int height) {
    }

    public void startTransition(PositionProvider position) {
        mPositions = position;
        mAnimation = new MyAnimation();
        mAnimation.start();
        if (mItems.size() != 0) invalidate();
    }

    public void savePositions(PositionRepository repository) {
        repository.clear();
        LinkedNode.List<ItemEntry> list = mItemList;
        ItemEntry entry = list.getFirst();
        Position position = new Position();
        while (entry != null) {
            position.set(entry.target);
            position.x -= mScrollX;
            position.y -= mScrollY;
            repository.putPosition(entry.item.getIdentity(), position);
            entry = list.nextOf(entry);
        }
    }

    private void updateScrollPosition(int position, boolean force) {
        if (!force && (WIDE ? position == mScrollX : position == mScrollY)) return;
        if (WIDE) {
            mScrollX = position;
        } else {
            mScrollY = position;
        }
        mLayout.setScrollPosition(position);
        onScrollPositionChanged(position);
    }

    protected void onScrollPositionChanged(int newPosition) {
        int limit = mLayout.getScrollLimit();
        mListener.onScrollPositionChanged(newPosition, limit);
    }

    public void putDisplayItem(Position target, Position base, DisplayItem item) {
        ItemEntry entry = new ItemEntry(item, target, base);
        mItemList.insertLast(entry);
        mItems.put(item, entry);
    }

    public void removeDisplayItem(DisplayItem item) {
        ItemEntry entry = mItems.remove(item);
        if (entry != null) entry.remove();
    }

    public Rect getSlotRect(int slotIndex) {
        return mLayout.getSlotRect(slotIndex);
    }

    @Override
    protected boolean onTouch(MotionEvent event) {
        if (mUIListener != null) mUIListener.onUserInteraction();
        mGestureDetector.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownInScrolling = !mScroller.isFinished();
                mScroller.forceFinished();
                break;
        }
        return true;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setUserInteractionListener(UserInteractionListener listener) {
        mUIListener = listener;
    }

    public void setOverscrollEffect(int kind) {
        mOverscrollEffect = kind;
        mScroller.setOverfling(kind == OVERSCROLL_SYSTEM);
    }

    @Override
    protected void render(GLCanvas canvas) {
        canvas.save(GLCanvas.SAVE_FLAG_CLIP);
        canvas.clipRect(0, 0, getWidth(), getHeight());
        super.render(canvas);

        long currentTimeMillis = canvas.currentAnimationTimeMillis();
        boolean more = mScroller.advanceAnimation(currentTimeMillis);
        boolean paperActive = (mOverscrollEffect == OVERSCROLL_3D)
                && mPaper.advanceAnimation(currentTimeMillis);
        updateScrollPosition(mScroller.getPosition(), false);
        float interpolate = 1f;
        if (mAnimation != null) {
            more |= mAnimation.calculate(currentTimeMillis);
            interpolate = mAnimation.value;
        }

        more |= paperActive;

        if (WIDE) {
            canvas.translate(-mScrollX, 0, 0);
        } else {
            canvas.translate(0, -mScrollY, 0);
        }

        LinkedNode.List<ItemEntry> list = mItemList;
        for (ItemEntry entry = list.getLast(); entry != null;) {
            if (renderItem(canvas, entry, interpolate, 0, paperActive)) {
                mCurrentItems.add(entry);
            }
            entry = list.previousOf(entry);
        }

        int pass = 1;
        while (!mCurrentItems.isEmpty()) {
            for (int i = 0, n = mCurrentItems.size(); i < n; i++) {
                ItemEntry entry = mCurrentItems.get(i);
                if (renderItem(canvas, entry, interpolate, pass, paperActive)) {
                    mNextItems.add(entry);
                }
            }
            mCurrentItems.clear();
            // swap mNextItems with mCurrentItems
            ArrayList<ItemEntry> tmp = mNextItems;
            mNextItems = mCurrentItems;
            mCurrentItems = tmp;
            pass += 1;
        }

        if (WIDE) {
            canvas.translate(mScrollX, 0, 0);
        } else {
            canvas.translate(0, mScrollY, 0);
        }

        if (more) invalidate();
        if (mMoreAnimation && !more && mUIListener != null) {
            mUIListener.onUserInteractionEnd();
        }
        mMoreAnimation = more;
        canvas.restore();
    }

    private boolean renderItem(GLCanvas canvas, ItemEntry entry,
            float interpolate, int pass, boolean paperActive) {
        canvas.save(GLCanvas.SAVE_FLAG_ALPHA | GLCanvas.SAVE_FLAG_MATRIX);
        Position position = entry.target;
        if (mPositions != null) {
            position = mTempPosition;
            position.set(entry.target);
            position.x -= mScrollX;
            position.y -= mScrollY;
            Position source = mPositions
                    .getPosition(entry.item.getIdentity(), position);
            source.x += mScrollX;
            source.y += mScrollY;
            position = mTempPosition;
            Position.interpolate(
                    source, entry.target, position, interpolate);
        }
        canvas.multiplyAlpha(position.alpha);
        if (paperActive) {
            canvas.multiplyMatrix(mPaper.getTransform(
                    position, entry.base, mScrollX, mScrollY), 0);
        } else {
            canvas.translate(position.x, position.y, position.z);
        }
        canvas.rotate(position.theta, 0, 0, 1);
        boolean more = entry.item.render(canvas, pass);
        canvas.restore();
        return more;
    }

    public static class MyAnimation extends Animation {
        public float value;

        public MyAnimation() {
            setInterpolator(new DecelerateInterpolator(4));
            setDuration(1500);
        }

        @Override
        protected void onCalculate(float progress) {
            value = progress;
        }
    }

    private static class ItemEntry extends LinkedNode {
        public DisplayItem item;
        public Position target;
        public Position base;

        public ItemEntry(DisplayItem item, Position target, Position base) {
            this.item = item;
            this.target = target;
            this.base = base;
        }
    }

    public static class Layout {

        private int mVisibleStart;
        private int mVisibleEnd;

        private int mSlotCount;
        private int mSlotWidth;
        private int mSlotHeight;

        private int mWidth;
        private int mHeight;

        private int mUnitCount;
        private int mContentLength;
        private int mScrollPosition;

        private int mVerticalPadding;
        private int mHorizontalPadding;

        public void setSlotSize(int slotWidth, int slotHeight) {
            mSlotWidth = slotWidth;
            mSlotHeight = slotHeight;
        }

        public boolean setSlotCount(int slotCount) {
            mSlotCount = slotCount;
            int hPadding = mHorizontalPadding;
            int vPadding = mVerticalPadding;
            initLayoutParameters();
            return vPadding != mVerticalPadding || hPadding != mHorizontalPadding;
        }

        public Rect getSlotRect(int index) {
            int col, row;
            if (WIDE) {
                col = index / mUnitCount;
                row = index - col * mUnitCount;
            } else {
                row = index / mUnitCount;
                col = index - row * mUnitCount;
            }

            int x = mHorizontalPadding + col * mSlotWidth;
            int y = mVerticalPadding + row * mSlotHeight;
            return new Rect(x, y, x + mSlotWidth, y + mSlotHeight);
        }

        public int getContentLength() {
            return mContentLength;
        }

        // Calculate
        // (1) mUnitCount: the number of slots we can fit into one column (or row).
        // (2) mContentLength: the width (or height) we need to display all the
        //     columns (rows).
        // (3) padding[]: the vertical and horizontal padding we need in order
        //     to put the slots towards to the center of the display.
        //
        // The "major" direction is the direction the user can scroll. The other
        // direction is the "minor" direction.
        //
        // The comments inside this method are the description when the major
        // directon is horizontal (X), and the minor directon is vertical (Y).
        private void initLayoutParameters(
                int majorLength, int minorLength,  /* The view width and height */
                int majorUnitSize, int minorUnitSize,  /* The slot width and height */
                int[] padding) {
            int unitCount = minorLength / minorUnitSize;
            if (unitCount == 0) unitCount = 1;
            mUnitCount = unitCount;

            // We put extra padding above and below the column.
            int availableUnits = Math.min(mUnitCount, mSlotCount);
            padding[0] = (minorLength - availableUnits * minorUnitSize) / 2;

            // Then calculate how many columns we need for all slots.
            int count = ((mSlotCount + mUnitCount - 1) / mUnitCount);
            mContentLength = count * majorUnitSize;

            // If the content length is less then the screen width, put
            // extra padding in left and right.
            padding[1] = Math.max(0, (majorLength - mContentLength) / 2);
        }

        private void initLayoutParameters() {
            int[] padding = new int[2];
            if (WIDE) {
                initLayoutParameters(mWidth, mHeight, mSlotWidth, mSlotHeight, padding);
                mVerticalPadding = padding[0];
                mHorizontalPadding = padding[1];
            } else {
                initLayoutParameters(mHeight, mWidth, mSlotHeight, mSlotWidth, padding);
                mVerticalPadding = padding[1];
                mHorizontalPadding = padding[0];
            }
            updateVisibleSlotRange();
        }

        public void setSize(int width, int height) {
            mWidth = width;
            mHeight = height;
            initLayoutParameters();
        }

        private void updateVisibleSlotRange() {
            int position = mScrollPosition;

            if (WIDE) {
                int start = Math.max(0, (position / mSlotWidth) * mUnitCount);
                int end = Math.min(mSlotCount, mUnitCount
                        * (position + mWidth + mSlotWidth - 1) / mSlotWidth);
                setVisibleRange(start, end);
            } else {
                int start = Math.max(0, mUnitCount * (position / mSlotHeight));
                int end = Math.min(mSlotCount, mUnitCount
                        * (position + mHeight + mSlotHeight - 1) / mSlotHeight);
                setVisibleRange(start, end);
            }
        }

        public void setScrollPosition(int position) {
            if (mScrollPosition == position) return;
            mScrollPosition = position;
            updateVisibleSlotRange();
        }

        private void setVisibleRange(int start, int end) {
            if (start == mVisibleStart && end == mVisibleEnd) return;
            if (start < end) {
                mVisibleStart = start;
                mVisibleEnd = end;
            } else {
                mVisibleStart = mVisibleEnd = 0;
            }
        }

        public int getVisibleStart() {
            return mVisibleStart;
        }

        public int getVisibleEnd() {
            return mVisibleEnd;
        }

        public int getSlotIndexByPosition(float x, float y) {
            float absoluteX = x + (WIDE ? mScrollPosition : 0);
            absoluteX -= mHorizontalPadding;
            int columnIdx = (int) (absoluteX + 0.5) / mSlotWidth;
            if ((absoluteX - mSlotWidth * columnIdx) < 0
                    || (!WIDE && columnIdx >= mUnitCount)) {
                return INDEX_NONE;
            }

            float absoluteY = y + (WIDE ? 0 : mScrollPosition);
            absoluteY -= mVerticalPadding;
            int rowIdx = (int) (absoluteY + 0.5) / mSlotHeight;
            if (((absoluteY - mSlotHeight * rowIdx) < 0)
                    || (WIDE && rowIdx >= mUnitCount)) {
                return INDEX_NONE;
            }
            int index = WIDE
                    ? (columnIdx * mUnitCount + rowIdx)
                    : (rowIdx * mUnitCount + columnIdx);

            return index >= mSlotCount ? INDEX_NONE : index;
        }

        public int getScrollLimit() {
            int limit = WIDE ? mContentLength - mWidth : mContentLength - mHeight;
            return limit <= 0 ? 0 : limit;
        }
    }

    private class MyGestureListener
            extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onFling(MotionEvent e1,
                MotionEvent e2, float velocityX, float velocityY) {
            int scrollLimit = mLayout.getScrollLimit();
            if (scrollLimit == 0) return false;
            float velocity = WIDE ? velocityX : velocityY;
            mScroller.fling((int) -velocity, 0, scrollLimit);
            if (mUIListener != null) mUIListener.onUserInteractionBegin();
            invalidate();
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1,
                MotionEvent e2, float distanceX, float distanceY) {
            float distance = WIDE ? distanceX : distanceY;
            boolean canMove = mScroller.startScroll(
                    Math.round(distance), 0, mLayout.getScrollLimit());
            if (mOverscrollEffect == OVERSCROLL_3D && !canMove) {
                mPaper.overScroll(distance);
            }
            invalidate();
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (mDownInScrolling) return true;
            int index = mLayout.getSlotIndexByPosition(e.getX(), e.getY());
            if (index != INDEX_NONE) mListener.onSingleTapUp(index);
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (mDownInScrolling) return;
            lockRendering();
            try {
                int index = mLayout.getSlotIndexByPosition(e.getX(), e.getY());
                if (index != INDEX_NONE) mListener.onLongTap(index);
            } finally {
                unlockRendering();
            }
        }
    }

    public void setStartIndex(int index) {
        mStartIndex = index;
    }

    // Return true if the layout parameters have been changed
    public boolean setSlotCount(int slotCount) {
        boolean changed = mLayout.setSlotCount(slotCount);

        // mStartIndex is applied the first time setSlotCount is called.
        if (mStartIndex != INDEX_NONE) {
            setCenterIndex(mStartIndex);
            mStartIndex = INDEX_NONE;
        }
        updateScrollPosition(WIDE ? mScrollX : mScrollY, true);
        return changed;
    }

    public int getVisibleStart() {
        return mLayout.getVisibleStart();
    }

    public int getVisibleEnd() {
        return mLayout.getVisibleEnd();
    }

    public int getScrollX() {
        return mScrollX;
    }

    public int getScrollY() {
        return mScrollY;
    }
}
