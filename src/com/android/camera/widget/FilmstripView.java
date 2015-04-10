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

package com.android.camera.widget;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

import com.android.camera.CameraActivity;
import com.android.camera.data.FilmstripItem;
import com.android.camera.data.FilmstripItem.VideoClickedCallback;
import com.android.camera.debug.Log;
import com.android.camera.filmstrip.FilmstripController;
import com.android.camera.filmstrip.FilmstripDataAdapter;
import com.android.camera.ui.FilmstripGestureRecognizer;
import com.android.camera.ui.ZoomView;
import com.android.camera.util.CameraUtil;
import com.android.camera2.R;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

public class FilmstripView extends ViewGroup {
    /**
     * An action callback to be used for actions on the local media data items.
     */
    public static class PlayVideoIntent implements VideoClickedCallback {
        private final WeakReference<CameraActivity> mActivity;

        /**
         * The given activity is used to start intents. It is wrapped in a weak
         * reference to prevent leaks.
         */
        public PlayVideoIntent(CameraActivity activity) {
            mActivity = new WeakReference<CameraActivity>(activity);
        }

        /**
         * Fires an intent to play the video with the given URI and title.
         */
        @Override
        public void playVideo(Uri uri, String title) {
            CameraActivity activity = mActivity.get();
            if (activity != null) {
              CameraUtil.playVideo(activity, uri, title);
            }
        }
    }


    private static final Log.Tag TAG = new Log.Tag("FilmstripView");

    private static final int BUFFER_SIZE = 5;
    private static final int BUFFER_CENTER = (BUFFER_SIZE - 1) / 2;
    private static final int GEOMETRY_ADJUST_TIME_MS = 400;
    private static final int SNAP_IN_CENTER_TIME_MS = 600;
    private static final float FLING_COASTING_DURATION_S = 0.05f;
    private static final int ZOOM_ANIMATION_DURATION_MS = 200;
    private static final int CAMERA_PREVIEW_SWIPE_THRESHOLD = 300;
    private static final float FILM_STRIP_SCALE = 0.7f;
    private static final float FULL_SCREEN_SCALE = 1f;

    // The min velocity at which the user must have moved their finger in
    // pixels per millisecond to count a vertical gesture as a promote/demote
    // at short vertical distances.
    private static final float PROMOTE_VELOCITY = 3.5f;
    // The min distance relative to this view's height the user must have
    // moved their finger to count a vertical gesture as a promote/demote if
    // they moved their finger at least at PROMOTE_VELOCITY.
    private static final float VELOCITY_PROMOTE_HEIGHT_RATIO = 1/10f;
    // The min distance relative to this view's height the user must have
    // moved their finger to count a vertical gesture as a promote/demote if
    // they moved their finger at less than PROMOTE_VELOCITY.
    private static final float PROMOTE_HEIGHT_RATIO = 1/2f;

    private static final float TOLERANCE = 0.1f;
    // Only check for intercepting touch events within first 500ms
    private static final int SWIPE_TIME_OUT = 500;
    private static final int DECELERATION_FACTOR = 4;
    private static final float MOUSE_SCROLL_FACTOR = 128f;

    private CameraActivity mActivity;
    private VideoClickedCallback mVideoClickedCallback;
    private FilmstripGestureRecognizer mGestureRecognizer;
    private FilmstripGestureRecognizer.Listener mGestureListener;
    private FilmstripDataAdapter mDataAdapter;
    private int mViewGapInPixel;
    private final Rect mDrawArea = new Rect();

    private float mScale;
    private FilmstripControllerImpl mController;
    private int mCenterX = -1;
    private final ViewItem[] mViewItems = new ViewItem[BUFFER_SIZE];

    private FilmstripController.FilmstripListener mListener;
    private ZoomView mZoomView = null;

    private MotionEvent mDown;
    private boolean mCheckToIntercept = true;
    private int mSlop;
    private TimeInterpolator mViewAnimInterpolator;

    // This is true if and only if the user is scrolling,
    private boolean mIsUserScrolling;
    private int mAdapterIndexUserIsScrollingOver;
    private float mOverScaleFactor = 1f;

    private boolean mFullScreenUIHidden = false;
    private final SparseArray<Queue<View>> recycledViews = new SparseArray<>();

    /**
     * A helper class to tract and calculate the view coordination.
     */
    private static class ViewItem {
        private static enum RenderSize {
            TINY,
            THUMBNAIL,
            FULL_RES
        }

        private final FilmstripView mFilmstrip;
        private final View mView;
        private final RectF mViewArea;

        private int mIndex;
        /** The position of the left of the view in the whole filmstrip. */
        private int mLeftPosition;
        private FilmstripItem mData;
        private RenderSize mRenderSize;

        private ValueAnimator mTranslationXAnimator;
        private ValueAnimator mTranslationYAnimator;
        private ValueAnimator mAlphaAnimator;

        private boolean mLockAtFullOpacity;

        /**
         * Constructor.
         *
         * @param index The index of the data from
         *            {@link com.android.camera.filmstrip.FilmstripDataAdapter}.
         * @param v The {@code View} representing the data.
         */
        public ViewItem(int index, View v, FilmstripItem data, FilmstripView filmstrip) {
            mFilmstrip = filmstrip;
            mView = v;
            mViewArea = new RectF();

            mIndex = index;
            mData = data;
            mLeftPosition = -1;
            mRenderSize = RenderSize.TINY;
            mLockAtFullOpacity = false;

            mView.setPivotX(0f);
            mView.setPivotY(0f);
        }

        public FilmstripItem getData() {
            return mData;
        }

        public void setData(FilmstripItem item) {
            mData = item;

            renderTiny();
        }

        public void renderTiny() {
            if (mRenderSize != RenderSize.TINY) {
                mRenderSize = RenderSize.TINY;

                Log.i(TAG, "[ViewItem:" + mIndex + "] mData.renderTiny()");
                mData.renderTiny(mView);
            }
        }

        public void renderThumbnail() {
            if (mRenderSize != RenderSize.THUMBNAIL) {
                mRenderSize = RenderSize.THUMBNAIL;

                Log.i(TAG, "[ViewItem:" + mIndex + "] mData.renderThumbnail()");
                mData.renderThumbnail(mView);
            }
        }

        public void renderFullRes() {
            if (mRenderSize != RenderSize.FULL_RES) {
                mRenderSize = RenderSize.FULL_RES;

                Log.i(TAG, "[ViewItem:" + mIndex + "] mData.renderFullRes()");
                mData.renderFullRes(mView);
            }
        }

        public void lockAtFullOpacity() {
            if (!mLockAtFullOpacity) {
                mLockAtFullOpacity = true;
                mView.setAlpha(1.0f);
            }
        }

        public void unlockOpacity() {
            mLockAtFullOpacity = false;
        }

        /**
         * Returns the index from
         * {@link com.android.camera.filmstrip.FilmstripDataAdapter}.
         */
        public int getAdapterIndex() {
            return mIndex;
        }

        /**
         * Sets the index used in the
         * {@link com.android.camera.filmstrip.FilmstripDataAdapter}.
         */
        public void setIndex(int index) {
            mIndex = index;
        }

        /** Sets the left position of the view in the whole filmstrip. */
        public void setLeftPosition(int pos) {
            mLeftPosition = pos;
        }

        /** Returns the left position of the view in the whole filmstrip. */
        public int getLeftPosition() {
            return mLeftPosition;
        }

        /** Returns the translation of Y regarding the view scale. */
        public float getTranslationY() {
            return mView.getTranslationY() / mFilmstrip.mScale;
        }

        /** Returns the translation of X regarding the view scale. */
        public float getTranslationX() {
            return mView.getTranslationX() / mFilmstrip.mScale;
        }

        /** Sets the translation of Y regarding the view scale. */
        public void setTranslationY(float transY) {
            mView.setTranslationY(transY * mFilmstrip.mScale);
        }

        /** Sets the translation of X regarding the view scale. */
        public void setTranslationX(float transX) {
            mView.setTranslationX(transX * mFilmstrip.mScale);
        }

        /** Forwarding of {@link android.view.View#setAlpha(float)}. */
        public void setAlpha(float alpha) {
            if (!mLockAtFullOpacity) {
                mView.setAlpha(alpha);
            }
        }

        /** Forwarding of {@link android.view.View#getAlpha()}. */
        public float getAlpha() {
            return mView.getAlpha();
        }

        /** Forwarding of {@link android.view.View#getMeasuredWidth()}. */
        public int getMeasuredWidth() {
            return mView.getMeasuredWidth();
        }

