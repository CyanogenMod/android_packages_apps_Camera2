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
import android.animation.TimeInterpolator;
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
    // Duration to go back to the first.
    private static final int DURATION_BACK_ANIM = 500;
    private static final int DURATION_SCROLL_TO_FILMSTRIP = 350;
    private static final int DURATION_GEOMETRY_ADJUST = 200;
    private static final float FILM_STRIP_SCALE = 0.6f;
    private static final float MAX_SCALE = 1f;

    private Context mContext;
    private FilmStripGestureRecognizer mGestureRecognizer;
    private DataAdapter mDataAdapter;
    private int mViewGap;
    private final Rect mDrawArea = new Rect();

    private final int mCurrentInfo = (BUFFER_SIZE - 1) / 2;
    private float mScale;
    private MyController mController;
    private int mCenterX = -1;
    private ViewInfo[] mViewInfo = new ViewInfo[BUFFER_SIZE];

    private Listener mListener;

    private View mCameraView;
    private ImageData mCameraData;

    private TimeInterpolator mViewAnimInterpolator;

    // This is used to resolve the misalignment problem when the device
    // orientation is changed. If the current item is in fullscreen, it might
    // be shifted because mCenterX is not adjusted with the orientation.
    // Set this to true when onSizeChanged is called to make sure we adjust
    // mCenterX accordingly.
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

    public interface Controller {
        public boolean isScalling();

        public void fling(float velocity);
        public void scrollTo(int position, int duration, boolean interruptible);
        public boolean stopScrolling();
        public boolean isScrolling();

        public void lockAtCurrentView();
        public void unlockPosition();

        public void gotoCameraFullScreen();
        public void gotoFilmStrip();
        public void gotoFullScreen();
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

        public void translateXBy(float transX, float scale) {
            mView.setTranslationX(mView.getTranslationX() + transX * scale);
        }

        public int getCenterX() {
            return mLeftPosition + mView.getWidth() / 2;
        }

        public int getMeasuredCenterX(float scale) {
            return mLeftPosition + (int) (mView.getMeasuredWidth() * scale / 2);
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
        // This is for positioning camera controller at the same place in
        // different orientations.
        setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        setWillNotDraw(false);
        mContext = context;
        mScale = 1.0f;
        mController = new MyController(context);
        mViewAnimInterpolator = new LinearInterpolator();
        mGestureRecognizer =
                new FilmStripGestureRecognizer(context, new MyGestureReceiver());
    }

    public Controller getController() {
        return mController;
    }

    public void setListener(Listener l) {
        mListener = l;
    }

    public void setViewGap(int viewGap) {
        mViewGap = viewGap;
    }

    public float getScale() {
        return mScale;
    }

    public boolean isAnchoredTo(int id) {
        if (mViewInfo[mCurrentInfo].getID() == id
                && mViewInfo[mCurrentInfo].getCenterX() == mCenterX) {
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
        if (mController.hasNewGeometry()) {
            layoutChildren();
        }
    }

    // returns [width, height] preserving image aspect ratio
    private int[] calculateChildDimension(
            int imageWidth, int imageHeight,
            int boundWidth, int boundHeight) {

        if (imageWidth == ImageData.SIZE_FULL
                || imageHeight == ImageData.SIZE_FULL) {
            imageWidth = boundWidth;
            imageHeight = boundHeight;
        }

        int[] ret = new int[2];
        ret[0] = boundWidth;
        ret[1] = boundHeight;

        if (imageWidth * ret[1] > ret[0] * imageHeight) {
            ret[1] = imageHeight * ret[0] / imageWidth;
        } else {
            ret[0] = imageWidth * ret[1] / imageHeight;
        }

        return ret;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int boundWidth = MeasureSpec.getSize(widthMeasureSpec);
        int boundHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (mDataAdapter != null) {
            mDataAdapter.suggestDecodeSize(boundWidth / 2, boundHeight / 2);
        }

        for (int i = 0; i < mViewInfo.length; i++) {
            ViewInfo info = mViewInfo[i];
            if (mViewInfo[i] == null) continue;

            int id = info.getID();
            int[] dim = calculateChildDimension(
                    mDataAdapter.getImageData(id).getWidth(),
                    mDataAdapter.getImageData(id).getHeight(),
                    boundWidth, boundHeight);

            mViewInfo[i].getView().measure(
                    View.MeasureSpec.makeMeasureSpec(
                            dim[0], View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(
                            dim[1], View.MeasureSpec.EXACTLY));
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
        v = info.getView();
        if (v != mCameraView) {
            addView(info.getView());
        } else {
            v.setVisibility(View.VISIBLE);
        }
        return info;
    }

    private void removeInfo(int infoID) {
        if (infoID >= mViewInfo.length || mViewInfo[infoID] == null) return;

        ImageData data = mDataAdapter.getImageData(mViewInfo[infoID].getID());
        checkForRemoval(data, mViewInfo[infoID].getView());
        mViewInfo[infoID] = null;
    }

    // We try to keep the one closest to the center of the screen at position mCurrentInfo.
    private void stepIfNeeded() {
        int nearest = findTheNearestView(mCenterX);
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
    private void adjustCenterX() {
        ViewInfo curr = mViewInfo[mCurrentInfo];
        if (curr == null) return;

        if (curr.getID() == 0 && mCenterX < curr.getCenterX()) {
            mCenterX = curr.getCenterX();
            if (mController.isScrolling()) {
                mController.stopScrolling();
            }
            if (getCurrentType() == ImageData.TYPE_CAMERA_PREVIEW
                    && !mController.isScalling()
                    && mScale != MAX_SCALE) {
                mController.scaleTo(MAX_SCALE, DURATION_GEOMETRY_ADJUST);
            }
        }
        if (curr.getID() == mDataAdapter.getTotalNumber() - 1
                && mCenterX > curr.getCenterX()) {
            mCenterX = curr.getCenterX();
            if (!mController.isScrolling()) {
                mController.stopScrolling();
            }
        }
    }

    private void layoutChildren() {
        if (mAnchorPending) {
            mCenterX = mViewInfo[mCurrentInfo].getCenterX();
            mAnchorPending = false;
        }

        if (mController.hasNewGeometry()) {
            mCenterX = mController.getNewPosition();
            mScale = mController.getNewScale();
        }

        adjustCenterX();

        mViewInfo[mCurrentInfo].layoutIn(mDrawArea, mCenterX, mScale);

        // images on the left
        for (int infoID = mCurrentInfo - 1; infoID >= 0; infoID--) {
            ViewInfo curr = mViewInfo[infoID];
            if (curr != null) {
                ViewInfo next = mViewInfo[infoID + 1];
                curr.setLeftPosition(
                        next.getLeftPosition() - curr.getView().getMeasuredWidth() - mViewGap);
                curr.layoutIn(mDrawArea, mCenterX, mScale);
            }
        }

        // images on the right
        for (int infoID = mCurrentInfo + 1; infoID < BUFFER_SIZE; infoID++) {
            ViewInfo curr = mViewInfo[infoID];
            if (curr != null) {
                ViewInfo prev = mViewInfo[infoID - 1];
                curr.setLeftPosition(
                        prev.getLeftPosition() + prev.getView().getMeasuredWidth() + mViewGap);
                curr.layoutIn(mDrawArea, mCenterX, mScale);
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

    // Keeps the view in the view hierarchy if it's camera preview.
    // Remove from the hierarchy otherwise.
    private void checkForRemoval(ImageData data, View v) {
        if (data.getType() != ImageData.TYPE_CAMERA_PREVIEW) {
            removeView(v);
            data.recycle();
        } else {
            v.setVisibility(View.INVISIBLE);
            if (mCameraView != null && mCameraView != v) {
                removeView(mCameraView);
                mCameraData = null;
            }
            mCameraView = v;
            mCameraData = data;
        }
    }

    private void slideViewBack(View v) {
        v.animate()
                .translationX(0)
                .alpha(1f)
                .setDuration(DURATION_GEOMETRY_ADJUST)
                .setInterpolator(mViewAnimInterpolator)
                .start();
    }

    private void updateRemoval(int removedInfo, final ImageData data) {
        final View removedView = mViewInfo[removedInfo].getView();
        final int offsetX = (int) (removedView.getMeasuredWidth() + mViewGap);

        for (int i = removedInfo + 1; i < BUFFER_SIZE; i++) {
            if (mViewInfo[i] != null) {
                mViewInfo[i].setID(mViewInfo[i].getID() - 1);
                mViewInfo[i].setLeftPosition(mViewInfo[i].getLeftPosition() - offsetX);
            }
        }

        if (removedInfo >= mCurrentInfo
                && mViewInfo[removedInfo].getID() < mDataAdapter.getTotalNumber()) {
            // fill the removed info by left shift when the current one or anyone on the
            // right is removed, and there's more data on the right available.
            for (int i = removedInfo; i < BUFFER_SIZE - 1; i++) {
                mViewInfo[i] = mViewInfo[i + 1];
            }

            // pull data out from the DataAdapter for the last one.
            int curr = BUFFER_SIZE - 1;
            int prev = curr - 1;
            if (mViewInfo[prev] != null) {
                mViewInfo[curr] = buildInfoFromData(mViewInfo[prev].getID() + 1);
            }

            // Translate the views to their original places.
            for (int i = removedInfo; i < BUFFER_SIZE; i++) {
                if (mViewInfo[i] != null) {
                    mViewInfo[i].setTranslationX(offsetX, mScale);
                }
            }

            // The end of the filmstrip might have been changed.
            // The mCenterX might be out of the bound.
            ViewInfo currInfo = mViewInfo[mCurrentInfo];
            if (currInfo.getID() == mDataAdapter.getTotalNumber() - 1
                    && mCenterX > currInfo.getCenterX()) {
                int adjustDiff = currInfo.getCenterX() - mCenterX;
                mCenterX = currInfo.getCenterX();
                for (int i = 0; i < BUFFER_SIZE; i++) {
                    if (mViewInfo[i] != null) {
                        mViewInfo[i].translateXBy(adjustDiff, mScale);
                    }
                }
            }
        } else {
            // fill the removed place by right shift
            mCenterX -= offsetX;

            for (int i = removedInfo; i > 0; i--) {
                mViewInfo[i] = mViewInfo[i - 1];
            }

            // pull data out from the DataAdapter for the first one.
            int curr = 0;
            int next = curr + 1;
            if (mViewInfo[next] != null) {
                mViewInfo[curr] = buildInfoFromData(mViewInfo[next].getID() - 1);
            }

            // Translate the views to their original places.
            for (int i = removedInfo; i >= 0; i--) {
                if (mViewInfo[i] != null) {
                    mViewInfo[i].setTranslationX(-offsetX, mScale);
                }
            }
        }

        // Now, slide every one back.
        for (int i = 0; i < BUFFER_SIZE; i++) {
            if (mViewInfo[i] != null
                    && mViewInfo[i].getTranslationX(mScale) != 0f) {
                slideViewBack(mViewInfo[i].getView());
            }
        }

        int transY = getHeight() / 8;
        if (removedView.getTranslationY() < 0) {
            transY = -transY;
        }
        removedView.animate()
                .alpha(0f)
                .translationYBy(transY)
                .setInterpolator(mViewAnimInterpolator)
                .setDuration(DURATION_GEOMETRY_ADJUST)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        checkForRemoval(data, removedView);
                    }
                })
                .start();
        layoutChildren();
    }

    // returns -1 on failure.
    private int findInfoByDataID(int dataID) {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            if (mViewInfo[i] != null
                    && mViewInfo[i].getID() == dataID) return i;
        }
        return -1;
    }

    private void updateInsertion(int dataID) {
        int insertedInfo = findInfoByDataID(dataID);
        if (insertedInfo  == -1) {
            // Not in the current info buffers. Check if it's inserted
            // at the end.
            if (dataID == mDataAdapter.getTotalNumber() - 1) {
                int prev = findInfoByDataID(dataID - 1);
                if (prev >= 0 && prev < BUFFER_SIZE - 1) {
                    // The previous data is in the buffer and we still
                    // have room for the inserted data.
                    insertedInfo = prev + 1;
                }
            }
        }

        // adjust the data id to be consistent
        for (int i = 0; i < BUFFER_SIZE; i++) {
            if (mViewInfo[i] == null || mViewInfo[i].getID() < dataID) continue;
            mViewInfo[i].setID(mViewInfo[i].getID() + 1);
        }
        if (insertedInfo == -1) return;

        final ImageData data = mDataAdapter.getImageData(dataID);
        int[] dim = calculateChildDimension(
                data.getWidth(), data.getHeight(),
                getMeasuredWidth(), getMeasuredHeight());
        final int offsetX = dim[0] + mViewGap;
        ViewInfo viewInfo = buildInfoFromData(dataID);

        if (insertedInfo >= mCurrentInfo) {
            if (insertedInfo == mCurrentInfo) {
                viewInfo.setLeftPosition(mViewInfo[mCurrentInfo].getLeftPosition());
            }
            // Shift right to make rooms for newly inserted item.
            removeInfo(BUFFER_SIZE - 1);
            for (int i = BUFFER_SIZE - 1; i > insertedInfo; i--) {
                mViewInfo[i] = mViewInfo[i - 1];
                if (mViewInfo[i] != null) {
                    mViewInfo[i].setTranslationX(-offsetX, mScale);
                    slideViewBack(mViewInfo[i].getView());
                }
            }
        } else {
            // Shift left. Put the inserted data on the left instead of the found position.
            --insertedInfo;
            if (insertedInfo < 0) return;
            removeInfo(0);
            for (int i = 1; i <= insertedInfo; i++) {
                if (mViewInfo[i] != null) {
                    mViewInfo[i].setTranslationX(offsetX, mScale);
                    slideViewBack(mViewInfo[i].getView());
                    mViewInfo[i - 1] = mViewInfo[i];
                }
            }
        }

        mViewInfo[insertedInfo] = viewInfo;
        View insertedView = mViewInfo[insertedInfo].getView();
        insertedView.setAlpha(0f);
        insertedView.setTranslationY(getHeight() / 8);
        insertedView.animate()
                .alpha(1f)
                .translationY(0f)
                .setInterpolator(mViewAnimInterpolator)
                .setDuration(DURATION_GEOMETRY_ADJUST)
                .start();
        invalidate();
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
                if (mViewInfo[mCurrentInfo] == null) {
                    // empty now, simply do a reload.
                    reload();
                    return;
                }
                updateInsertion(dataID);
            }

            @Override
            public void onDataRemoved(int dataID, ImageData data) {
                int info = findInfoByDataID(dataID);
                if (info == -1) return;
                updateRemoval(info, data);
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
            mCenterX = -1;
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

        mViewInfo[mCurrentInfo] = buildInfoFromData(0);
        mViewInfo[mCurrentInfo].setLeftPosition(0);
        if (getCurrentType() == ImageData.TYPE_CAMERA_PREVIEW) {
            // we are in camera mode by default.
            mController.lockAtCurrentView();
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

    // MyController controls all the geometry animations. It passively
    // tells the geometry information on demand.
    private class MyController implements
            Controller,
            ValueAnimator.AnimatorUpdateListener,
            Animator.AnimatorListener {

        private ValueAnimator mScaleAnimator;
        private boolean mHasNewScale;
        private float mNewScale;

        private Scroller mScroller;
        private boolean mHasNewPosition;
        private DecelerateInterpolator mDecelerateInterpolator;

        private boolean mCanStopScroll;

        private boolean mIsPositionLocked;
        private int mLockedViewInfo;

        MyController(Context context) {
            mScroller = new Scroller(context);
            mHasNewPosition = false;
            mScaleAnimator = new ValueAnimator();
            mScaleAnimator.addUpdateListener(MyController.this);
            mScaleAnimator.addListener(MyController.this);
            mDecelerateInterpolator = new DecelerateInterpolator();
            mCanStopScroll = true;
            mHasNewScale = false;
        }

        @Override
        public boolean isScrolling() {
            return !mScroller.isFinished();
        }

        @Override
        public boolean isScalling() {
            return mScaleAnimator.isRunning();
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
            if (mIsPositionLocked) {
                if (mViewInfo[mLockedViewInfo] == null) return mCenterX;
                return mViewInfo[mLockedViewInfo].getCenterX();
            }
            if (!mHasNewPosition) return mCenterX;
            return mScroller.getCurrX();
        }

        @Override
        public void lockAtCurrentView() {
            mIsPositionLocked = true;
            mLockedViewInfo = mCurrentInfo;
        }

        @Override
        public void unlockPosition() {
            if (mIsPositionLocked) {
                // only when the position is previously locked we set the current
                // position to make it consistent.
                if (mViewInfo[mLockedViewInfo] != null) {
                    mCenterX = mViewInfo[mLockedViewInfo].getCenterX();
                }
                mIsPositionLocked = false;
            }
        }

        private int estimateMinX(int dataID, int leftPos, int viewWidth) {
            return (leftPos - (dataID + 100) * (viewWidth + mViewGap));
        }

        private int estimateMaxX(int dataID, int leftPos, int viewWidth) {
            return (leftPos
                    + (mDataAdapter.getTotalNumber() - dataID + 100)
                    * (viewWidth + mViewGap));
        }

        @Override
        public void fling(float velocityX) {
            if (!stopScrolling() || mIsPositionLocked) return;
            ViewInfo info = mViewInfo[mCurrentInfo];
            if (info == null) return;

            float scaledVelocityX = velocityX / mScale;
            if (isInCameraFullscreen() && scaledVelocityX < 0) {
                gotoFilmStrip();
            }

            int w = getWidth();
            // Estimation of possible length on the left. To ensure the
            // velocity doesn't become too slow eventually, we add a huge number
            // to the estimated maximum.
            int minX = estimateMinX(info.getID(), info.getLeftPosition(), w);
            // Estimation of possible length on the right. Likewise, exaggerate
            // the possible maximum too.
            int maxX = estimateMaxX(info.getID(), info.getLeftPosition(), w);
            mScroller.fling(mCenterX, 0, (int) -velocityX, 0, minX, maxX, 0, 0);

            layoutChildren();
        }

        @Override
        public boolean stopScrolling() {
            if (!mCanStopScroll) return false;
            mScroller.forceFinished(true);
            mHasNewPosition = false;
            return true;
        }

        private void stopScale() {
            mScaleAnimator.cancel();
            mHasNewScale = false;
        }

        @Override
        public void scrollTo(int position, int duration, boolean interruptible) {
            if (!stopScrolling() || mIsPositionLocked) return;
            mCanStopScroll = interruptible;
            stopScrolling();
            mScroller.startScroll(mCenterX, 0, position - mCenterX,
                    0, duration);
        }

        void scrollTo(int position, int duration) {
            scrollTo(position, duration, true);
        }

        private void scaleTo(float scale, int duration) {
            stopScale();
            mScaleAnimator.setDuration(duration);
            mScaleAnimator.setFloatValues(mScale, scale);
            mScaleAnimator.setInterpolator(mDecelerateInterpolator);
            mScaleAnimator.start();
            mHasNewScale = true;
        }

        @Override
        public void gotoFilmStrip() {
            unlockPosition();
            scaleTo(FILM_STRIP_SCALE, DURATION_GEOMETRY_ADJUST);
        }

        @Override
        public void gotoFullScreen() {
            scaleTo(1f, DURATION_GEOMETRY_ADJUST);
        }

        @Override
        public void gotoCameraFullScreen() {
            if (mDataAdapter.getImageData(0).getType()
                    != ImageData.TYPE_CAMERA_PREVIEW) {
                return;
            }
            gotoFullScreen();
            scrollTo(
                    estimateMinX(mViewInfo[mCurrentInfo].getID(),
                        mViewInfo[mCurrentInfo].getLeftPosition(),
                        getWidth()),
                    DURATION_GEOMETRY_ADJUST, false);
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
            ViewInfo info = mViewInfo[mCurrentInfo];
            if (info != null && mCenterX == info.getCenterX()) {
                if (mScale == 1f) {
                    lockAtCurrentView();
                } else if (mScale == FILM_STRIP_SCALE) {
                    unlockPosition();
                }
            }
        }

        @Override
        public void onAnimationCancel(Animator anim) {
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
            mController.stopScrolling();
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
                    // put the view back.
                    mViewInfo[i].getView().animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(DURATION_GEOMETRY_ADJUST)
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
                    mController.gotoFilmStrip();
                }
                mCenterX += deltaX;
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
                mController.fling(velocityX);
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
                    mController.gotoFullScreen();
                }

                if (getCurrentType() == ImageData.TYPE_CAMERA_PREVIEW) {
                    if (isAnchoredTo(0)) {
                        mController.lockAtCurrentView();
                    } else {
                        mController.scrollTo(
                                mViewInfo[mCurrentInfo].getCenterX(),
                                DURATION_GEOMETRY_ADJUST, false);
                    }
                }
            } else {
                // Scale down to film strip mode.
                if (mScale == FILM_STRIP_SCALE) {
                    mController.unlockPosition();
                } else {
                    mController.gotoFilmStrip();
                }
            }
        }
    }
}
