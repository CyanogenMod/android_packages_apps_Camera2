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

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Scroller;

public class FilmStripView extends ViewGroup {
    private static final String TAG = FilmStripView.class.getSimpleName();
    private static final int BUFFER_SIZE = 5;
    // Horizontal padding of children.
    private static final int H_PADDING = 100;
    // Duration to go back to the first.
    private static final int DURATION_BACK_ANIM = 500;
    private static final int DURATION_SCROLL_TO_FILMSTRIP = 350;
    private static final int DURATION_GEOMETRY_ADJUST = 200;
    private static final float FILM_STRIP_SCALE = 0.6f;
    private static final float MAX_SCALE = 1f;

    private Context mContext;
    private FilmStripGestureRecognizer mGestureRecognizer;
    private DataAdapter mDataAdapter;
    private final Rect mDrawArea = new Rect();

    private int mCurrentInfo;
    private float mScale;
    private GeometryAnimator mGeometryAnimator;
    private LinearInterpolator mLinearInterpolator;
    private int mCenterPosition = -1;
    private ViewInfo[] mViewInfo = new ViewInfo[BUFFER_SIZE];

    private Listener mListener;

    // This is used to resolve the misalignment problem when the device
    // orientation is changed. If the current item is in fullscreen, it might
    // be shifted because mCenterPosition is not adjusted with the orientation.
    // Set this to true when onSizeChanged is called to make sure we adjust
    // mCenterPosition accordingly.
    private boolean mAnchorPending;

    public interface ImageData {
        public static final int TYPE_NONE = 0;
        public static final int TYPE_CAMERA_PREVIEW = 1;
        public static final int TYPE_PHOTO = 2;
        public static final int TYPE_VIDEO = 3;
        public static final int TYPE_PHOTOSPHERE = 4;

        // The actions are defined bit-wise so we can use bit operations like
        // | and &.
        public static final int ACTION_NONE = 0;
        public static final int ACTION_PROMOTE = 1;
        public static final int ACTION_DEMOTE = (1 << 1);
        public static final int ACTION_PLAY = (1 << 2);

        // SIZE_FULL means disgard the width or height when deciding the view size
        // of this ImageData, just use full screen size.
        public static final int SIZE_FULL = -2;

        // The values returned by getWidth() and getHeight() will be used for layout.
        public int getWidth();
        public int getHeight();
        public int getType();
        public boolean isActionSupported(int action);

        // prepare() should be called first time before using it.
        public void prepare();

        // recycle() should be called before we nullify the reference to this
        // data.
        public void recycle();
    }

    public interface DataAdapter {
        public interface UpdateReporter {
            public boolean isDataRemoved(int id);
            public boolean isDataUpdated(int id);
        }

        public interface Listener {
            // Called when the whole data loading is done. No any assumption
            // on previous data.
            public void onDataLoaded();
            // Only some of the data is changed. The listener should check
            // if any thing needs to be updated.
            public void onDataUpdated(UpdateReporter reporter);
            public void onDataInserted(int dataID, ImageData data);
            public void onDataRemoved(int dataID, ImageData data);
        }

        public int getTotalNumber();
        public View getView(Context context, int id);
        public ImageData getImageData(int id);
        public void suggestDecodeSize(int w, int h);

        public void setListener(Listener listener);
    }

    public interface Listener {
        public void onDataPromoted(int dataID);
        public void onDataDemoted(int dataID);
    }

    // A helper class to tract and calculate the view coordination.
    private static class ViewInfo {
        private int mDataID;
        // the position of the left of the view in the whole filmstrip.
        private int mLeftPosition;
        private View mView;

        public ViewInfo(int id, View v) {
            v.setPivotX(0f);
            v.setPivotY(0f);
            mDataID = id;
            mView = v;
            mLeftPosition = -1;
        }

        public int getID() {
            return mDataID;
        }

        public void setID(int id) {
            mDataID = id;
        }

        public void setLeftPosition(int pos) {
            mLeftPosition = pos;
        }

        public int getLeftPosition() {
            return mLeftPosition;
        }

        public float getTranslationY(float scale) {
            return mView.getTranslationY() / scale;
        }

        public float getTranslationX(float scale) {
            return mView.getTranslationX();
        }

        public void setTranslationY(float transY, float scale) {
            mView.setTranslationY(transY * scale);
        }

        public void setTranslationX(float transX, float scale) {
            mView.setTranslationX(transX * scale);
        }