        /**
         * Animates the X translation of the view. Note: the animated value is
         * not set directly by {@link android.view.View#setTranslationX(float)}
         * because the value might be changed during in {@code onLayout()}.
         * The animated value of X translation is specially handled in {@code
         * layoutIn()}.
         *
         * @param targetX The final value.
         * @param duration_ms The duration of the animation.
         * @param interpolator Time interpolator.
         */
        public void animateTranslationX(
                float targetX, long duration_ms, TimeInterpolator interpolator) {
            if (mTranslationXAnimator == null) {
                mTranslationXAnimator = new ValueAnimator();
                mTranslationXAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        // We invalidate the filmstrip view instead of setting the
                        // translation X because the translation X of the view is
                        // touched in onLayout(). See the documentation of
                        // animateTranslationX().
                        mFilmstrip.invalidate();
                    }
                });
            }
            runAnimation(mTranslationXAnimator, getTranslationX(), targetX, duration_ms,
                    interpolator);
        }

        /**
         * Animates the Y translation of the view.
         *
         * @param targetY The final value.
         * @param duration_ms The duration of the animation.
         * @param interpolator Time interpolator.
         */
        public void animateTranslationY(
                float targetY, long duration_ms, TimeInterpolator interpolator) {
            if (mTranslationYAnimator == null) {
                mTranslationYAnimator = new ValueAnimator();
                mTranslationYAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        setTranslationY((Float) valueAnimator.getAnimatedValue());
                    }
                });
            }
            runAnimation(mTranslationYAnimator, getTranslationY(), targetY, duration_ms,
                    interpolator);
        }

        /**
         * Animates the alpha value of the view.
         *
         * @param targetAlpha The final value.
         * @param duration_ms The duration of the animation.
         * @param interpolator Time interpolator.
         */
        public void animateAlpha(float targetAlpha, long duration_ms,
                TimeInterpolator interpolator) {
            if (mAlphaAnimator == null) {
                mAlphaAnimator = new ValueAnimator();
                mAlphaAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        ViewItem.this.setAlpha((Float) valueAnimator.getAnimatedValue());
                    }
                });
            }
            runAnimation(mAlphaAnimator, getAlpha(), targetAlpha, duration_ms, interpolator);
        }

        private void runAnimation(final ValueAnimator animator, final float startValue,
                final float targetValue, final long duration_ms,
                final TimeInterpolator interpolator) {
            if (startValue == targetValue) {
                return;
            }
            animator.setInterpolator(interpolator);
            animator.setDuration(duration_ms);
            animator.setFloatValues(startValue, targetValue);
            animator.start();
        }

        /** Adjusts the translation of X regarding the view scale. */
        public void translateXScaledBy(float transX) {
            setTranslationX(getTranslationX() + transX * mFilmstrip.mScale);
        }

        /**
         * Forwarding of {@link android.view.View#getHitRect(android.graphics.Rect)}.
         */
        public void getHitRect(Rect rect) {
            mView.getHitRect(rect);
        }

        public int getCenterX() {
            return mLeftPosition + mView.getMeasuredWidth() / 2;
        }

        /** Forwarding of {@link android.view.View#getVisibility()}. */
        public int getVisibility() {
            return mView.getVisibility();
        }

        /** Forwarding of {@link android.view.View#setVisibility(int)}. */
        public void setVisibility(int visibility) {
            mView.setVisibility(visibility);
        }

        /**
         * Adds the view of the data to the view hierarchy if necessary.
         */
        public void addViewToHierarchy() {
            if (mFilmstrip.indexOfChild(mView) < 0) {
                mFilmstrip.addView(mView);
            }

            // all new views added should not display until layout positions
            // them and sets them visible
            setVisibility(View.INVISIBLE);
            setAlpha(1f);
            setTranslationX(0);
            setTranslationY(0);
        }

        /**
         * Removes from the hierarchy.
         */
        public void removeViewFromHierarchy() {
            mFilmstrip.removeView(mView);
            mData.recycle(mView);
            mFilmstrip.recycleView(mView, mIndex);
        }

        /**
         * Brings the view to front by
         * {@link #bringChildToFront(android.view.View)}
         */
        public void bringViewToFront() {
            mFilmstrip.bringChildToFront(mView);
        }

        /**
         * The visual x position of this view, in pixels.
         */
        public float getX() {
            return mView.getX();
        }

        /**
         * The visual y position of this view, in pixels.
         */
        public float getY() {
            return mView.getY();
        }

        /**
         * Forwarding of {@link android.view.View#measure(int, int)}.
         */
        public void measure(int widthSpec, int heightSpec) {
            mView.measure(widthSpec, heightSpec);
        }

        private void layoutAt(int left, int top) {
            mView.layout(left, top, left + mView.getMeasuredWidth(),
                    top + mView.getMeasuredHeight());
        }

        /**
         * The bounding rect of the view.
         */
        public RectF getViewRect() {
            RectF r = new RectF();
            r.left = mView.getX();
            r.top = mView.getY();
            r.right = r.left + mView.getWidth() * mView.getScaleX();
            r.bottom = r.top + mView.getHeight() * mView.getScaleY();
            return r;
        }

        private View getView() {
            return mView;
        }

        /**
         * Layouts the view in the area assuming the center of the area is at a
         * specific point of the whole filmstrip.
         *
         * @param drawArea The area when filmstrip will show in.
         * @param refCenter The absolute X coordination in the whole filmstrip
         *            of the center of {@code drawArea}.
         * @param scale The scale of the view on the filmstrip.
         */
        public void layoutWithTranslationX(Rect drawArea, int refCenter, float scale) {
            final float translationX =
                    ((mTranslationXAnimator != null && mTranslationXAnimator.isRunning()) ?
                            (Float) mTranslationXAnimator.getAnimatedValue() : 0);
            int left =
                    (int) (drawArea.centerX() + (mLeftPosition - refCenter + translationX) * scale);
            int top = (int) (drawArea.centerY() - (mView.getMeasuredHeight() / 2) * scale);
            layoutAt(left, top);
            mView.setScaleX(scale);
            mView.setScaleY(scale);

            // update mViewArea for touch detection.
            int l = mView.getLeft();
            int t = mView.getTop();
            mViewArea.set(l, t,
                    l + mView.getMeasuredWidth() * scale,
                    t + mView.getMeasuredHeight() * scale);
        }

        /** Returns true if the point is in the view. */
        public boolean areaContains(float x, float y) {
            return mViewArea.contains(x, y);
        }

        /**
         * Return the width of the view.
         */
        public int getWidth() {
            return mView.getWidth();
        }

        /**
         * Returns the position of the left edge of the view area content is drawn in.
         */
        public int getDrawAreaLeft() {
            return Math.round(mViewArea.left);
        }

        /**
         * Apply a scale factor (i.e. {@code postScale}) on top of current scale at
         * pivot point ({@code focusX}, {@code focusY}). Visually it should be the
         * same as post concatenating current view's matrix with specified scale.
         */
        void postScale(float focusX, float focusY, float postScale, int viewportWidth,
                int viewportHeight) {
            float transX = mView.getTranslationX();
            float transY = mView.getTranslationY();
            // Pivot point is top left of the view, so we need to translate
            // to scale around focus point
            transX -= (focusX - getX()) * (postScale - 1f);
            transY -= (focusY - getY()) * (postScale - 1f);
            float scaleX = mView.getScaleX() * postScale;
            float scaleY = mView.getScaleY() * postScale;
            updateTransform(transX, transY, scaleX, scaleY, viewportWidth,
                    viewportHeight);
        }

        void updateTransform(float transX, float transY, float scaleX, float scaleY,
                int viewportWidth, int viewportHeight) {
            float left = transX + mView.getLeft();
            float top = transY + mView.getTop();
            RectF r = ZoomView.adjustToFitInBounds(new RectF(left, top,
                    left + mView.getWidth() * scaleX,
                    top + mView.getHeight() * scaleY),
                    viewportWidth, viewportHeight);
            mView.setScaleX(scaleX);
            mView.setScaleY(scaleY);
            transX = r.left - mView.getLeft();
            transY = r.top - mView.getTop();
            mView.setTranslationX(transX);
            mView.setTranslationY(transY);
        }

        void resetTransform() {
            mView.setScaleX(FULL_SCREEN_SCALE);
            mView.setScaleY(FULL_SCREEN_SCALE);
            mView.setTranslationX(0f);
            mView.setTranslationY(0f);
        }

        @Override
        public String toString() {
            return "AdapterIndex = " + mIndex + "\n\t left = " + mLeftPosition
                    + "\n\t viewArea = " + mViewArea
                    + "\n\t centerX = " + getCenterX()
                    + "\n\t view MeasuredSize = "
                    + mView.getMeasuredWidth() + ',' + mView.getMeasuredHeight()
                    + "\n\t view Size = " + mView.getWidth() + ',' + mView.getHeight()
                    + "\n\t view scale = " + mView.getScaleX();
        }
    }

    /** Constructor. */
    public FilmstripView(Context context) {
        super(context);
        init((CameraActivity) context);
    }

    /** Constructor. */
    public FilmstripView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init((CameraActivity) context);
    }

    /** Constructor. */
    public FilmstripView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init((CameraActivity) context);
    }

    private void init(CameraActivity cameraActivity) {
        setWillNotDraw(false);
        mActivity = cameraActivity;
        mVideoClickedCallback = new PlayVideoIntent(mActivity);
        mScale = 1.0f;
        mAdapterIndexUserIsScrollingOver = 0;
        mController = new FilmstripControllerImpl();
        mViewAnimInterpolator = new DecelerateInterpolator();
        mZoomView = new ZoomView(cameraActivity);
        mZoomView.setVisibility(GONE);
        addView(mZoomView);

        mGestureListener = new FilmstripGestures();
        mGestureRecognizer =
                new FilmstripGestureRecognizer(cameraActivity, mGestureListener);
        mSlop = (int) getContext().getResources().getDimension(R.dimen.pie_touch_slop);
        DisplayMetrics metrics = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        // Allow over scaling because on high density screens, pixels are too
        // tiny to clearly see the details at 1:1 zoom. We should not scale
        // beyond what 1:1 would look like on a medium density screen, as
        // scaling beyond that would only yield blur.
        mOverScaleFactor = (float) metrics.densityDpi / (float) DisplayMetrics.DENSITY_HIGH;
        if (mOverScaleFactor < 1f) {
            mOverScaleFactor = 1f;
        }

        setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);

                info.setClassName(FilmstripView.class.getName());
                info.setScrollable(true);
                info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            }

            @Override
            public boolean performAccessibilityAction(View host, int action, Bundle args) {
                if (!mController.isScrolling()) {
                    switch (action) {
                        case AccessibilityNodeInfo.ACTION_SCROLL_FORWARD: {
                            mController.goToNextItem();
                            return true;
                        }
                        case AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD: {
                            boolean wentToPrevious = mController.goToPreviousItem();
                            if (!wentToPrevious) {
                                // at beginning of filmstrip, hide and go back to preview
                                mActivity.getCameraAppUI().hideFilmstrip();
                            }
                            return true;
                        }
                        case AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS: {
                            // Prevent the view group itself from being selected.
                            // Instead, select the item in the center
                            final ViewItem currentItem = mViewItems[BUFFER_CENTER];
                            currentItem.getView().performAccessibilityAction(action, args);
                            return true;
                        }
                    }
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
    }

    private void recycleView(View view, int index) {
        Log.v(TAG, "recycleView");
        final int viewType = (Integer) view.getTag(R.id.mediadata_tag_viewtype);
        if (viewType > 0) {
            Queue<View> recycledViewsForType = recycledViews.get(viewType);
            if (recycledViewsForType == null) {
                recycledViewsForType = new ArrayDeque<View>();
                recycledViews.put(viewType, recycledViewsForType);
            }
            recycledViewsForType.offer(view);
        }
    }

    private View getRecycledView(int index) {
        final int viewType = mDataAdapter.getItemViewType(index);
        Queue<View> recycledViewsForType = recycledViews.get(viewType);
        View view = null;
        if (recycledViewsForType != null) {
            view = recycledViewsForType.poll();
        }
        if (view != null) {
            view.setVisibility(View.GONE);
        }
        Log.v(TAG, "getRecycledView, recycled=" + (view != null));
        return view;
    }

    /**
     * Returns the controller.
     *
     * @return The {@code Controller}.
     */
    public FilmstripController getController() {
        return mController;
    }

    /**
     * Returns the draw area width of the current item.
     */
    public int  getCurrentItemLeft() {
        return mViewItems[BUFFER_CENTER].getDrawAreaLeft();
    }

    private void setListener(FilmstripController.FilmstripListener l) {
        mListener = l;
    }

    private void setViewGap(int viewGap) {
        mViewGapInPixel = viewGap;
    }

    /**
     * Called after current item or zoom level has changed.
     */
    public void zoomAtIndexChanged() {
        if (mViewItems[BUFFER_CENTER] == null) {
            return;
        }
        int index = mViewItems[BUFFER_CENTER].getAdapterIndex();
        mListener.onZoomAtIndexChanged(index, mScale);
    }

    /**
     * Checks if the data is at the center.
     *
     * @param index The index of the item in the data adapter to check.
     * @return {@code True} if the data is currently at the center.
     */
    private boolean isItemAtIndexCentered(int index) {
        if (mViewItems[BUFFER_CENTER] == null) {
            return false;
        }
        if (mViewItems[BUFFER_CENTER].getAdapterIndex() == index
                && isCurrentItemCentered()) {
            return true;
        }
        return false;
    }

    private void measureViewItem(ViewItem item, int boundWidth, int boundHeight) {
        int index = item.getAdapterIndex();
        FilmstripItem imageData = mDataAdapter.getFilmstripItemAt(index);
        if (imageData == null) {
            Log.w(TAG, "measureViewItem() - Trying to measure a null item!");
            return;
        }

        Point dim = CameraUtil.resizeToFill(
              imageData.getDimensions().getWidth(),
              imageData.getDimensions().getHeight(),
              imageData.getOrientation(),
              boundWidth,
              boundHeight);

        item.measure(MeasureSpec.makeMeasureSpec(dim.x, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dim.y, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int boundWidth = MeasureSpec.getSize(widthMeasureSpec);
        int boundHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (boundWidth == 0 || boundHeight == 0) {
            // Either width or height is unknown, can't measure children yet.
            return;
        }

        for (ViewItem item : mViewItems) {
            if (item != null) {
                measureViewItem(item, boundWidth, boundHeight);
            }
        }
        clampCenterX();
        // Measure zoom view
        mZoomView.measure(MeasureSpec.makeMeasureSpec(widthMeasureSpec, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(heightMeasureSpec, MeasureSpec.EXACTLY));
    }

    private int findTheNearestView(int viewX) {
        int nearest = 0;
        // Find the first non-null ViewItem.
        while (nearest < BUFFER_SIZE
                && (mViewItems[nearest] == null || mViewItems[nearest].getLeftPosition() == -1)) {
            nearest++;
        }
        // No existing available ViewItem
        if (nearest == BUFFER_SIZE) {
            return -1;
        }

        int min = Math.abs(viewX - mViewItems[nearest].getCenterX());

        for (int i = nearest + 1; i < BUFFER_SIZE && mViewItems[i] != null; i++) {
            // Not measured yet.
            if (mViewItems[i].getLeftPosition() == -1) {
                continue;
            }

            int centerX = mViewItems[i].getCenterX();
            int dist = Math.abs(viewX - centerX);
            if (dist < min) {
                min = dist;
                nearest = i;
            }
        }
        return nearest;
    }

    private ViewItem buildViewItemAt(int index) {
        if (mActivity.isDestroyed()) {
            // Loading item data is call from multiple AsyncTasks and the
            // activity may be finished when buildViewItemAt is called.
            Log.d(TAG, "Activity destroyed, don't load data");
            return null;
        }
        FilmstripItem data = mDataAdapter.getFilmstripItemAt(index);
        if (data == null) {
            return null;
        }

        // Always scale by fixed filmstrip scale, since we only show items when
        // in filmstrip. Preloading images with a different scale and bounds
        // interferes with caching.
        int width = Math.round(FULL_SCREEN_SCALE * getWidth());
        int height = Math.round(FULL_SCREEN_SCALE * getHeight());

        Log.v(TAG, "suggesting item bounds: " + width + "x" + height);
        mDataAdapter.suggestViewSizeBound(width, height);

        View recycled = getRecycledView(index);
        View v = mDataAdapter.getView(recycled, index, mVideoClickedCallback);
        if (v == null) {
            return null;
        }
        ViewItem item = new ViewItem(index, v, data, this);
        item.addViewToHierarchy();
        return item;
    }

    private void renderFullRes(int bufferIndex) {
        ViewItem item = mViewItems[bufferIndex];
        if (item == null) {
            return;
        }

        item.renderFullRes();
    }

    private void renderThumbnail(int bufferIndex) {
        ViewItem item = mViewItems[bufferIndex];
        if (item == null) {
            return;
        }

        item.renderThumbnail();
    }

    private void renderAllThumbnails() {
        for(int i = 0; i < BUFFER_SIZE; i++) {
            renderThumbnail(i);
        }
    }

    private void removeItem(int bufferIndex) {
        if (bufferIndex >= mViewItems.length || mViewItems[bufferIndex] == null) {
            return;
        }
        FilmstripItem data = mDataAdapter.getFilmstripItemAt(
              mViewItems[bufferIndex].getAdapterIndex());
        if (data == null) {
            Log.w(TAG, "removeItem() - Trying to remove a null item!");
            return;
        }
        mViewItems[bufferIndex].removeViewFromHierarchy();
        mViewItems[bufferIndex] = null;
    }

    /**
     * We try to keep the one closest to the center of the screen at position
     * BUFFER_CENTER.
     */
    private void stepIfNeeded() {
        if (!inFilmstrip() && !inFullScreen()) {
            // The good timing to step to the next view is when everything is
            // not in transition.
            return;
        }
        final int nearestBufferIndex = findTheNearestView(mCenterX);
        // if the nearest view is the current view, or there is no nearest
        // view, then we do not need to adjust the view buffers.
        if (nearestBufferIndex == -1 || nearestBufferIndex == BUFFER_CENTER) {
            return;
        }
        int prevIndex = (mViewItems[BUFFER_CENTER] == null ? -1 :
              mViewItems[BUFFER_CENTER].getAdapterIndex());
        final int adjust = nearestBufferIndex - BUFFER_CENTER;
        if (adjust > 0) {
            // Remove from beginning of the buffer.
            for (int k = 0; k < adjust; k++) {
                removeItem(k);
            }
            // Shift items inside the buffer
            for (int k = 0; k + adjust < BUFFER_SIZE; k++) {
                mViewItems[k] = mViewItems[k + adjust];
            }
            // Fill the end with new items.
            for (int k = BUFFER_SIZE - adjust; k < BUFFER_SIZE; k++) {
                mViewItems[k] = null;
                if (mViewItems[k - 1] != null) {
                    mViewItems[k] = buildViewItemAt(mViewItems[k - 1].getAdapterIndex() + 1);
                }
            }
            adjustChildZOrder();
        } else {
            // Remove from the end of the buffer
            for (int k = BUFFER_SIZE - 1; k >= BUFFER_SIZE + adjust; k--) {
                removeItem(k);
            }
            // Shift items inside the buffer
            for (int k = BUFFER_SIZE - 1; k + adjust >= 0; k--) {
                mViewItems[k] = mViewItems[k + adjust];
            }
            // Fill the beginning with new items.
            for (int k = -1 - adjust; k >= 0; k--) {
                mViewItems[k] = null;
                if (mViewItems[k + 1] != null) {
                    mViewItems[k] = buildViewItemAt(mViewItems[k + 1].getAdapterIndex() - 1);
                }
            }
        }
        invalidate();
        if (mListener != null) {
            mListener.onDataFocusChanged(prevIndex, mViewItems[BUFFER_CENTER]
                  .getAdapterIndex());
            final int firstVisible = mViewItems[BUFFER_CENTER].getAdapterIndex() - 2;
            final int visibleItemCount = firstVisible + BUFFER_SIZE;
            final int totalItemCount = mDataAdapter.getTotalNumber();
            mListener.onScroll(firstVisible, visibleItemCount, totalItemCount);
        }
        zoomAtIndexChanged();
    }

    /**
     * Check the bounds of {@code mCenterX}. Always call this function after: 1.
     * Any changes to {@code mCenterX}. 2. Any size change of the view items.
     *
     * @return Whether clamp happened.
     */
    private boolean clampCenterX() {
        ViewItem currentItem = mViewItems[BUFFER_CENTER];
        if (currentItem == null) {
            return false;
        }

        boolean stopScroll = false;
        if (currentItem.getAdapterIndex() == 0 && mCenterX < currentItem.getCenterX()) {
            // Stop at the first ViewItem.
            stopScroll = true;
        } else if (currentItem.getAdapterIndex() == mDataAdapter.getTotalNumber() - 1
                && mCenterX > currentItem.getCenterX()) {
            // Stop at the end.
            stopScroll = true;
        }

        if (stopScroll) {
            mCenterX = currentItem.getCenterX();
        }

        return stopScroll;
    }

    /**
     * Reorders the child views to be consistent with their index. This method
     * should be called after adding/removing views.
     */
    private void adjustChildZOrder() {
        for (int i = BUFFER_SIZE - 1; i >= 0; i--) {
            if (mViewItems[i] == null) {
                continue;
            }
            mViewItems[i].bringViewToFront();
        }
        // ZoomView is a special case to always be in the front.
        bringChildToFront(mZoomView);
    }

    /**
     * Returns the index of the current item, or -1 if there is no data.
     */
    private int getCurrentItemAdapterIndex() {
        ViewItem current = mViewItems[BUFFER_CENTER];
        if (current == null) {
            return -1;
        }
        return current.getAdapterIndex();
    }

    /**
     * Keep the current item in the center. This functions does not check if the
     * current item is null.
     */
    private void scrollCurrentItemToCenter() {
        final ViewItem currItem = mViewItems[BUFFER_CENTER];
        if (currItem == null) {
            return;
        }
        final int currentViewCenter = currItem.getCenterX();
        if (mController.isScrolling() || mIsUserScrolling
                || isCurrentItemCentered()) {
            Log.d(TAG, "[fling] mController.isScrolling() - " + mController.isScrolling());
            return;
        }

        int snapInTime = (int) (SNAP_IN_CENTER_TIME_MS
                * ((float) Math.abs(mCenterX - currentViewCenter))
                / mDrawArea.width());

        Log.d(TAG, "[fling] Scroll to center.");
        mController.scrollToPosition(currentViewCenter,
              snapInTime, false);
    }

    /**
     * Translates the {@link ViewItem} on the left of the current one to match
     * the full-screen layout. In full-screen, we show only one {@link ViewItem}
     * which occupies the whole screen. The other left ones are put on the left
     * side in full scales. Does nothing if there's no next item.
     *
     * @param index The index of the current one to be translated.
     * @param drawAreaWidth The width of the current draw area.
     * @param scaleFraction A {@code float} between 0 and 1. 0 if the current
     *            scale is {@code FILM_STRIP_SCALE}. 1 if the current scale is
     *            {@code FULL_SCREEN_SCALE}.
     */
    private void translateLeftViewItem(
            int index, int drawAreaWidth, float scaleFraction) {
        if (index < 0 || index > BUFFER_SIZE - 1) {
            Log.w(TAG, "translateLeftViewItem() - Index out of bound!");
            return;
        }

        final ViewItem curr = mViewItems[index];
        final ViewItem next = mViewItems[index + 1];
        if (curr == null || next == null) {
            Log.w(TAG, "translateLeftViewItem() - Invalid view item (curr or next == null). curr = "
                    + index);
            return;
        }

        final int currCenterX = curr.getCenterX();
        final int nextCenterX = next.getCenterX();
        final int translate = (int) ((nextCenterX - drawAreaWidth
                - currCenterX) * scaleFraction);

        curr.layoutWithTranslationX(mDrawArea, mCenterX, mScale);
        curr.setAlpha(1f);
        curr.setVisibility(VISIBLE);

        if (inFullScreen()) {
            curr.setTranslationX(translate * (mCenterX - currCenterX) /
                  (nextCenterX - currCenterX));
        } else {
            curr.setTranslationX(translate);
        }
    }

    /**
     * Fade out the {@link ViewItem} on the right of the current one in
     * full-screen layout. Does nothing if there's no previous item.
     *
     * @param bufferIndex The index of the item in the buffer to fade.
     */
    private void fadeAndScaleRightViewItem(int bufferIndex) {
        if (bufferIndex < 1 || bufferIndex > BUFFER_SIZE) {
            Log.w(TAG, "fadeAndScaleRightViewItem() - bufferIndex out of bound!");
            return;
        }

        final ViewItem item = mViewItems[bufferIndex];
        final ViewItem previousItem = mViewItems[bufferIndex - 1];
        if (item == null || previousItem == null) {
            Log.w(TAG, "fadeAndScaleRightViewItem() - Invalid view item (curr or prev == null)."
                  + "curr = " + bufferIndex);
            return;
        }

        if (bufferIndex > BUFFER_CENTER + 1) {
            // Every item not right next to the BUFFER_CENTER is invisible.
            item.setVisibility(INVISIBLE);
            return;
        }
        final int prevCenterX = previousItem.getCenterX();
        if (mCenterX <= prevCenterX) {
            // Shortcut. If the position is at the center of the previous one,
            // set to invisible too.
            item.setVisibility(INVISIBLE);
            return;
        }
        final int currCenterX = item.getCenterX();
        final float fadeDownFraction =
                ((float) mCenterX - prevCenterX) / (currCenterX - prevCenterX);
        item.layoutWithTranslationX(mDrawArea, currCenterX,
              FILM_STRIP_SCALE + (1f - FILM_STRIP_SCALE) * fadeDownFraction);

        item.setAlpha(fadeDownFraction);
        item.setTranslationX(0);
        item.setVisibility(VISIBLE);
    }

    private void layoutViewItems(boolean layoutChanged) {
        if (mViewItems[BUFFER_CENTER] == null ||
                mDrawArea.width() == 0 ||
                mDrawArea.height() == 0) {
            return;
        }

        // If the layout changed, we need to adjust the current position so
        // that if an item is centered before the change, it's still centered.
        if (layoutChanged) {
            mViewItems[BUFFER_CENTER].setLeftPosition(
                  mCenterX - mViewItems[BUFFER_CENTER].getMeasuredWidth() / 2);
        }

        if (inZoomView()) {
            return;
        }
        /**
         * Transformed scale fraction between 0 and 1. 0 if the scale is
         * {@link FILM_STRIP_SCALE}. 1 if the scale is {@link FULL_SCREEN_SCALE}
         * .
         */
        final float scaleFraction = mViewAnimInterpolator.getInterpolation(
                (mScale - FILM_STRIP_SCALE) / (FULL_SCREEN_SCALE - FILM_STRIP_SCALE));
        final int fullScreenWidth = mDrawArea.width() + mViewGapInPixel;

        // Decide the position for all view items on the left and the right
        // first.

        // Left items.
        for (int i = BUFFER_CENTER - 1; i >= 0; i--) {
            final ViewItem curr = mViewItems[i];
            if (curr == null) {
                break;
            }

            // First, layout relatively to the next one.
            final int currLeft = mViewItems[i + 1].getLeftPosition()
                    - curr.getMeasuredWidth() - mViewGapInPixel;
            curr.setLeftPosition(currLeft);
        }
        // Right items.
        for (int i = BUFFER_CENTER + 1; i < BUFFER_SIZE; i++) {
            final ViewItem curr = mViewItems[i];
            if (curr == null) {
                break;
            }

            // First, layout relatively to the previous one.
            final ViewItem prev = mViewItems[i - 1];
            final int currLeft =
                    prev.getLeftPosition() + prev.getMeasuredWidth()
                            + mViewGapInPixel;
            curr.setLeftPosition(currLeft);
        }

        if (scaleFraction == 1f) {
            final ViewItem currItem = mViewItems[BUFFER_CENTER];
            final int currCenterX = currItem.getCenterX();
            if (mCenterX < currCenterX) {
                // In full-screen and mCenterX is on the left of the center,
                // we draw the current one to "fade down".
                fadeAndScaleRightViewItem(BUFFER_CENTER);
            } else if (mCenterX > currCenterX) {
                // In full-screen and mCenterX is on the right of the center,
                // we draw the current one translated.
                translateLeftViewItem(BUFFER_CENTER, fullScreenWidth, scaleFraction);
            } else {
                currItem.layoutWithTranslationX(mDrawArea, mCenterX, mScale);
                currItem.setTranslationX(0f);
                currItem.setAlpha(1f);
            }
        } else {
            final ViewItem currItem = mViewItems[BUFFER_CENTER];
            currItem.setVisibility(View.VISIBLE);
            // The normal filmstrip has no translation for the current item. If
            // it has translation before, gradually set it to zero.
            currItem.setTranslationX(currItem.getTranslationX() * scaleFraction);
            currItem.layoutWithTranslationX(mDrawArea, mCenterX, mScale);
            if (mViewItems[BUFFER_CENTER - 1] == null) {
                currItem.setAlpha(1f);
            } else {
                final int currCenterX = currItem.getCenterX();
                final int prevCenterX = mViewItems[BUFFER_CENTER - 1].getCenterX();
                final float fadeDownFraction =
                        ((float) mCenterX - prevCenterX) / (currCenterX - prevCenterX);
                currItem.setAlpha(
                        (1 - fadeDownFraction) * (1 - scaleFraction) + fadeDownFraction);
            }
        }

        // Layout the rest dependent on the current scale.

        // Items on the left
        for (int i = BUFFER_CENTER - 1; i >= 0; i--) {
            final ViewItem curr = mViewItems[i];
            if (curr == null) {
                break;
            }
            translateLeftViewItem(i, fullScreenWidth, scaleFraction);
        }

        // Items on the right
        for (int i = BUFFER_CENTER + 1; i < BUFFER_SIZE; i++) {
            final ViewItem curr = mViewItems[i];
            if (curr == null) {
                break;
            }

            curr.layoutWithTranslationX(mDrawArea, mCenterX, mScale);

            if (scaleFraction == 1) {
                // It's in full-screen mode.
                fadeAndScaleRightViewItem(i);
            } else {
                boolean isVisible = (curr.getVisibility() == VISIBLE);
                boolean setToVisible = !isVisible;

                if (i == BUFFER_CENTER + 1) {
                    // right hand neighbor needs to fade based on scale of
                    // center
                    curr.setAlpha(1f - scaleFraction);
                } else {
                    if (scaleFraction == 0f) {
                        curr.setAlpha(1f);
                    } else {
                        // further right items should not display when center
                        // is being scaled
                        setToVisible = false;
                        if (isVisible) {
                            curr.setVisibility(INVISIBLE);
                        }
                    }
                }

                if (setToVisible && !isVisible) {
                    curr.setVisibility(VISIBLE);
                }

                curr.setTranslationX((mViewItems[BUFFER_CENTER].getLeftPosition() -
                      curr.getLeftPosition()) * scaleFraction);
            }
        }

        stepIfNeeded();
    }

    @Override
    public void onDraw(Canvas c) {
        // TODO: remove layoutViewItems() here.
        layoutViewItems(false);
        super.onDraw(c);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mDrawArea.left = 0;
        mDrawArea.top = 0;
        mDrawArea.right = r - l;
        mDrawArea.bottom = b - t;
        mZoomView.layout(mDrawArea.left, mDrawArea.top, mDrawArea.right, mDrawArea.bottom);
        // TODO: Need a more robust solution to decide when to re-layout
        // If in the middle of zooming, only re-layout when the layout has
        // changed.
        if (!inZoomView() || changed) {
            resetZoomView();
            layoutViewItems(changed);
        }
    }

    /**
     * Clears the translation and scale that has been set on the view, cancels
     * any loading request for image partial decoding, and hides zoom view. This
     * is needed for when there is a layout change (e.g. when users re-enter the
     * app, or rotate the device, etc).
     */
    private void resetZoomView() {
        if (!inZoomView()) {
            return;
        }
        ViewItem current = mViewItems[BUFFER_CENTER];
        if (current == null) {
            return;
        }
        mScale = FULL_SCREEN_SCALE;
        mController.cancelZoomAnimation();
        mController.cancelFlingAnimation();
        current.resetTransform();
        mController.cancelLoadingZoomedImage();
        mZoomView.setVisibility(GONE);
        mController.setSurroundingViewsVisible(true);
    }

    private void hideZoomView() {
        if (inZoomView()) {
            mController.cancelLoadingZoomedImage();
            mZoomView.setVisibility(GONE);
        }
    }

    private void slideViewBack(ViewItem item) {
        item.animateTranslationX(0, GEOMETRY_ADJUST_TIME_MS, mViewAnimInterpolator);
        item.animateTranslationY(0, GEOMETRY_ADJUST_TIME_MS, mViewAnimInterpolator);
        item.animateAlpha(1f, GEOMETRY_ADJUST_TIME_MS, mViewAnimInterpolator);
    }

    private void animateItemRemoval(int index) {
        if (mScale > FULL_SCREEN_SCALE) {
            resetZoomView();
        }
        int removeAtBufferIndex = findItemInBufferByAdapterIndex(index);

        // adjust the buffer to be consistent
        for (int i = 0; i < BUFFER_SIZE; i++) {
            if (mViewItems[i] == null || mViewItems[i].getAdapterIndex() <= index) {
                continue;
            }
            mViewItems[i].setIndex(mViewItems[i].getAdapterIndex() - 1);
        }
        if (removeAtBufferIndex == -1) {
            return;
        }

        final ViewItem removedItem = mViewItems[removeAtBufferIndex];
        final int offsetX = removedItem.getMeasuredWidth() + mViewGapInPixel;

        for (int i = removeAtBufferIndex + 1; i < BUFFER_SIZE; i++) {
            if (mViewItems[i] != null) {
                mViewItems[i].setLeftPosition(mViewItems[i].getLeftPosition() - offsetX);
            }
        }

        if (removeAtBufferIndex >= BUFFER_CENTER
                && mViewItems[removeAtBufferIndex].getAdapterIndex() < mDataAdapter.getTotalNumber()) {
            // Fill the removed item by left shift when the current one or
            // anyone on the right is removed, and there's more data on the
            // right available.
            for (int i = removeAtBufferIndex; i < BUFFER_SIZE - 1; i++) {
                mViewItems[i] = mViewItems[i + 1];
            }

            // pull data out from the DataAdapter for the last one.
            int curr = BUFFER_SIZE - 1;
            int prev = curr - 1;
            if (mViewItems[prev] != null) {
                mViewItems[curr] = buildViewItemAt(mViewItems[prev].getAdapterIndex() + 1);
            }

            // The animation part.
            if (inFullScreen()) {
                mViewItems[BUFFER_CENTER].setVisibility(VISIBLE);
                ViewItem nextItem = mViewItems[BUFFER_CENTER + 1];
                if (nextItem != null) {
                    nextItem.setVisibility(INVISIBLE);
                }
            }

            // Translate the views to their original places.
            for (int i = removeAtBufferIndex; i < BUFFER_SIZE; i++) {
                if (mViewItems[i] != null) {
                    mViewItems[i].setTranslationX(offsetX);
                }
            }

            // The end of the filmstrip might have been changed.
            // The mCenterX might be out of the bound.
            ViewItem currItem = mViewItems[BUFFER_CENTER];
            if (currItem!=null) {
                if (currItem.getAdapterIndex() == mDataAdapter.getTotalNumber() - 1
                        && mCenterX > currItem.getCenterX()) {
                    int adjustDiff = currItem.getCenterX() - mCenterX;
                    mCenterX = currItem.getCenterX();
                    for (int i = 0; i < BUFFER_SIZE; i++) {
                        if (mViewItems[i] != null) {
                            mViewItems[i].translateXScaledBy(adjustDiff);
                        }
                    }
                }
            } else {
                // CurrItem should NOT be NULL, but if is, at least don't crash.
                Log.w(TAG,"Caught invalid update in removal animation.");
            }
        } else {
            // fill the removed place by right shift
            mCenterX -= offsetX;

            for (int i = removeAtBufferIndex; i > 0; i--) {
                mViewItems[i] = mViewItems[i - 1];
            }

            // pull data out from the DataAdapter for the first one.
            int curr = 0;
            int next = curr + 1;
            if (mViewItems[next] != null) {
                mViewItems[curr] = buildViewItemAt(mViewItems[next].getAdapterIndex() - 1);

            }

            // Translate the views to their original places.
            for (int i = removeAtBufferIndex; i >= 0; i--) {
                if (mViewItems[i] != null) {
                    mViewItems[i].setTranslationX(-offsetX);
                }
            }
        }

        int transY = getHeight() / 8;
        if (removedItem.getTranslationY() < 0) {
            transY = -transY;
        }
        removedItem.animateTranslationY(removedItem.getTranslationY() + transY,
                GEOMETRY_ADJUST_TIME_MS, mViewAnimInterpolator);
        removedItem.animateAlpha(0f, GEOMETRY_ADJUST_TIME_MS, mViewAnimInterpolator);
        postDelayed(new Runnable() {
            @Override
            public void run() {
                removedItem.removeViewFromHierarchy();
            }
        }, GEOMETRY_ADJUST_TIME_MS);

        adjustChildZOrder();
        invalidate();

        // Now, slide every one back.
        if (mViewItems[BUFFER_CENTER] == null) {
            return;
        }
        for (int i = 0; i < BUFFER_SIZE; i++) {
            if (mViewItems[i] != null
                    && mViewItems[i].getTranslationX() != 0f) {
                slideViewBack(mViewItems[i]);
            }
        }
    }

    // returns -1 on failure.
    private int findItemInBufferByAdapterIndex(int index) {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            if (mViewItems[i] != null
                    && mViewItems[i].getAdapterIndex() == index) {
                return i;
            }
        }
        return -1;
    }

    private void updateInsertion(int index) {
        int bufferIndex = findItemInBufferByAdapterIndex(index);
        if (bufferIndex == -1) {
            // Not in the current item buffers. Check if it's inserted
            // at the end.
            if (index == mDataAdapter.getTotalNumber() - 1) {
                int prev = findItemInBufferByAdapterIndex(index - 1);
                if (prev >= 0 && prev < BUFFER_SIZE - 1) {
                    // The previous data is in the buffer and we still
                    // have room for the inserted data.
                    bufferIndex = prev + 1;
                }
            }
        }

        // adjust the indexes to be consistent
        for (int i = 0; i < BUFFER_SIZE; i++) {
            if (mViewItems[i] == null || mViewItems[i].getAdapterIndex() < index) {
                continue;
            }
            mViewItems[i].setIndex(mViewItems[i].getAdapterIndex() + 1);
        }
        if (bufferIndex == -1) {
            return;
        }

        final FilmstripItem data = mDataAdapter.getFilmstripItemAt(index);
        Point dim = CameraUtil
                .resizeToFill(
                      data.getDimensions().getWidth(),
                      data.getDimensions().getHeight(),
                      data.getOrientation(),
                      getMeasuredWidth(),
                      getMeasuredHeight());
        final int offsetX = dim.x + mViewGapInPixel;
        ViewItem viewItem = buildViewItemAt(index);
        if (viewItem == null) {
            Log.w(TAG, "unable to build inserted item from data");
            return;
        }

        if (bufferIndex >= BUFFER_CENTER) {
            if (bufferIndex == BUFFER_CENTER) {
                viewItem.setLeftPosition(mViewItems[BUFFER_CENTER].getLeftPosition());
            }
            // Shift right to make rooms for newly inserted item.
            removeItem(BUFFER_SIZE - 1);
            for (int i = BUFFER_SIZE - 1; i > bufferIndex; i--) {
                mViewItems[i] = mViewItems[i - 1];
                if (mViewItems[i] != null) {
                    mViewItems[i].setTranslationX(-offsetX);
                    slideViewBack(mViewItems[i]);
                }
            }
        } else {
            // Shift left. Put the inserted data on the left instead of the
            // found position.
            --bufferIndex;
            if (bufferIndex < 0) {
                return;
            }
            removeItem(0);
            for (int i = 1; i <= bufferIndex; i++) {
                if (mViewItems[i] != null) {
                    mViewItems[i].setTranslationX(offsetX);
                    slideViewBack(mViewItems[i]);
                    mViewItems[i - 1] = mViewItems[i];
                }
            }
        }

        mViewItems[bufferIndex] = viewItem;
        renderThumbnail(bufferIndex);
        viewItem.setAlpha(0f);
        viewItem.setTranslationY(getHeight() / 8);
        slideViewBack(viewItem);
        adjustChildZOrder();

        invalidate();
    }

    private void setDataAdapter(FilmstripDataAdapter adapter) {
        mDataAdapter = adapter;
        int maxEdge = (int) (Math.max(this.getHeight(), this.getWidth())
                * FILM_STRIP_SCALE);
        mDataAdapter.suggestViewSizeBound(maxEdge, maxEdge);
        mDataAdapter.setListener(new FilmstripDataAdapter.Listener() {
            @Override
            public void onFilmstripItemLoaded() {
                reload();
            }

            @Override
            public void onFilmstripItemUpdated(FilmstripDataAdapter.UpdateReporter reporter) {
                update(reporter);
            }

            @Override
            public void onFilmstripItemInserted(int index, FilmstripItem item) {
                if (mViewItems[BUFFER_CENTER] == null) {
                    // empty now, simply do a reload.
                    reload();
                } else {
                    updateInsertion(index);
                }
                if (mListener != null) {
                    mListener.onDataFocusChanged(index, getCurrentItemAdapterIndex());
                }
                Log.d(TAG, "onFilmstripItemInserted()");
                renderAllThumbnails();
            }

            @Override
            public void onFilmstripItemRemoved(int index, FilmstripItem item) {
                animateItemRemoval(index);
                if (mListener != null) {
                    mListener.onDataFocusChanged(index, getCurrentItemAdapterIndex());
                }
                Log.d(TAG, "onFilmstripItemRemoved()");
                renderAllThumbnails();
            }
        });
    }

    private boolean inFilmstrip() {
        return (mScale == FILM_STRIP_SCALE);
    }

    private boolean inFullScreen() {
        return (mScale == FULL_SCREEN_SCALE);
    }

    private boolean inZoomView() {
        return (mScale > FULL_SCREEN_SCALE);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mController.isScrolling()) {
            return true;
        }

        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mCheckToIntercept = true;
            mDown = MotionEvent.obtain(ev);
            return false;
        } else if (ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            // Do not intercept touch once child is in zoom mode
            mCheckToIntercept = false;
            return false;
        } else {
            if (!mCheckToIntercept) {
                return false;
            }
            if (ev.getEventTime() - ev.getDownTime() > SWIPE_TIME_OUT) {
                return false;
            }
            int deltaX = (int) (ev.getX() - mDown.getX());
            int deltaY = (int) (ev.getY() - mDown.getY());
            if (ev.getActionMasked() == MotionEvent.ACTION_MOVE
                    && deltaX < mSlop * (-1)) {
                // intercept left swipe
                if (Math.abs(deltaX) >= Math.abs(deltaY) * 2) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mGestureRecognizer.onTouchEvent(ev);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent ev) {
        mGestureRecognizer.onGenericMotionEvent(ev);
        return true;
    }

    FilmstripGestureRecognizer.Listener getGestureListener() {
        return mGestureListener;
    }

    private void updateViewItem(int bufferIndex) {
        ViewItem item = mViewItems[bufferIndex];
        if (item == null) {
            Log.w(TAG, "updateViewItem() - Trying to update an null item!");
            return;
        }

        int adapterIndex = item.getAdapterIndex();
        FilmstripItem filmstripItem = mDataAdapter.getFilmstripItemAt(adapterIndex);
        if (filmstripItem == null) {
            Log.w(TAG, "updateViewItem() - Trying to update item with null FilmstripItem!");
            return;
        }

        FilmstripItem oldFilmstripItem = item.getData();

        // In case the underlying data item is changed (commonly from
        // SessionItem to PhotoItem for an image requiring processing), set the
        // new FilmstripItem on the ViewItem
        if (!filmstripItem.equals(oldFilmstripItem)) {
            oldFilmstripItem.recycle(item.getView());
            item.setData(filmstripItem);
            Log.v(TAG, "updateViewItem() - recycling old data item and setting new");
        } else {
            Log.v(TAG, "updateViewItem() - updating data with the same item");
        }

        // In case state changed from a new FilmStripItem or the existing one,
        // redraw the View contents. We call getView here as it will refill the
        // view contents, but it is not clear as we are not using the documented
        // method intent to get a View, we know that this always uses the view
        // passed in to populate it.
        // TODO: refactor 'getView' to more explicitly just update view contents
        mDataAdapter.getView(item.getView(), adapterIndex, mVideoClickedCallback);

        mZoomView.resetDecoder();

        boolean stopScroll = clampCenterX();
        if (stopScroll) {
            mController.stopScrolling(true);
        }

        Log.d(TAG, "updateViewItem(bufferIndex: " + bufferIndex + ")");
        Log.d(TAG, "updateViewItem() - mIsUserScrolling: " + mIsUserScrolling);
        Log.d(TAG, "updateViewItem() - mController.isScrolling() - " + mController.isScrolling());

        // Relying on only isScrolling or isUserScrolling independently
        // is unreliable. Load the full resolution if either value
        // reports that the item is not scrolling.
        if (!mController.isScrolling() || !mIsUserScrolling) {
            renderThumbnail(bufferIndex);
        }

        adjustChildZOrder();
        invalidate();
        if (mListener != null) {
            mListener.onDataUpdated(adapterIndex);
        }
    }

    /** Some of the data is changed. */
    private void update(FilmstripDataAdapter.UpdateReporter reporter) {
        // No data yet.
        if (mViewItems[BUFFER_CENTER] == null) {
            reload();
            return;
        }

        // Check the current one.
        ViewItem curr = mViewItems[BUFFER_CENTER];
        int index = curr.getAdapterIndex();
        if (reporter.isDataRemoved(index)) {
            reload();
            return;
        }
        if (reporter.isDataUpdated(index)) {
            updateViewItem(BUFFER_CENTER);
            final FilmstripItem data = mDataAdapter.getFilmstripItemAt(index);
            if (!mIsUserScrolling && !mController.isScrolling()) {
                // If there is no scrolling at all, adjust mCenterX to place
                // the current item at the center.
                Point dim = CameraUtil.resizeToFill(
                      data.getDimensions().getWidth(),
                      data.getDimensions().getHeight(),
                      data.getOrientation(),
                      getMeasuredWidth(),
                      getMeasuredHeight());
                mCenterX = curr.getLeftPosition() + dim.x / 2;
            }
        }

        // Check left
        for (int i = BUFFER_CENTER - 1; i >= 0; i--) {
            curr = mViewItems[i];
            if (curr != null) {
                index = curr.getAdapterIndex();
                if (reporter.isDataRemoved(index) || reporter.isDataUpdated(index)) {
                    updateViewItem(i);
                }

            } else {
                ViewItem next = mViewItems[i + 1];
                if (next != null) {
                    mViewItems[i] = buildViewItemAt(next.getAdapterIndex() - 1);
                }
            }
        }

        // Check right
        for (int i = BUFFER_CENTER + 1; i < BUFFER_SIZE; i++) {
            curr = mViewItems[i];
            if (curr != null) {
                index = curr.getAdapterIndex();
                if (reporter.isDataRemoved(index) || reporter.isDataUpdated(index)) {
                    updateViewItem(i);
                }
            } else {
                ViewItem prev = mViewItems[i - 1];
                if (prev != null) {
                    mViewItems[i] = buildViewItemAt(prev.getAdapterIndex() + 1);
                }
            }
        }
        adjustChildZOrder();
        // Request a layout to find the measured width/height of the view first.
        requestLayout();
    }

    /**
     * The whole data might be totally different. Flush all and load from the
     * start. Filmstrip will be centered on the first item, i.e. the camera
     * preview.
     */
    private void reload() {
        mController.stopScrolling(true);
        mController.stopScale();
        mAdapterIndexUserIsScrollingOver = 0;

        int prevId = -1;
        if (mViewItems[BUFFER_CENTER] != null) {
            prevId = mViewItems[BUFFER_CENTER].getAdapterIndex();
        }

        // Remove all views from the mViewItems buffer, except the camera view.
        for (int i = 0; i < mViewItems.length; i++) {
            if (mViewItems[i] == null) {
                continue;
            }
            mViewItems[i].removeViewFromHierarchy();
        }

        // Clear out the mViewItems and rebuild with camera in the center.
        Arrays.fill(mViewItems, null);
        int dataNumber = mDataAdapter.getTotalNumber();
        if (dataNumber == 0) {
            return;
        }

        mViewItems[BUFFER_CENTER] = buildViewItemAt(0);
        if (mViewItems[BUFFER_CENTER] == null) {
            return;
        }
        mViewItems[BUFFER_CENTER].setLeftPosition(0);
        for (int i = BUFFER_CENTER + 1; i < BUFFER_SIZE; i++) {
            mViewItems[i] = buildViewItemAt(mViewItems[i - 1].getAdapterIndex() + 1);
            if (mViewItems[i] == null) {
                break;
            }
        }

        // Ensure that the views in mViewItems will layout the first in the
        // center of the display upon a reload.
        mCenterX = -1;
        mScale = FILM_STRIP_SCALE;

        adjustChildZOrder();

        Log.d(TAG, "reload() - Ensure all items are loaded at max size.");
        renderAllThumbnails();
        invalidate();

        if (mListener != null) {
            mListener.onDataReloaded();
            mListener.onDataFocusChanged(prevId, mViewItems[BUFFER_CENTER].getAdapterIndex());
        }
    }

    private void promoteData(int index) {
        if (mListener != null) {
            mListener.onFocusedDataPromoted(index);
        }
    }

    private void demoteData(int index) {
        if (mListener != null) {
            mListener.onFocusedDataDemoted(index);
        }
    }

    private void onEnterFilmstrip() {
        Log.d(TAG, "onEnterFilmstrip()");
        if (mListener != null) {
            mListener.onEnterFilmstrip(getCurrentItemAdapterIndex());
        }
    }

    private void onLeaveFilmstrip() {
        if (mListener != null) {
            mListener.onLeaveFilmstrip(getCurrentItemAdapterIndex());
        }
    }

    private void onEnterFullScreen() {
        mFullScreenUIHidden = false;
        if (mListener != null) {
            mListener.onEnterFullScreenUiShown(getCurrentItemAdapterIndex());
        }
    }

    private void onLeaveFullScreen() {
        if (mListener != null) {
            mListener.onLeaveFullScreenUiShown(getCurrentItemAdapterIndex());
        }
    }

    private void onEnterFullScreenUiHidden() {
        mFullScreenUIHidden = true;
        if (mListener != null) {
            mListener.onEnterFullScreenUiHidden(getCurrentItemAdapterIndex());
        }
    }

    private void onLeaveFullScreenUiHidden() {
        mFullScreenUIHidden = false;
        if (mListener != null) {
            mListener.onLeaveFullScreenUiHidden(getCurrentItemAdapterIndex());
        }
    }

    private void onEnterZoomView() {
        if (mListener != null) {
            mListener.onEnterZoomView(getCurrentItemAdapterIndex());
        }
    }

    private void onLeaveZoomView() {
        mController.setSurroundingViewsVisible(true);
    }

    /**
     * MyController controls all the geometry animations. It passively tells the
     * geometry information on demand.
     */
    private class FilmstripControllerImpl implements FilmstripController {

        private final ValueAnimator mScaleAnimator;
        private ValueAnimator mZoomAnimator;
        private AnimatorSet mFlingAnimator;

        private final FilmstripScrollGesture mScrollGesture;
        private boolean mCanStopScroll;

        private final FilmstripScrollGesture.Listener mScrollListener =
                new FilmstripScrollGesture.Listener() {
                    @Override
                    public void onScrollUpdate(int currX, int currY) {
                        mCenterX = currX;

                        boolean stopScroll = clampCenterX();
                        if (stopScroll) {
                            Log.d(TAG, "[fling] onScrollUpdate() - stopScrolling!");
                            mController.stopScrolling(true);
                        }
                        invalidate();
                    }

                    @Override
                    public void onScrollEnd() {
                        mCanStopScroll = true;
                        Log.d(TAG, "[fling] onScrollEnd() - onScrollEnd");
                        if (mViewItems[BUFFER_CENTER] == null) {
                            return;
                        }
                        scrollCurrentItemToCenter();

                        // onScrollEnd will get called twice, once when
                        // the fling part ends, and once when the manual
                        // scroll center animation finishes. Once everything
                        // stops moving ensure that the items are loaded at
                        // full resolution.
                        if (isCurrentItemCentered()) {
                            // Since these are getting pushed into a queue,
                            // we want to ensure the item that is "most in view" is
                            // the first one rendered at max size.

                            Log.d(TAG, "[fling] onScrollEnd() - Ensuring that items are at"
                                  + " full resolution.");
                            renderThumbnail(BUFFER_CENTER);
                            renderThumbnail(BUFFER_CENTER + 1);
                            renderThumbnail(BUFFER_CENTER - 1);
                            renderThumbnail(BUFFER_CENTER + 2);
                        }
                    }
                };

        private final ValueAnimator.AnimatorUpdateListener mScaleAnimatorUpdateListener =
                new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        if (mViewItems[BUFFER_CENTER] == null) {
                            return;
                        }
                        mScale = (Float) animation.getAnimatedValue();
                        invalidate();
                    }
                };

        FilmstripControllerImpl() {
            TimeInterpolator decelerateInterpolator = new DecelerateInterpolator(1.5f);
            mScrollGesture = new FilmstripScrollGesture(mActivity.getAndroidContext(),
                    new Handler(mActivity.getMainLooper()),
                  mScrollListener, decelerateInterpolator);
            mCanStopScroll = true;

            mScaleAnimator = new ValueAnimator();
            mScaleAnimator.addUpdateListener(mScaleAnimatorUpdateListener);
            mScaleAnimator.setInterpolator(decelerateInterpolator);
            mScaleAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                    if (mScale == FULL_SCREEN_SCALE) {
                        onLeaveFullScreen();
                    } else {
                        if (mScale == FILM_STRIP_SCALE) {
                            onLeaveFilmstrip();
                        }
                    }
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    if (mScale == FULL_SCREEN_SCALE) {
                        onEnterFullScreen();
                    } else {
                        if (mScale == FILM_STRIP_SCALE) {
                            onEnterFilmstrip();
                        }
                    }
                    zoomAtIndexChanged();
                }

                @Override
                public void onAnimationCancel(Animator animator) {

                }

                @Override
                public void onAnimationRepeat(Animator animator) {

                }
            });
        }

        @Override
        public void setImageGap(int imageGap) {
            FilmstripView.this.setViewGap(imageGap);
        }

        @Override
        public int getCurrentAdapterIndex() {
            return FilmstripView.this.getCurrentItemAdapterIndex();
        }

        @Override
        public void setDataAdapter(FilmstripDataAdapter adapter) {
            FilmstripView.this.setDataAdapter(adapter);
        }

        @Override
        public boolean inFilmstrip() {
            return FilmstripView.this.inFilmstrip();
        }

        @Override
        public boolean inFullScreen() {
            return FilmstripView.this.inFullScreen();
        }

        @Override
        public void setListener(FilmstripListener listener) {
            FilmstripView.this.setListener(listener);
        }

        @Override
        public boolean isScrolling() {
            return !mScrollGesture.isFinished();
        }

        @Override
        public boolean isScaling() {
            return mScaleAnimator.isRunning();
        }

        private int estimateMinX(int index, int leftPos, int viewWidth) {
            return leftPos - (index + 100) * (viewWidth + mViewGapInPixel);
        }

        private int estimateMaxX(int index, int leftPos, int viewWidth) {
            return leftPos
                    + (mDataAdapter.getTotalNumber() - index + 100)
                    * (viewWidth + mViewGapInPixel);
        }

        /** Zoom all the way in or out on the image at the given pivot point. */
        private void zoomAt(final ViewItem current, final float focusX, final float focusY) {
            // End previous zoom animation, if any
            if (mZoomAnimator != null) {
                mZoomAnimator.end();
            }
            // Calculate end scale
            final float maxScale = getCurrentDataMaxScale(false);
            final float endScale = mScale < maxScale - maxScale * TOLERANCE
                    ? maxScale : FULL_SCREEN_SCALE;

            mZoomAnimator = new ValueAnimator();
            mZoomAnimator.setFloatValues(mScale, endScale);
            mZoomAnimator.setDuration(ZOOM_ANIMATION_DURATION_MS);
            mZoomAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (mScale == FULL_SCREEN_SCALE) {
                        if (mFullScreenUIHidden) {
                            onLeaveFullScreenUiHidden();
                        } else {
                            onLeaveFullScreen();
                        }
                        setSurroundingViewsVisible(false);
                    } else if (inZoomView()) {
                        onLeaveZoomView();
                    }
                    cancelLoadingZoomedImage();
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    // Make sure animation ends up having the correct scale even
                    // if it is cancelled before it finishes
                    if (mScale != endScale) {
                        current.postScale(focusX, focusY, endScale / mScale, mDrawArea.width(),
                                mDrawArea.height());
                        mScale = endScale;
                    }

                    if (inFullScreen()) {
                        setSurroundingViewsVisible(true);
                        mZoomView.setVisibility(GONE);
                        current.resetTransform();
                        onEnterFullScreenUiHidden();
                    } else {
                        mController.loadZoomedImage();
                        onEnterZoomView();
                    }
                    mZoomAnimator = null;
                    zoomAtIndexChanged();
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    // Do nothing.
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                    // Do nothing.
                }
            });

            mZoomAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float newScale = (Float) animation.getAnimatedValue();
                    float postScale = newScale / mScale;
                    mScale = newScale;
                    current.postScale(focusX, focusY, postScale, mDrawArea.width(),
                            mDrawArea.height());
                }
            });
            mZoomAnimator.start();
        }

        @Override
        public void scroll(float deltaX) {
            if (!stopScrolling(false)) {
                return;
            }
            mCenterX += deltaX;

            boolean stopScroll = clampCenterX();
            if (stopScroll) {
                mController.stopScrolling(true);
            }
            invalidate();
        }

        @Override
        public void fling(float velocityX) {
            if (!stopScrolling(false)) {
                return;
            }
            final ViewItem item = mViewItems[BUFFER_CENTER];
            if (item == null) {
                return;
            }

            float scaledVelocityX = velocityX / mScale;
            if (inFullScreen() && scaledVelocityX < 0) {
                // Swipe left in camera preview.
                goToFilmstrip();
            }

            int w = getWidth();
            // Estimation of possible length on the left. To ensure the
            // velocity doesn't become too slow eventually, we add a huge number
            // to the estimated maximum.
            int minX = estimateMinX(item.getAdapterIndex(), item.getLeftPosition(), w);
            // Estimation of possible length on the right. Likewise, exaggerate
            // the possible maximum too.
            int maxX = estimateMaxX(item.getAdapterIndex(), item.getLeftPosition(), w);

            mScrollGesture.fling(mCenterX, 0, (int) -velocityX, 0, minX, maxX, 0, 0);
        }

        void flingInsideZoomView(float velocityX, float velocityY) {
            if (!inZoomView()) {
                return;
            }

            final ViewItem current = mViewItems[BUFFER_CENTER];
            if (current == null) {
                return;
            }

            final int factor = DECELERATION_FACTOR;
            // Deceleration curve for distance:
            // S(t) = s + (e - s) * (1 - (1 - t/T) ^ factor)
            // Need to find the ending distance (e), so that the starting
            // velocity is the velocity of fling.
            // Velocity is the derivative of distance
            // V(t) = (e - s) * factor * (-1) * (1 - t/T) ^ (factor - 1) * (-1/T)
            //      = (e - s) * factor * (1 - t/T) ^ (factor - 1) / T
            // Since V(0) = V0, we have e = T / factor * V0 + s

            // Duration T should be long enough so that at the end of the fling,
            // image moves at 1 pixel/s for about P = 50ms = 0.05s
            // i.e. V(T - P) = 1
            // V(T - P) = V0 * (1 - (T -P) /T) ^ (factor - 1) = 1
            // T = P * V0 ^ (1 / (factor -1))

            final float velocity = Math.max(Math.abs(velocityX), Math.abs(velocityY));
            // Dynamically calculate duration
            final float duration = (float) (FLING_COASTING_DURATION_S
                    * Math.pow(velocity, (1f / (factor - 1f))));

            final float translationX = current.getTranslationX() * mScale;
            final float translationY = current.getTranslationY() * mScale;

            final ValueAnimator decelerationX = ValueAnimator.ofFloat(translationX,
                    translationX + duration / factor * velocityX);
            final ValueAnimator decelerationY = ValueAnimator.ofFloat(translationY,
                    translationY + duration / factor * velocityY);

            decelerationY.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float transX = (Float) decelerationX.getAnimatedValue();
                    float transY = (Float) decelerationY.getAnimatedValue();

                    current.updateTransform(transX, transY, mScale,
                            mScale, mDrawArea.width(), mDrawArea.height());
                }
            });

            mFlingAnimator = new AnimatorSet();
            mFlingAnimator.play(decelerationX).with(decelerationY);
            mFlingAnimator.setDuration((int) (duration * 1000));
            mFlingAnimator.setInterpolator(new TimeInterpolator() {
                @Override
                public float getInterpolation(float input) {
                    return (float) (1.0f - Math.pow((1.0f - input), factor));
                }
            });
            mFlingAnimator.addListener(new Animator.AnimatorListener() {
                private boolean mCancelled = false;

                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!mCancelled) {
                        loadZoomedImage();
                    }
                    mFlingAnimator = null;
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    mCancelled = true;
                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            mFlingAnimator.start();
        }

        @Override
        public boolean stopScrolling(boolean forced) {
            if (!isScrolling()) {
                return true;
            } else if (!mCanStopScroll && !forced) {
                return false;
            }

            mScrollGesture.forceFinished(true);
            return true;
        }

        private void stopScale() {
            mScaleAnimator.cancel();
        }

        @Override
        public void scrollToPosition(int position, int duration, boolean interruptible) {
            if (mViewItems[BUFFER_CENTER] == null) {
                return;
            }
            mCanStopScroll = interruptible;
            mScrollGesture.startScroll(mCenterX, position - mCenterX, duration);
        }

        @Override
        public boolean goToNextItem() {
            return goToItem(BUFFER_CENTER + 1);
        }

        @Override
        public boolean goToPreviousItem() {
            return goToItem(BUFFER_CENTER - 1);
        }

        private boolean goToItem(int itemIndex) {
            final ViewItem nextItem = mViewItems[itemIndex];
            if (nextItem == null) {
                return false;
            }
            stopScrolling(true);
            scrollToPosition(nextItem.getCenterX(), GEOMETRY_ADJUST_TIME_MS * 2, false);

            return true;
        }

        private void scaleTo(float scale, int duration) {
            if (mViewItems[BUFFER_CENTER] == null) {
                return;
            }
            stopScale();
            mScaleAnimator.setDuration(duration);
            mScaleAnimator.setFloatValues(mScale, scale);
            mScaleAnimator.start();
        }

        @Override
        public void goToFilmstrip() {
            if (mViewItems[BUFFER_CENTER] == null) {
                return;
            }
            if (mScale == FILM_STRIP_SCALE) {
                return;
            }
            scaleTo(FILM_STRIP_SCALE, GEOMETRY_ADJUST_TIME_MS);

            final ViewItem currItem = mViewItems[BUFFER_CENTER];
            final ViewItem nextItem = mViewItems[BUFFER_CENTER + 1];

            if (mScale == FILM_STRIP_SCALE) {
                onLeaveFilmstrip();
            }
        }

        @Override
        public void goToFullScreen() {
            if (inFullScreen()) {
                return;
            }

            scaleTo(FULL_SCREEN_SCALE, GEOMETRY_ADJUST_TIME_MS);
        }

        private void cancelFlingAnimation() {
            // Cancels flinging for zoomed images
            if (isFlingAnimationRunning()) {
                mFlingAnimator.cancel();
            }
        }

        private void cancelZoomAnimation() {
            if (isZoomAnimationRunning()) {
                mZoomAnimator.cancel();
            }
        }

        private void setSurroundingViewsVisible(boolean visible) {
            // Hide everything on the left
            // TODO: Need to find a better way to toggle the visibility of views
            // around the current view.
            for (int i = 0; i < BUFFER_CENTER; i++) {
                if (mViewItems[i] != null) {
                    mViewItems[i].setVisibility(visible ? VISIBLE : INVISIBLE);
                }
            }
        }

        private Uri getCurrentUri() {
            ViewItem curr = mViewItems[BUFFER_CENTER];
            if (curr == null) {
                return Uri.EMPTY;
            }
            return mDataAdapter.getFilmstripItemAt(curr.getAdapterIndex()).getData().getUri();
        }

        /**
         * Here we only support up to 1:1 image zoom (i.e. a 100% view of the
         * actual pixels). The max scale that we can apply on the view should
         * make the view same size as the image, in pixels.
         */
        private float getCurrentDataMaxScale(boolean allowOverScale) {
            ViewItem curr = mViewItems[BUFFER_CENTER];
            if (curr == null) {
                return FULL_SCREEN_SCALE;
            }
            FilmstripItem imageData = mDataAdapter.getFilmstripItemAt(curr.getAdapterIndex());
            if (imageData == null || !imageData.getAttributes().canZoomInPlace()) {
                return FULL_SCREEN_SCALE;
            }
            float imageWidth = imageData.getDimensions().getWidth();
            if (imageData.getOrientation() == 90
                    || imageData.getOrientation() == 270) {
                imageWidth = imageData.getDimensions().getHeight();
            }
            float scale = imageWidth / curr.getWidth();
            if (allowOverScale) {
                // In addition to the scale we apply to the view for 100% view
                // (i.e. each pixel on screen corresponds to a pixel in image)
                // we allow scaling beyond that for better detail viewing.
                scale *= mOverScaleFactor;
            }
            return scale;
        }

        private void loadZoomedImage() {
            if (!inZoomView()) {
                return;
            }
            ViewItem curr = mViewItems[BUFFER_CENTER];
            if (curr == null) {
                return;
            }
            FilmstripItem imageData = mDataAdapter.getFilmstripItemAt(curr.getAdapterIndex());
            if (!imageData.getAttributes().canZoomInPlace()) {
                return;
            }
            Uri uri = getCurrentUri();
            RectF viewRect = curr.getViewRect();
            if (uri == null || uri == Uri.EMPTY) {
                return;
            }
            int orientation = imageData.getOrientation();
            mZoomView.loadBitmap(uri, orientation, viewRect);
        }

        private void cancelLoadingZoomedImage() {
            mZoomView.cancelPartialDecodingTask();
        }

        @Override
        public void goToFirstItem() {
            if (mViewItems[BUFFER_CENTER] == null) {
                return;
            }
            resetZoomView();
            // TODO: animate to camera if it is still in the mViewItems buffer
            // versus a full reload which will perform an immediate transition
            reload();
        }

        public boolean inZoomView() {
            return FilmstripView.this.inZoomView();
        }

        public boolean isFlingAnimationRunning() {
            return mFlingAnimator != null && mFlingAnimator.isRunning();
        }

        public boolean isZoomAnimationRunning() {
            return mZoomAnimator != null && mZoomAnimator.isRunning();
        }

        @Override
        public boolean isVisible(FilmstripItem data) {
            for (ViewItem viewItem : mViewItems) {
                if (data != null && viewItem != null && viewItem.getVisibility() == VISIBLE
                        && data.equals(viewItem.mData)) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean isCurrentItemCentered() {
        return mViewItems[BUFFER_CENTER].getCenterX() == mCenterX;
    }

    private static class FilmstripScrollGesture {
        public interface Listener {
            public void onScrollUpdate(int currX, int currY);

            public void onScrollEnd();
        }

        private final Handler mHandler;
        private final Listener mListener;

        private final Scroller mScroller;

        private final ValueAnimator mXScrollAnimator;
        private final Runnable mScrollChecker = new Runnable() {
            @Override
            public void run() {
                boolean newPosition = mScroller.computeScrollOffset();
                if (!newPosition) {
                    Log.d(TAG, "[fling] onScrollEnd from computeScrollOffset");
                    mListener.onScrollEnd();
                    return;
                }
                mListener.onScrollUpdate(mScroller.getCurrX(), mScroller.getCurrY());
                mHandler.removeCallbacks(this);
                mHandler.post(this);
            }
        };

        private final ValueAnimator.AnimatorUpdateListener mXScrollAnimatorUpdateListener =
                new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        mListener.onScrollUpdate((Integer) animation.getAnimatedValue(), 0);
                    }
                };

        private final Animator.AnimatorListener mXScrollAnimatorListener =
                new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationCancel(Animator animation) {
                        Log.d(TAG, "[fling] mXScrollAnimatorListener.onAnimationCancel");
                        // Do nothing.
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        Log.d(TAG, "[fling] onScrollEnd from mXScrollAnimatorListener.onAnimationEnd");
                        mListener.onScrollEnd();
                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {
                        Log.d(TAG, "[fling] mXScrollAnimatorListener.onAnimationRepeat");
                        // Do nothing.
                    }

                    @Override
                    public void onAnimationStart(Animator animation) {
                        Log.d(TAG, "[fling] mXScrollAnimatorListener.onAnimationStart");
                        // Do nothing.
                    }
                };

        public FilmstripScrollGesture(Context ctx, Handler handler, Listener listener,
              TimeInterpolator interpolator) {
            mHandler = handler;
            mListener = listener;
            mScroller = new Scroller(ctx);
            mXScrollAnimator = new ValueAnimator();
            mXScrollAnimator.addUpdateListener(mXScrollAnimatorUpdateListener);
            mXScrollAnimator.addListener(mXScrollAnimatorListener);
            mXScrollAnimator.setInterpolator(interpolator);
        }

        public void fling(
                int startX, int startY,
                int velocityX, int velocityY,
                int minX, int maxX,
                int minY, int maxY) {
            mScroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY);
            runChecker();
        }

        public void startScroll(int startX, int startY, int dx, int dy) {
            mScroller.startScroll(startX, startY, dx, dy);
            runChecker();
        }

        /** Only starts and updates scroll in x-axis. */
        public void startScroll(int startX, int dx, int duration) {
            mXScrollAnimator.cancel();
            mXScrollAnimator.setDuration(duration);
            mXScrollAnimator.setIntValues(startX, startX + dx);
            mXScrollAnimator.start();
        }

        public boolean isFinished() {
            return (mScroller.isFinished() && !mXScrollAnimator.isRunning());
        }

        public void forceFinished(boolean finished) {
            mScroller.forceFinished(finished);
            if (finished) {
                mXScrollAnimator.cancel();
            }
        }

        private void runChecker() {
            if (mHandler == null || mListener == null) {
                return;
            }
            mHandler.removeCallbacks(mScrollChecker);
            mHandler.post(mScrollChecker);
        }
    }

    private class FilmstripGestures implements FilmstripGestureRecognizer.Listener {

        private static final int SCROLL_DIR_NONE = 0;
        private static final int SCROLL_DIR_VERTICAL = 1;
        private static final int SCROLL_DIR_HORIZONTAL = 2;
        // Indicating the current trend of scaling is up (>1) or down (<1).
        private float mScaleTrend;
        private float mMaxScale;
        private int mScrollingDirection = SCROLL_DIR_NONE;
        private long mLastDownTime;
        private float mLastDownY;

        private ViewItem mCurrentlyScalingItem;

        @Override
        public boolean onSingleTapUp(float x, float y) {
            ViewItem centerItem = mViewItems[BUFFER_CENTER];
            if (inFilmstrip()) {
                if (centerItem != null && centerItem.areaContains(x, y)) {
                    mController.goToFullScreen();
                    return true;
                }
            } else if (inFullScreen()) {
                if (mFullScreenUIHidden) {
                    onLeaveFullScreenUiHidden();
                    onEnterFullScreen();
                } else {
                    onLeaveFullScreen();
                    onEnterFullScreenUiHidden();
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean onDoubleTap(float x, float y) {
            ViewItem current = mViewItems[BUFFER_CENTER];
            if (current == null) {
                return false;
            }
            if (inFilmstrip()) {
                mController.goToFullScreen();
                return true;
            } else if (mScale < FULL_SCREEN_SCALE) {
                return false;
            }
            if (!mController.stopScrolling(false)) {
                return false;
            }
            if (inFullScreen()) {
                mController.zoomAt(current, x, y);
                renderFullRes(BUFFER_CENTER);
                return true;
            } else if (mScale > FULL_SCREEN_SCALE) {
                // In zoom view.
                mController.zoomAt(current, x, y);
            }
            return false;
        }

        @Override
        public boolean onDown(float x, float y) {
            mLastDownTime = SystemClock.uptimeMillis();
            mLastDownY = y;
            mController.cancelFlingAnimation();
            if (!mController.stopScrolling(false)) {
                return false;
            }

            return true;
        }

        @Override
        public boolean onUp(float x, float y) {
            ViewItem currItem = mViewItems[BUFFER_CENTER];
            if (currItem == null) {
                return false;
            }
            if (mController.isZoomAnimationRunning() || mController.isFlingAnimationRunning()) {
                return false;
            }
            if (inZoomView()) {
                mController.loadZoomedImage();
                return true;
            }
            float promoteHeight = getHeight() * PROMOTE_HEIGHT_RATIO;
            float velocityPromoteHeight = getHeight() * VELOCITY_PROMOTE_HEIGHT_RATIO;
            mIsUserScrolling = false;
            mScrollingDirection = SCROLL_DIR_NONE;
            // Finds items promoted/demoted.
            float speedY = Math.abs(y - mLastDownY)
                    / (SystemClock.uptimeMillis() - mLastDownTime);
            for (int i = 0; i < BUFFER_SIZE; i++) {
                if (mViewItems[i] == null) {
                    continue;
                }
                float transY = mViewItems[i].getTranslationY();
                if (transY == 0) {
                    continue;
                }
                int index = mViewItems[i].getAdapterIndex();

                if (mDataAdapter.getFilmstripItemAt(index).getAttributes().canSwipeAway()
                        && ((transY > promoteHeight)
                            || (transY > velocityPromoteHeight && speedY > PROMOTE_VELOCITY))) {
                    demoteData(index);
                } else if (mDataAdapter.getFilmstripItemAt(index).getAttributes().canSwipeAway()
                        && (transY < -promoteHeight
                            || (transY < -velocityPromoteHeight && speedY > PROMOTE_VELOCITY))) {
                    promoteData(index);
                } else {
                    // put the view back.
                    slideViewBack(mViewItems[i]);
                }
            }

            // The data might be changed. Re-check.
            currItem = mViewItems[BUFFER_CENTER];
            if (currItem == null) {
                return true;
            }

            int currId = currItem.getAdapterIndex();
            if (mAdapterIndexUserIsScrollingOver == 0 && currId != 0) {
                // Special case to go to filmstrip when the user scroll away
                // from the camera preview and the current one is not the
                // preview anymore.
                mController.goToFilmstrip();
                mAdapterIndexUserIsScrollingOver = currId;
            }
            scrollCurrentItemToCenter();
            return false;
        }

        @Override
        public void onLongPress(float x, float y) {
            final int index = getCurrentItemAdapterIndex();
            if (index == -1) {
                return;
            }
            mListener.onFocusedDataLongPressed(index);
        }

        @Override
        public boolean onScroll(float x, float y, float dx, float dy) {
            final ViewItem currItem = mViewItems[BUFFER_CENTER];
            if (currItem == null) {
                return false;
            }

            hideZoomView();
            // When image is zoomed in to be bigger than the screen
            if (inZoomView()) {
                ViewItem curr = mViewItems[BUFFER_CENTER];
                float transX = curr.getTranslationX() * mScale - dx;
                float transY = curr.getTranslationY() * mScale - dy;
                curr.updateTransform(transX, transY, mScale, mScale, mDrawArea.width(),
                        mDrawArea.height());
                return true;
            }
            int deltaX = (int) (dx / mScale);
            // Forces the current scrolling to stop.
            mController.stopScrolling(true);
            if (!mIsUserScrolling) {
                mIsUserScrolling = true;
                mAdapterIndexUserIsScrollingOver =
                      mViewItems[BUFFER_CENTER].getAdapterIndex();
            }
            if (inFilmstrip()) {
                // Disambiguate horizontal/vertical first.
                if (mScrollingDirection == SCROLL_DIR_NONE) {
                    mScrollingDirection = (Math.abs(dx) > Math.abs(dy)) ? SCROLL_DIR_HORIZONTAL :
                            SCROLL_DIR_VERTICAL;
                }
                if (mScrollingDirection == SCROLL_DIR_HORIZONTAL) {
                    if (mCenterX == currItem.getCenterX() && currItem.getAdapterIndex() == 0 &&
                          dx < 0) {
                        // Already at the beginning, don't process the swipe.
                        mIsUserScrolling = false;
                        mScrollingDirection = SCROLL_DIR_NONE;
                        return false;
                    }
                    mController.scroll(deltaX);
                } else {
                    // Vertical part. Promote or demote.
                    int hit = 0;
                    Rect hitRect = new Rect();
                    for (; hit < BUFFER_SIZE; hit++) {
                        if (mViewItems[hit] == null) {
                            continue;
                        }
                        mViewItems[hit].getHitRect(hitRect);
                        if (hitRect.contains((int) x, (int) y)) {
                            break;
                        }
                    }
                    if (hit == BUFFER_SIZE) {
                        // Hit none.
                        return true;
                    }

                    FilmstripItem data = mDataAdapter.getFilmstripItemAt(
                          mViewItems[hit].getAdapterIndex());
                    float transY = mViewItems[hit].getTranslationY() - dy / mScale;
                    if (!data.getAttributes().canSwipeAway() &&
                            transY > 0f) {
                        transY = 0f;
                    }
                    if (!data.getAttributes().canSwipeAway() &&
                            transY < 0f) {
                        transY = 0f;
                    }
                    mViewItems[hit].setTranslationY(transY);
                }
            } else if (inFullScreen()) {
                if (mViewItems[BUFFER_CENTER] == null || (deltaX < 0 && mCenterX <=
                        currItem.getCenterX() && currItem.getAdapterIndex() == 0)) {
                    return false;
                }
                // Multiplied by 1.2 to make it more easy to swipe.
                mController.scroll((int) (deltaX * 1.2));
            }
            invalidate();

            return true;
        }

        @Override
        public boolean onMouseScroll(float hscroll, float vscroll) {
            final float scroll;

            hscroll *= MOUSE_SCROLL_FACTOR;
            vscroll *= MOUSE_SCROLL_FACTOR;

            if (vscroll != 0f) {
                scroll = vscroll;
            } else {
                scroll = hscroll;
            }

            if (inFullScreen()) {
                onFling(-scroll, 0f);
            } else if (inZoomView()) {
                onScroll(0f, 0f, hscroll, vscroll);
            } else {
                onScroll(0f, 0f, scroll, 0f);
            }

            return true;
        }

        @Override
        public boolean onFling(float velocityX, float velocityY) {
            final ViewItem currItem = mViewItems[BUFFER_CENTER];
            if (currItem == null) {
                return false;
            }

            if (inZoomView()) {
                // Fling within the zoomed image
                mController.flingInsideZoomView(velocityX, velocityY);
                return true;
            }
            if (Math.abs(velocityX) < Math.abs(velocityY)) {
                // ignore vertical fling.
                return true;
            }

            // In full-screen, fling of a velocity above a threshold should go
            // to the next/prev photos
            if (mScale == FULL_SCREEN_SCALE) {
                int currItemCenterX = currItem.getCenterX();

                if (velocityX > 0) { // left
                    if (mCenterX > currItemCenterX) {
                        // The visually previous item is actually the current
                        // item.
                        mController.scrollToPosition(
                                currItemCenterX, GEOMETRY_ADJUST_TIME_MS, true);
                        return true;
                    }
                    ViewItem prevItem = mViewItems[BUFFER_CENTER - 1];
                    if (prevItem == null) {
                        return false;
                    }
                    mController.scrollToPosition(
                            prevItem.getCenterX(), GEOMETRY_ADJUST_TIME_MS, true);
                } else { // right
                    if (mController.stopScrolling(false)) {
                        if (mCenterX < currItemCenterX) {
                            // The visually next item is actually the current
                            // item.
                            mController.scrollToPosition(
                                    currItemCenterX, GEOMETRY_ADJUST_TIME_MS, true);
                            return true;
                        }
                        final ViewItem nextItem = mViewItems[BUFFER_CENTER + 1];
                        if (nextItem == null) {
                            return false;
                        }
                        mController.scrollToPosition(
                                nextItem.getCenterX(), GEOMETRY_ADJUST_TIME_MS, true);
                    }
                }
            }


            if (mScale == FILM_STRIP_SCALE) {
                mController.fling(velocityX);
            }
            return true;
        }

        @Override
        public boolean onScaleBegin(float focusX, float focusY) {
            hideZoomView();

            // This ensures that the item currently being manipulated
            // is locked at full opacity.
            mCurrentlyScalingItem = mViewItems[BUFFER_CENTER];
            if (mCurrentlyScalingItem != null) {
                mCurrentlyScalingItem.lockAtFullOpacity();
            }

            mScaleTrend = 1f;
            // If the image is smaller than screen size, we should allow to zoom
            // in to full screen size
            mMaxScale = Math.max(mController.getCurrentDataMaxScale(true), FULL_SCREEN_SCALE);
            return true;
        }

        @Override
        public boolean onScale(float focusX, float focusY, float scale) {
            mScaleTrend = mScaleTrend * 0.3f + scale * 0.7f;
            float newScale = mScale * scale;
            if (mScale < FULL_SCREEN_SCALE && newScale < FULL_SCREEN_SCALE) {
                if (newScale <= FILM_STRIP_SCALE) {
                    newScale = FILM_STRIP_SCALE;
                }
                // Scaled view is smaller than or equal to screen size both
                // before and after scaling
                if (mScale != newScale) {
                    if (mScale == FILM_STRIP_SCALE) {
                        onLeaveFilmstrip();
                    }
                    if (newScale == FILM_STRIP_SCALE) {
                        onEnterFilmstrip();
                    }
                }
                mScale = newScale;
                invalidate();
            } else if (mScale < FULL_SCREEN_SCALE && newScale >= FULL_SCREEN_SCALE) {
                // Going from smaller than screen size to bigger than or equal
                // to screen size
                if (mScale == FILM_STRIP_SCALE) {
                    onLeaveFilmstrip();
                }
                mScale = FULL_SCREEN_SCALE;
                onEnterFullScreen();
                mController.setSurroundingViewsVisible(false);
                invalidate();
            } else if (mScale >= FULL_SCREEN_SCALE && newScale < FULL_SCREEN_SCALE) {
                // Going from bigger than or equal to screen size to smaller
                // than screen size
                if (inFullScreen()) {
                    if (mFullScreenUIHidden) {
                        onLeaveFullScreenUiHidden();
                    } else {
                        onLeaveFullScreen();
                    }
                } else {
                    onLeaveZoomView();
                }
                mScale = newScale;
                renderThumbnail(BUFFER_CENTER);
                onEnterFilmstrip();
                invalidate();
            } else {
                // Scaled view bigger than or equal to screen size both before
                // and after scaling
                if (!inZoomView()) {
                    mController.setSurroundingViewsVisible(false);
                }
                ViewItem curr = mViewItems[BUFFER_CENTER];
                // Make sure the image is not overly scaled
                newScale = Math.min(newScale, mMaxScale);
                if (newScale == mScale) {
                    return true;
                }
                float postScale = newScale / mScale;
                curr.postScale(focusX, focusY, postScale, mDrawArea.width(), mDrawArea.height());
                mScale = newScale;
                if (mScale == FULL_SCREEN_SCALE) {
                    onEnterFullScreen();
                } else {
                    onEnterZoomView();
                }
                renderFullRes(BUFFER_CENTER);
            }
            return true;
        }

        @Override
        public void onScaleEnd() {
            // Once the item is no longer under direct manipulation, unlock
            // the opacity so it can be set by other parts of the layout code.
            if (mCurrentlyScalingItem != null) {
                mCurrentlyScalingItem.unlockOpacity();
            }

            zoomAtIndexChanged();
            if (mScale > FULL_SCREEN_SCALE + TOLERANCE) {
                return;
            }
            mController.setSurroundingViewsVisible(true);
            if (mScale <= FILM_STRIP_SCALE + TOLERANCE) {
                mController.goToFilmstrip();
            } else if (mScaleTrend > 1f || mScale > FULL_SCREEN_SCALE - TOLERANCE) {
                if (inZoomView()) {
                    mScale = FULL_SCREEN_SCALE;
                    resetZoomView();
                }
                mController.goToFullScreen();
            } else {
                mController.goToFilmstrip();
            }
            mScaleTrend = 1f;
        }
    }
}
