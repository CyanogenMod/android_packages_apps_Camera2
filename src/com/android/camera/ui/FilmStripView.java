/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.camera.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Scroller;

public class FilmStripView extends ViewGroup {

    private static final String TAG = "FilmStripView";
    private static final int BUFFER_SIZE = 5;
    // Horizontal padding of children.
    private static final int H_PADDING = 50;
    // Duration to go back to the first.
    private static final int BACK_SCROLL_DURATION = 500;
    private static final float MIN_SCALE = 0.7f;

    private Context mContext;
    private GestureDetector mGestureDetector;
    private DataAdapter mDataAdapter;
    private final Rect mDrawArea = new Rect();

    private int mCurrentInfo;
    private Scroller mScroller;
    private boolean mIsScrolling;
    private int mCenterPosition = -1;
    private ViewInfo[] mViewInfo = new ViewInfo[BUFFER_SIZE];

    public interface ImageData {
        // The values returned by getWidth() and getHeight() will be used for layout.
        public int getWidth();
        public int getHeight();
    }

    public interface DataAdapter {

        public int getTotalNumber();
        public View getView(Context context, int id);
        public ImageData getImageData(int id);
        public void suggestSize(int w, int h);

        public void requestLoad(ContentResolver r);
        public void setDataListener(FilmStripView v);
    }

    private static class ViewInfo {
        private int mDataID;
        // the position of the left of the view in the whole filmstrip.
        private int mLeftPosition;
        private  View mView;

        public ViewInfo(int id, View v) {
            mDataID = id;
            mView = v;
            mLeftPosition = -1;
        }

        public int getId() {
            return mDataID;
        }

        public void setLeftPosition(int pos) {
            mLeftPosition = pos;
        }

        public int getLeftPosition() {
            return mLeftPosition;
        }

        public int getCenterPosition() {
            return mLeftPosition + mView.getWidth() / 2;
        }

        public View getView() {
            return mView;
        }

        private void layoutAt(int l, int t) {
            mView.layout(l, t, l + mView.getMeasuredWidth(), t + mView.getMeasuredHeight());
        }

        public void layoutIn(Rect drawArea, int refCenter) {
            // drawArea is where to layout in.
            // refCenter is the absolute horizontal position of the center of drawArea.
            layoutAt(drawArea.centerX() + mLeftPosition - refCenter,
                     drawArea.centerY() - mView.getMeasuredHeight() / 2);
        }
    }

    public FilmStripView(Context context) {
        super(context);
        init(context);
    }