        public int getCenterX() {
            return mLeftPosition + mView.getWidth() / 2;
        }

        public View getView() {
            return mView;
        }

        private void layoutAt(int left, int top) {
            mView.layout(left, top, left + mView.getMeasuredWidth(),
                    top + mView.getMeasuredHeight());
        }

        public void layoutIn(Rect drawArea, int refCenter, float scale) {
            // drawArea is where to layout in.
            // refCenter is the absolute horizontal position of the center of drawArea.
            int left = (int) (drawArea.centerX() + (mLeftPosition - refCenter) * scale);
            int top = (int) (drawArea.centerY() - (mView.getMeasuredHeight() / 2) * scale);
            layoutAt(left, top);
            mView.setScaleX(scale);
            mView.setScaleY(scale);
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
        // This is for positioning camera controller at the same place in
        // different orientations.
        setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        setWillNotDraw(false);
        mContext = context;
        mScale = 1.0f;
        mGeometryAnimator = new GeometryAnimator(context);
        mLinearInterpolator = new LinearInterpolator();
        mGestureRecognizer =
                new FilmStripGestureRecognizer(context, new MyGestureReceiver());
    }

    public void setListener(Listener l) {
        mListener = l;
    }

    public float getScale() {
        return mScale;
    }

    public boolean isAnchoredTo(int id) {
        if (mViewInfo[mCurrentInfo].getID() == id
                && mViewInfo[mCurrentInfo].getCenterX() == mCenterPosition) {
            return true;
        }
        return false;
    }

    public int getCurrentType() {
        if (mDataAdapter == null) return ImageData.TYPE_NONE;
        ViewInfo curr = mViewInfo[mCurrentInfo];
        if (curr == null) return ImageData.TYPE_NONE;
        return mDataAdapter.getImageData(curr.getID()).getType();
    }

    @Override
    public void onDraw(Canvas c) {
        if (mGeometryAnimator.hasNewGeometry()) {
            layoutChildren();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int boundWidth = MeasureSpec.getSize(widthMeasureSpec);
        int boundHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (mDataAdapter != null) {
            mDataAdapter.suggestDecodeSize(boundWidth / 2, boundHeight / 2);
        }

        int wMode = View.MeasureSpec.EXACTLY;
        int hMode = View.MeasureSpec.EXACTLY;

        for (int i = 0; i < mViewInfo.length; i++) {
            ViewInfo info = mViewInfo[i];
            if (mViewInfo[i] == null) continue;

            int imageWidth = mDataAdapter.getImageData(info.getID()).getWidth();
            int imageHeight = mDataAdapter.getImageData(info.getID()).getHeight();
            if (imageWidth == ImageData.SIZE_FULL) imageWidth = boundWidth;
            if (imageHeight == ImageData.SIZE_FULL) imageHeight = boundHeight;

            int scaledWidth = boundWidth;
            int scaledHeight = boundHeight;

            if (imageWidth * scaledHeight > scaledWidth * imageHeight) {
                scaledHeight = imageHeight * scaledWidth / imageWidth;
            } else {
                scaledWidth = imageWidth * scaledHeight / imageHeight;
            }
            mViewInfo[i].getView().measure(
                    View.MeasureSpec.makeMeasureSpec(scaledWidth, wMode)
                    , View.MeasureSpec.makeMeasureSpec(scaledHeight, hMode));
        }
        setMeasuredDimension(boundWidth, boundHeight);
    }

    private int findTheNearestView(int pointX) {

        int nearest = 0;
        // find the first non-null ViewInfo.
        for (; nearest < BUFFER_SIZE
                && (mViewInfo[nearest] == null || mViewInfo[nearest].getLeftPosition() == -1);
                nearest++);
        // no existing available ViewInfo
        if (nearest == BUFFER_SIZE) return -1;
        int min = Math.abs(pointX - mViewInfo[nearest].getCenterX());

        for (int infoID = nearest + 1;
                infoID < BUFFER_SIZE && mViewInfo[infoID] != null; infoID++) {
            // not measured yet.
            if  (mViewInfo[infoID].getLeftPosition() == -1) continue;

            int c = mViewInfo[infoID].getCenterX();
            int dist = Math.abs(pointX - c);
            if (dist < min) {
                min = dist;
                nearest = infoID;
            }
        }
        return nearest;
    }

    private ViewInfo buildInfoFromData(int dataID) {
        ImageData data = mDataAdapter.getImageData(dataID);
        if (data == null) return null;
        data.prepare();
        View v = mDataAdapter.getView(mContext, dataID);
        if (v == null) return null;
        ViewInfo info = new ViewInfo(dataID, v);
        addView(info.getView());
        return info;
    }

    private void removeInfo(int infoID) {
        if (infoID >= mViewInfo.length || mViewInfo[infoID] == null) return;

        removeView(mViewInfo[infoID].getView());
        mDataAdapter.getImageData(mViewInfo[infoID].getID()).recycle();
        mViewInfo[infoID] = null;
    }

    // We try to keep the one closest to the center of the screen at position mCurrentInfo.
    private void stepIfNeeded() {
        int nearest = findTheNearestView(mCenterPosition);
        // no change made.
        if (nearest == -1 || nearest == mCurrentInfo) return;

        int adjust = nearest - mCurrentInfo;
        if (adjust > 0) {
            for (int k = 0; k < adjust; k++) {
                removeInfo(k);
            }
            for (int k = 0; k + adjust < BUFFER_SIZE; k++) {
                mViewInfo[k] = mViewInfo[k + adjust];
            }
            for (int k = BUFFER_SIZE - adjust; k < BUFFER_SIZE; k++) {
                mViewInfo[k] = null;
                if (mViewInfo[k - 1] != null) {
                        mViewInfo[k] = buildInfoFromData(mViewInfo[k - 1].getID() + 1);
                }
            }
        } else {
            for (int k = BUFFER_SIZE - 1; k >= BUFFER_SIZE + adjust; k--) {
                removeInfo(k);
            }
            for (int k = BUFFER_SIZE - 1; k + adjust >= 0; k--) {
                mViewInfo[k] = mViewInfo[k + adjust];
            }
            for (int k = -1 - adjust; k >= 0; k--) {
                mViewInfo[k] = null;
                if (mViewInfo[k + 1] != null) {
                        mViewInfo[k] = buildInfoFromData(mViewInfo[k + 1].getID() - 1);
                }
            }
        }
    }

    // Don't go beyond the bound.
    private void adjustCenterPosition() {
        ViewInfo curr = mViewInfo[mCurrentInfo];
        if (curr == null) return;

        if (curr.getID() == 0 && mCenterPosition < curr.getCenterX()) {
            mCenterPosition = curr.getCenterX();
            mGeometryAnimator.stopScroll();
        }
        if (curr.getID() == mDataAdapter.getTotalNumber() - 1
                && mCenterPosition > curr.getCenterX()) {
            mCenterPosition = curr.getCenterX();
            mGeometryAnimator.stopScroll();
        }
    }

    private void layoutChildren() {
        if (mAnchorPending) {
            mCenterPosition = mViewInfo[mCurrentInfo].getCenterX();
            mAnchorPending = false;
        }

        if (mGeometryAnimator.hasNewGeometry()) {
            mCenterPosition = mGeometryAnimator.getNewPosition();
            mScale = mGeometryAnimator.getNewScale();
        }

        adjustCenterPosition();

        mViewInfo[mCurrentInfo].layoutIn(mDrawArea, mCenterPosition, mScale);

        // images on the left
        for (int infoID = mCurrentInfo - 1; infoID >= 0; infoID--) {
            ViewInfo curr = mViewInfo[infoID];
            if (curr != null) {
                ViewInfo next = mViewInfo[infoID + 1];
                curr.setLeftPosition(
                        next.getLeftPosition() - curr.getView().getMeasuredWidth() - H_PADDING);
                curr.layoutIn(mDrawArea, mCenterPosition, mScale);
            }
        }

        // images on the right
        for (int infoID = mCurrentInfo + 1; infoID < BUFFER_SIZE; infoID++) {
            ViewInfo curr = mViewInfo[infoID];
            if (curr != null) {
                ViewInfo prev = mViewInfo[infoID - 1];
                curr.setLeftPosition(
                        prev.getLeftPosition() + prev.getView().getMeasuredWidth() + H_PADDING);
                curr.layoutIn(mDrawArea, mCenterPosition, mScale);
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

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w == oldw && h == oldh) return;
        if (mViewInfo[mCurrentInfo] != null && mScale == 1f
                && isAnchoredTo(mViewInfo[mCurrentInfo].getID())) {
            mAnchorPending = true;
        }
    }

    private void animateViewBack(View v) {
        v.animate().translationX(0).alpha(1f).setDuration(200).start();
    }

    private void updateRemoval(int removedInfo, final ImageData data) {
        final View removedView = mViewInfo[removedInfo].getView();
        final int offsetX = (int) (removedView.getMeasuredWidth() + H_PADDING);

        for (int i = removedInfo + 1; i < BUFFER_SIZE; i++) {
            if (mViewInfo[i] != null) {
                mViewInfo[i].setID(mViewInfo[i].getID() - 1);
                mViewInfo[i].setLeftPosition(mViewInfo[i].getLeftPosition() - offsetX);
            }
        }

        if (removedInfo >= mCurrentInfo
                && mViewInfo[removedInfo].getID() < mDataAdapter.getTotalNumber() - 1) {
            // fill the removed info by left shift when the current one or anyone on the
            // right is removed, and there's more data on the right available.
            for (int i = removedInfo; i < BUFFER_SIZE - 1; i++) {
                mViewInfo[i] = mViewInfo[i + 1];
                if (mViewInfo[i] != null) {
                    mViewInfo[i].setTranslationX(offsetX, mScale);
                    animateViewBack(mViewInfo[i].getView());
                }
            }

            // pull data out from the DataAdapter for the last one.
            int curr = BUFFER_SIZE - 1;
            int prev = curr - 1;
            if (mViewInfo[prev] != null) {
                mViewInfo[curr] = buildInfoFromData(mViewInfo[prev].getID() + 1);
            }
        } else {
            mCenterPosition -= offsetX;
            // fill the removed place by right shift
            for (int i = removedInfo; i > 0; i--) {
                mViewInfo[i] = mViewInfo[i - 1];
                if (mViewInfo[i] != null) {
                    mViewInfo[i].setLeftPosition(mViewInfo[i].getLeftPosition() - offsetX);
                    mViewInfo[i].setTranslationX(-offsetX, mScale);
                    animateViewBack(mViewInfo[i].getView());
                }
            }

            // pull data out from the DataAdapter for the first one.
            int curr = 0;
            int next = curr + 1;
            if (mViewInfo[next] != null) {
                mViewInfo[curr] = buildInfoFromData(mViewInfo[next].getID() - 1);
            }
        }

        int transY = getHeight() / 8;
        if (removedView.getTranslationY() < 0) {
            transY = -transY;
        }
        removedView.animate()
                .alpha(0f)
                .translationYBy(transY)
                .setInterpolator(mLinearInterpolator)
                .setDuration(200)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        removeView(removedView);
                        data.recycle();
                    }
                })
                .start();
        layoutChildren();
    }