    public FilmStripView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FilmStripView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        mCurrentInfo = (BUFFER_SIZE - 1) / 2;
        setWillNotDraw(false);
        mContext = context;
        mScroller = new Scroller(context);
        mGestureDetector =
                new GestureDetector(context, new MyGestureListener(),
                        null, true /* ignoreMultitouch */);
    }

    @Override
    public void onDraw(Canvas c) {
        if (mIsScrolling) {
            layoutChildren();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        float scale = MIN_SCALE;
        if (mDataAdapter != null) mDataAdapter.suggestSize(w / 2, h / 2);

        int boundWidth = (int) (w * scale);
        int boundHeight = (int) (h * scale);

        int wMode = View.MeasureSpec.EXACTLY;
        int hMode = View.MeasureSpec.EXACTLY;

        for (int i = 0; i < mViewInfo.length; i++) {
            ViewInfo info = mViewInfo[i];
            if (mViewInfo[i] == null) continue;

            int imageWidth = mDataAdapter.getImageData(info.getId()).getWidth();
            int imageHeight = mDataAdapter.getImageData(info.getId()).getHeight();

            int scaledWidth = boundWidth;
            int scaledHeight = boundHeight;
            if (imageWidth * scaledHeight > scaledWidth * imageHeight) {
                scaledHeight = imageHeight * scaledWidth / imageWidth;
            } else {
                scaledWidth = imageWidth * scaledHeight / imageHeight;
            }
            scaledWidth += H_PADDING * 2 * scale;
            mViewInfo[i].getView().measure(
                    View.MeasureSpec.makeMeasureSpec(scaledWidth, wMode)
                    , View.MeasureSpec.makeMeasureSpec(scaledHeight, hMode));
        }
        setMeasuredDimension(w, h);
    }

    private int findTheNearestView(int pointX) {

        int nearest = 0;
        // find the first non-null ViewInfo.
        for (; nearest < BUFFER_SIZE
                && (mViewInfo[nearest] == null || mViewInfo[nearest].getLeftPosition() == -1);
                nearest++);
        // no existing available ViewInfo
        if (nearest == BUFFER_SIZE) return -1;
        int min = Math.abs(pointX - mViewInfo[nearest].getCenterPosition());

        for (int infoID = nearest + 1;
                infoID < BUFFER_SIZE && mViewInfo[infoID] != null; infoID++) {
            // not measured yet.
            if  (mViewInfo[infoID].getLeftPosition() == -1) continue;

            int c = mViewInfo[infoID].getCenterPosition();
            int dist = Math.abs(pointX - c);
            if (dist < min) {
                min = dist;
                nearest = infoID;
            }
        }
        return nearest;
    }

    // We try to keep the one closest to the center of the screen at position mCurrentInfo.
    private void stepIfNeeded() {
        int nearest = findTheNearestView(mCenterPosition);
        // no change made.
        if (nearest == -1 || nearest == mCurrentInfo) return;

        int adjust = nearest - mCurrentInfo;
        if (adjust > 0) {
            for (int k = 0; k < adjust; k++) {
                if (mViewInfo[k] != null) {
                    removeView(mViewInfo[k].getView());
                }
            }
            for (int k = 0; k + adjust < BUFFER_SIZE; k++) {
                mViewInfo[k] = mViewInfo[k + adjust];
            }
            for (int k = BUFFER_SIZE - adjust; k < BUFFER_SIZE; k++) {
                mViewInfo[k] = null;
                if (mViewInfo[k - 1] != null) getInfo(k, mViewInfo[k - 1].getId() + 1);
            }
        } else {
            for (int k = BUFFER_SIZE - 1; k >= BUFFER_SIZE + adjust; k--) {
                if (mViewInfo[k] != null) {
                    removeView(mViewInfo[k].getView());
                }
            }
            for (int k = BUFFER_SIZE - 1; k + adjust >= 0; k--) {
                mViewInfo[k] = mViewInfo[k + adjust];
            }
            for (int k = -1 - adjust; k >= 0; k--) {
                mViewInfo[k] = null;
                if (mViewInfo[k + 1] != null) getInfo(k, mViewInfo[k + 1].getId() - 1);
            }
        }
    }

    private void stopScroll() {
        mScroller.forceFinished(true);
        mIsScrolling = false;
    }

    private void adjustCenterPosition() {
        ViewInfo curr = mViewInfo[mCurrentInfo];
        if (curr == null) return;

        if (curr.getId() == 0 && mCenterPosition < curr.getCenterPosition()) {
            mCenterPosition = curr.getCenterPosition();
            if (mIsScrolling) stopScroll();
        }
        if (curr.getId() == mDataAdapter.getTotalNumber() - 1
                && mCenterPosition > curr.getCenterPosition()) {
            mCenterPosition = curr.getCenterPosition();
            if (mIsScrolling) stopScroll();
        }
    }

    private void layoutChildren() {
        mIsScrolling = mScroller.computeScrollOffset();

        if (mIsScrolling) mCenterPosition = mScroller.getCurrX();

        adjustCenterPosition();

        mViewInfo[mCurrentInfo].layoutIn(mDrawArea, mCenterPosition);

        // images on the left
        for (int infoID = mCurrentInfo - 1; infoID >= 0; infoID--) {
            ViewInfo curr = mViewInfo[infoID];
            if (curr != null) {
                ViewInfo next = mViewInfo[infoID + 1];
                curr.setLeftPosition(next.getLeftPosition() - curr.getView().getMeasuredWidth());
                curr.layoutIn(mDrawArea, mCenterPosition);
            }
        }

        // images on the right
        for (int infoID = mCurrentInfo + 1; infoID < BUFFER_SIZE; infoID++) {
            ViewInfo curr = mViewInfo[infoID];
            if (curr != null) {
                ViewInfo prev = mViewInfo[infoID - 1];
                curr.setLeftPosition(prev.getLeftPosition() + prev.getView().getMeasuredWidth());
                curr.layoutIn(mDrawArea, mCenterPosition);
            }
        }

        stepIfNeeded();
        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mViewInfo[mCurrentInfo] == null) return;

        mDrawArea.left = l;
        mDrawArea.top = t;
        mDrawArea.right = r;
        mDrawArea.bottom = b;

        layoutChildren();
    }

    public void setDataAdapter(
            DataAdapter adapter, ContentResolver resolver) {
        mDataAdapter = adapter;
        mDataAdapter.suggestSize(getMeasuredWidth(), getMeasuredHeight());
        mDataAdapter.setDataListener(this);
        mDataAdapter.requestLoad(resolver);
    }

    private void getInfo(int infoID, int dataID) {
        View v = mDataAdapter.getView(mContext, dataID);
        if (v == null) return;
        v.setPadding(H_PADDING, 0, H_PADDING, 0);
        addView(v);
        ViewInfo info = new ViewInfo(dataID, v);
        mViewInfo[infoID] = info;
    }

    public void onDataChanged() {
        removeAllViews();
        int dataNumber = mDataAdapter.getTotalNumber();
        if (dataNumber == 0) return;

        int currentData = 0;
        int currentLeft = 0;
        // previous data exists.
        if (mViewInfo[mCurrentInfo] != null) {
            currentLeft = mViewInfo[mCurrentInfo].getLeftPosition();
            currentData = mViewInfo[mCurrentInfo].getId();
        }
        getInfo(mCurrentInfo, currentData);
        mViewInfo[mCurrentInfo].setLeftPosition(currentLeft);
        for (int i = 1; mCurrentInfo + i < BUFFER_SIZE || mCurrentInfo - i >= 0; i++) {
            int infoID = mCurrentInfo + i;
            if (infoID < BUFFER_SIZE && mViewInfo[infoID - 1] != null) {
                getInfo(infoID, mViewInfo[infoID - 1].getId() + 1);
            }
            infoID = mCurrentInfo - i;
            if (infoID >= 0 && mViewInfo[infoID + 1] != null) {
                getInfo(infoID, mViewInfo[infoID + 1].getId() - 1);
            }
        }
        layoutChildren();
    }

    private void movePositionTo(int position) {
        mScroller.startScroll(mCenterPosition, 0, position - mCenterPosition,
                0, BACK_SCROLL_DURATION);
        layoutChildren();
    }

    public void goToFirst() {
        movePositionTo(0);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mGestureDetector.onTouchEvent(ev);
    }

    private class MyGestureListener
                extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            float x = (float) e.getX();
            float y = (float) e.getY();
            for (int i = 0; i < BUFFER_SIZE; i++) {
                if (mViewInfo[i] == null) continue;
                View v = mViewInfo[i].getView();
                if (x >= v.getLeft() && x < v.getRight()
                        && y >= v.getTop() && y < v.getBottom()) {
                    Log.v(TAG, "l, r, t, b " + v.getLeft() + ',' + v.getRight()
                          + ',' + v.getTop() + ',' + v.getBottom());
                    movePositionTo(mViewInfo[i].getCenterPosition());
                    break;
                }
            }
            return true;
        }

        @Override
        public boolean onDown(MotionEvent ev) {
            if (mIsScrolling) stopScroll();
            return true;
        }

        @Override
        public boolean onScroll(
                MotionEvent e1, MotionEvent e2, float dx, float dy) {
            stopScroll();
            mCenterPosition += dx;
            layoutChildren();
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                float velocityY) {
            ViewInfo info = mViewInfo[mCurrentInfo];
            int w = getWidth();
            if (info == null) return true;
            mScroller.fling(mCenterPosition, 0, (int) -velocityX, (int) velocityY,
                    // estimation of possible length on the left
                    info.getLeftPosition() - info.getId() * w * 2,
                    // estimation of possible length on the right
                    info.getLeftPosition()
                            + (mDataAdapter.getTotalNumber() - info.getId()) * w * 2,
                    0, 0);
            layoutChildren();
            return true;
        }
    }
}