    public void setDataAdapter(DataAdapter adapter) {
        mDataAdapter = adapter;
        mDataAdapter.suggestDecodeSize(getMeasuredWidth(), getMeasuredHeight());
        mDataAdapter.setListener(new DataAdapter.Listener() {
            @Override
            public void onDataLoaded() {
                reload();
            }

            @Override
            public void onDataUpdated(DataAdapter.UpdateReporter reporter) {
                update(reporter);
            }

            @Override
            public void onDataInserted(int dataID, ImageData data) {
            }

            @Override
            public void onDataRemoved(int dataID, ImageData data) {
                int removedInfo = 0;
                for (; removedInfo < BUFFER_SIZE; removedInfo++) {
                    if (mViewInfo[removedInfo] != null
                            && mViewInfo[removedInfo].getID() == dataID) break;
                }
                if (removedInfo == BUFFER_SIZE) return;
                updateRemoval(removedInfo, data);
            }
        });
    }

    public boolean isInCameraFullscreen() {
        return (isAnchoredTo(0) && mScale == 1f
                && getCurrentType() == ImageData.TYPE_CAMERA_PREVIEW);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isInCameraFullscreen()) return false;
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        mGestureRecognizer.onTouchEvent(ev);
        return true;
    }

    private void updateViewInfo(int infoID) {
        ViewInfo info = mViewInfo[infoID];
        removeView(info.getView());
        mViewInfo[infoID] = buildInfoFromData(info.getID());
    }

    // Some of the data is changed.
    private void update(DataAdapter.UpdateReporter reporter) {
        // No data yet.
        if (mViewInfo[mCurrentInfo] == null) {
            reload();
            return;
        }

        // Check the current one.
        ViewInfo curr = mViewInfo[mCurrentInfo];
        int dataID = curr.getID();
        if (reporter.isDataRemoved(dataID)) {
            mCenterPosition = -1;
            reload();
            return;
        }
        if (reporter.isDataUpdated(dataID)) {
            updateViewInfo(mCurrentInfo);
        }

        // Check left
        for (int i = mCurrentInfo - 1; i >= 0; i--) {
            curr = mViewInfo[i];
            if (curr != null) {
                dataID = curr.getID();
                if (reporter.isDataRemoved(dataID) || reporter.isDataUpdated(dataID)) {
                    updateViewInfo(i);
                }
            } else {
                ViewInfo next = mViewInfo[i + 1];
                if (next != null) {
                    mViewInfo[i] = buildInfoFromData(next.getID() - 1);
                }
            }
        }

        // Check right
        for (int i = mCurrentInfo + 1; i < BUFFER_SIZE; i++) {
            curr = mViewInfo[i];
            if (curr != null) {
                dataID = curr.getID();
                if (reporter.isDataRemoved(dataID) || reporter.isDataUpdated(dataID)) {
                    updateViewInfo(i);
                }
            } else {
                ViewInfo prev = mViewInfo[i - 1];
                if (prev != null) {
                    mViewInfo[i] = buildInfoFromData(prev.getID() + 1);
                }
            }
        }
    }

    // The whole data might be totally different. Flush all and load from the start.
    private void reload() {
        removeAllViews();
        int dataNumber = mDataAdapter.getTotalNumber();
        if (dataNumber == 0) return;

        int currentData = 0;
        int currentLeft = 0;
        mViewInfo[mCurrentInfo] = buildInfoFromData(currentData);
        mViewInfo[mCurrentInfo].setLeftPosition(currentLeft);
        if (getCurrentType() == ImageData.TYPE_CAMERA_PREVIEW
                && currentLeft == 0) {
            // we are in camera mode by default.
            mGeometryAnimator.lockPosition(currentLeft);
        }
        for (int i = 1; mCurrentInfo + i < BUFFER_SIZE || mCurrentInfo - i >= 0; i++) {
            int infoID = mCurrentInfo + i;
            if (infoID < BUFFER_SIZE && mViewInfo[infoID - 1] != null) {
                mViewInfo[infoID] = buildInfoFromData(mViewInfo[infoID - 1].getID() + 1);
            }
            infoID = mCurrentInfo - i;
            if (infoID >= 0 && mViewInfo[infoID + 1] != null) {
                mViewInfo[infoID] = buildInfoFromData(mViewInfo[infoID + 1].getID() - 1);
            }
        }
        layoutChildren();
    }

    private void promoteData(int infoID, int dataID) {
        if (mListener != null) {
            mListener.onDataPromoted(dataID);
        }
    }

    private void demoteData(int infoID, int dataID) {
        if (mListener != null) {
            mListener.onDataDemoted(dataID);
        }
    }

    // GeometryAnimator controls all the geometry animations. It passively
    // tells the geometry information on demand.
    private class GeometryAnimator implements
            ValueAnimator.AnimatorUpdateListener,
            Animator.AnimatorListener {

        private ValueAnimator mScaleAnimator;
        private boolean mHasNewScale;
        private float mNewScale;

        private Scroller mScroller;
        private boolean mHasNewPosition;
        private DecelerateInterpolator mDecelerateInterpolator;

        private boolean mCanStopScroll;
        private boolean mCanStopScale;

        private boolean mIsPositionLocked;
        private int mLockedPosition;

        private Runnable mPostAction;

        GeometryAnimator(Context context) {
            mScroller = new Scroller(context);
            mHasNewPosition = false;
            mScaleAnimator = new ValueAnimator();
            mScaleAnimator.addUpdateListener(GeometryAnimator.this);
            mScaleAnimator.addListener(GeometryAnimator.this);
            mDecelerateInterpolator = new DecelerateInterpolator();
            mCanStopScroll = true;
            mCanStopScale = true;
            mHasNewScale = false;
        }

        boolean hasNewGeometry() {
            mHasNewPosition = mScroller.computeScrollOffset();
            if (!mHasNewPosition) {
                mCanStopScroll = true;
            }
            // If the position is locked, then we always return true to force
            // the position value to use the locked value.
            return (mHasNewPosition || mHasNewScale || mIsPositionLocked);
        }

        // Always call hasNewGeometry() before getting the new scale value.
        float getNewScale() {
            if (!mHasNewScale) return mScale;
            mHasNewScale = false;
            return mNewScale;
        }

        // Always call hasNewGeometry() before getting the new position value.
        int getNewPosition() {
            if (mIsPositionLocked) return mLockedPosition;
            if (!mHasNewPosition) return mCenterPosition;
            return mScroller.getCurrX();
        }

        void lockPosition(int pos) {
            mIsPositionLocked = true;
            mLockedPosition = pos;
        }

        void unlockPosition() {
            if (mIsPositionLocked) {
                // only when the position is previously locked we set the current
                // position to make it consistent.
                mCenterPosition = mLockedPosition;
                mIsPositionLocked = false;
            }
        }

        void fling(int velocityX, int minX, int maxX) {
            if (!stopScroll() || mIsPositionLocked) return;
            mScroller.fling(mCenterPosition, 0, velocityX, 0, minX, maxX, 0, 0);
        }

        boolean stopScroll() {
            if (!mCanStopScroll) return false;
            mScroller.forceFinished(true);
            mHasNewPosition = false;
            return true;
        }

        boolean stopScale() {
            if (!mCanStopScale) return false;
            mScaleAnimator.cancel();
            mHasNewScale = false;
            return true;
        }

        void stop() {
            stopScroll();
            stopScale();
        }

        void scrollTo(int position, int duration, boolean interruptible) {
            if (!stopScroll() || mIsPositionLocked) return;
            mCanStopScroll = interruptible;
            stopScroll();
            mScroller.startScroll(mCenterPosition, 0, position - mCenterPosition,
                    0, duration);
        }

        void scrollTo(int position, int duration) {
            scrollTo(position, duration, true);
        }

        void scaleTo(float scale, int duration, boolean interruptible) {
            if (!stopScale()) return;
            mCanStopScale = interruptible;
            mScaleAnimator.setDuration(duration);
            mScaleAnimator.setFloatValues(mScale, scale);
            mScaleAnimator.setInterpolator(mDecelerateInterpolator);
            mScaleAnimator.start();
            mHasNewScale = true;
        }

        void scaleTo(float scale, int duration) {
            scaleTo(scale, duration, true);
        }

        void setPostAction(Runnable act) {
            mPostAction = act;
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            mHasNewScale = true;
            mNewScale = (Float) animation.getAnimatedValue();
            layoutChildren();
        }

        @Override
        public void onAnimationStart(Animator anim) {
        }

        @Override
        public void onAnimationEnd(Animator anim) {
            if (mPostAction != null) {
                mPostAction.run();
                mPostAction = null;
            }
            mCanStopScale = true;
        }

        @Override
        public void onAnimationCancel(Animator anim) {
            mPostAction = null;
        }

        @Override
        public void onAnimationRepeat(Animator anim) {
        }
    }

    private class MyGestureReceiver implements FilmStripGestureRecognizer.Listener {
        // Indicating the current trend of scaling is up (>1) or down (<1).
        private float mScaleTrend;

        @Override
        public boolean onSingleTapUp(float x, float y) {
            return false;
        }

        @Override
        public boolean onDoubleTap(float x, float y) {
            return false;
        }

        @Override
        public boolean onDown(float x, float y) {
            mGeometryAnimator.stop();
            return true;
        }

        @Override
        public boolean onUp(float x, float y) {
            float halfH = getHeight() / 2;
            for (int i = 0; i < BUFFER_SIZE; i++) {
                if (mViewInfo[i] == null) continue;
                float transY = mViewInfo[i].getTranslationY(mScale);
                if (transY == 0) continue;
                int id = mViewInfo[i].getID();

                if (mDataAdapter.getImageData(id)
                        .isActionSupported(ImageData.ACTION_DEMOTE)
                        && transY > halfH) {
                    demoteData(i, id);
                } else if (mDataAdapter.getImageData(id)
                        .isActionSupported(ImageData.ACTION_PROMOTE)
                        && transY < -halfH) {
                    promoteData(i, id);
                } else {
                    mViewInfo[i].getView().animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(200)
                            .start();
                }
            }
            return false;
        }

        @Override
        public boolean onScroll(float x, float y, float dx, float dy) {
            if (Math.abs(dx) > Math.abs(dy)) {
                int deltaX = (int) (dx / mScale);
                if (deltaX > 0 && isInCameraFullscreen()) {
                    mGeometryAnimator.unlockPosition();
                    mGeometryAnimator.scaleTo(FILM_STRIP_SCALE, DURATION_GEOMETRY_ADJUST, false);
                }
                mCenterPosition += deltaX;
            } else {
                // Vertical part. Promote or demote.
                //int scaledDeltaY = (int) (dy * mScale);
                int hit = 0;
                Rect hitRect = new Rect();
                for (; hit < BUFFER_SIZE; hit++) {
                    if (mViewInfo[hit] == null) continue;
                    mViewInfo[hit].getView().getHitRect(hitRect);
                    if (hitRect.contains((int) x, (int) y)) break;
                }
                if (hit == BUFFER_SIZE) return false;

                ImageData data = mDataAdapter.getImageData(mViewInfo[hit].getID());
                float transY = mViewInfo[hit].getTranslationY(mScale) - dy / mScale;
                if (!data.isActionSupported(ImageData.ACTION_DEMOTE) && transY > 0f) {
                    transY = 0f;
                }
                if (!data.isActionSupported(ImageData.ACTION_PROMOTE) && transY < 0f) {
                    transY = 0f;
                }
                mViewInfo[hit].setTranslationY(transY, mScale);
            }

            layoutChildren();
            return true;
        }

        @Override
        public boolean onFling(float velocityX, float velocityY) {
            if (Math.abs(velocityX) > Math.abs(velocityY)) {
                float scaledVelocityX = velocityX / mScale;
                if (isInCameraFullscreen() && scaledVelocityX < 0) {
                    mGeometryAnimator.unlockPosition();
                    mGeometryAnimator.scaleTo(FILM_STRIP_SCALE, DURATION_GEOMETRY_ADJUST, false);
                }
                ViewInfo info = mViewInfo[mCurrentInfo];
                int w = getWidth();
                if (info == null) return true;
                mGeometryAnimator.fling((int) -scaledVelocityX,
                        // estimation of possible length on the left
                        info.getLeftPosition() - info.getID() * w * 2,
                        // estimation of possible length on the right
                        info.getLeftPosition()
                        + (mDataAdapter.getTotalNumber() - info.getID()) * w * 2);
                layoutChildren();
            } else {
                // ignore horizontal fling.
            }
            return true;
        }

        @Override
        public boolean onScaleBegin(float focusX, float focusY) {
            if (isInCameraFullscreen()) return false;
            mScaleTrend = 1f;
            return true;
        }

        @Override
        public boolean onScale(float focusX, float focusY, float scale) {
            if (isInCameraFullscreen()) return false;

            mScaleTrend = mScaleTrend * 0.3f + scale * 0.7f;
            mScale *= scale;
            if (mScale <= FILM_STRIP_SCALE) {
                mScale = FILM_STRIP_SCALE;
            }
            if (mScale >= MAX_SCALE) {
                mScale = MAX_SCALE;
            }
            layoutChildren();
            return true;
        }

        @Override
        public void onScaleEnd() {
            if (mScaleTrend >= 1f) {
                if (mScale != 1f) {
                    mGeometryAnimator.scaleTo(1f, DURATION_GEOMETRY_ADJUST, false);
                }

                if (getCurrentType() == ImageData.TYPE_CAMERA_PREVIEW) {
                    if (isAnchoredTo(0)) {
                        mGeometryAnimator.lockPosition(mViewInfo[mCurrentInfo].getCenterX());
                    } else {
                        mGeometryAnimator.scrollTo(
                                mViewInfo[mCurrentInfo].getCenterX(),
                                DURATION_GEOMETRY_ADJUST, false);
                        mGeometryAnimator.setPostAction(mLockPositionRunnable);
                    }
                }
            } else {
                // Scale down to film strip mode.
                if (mScale == FILM_STRIP_SCALE) {
                    mGeometryAnimator.unlockPosition();
                    return;
                }
                mGeometryAnimator.scaleTo(FILM_STRIP_SCALE, DURATION_GEOMETRY_ADJUST, false);
                mGeometryAnimator.setPostAction(mUnlockPositionRunnable);
            }
        }

        private Runnable mLockPositionRunnable = new Runnable() {
            @Override
            public void run() {
                mGeometryAnimator.lockPosition(mViewInfo[mCurrentInfo].getCenterX());
            }
        };

        private Runnable mUnlockPositionRunnable = new Runnable() {
            @Override
            public void run() {
                mGeometryAnimator.unlockPosition();
            }
        };
    }
}
