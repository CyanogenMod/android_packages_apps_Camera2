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
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

import com.android.camera.CameraActivity;
import com.android.camera.data.LocalData;
import com.android.camera.ui.FilmStripView.ImageData.PanoramaSupportCallback;
import com.android.camera.ui.FilmstripBottomControls.BottomControlsListener;
import com.android.camera.util.PhotoSphereHelper.PanoramaViewHelper;
import com.android.camera2.R;

import java.util.Arrays;

public class FilmStripView extends ViewGroup implements BottomControlsListener {
    private static final String TAG = "CAM_FilmStripView";

    private static final int BUFFER_SIZE = 5;
    private static final int GEOMETRY_ADJUST_TIME_MS = 400;
    private static final int SNAP_IN_CENTER_TIME_MS = 600;
    private static final int ZOOM_ANIMATION_DURATION_MS = 200;
    private static final float FILM_STRIP_SCALE = 0.6f;
    private static final float FULL_SCREEN_SCALE = 1f;

    private static final float TOLERANCE = 0.1f;
    // Only check for intercepting touch events within first 500ms
    private static final int SWIPE_TIME_OUT = 500;

    private CameraActivity mActivity;
    private FilmStripGestureRecognizer mGestureRecognizer;
    private DataAdapter mDataAdapter;
    private int mViewGap;
    private final Rect mDrawArea = new Rect();

    private final int mCurrentItem = (BUFFER_SIZE - 1) / 2;
    private float mScale;
    private MyController mController;
    private int mCenterX = -1;
    private ViewItem[] mViewItem = new ViewItem[BUFFER_SIZE];
    private boolean mStopAtFirstPhoto;

    private Listener mListener;
    private ZoomView mZoomView = null;

    private MotionEvent mDown;
    private boolean mCheckToIntercept = true;
    private View mCameraView;
    private int mSlop;
    private TimeInterpolator mViewAnimInterpolator;

    private FilmstripBottomControls mBottomControls;
    private PanoramaViewHelper mPanoramaViewHelper;
    private long mLastItemId = -1;

    // This is used to resolve the misalignment problem when the device
    // orientation is changed. If the current item is in fullscreen, it might
    // be shifted because mCenterX is not adjusted with the orientation.
    // Set this to true when onSizeChanged is called to make sure we adjust
    // mCenterX accordingly.
    private boolean mAnchorPending;

    // This is true if and only if the user is scrolling,
    private boolean mIsUserScrolling;
    private ValueAnimator.AnimatorUpdateListener mViewItemUpdateListener;

    /**
     * Common interface for all images in the filmstrip.
     */
    public interface ImageData {

        /**
         * Interface that is used to tell the caller whether an image is a photo
         * sphere.
         */
        public static interface PanoramaSupportCallback {
            /**
             * Called then photo sphere info has been loaded.
             *
             * @param isPanorama whether the image is a valid photo sphere
             * @param isPanorama360 whether the photo sphere is a full 360
             *            degree horizontal panorama
             */
            void panoramaInfoAvailable(boolean isPanorama,
                    boolean isPanorama360);
        }

        // View types.
        public static final int TYPE_NONE = 0;
        public static final int TYPE_STICKY_VIEW = 1;
        public static final int TYPE_REMOVABLE_VIEW = 2;

        // Actions allowed to be performed on the image data.
        // The actions are defined bit-wise so we can use bit operations like
        // | and &.
        public static final int ACTION_NONE = 0;
        public static final int ACTION_PROMOTE = 1;
        public static final int ACTION_DEMOTE = (1 << 1);
        /**
         * For image data that supports zoom, it should also provide a valid
         * content uri.
         */
        public static final int ACTION_ZOOM = (1 << 2);

        /**
         * SIZE_FULL can be returned by {@link ImageData#getWidth()} and
         * {@link ImageData#getHeight()}. When SIZE_FULL is returned for
         * width/height, it means the the width or height will be disregarded
         * when deciding the view size of this ImageData, just use full screen
         * size.
         */
        public static final int SIZE_FULL = -2;

        /**
         * Returns the width of the image. The final layout of the view returned
         * by {@link DataAdapter#getView(android.content.Context, int)} will
         * preserve the aspect ratio of
         * {@link com.android.camera.ui.FilmStripView.ImageData#getWidth()} and
         * {@link com.android.camera.ui.FilmStripView.ImageData#getHeight()}.
         */
        public int getWidth();

        /**
         * Returns the width of the image. The final layout of the view returned
         * by {@link DataAdapter#getView(android.content.Context, int)} will
         * preserve the aspect ratio of
         * {@link com.android.camera.ui.FilmStripView.ImageData#getWidth()} and
         * {@link com.android.camera.ui.FilmStripView.ImageData#getHeight()}.
         */
        public int getHeight();

        /** Returns the image data type */
        public int getViewType();

        /**
         * Returns the coordinates of this item.
         *
         * @return A 2-element array containing {latitude, longitude}, or null,
         *         if no position is known for this item.
         */
        public double[] getLatLong();

        /**
         * Checks if the UI action is supported.
         *
         * @param action The UI actions to check.
         * @return {@code false} if at least one of the actions is not
         *         supported. {@code true} otherwise.
         */
        public boolean isUIActionSupported(int action);

        /**
         * Gives the data a hint when its view is going to be displayed.
         * {@code FilmStripView} should always call this function before showing
         * its corresponding view every time.
         */
        public void prepare();

        /**
         * Gives the data a hint when its view is going to be removed from the
         * view hierarchy. {@code FilmStripView} should always call this
         * function after its corresponding view is removed from the view
         * hierarchy.
         */
        public void recycle();

        /**
         * Asynchronously checks if the image is a photo sphere. Notified the
         * callback when the results are available.
         */
        public void isPhotoSphere(Context context, PanoramaSupportCallback callback);

        /**
         * If the item is a valid photo sphere panorama, this method will launch
         * the viewer.
         */
        public void viewPhotoSphere(PanoramaViewHelper helper);

        /** Whether this item is a photo. */
        public boolean isPhoto();

        /**
         * Returns the content URI of this data item.
         *
         * @return {@code Uri.EMPTY} if not valid.
         */
        public Uri getContentUri();
    }

    /**
     * An interfaces which defines the interactions between the
     * {@link ImageData} and the {@link FilmStripView}.
     */
    public interface DataAdapter {
        /**
         * An interface which defines the update report used to return to the
         * {@link com.android.camera.ui.FilmStripView.Listener}.
         */
        public interface UpdateReporter {
            /** Checks if the data of dataID is removed. */
            public boolean isDataRemoved(int dataID);

            /** Checks if the data of dataID is updated. */
            public boolean isDataUpdated(int dataID);
        }

        /**
         * An interface which defines the listener for data events over
         * {@link ImageData}. Usually {@link FilmStripView} itself.
         */
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

        /** Returns the total number of image data */
        public int getTotalNumber();

        /**
         * Returns the view to visually present the image data.
         *
         * @param activity The {@link Activity} context to create the view.
         * @param dataID The ID of the image data to be presented.
         * @return The view representing the image data. Null if unavailable or
         *         the {@code dataID} is out of range.
         */
        public View getView(Activity activity, int dataID);

        /**
         * Returns the {@link ImageData} specified by the ID.
         *
         * @param dataID The ID of the {@link ImageData}.
         * @return The specified {@link ImageData}. Null if not available.
         */
        public ImageData getImageData(int dataID);

        /**
         * Suggests the data adapter the maximum possible size of the layout so
         * the {@link DataAdapter} can optimize the view returned for the
         * {@link ImageData}.
         *
         * @param w Maximum width.
         * @param h Maximum height.
         */
        public void suggestViewSizeBound(int w, int h);

        /**
         * Sets the listener for data events over the ImageData.
         *
         * @param listener The listener to use.
         */
        public void setListener(Listener listener);

        /**
         * Returns {@code true} if the view of the data can be moved by swipe
         * gesture when in full-screen.
         *
         * @param dataID The ID of the data.
         * @return {@code true} if the view can be moved, {@code false}
         *         otherwise.
         */
        public boolean canSwipeInFullScreen(int dataID);
    }

    /**
     * An interface which defines the FilmStripView UI action listener.
     */
    public interface Listener {
        /**
         * Callback when the data is promoted.
         *
         * @param dataID The ID of the promoted data.
         */
        public void onDataPromoted(int dataID);

        /**
         * Callback when the data is demoted.
         *
         * @param dataID The ID of the demoted data.
         */
        public void onDataDemoted(int dataID);

        /**
         * The callback when the item enters/leaves full-screen. TODO: Call this
         * function actually.
         *
         * @param dataID The ID of the image data.
         * @param fullScreen {@code true} if the data is entering full-screen.
         *            {@code false} otherwise.
         */
        public void onDataFullScreenChange(int dataID, boolean fullScreen);

        /**
         * The callback when the item is centered/off-centered.
         *
         * @param dataID The ID of the image data.
         * @param current {@code true} if the data is the current one.
         *            {@code false} otherwise.
         */
        public void onCurrentDataChanged(int dataID, boolean current);

        /**
         * Toggles the visibility of the ActionBar.
         *
         * @param dataID The ID of the image data.
         * @return The ActionBar visibility after the toggle.
         */
        public boolean onToggleActionBarVisibility(int dataID);
    }

    /**
     * An interface which defines the controller of {@link FilmStripView}.
     */
    public interface Controller {
        public boolean isScaling();

        public void scroll(float deltaX);

        public void fling(float velocity);

        public void scrollToPosition(int position, int duration, boolean interruptible);

        public boolean goToNextItem();

        public boolean stopScrolling();

        public boolean isScrolling();

        public void goToFirstItem();

        public void goToFilmStrip();

        public void goToFullScreen();
    }

    /**
     * A helper class to tract and calculate the view coordination.
     */
    private static class ViewItem {
        private int mDataId;
        /** The position of the left of the view in the whole filmstrip. */
        private int mLeftPosition;
        private View mView;
        private RectF mViewArea;

        private ValueAnimator mTranslationXAnimator;

        /**
         * Constructor.
         *
         * @param id The id of the data from {@link DataAdapter}.
         * @param v The {@code View} representing the data.
         */
        public ViewItem(
                int id, View v, ValueAnimator.AnimatorUpdateListener listener) {
            v.setPivotX(0f);
            v.setPivotY(0f);
            mDataId = id;
            mView = v;
            mLeftPosition = -1;
            mViewArea = new RectF();
            mTranslationXAnimator = new ValueAnimator();
            mTranslationXAnimator.addUpdateListener(listener);
        }

        /** Returns the data id from {@link DataAdapter}. */
        public int getId() {
            return mDataId;
        }

        /** Sets the data id from {@link DataAdapter}. */
        public void setId(int id) {
            mDataId = id;
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
        public float getScaledTranslationY(float scale) {
            return mView.getTranslationY() / scale;
        }

        /** Returns the translation of X regarding the view scale. */
        public float getScaledTranslationX(float scale) {
            return mView.getTranslationX() / scale;
        }

        /**
         * The horizontal location of this view relative to its left position.
         * This position is post-layout, in addition to wherever the object's
         * layout placed it.
         *
         * @return The horizontal position of this view relative to its left position, in pixels.
         */
        public float getTranslationX() {
            return mView.getTranslationX();
        }

        /**
         * The vertical location of this view relative to its top position.
         * This position is post-layout, in addition to wherever the object's
         * layout placed it.
         *
         * @return The vertical position of this view relative to its top position,
         * in pixels.
         */
        public float getTranslationY() {
            return mView.getTranslationY();
        }

        /** Sets the translation of Y regarding the view scale. */
        public void setTranslationY(float transY, float scale) {
            mView.setTranslationY(transY * scale);
        }

        /** Sets the translation of X regarding the view scale. */
        public void setTranslationX(float transX, float scale) {
            mView.setTranslationX(transX * scale);
        }

        public void animateTranslationX(
                float targetX, long duration_ms, TimeInterpolator interpolator) {
            mTranslationXAnimator.setInterpolator(interpolator);
            mTranslationXAnimator.setDuration(duration_ms);
            mTranslationXAnimator.setFloatValues(mView.getTranslationX(), targetX);
            mTranslationXAnimator.start();
        }

        /** Adjusts the translation of X regarding the view scale. */
        public void translateXBy(float transX, float scale) {
            mView.setTranslationX(mView.getTranslationX() + transX * scale);
        }

        public int getCenterX() {
            return mLeftPosition + mView.getWidth() / 2;
        }

        /** Gets the view representing the data. */
        public View getView() {
            return mView;
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

        /**
         * Layouts the view in the area assuming the center of the area is at a
         * specific point of the whole filmstrip.
         *
         * @param drawArea The area when filmstrip will show in.
         * @param refCenter The absolute X coordination in the whole filmstrip
         *            of the center of {@code drawArea}.
         * @param scale The current scale of the filmstrip.
         */
        public void layoutIn(Rect drawArea, int refCenter, float scale) {
            final float translationX = (mTranslationXAnimator.isRunning() ?
                    (Float) mTranslationXAnimator.getAnimatedValue() : 0f);
            int left = (int) (drawArea.centerX() + (mLeftPosition - refCenter + translationX) * scale);
            int top = (int) (drawArea.centerY() - (mView.getMeasuredHeight() / 2) * scale);
            layoutAt(left, top);
            mView.setScaleX(scale);
            mView.setScaleY(scale);

            // update mViewArea for touch detection.
            int l = mView.getLeft();
            int t = mView.getTop();
            mViewArea.set(l, t,
                    l + mView.getWidth() * scale,
                    t + mView.getHeight() * scale);
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

        public void copyGeometry(ViewItem item) {
            setLeftPosition(item.getLeftPosition());
            View v = item.getView();
            mView.setTranslationY(v.getTranslationY());
            mView.setTranslationX(v.getTranslationX());
        }
        /**
         * Apply a scale factor (i.e. {@param postScale}) on top of current scale at
         * pivot point ({@param focusX}, {@param focusY}). Visually it should be the
         * same as post concatenating current view's matrix with specified scale.
         */
        void postScale(float focusX, float focusY, float postScale, int viewportWidth,
                       int viewportHeight) {
            float transX = getTranslationX();
            float transY = getTranslationY();
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
    }

    public FilmStripView(Context context) {
        super(context);
        init((CameraActivity) context);
    }

    /** Constructor. */
    public FilmStripView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init((CameraActivity) context);
    }

    /** Constructor. */
    public FilmStripView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init((CameraActivity) context);
    }

    private void init(CameraActivity cameraActivity) {
        // This is for positioning camera controller at the same place in
        // different orientations.
        setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        setWillNotDraw(false);
        mActivity = cameraActivity;
        mScale = 1.0f;
        mStopAtFirstPhoto = true;
        mController = new MyController(cameraActivity);
        mViewAnimInterpolator = new DecelerateInterpolator();
        mZoomView = new ZoomView(cameraActivity);
        mZoomView.setVisibility(GONE);
        addView(mZoomView);

        mGestureRecognizer =
                new FilmStripGestureRecognizer(cameraActivity, new MyGestureReceiver());
        mSlop = (int) getContext().getResources().getDimension(R.dimen.pie_touch_slop);
        mViewItemUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                // Force the geometry update to happen to layout the view items.
                mController.forceGeometryUpdate();
                invalidate();
            }
        };
    }

    /**
     * Returns the controller.
     *
     * @return The {@code Controller}.
     */
    public Controller getController() {
        return mController;
    }

    public void setListener(Listener l) {
        mListener = l;
    }

    public void setViewGap(int viewGap) {
        mViewGap = viewGap;
    }

    /**
     * Sets the helper that's to be used to open photo sphere panoramas.
     */
    public void setPanoramaViewHelper(PanoramaViewHelper helper) {
        mPanoramaViewHelper = helper;
    }

    /**
     * Checks if the data is at the center.
     *
     * @param id The id of the data to check.
     * @return {@code True} if the data is currently at the center.
     */
    protected boolean isDataAtCenter(int id) {
        if (mViewItem[mCurrentItem] == null) {
            return false;
        }
        if (mViewItem[mCurrentItem].getId() == id
                && mViewItem[mCurrentItem].getCenterX() == mCenterX) {
            return true;
        }
        return false;
    }

    private int getCurrentViewType() {
        ViewItem curr = mViewItem[mCurrentItem];
        if (curr == null) {
            return ImageData.TYPE_NONE;
        }
        return mDataAdapter.getImageData(curr.getId()).getViewType();
    }

    @Override
    public void onDraw(Canvas c) {
        if (mViewItem[mCurrentItem] != null && mController.hasNewGeometry()) {
            layoutViewItems();
            super.onDraw(c);
        }
    }

    /** Returns [width, height] preserving image aspect ratio. */
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

    private void measureViewItem(ViewItem item, int boundWidth, int boundHeight) {
        int id = item.getId();
        int[] dim = calculateChildDimension(
                mDataAdapter.getImageData(id).getWidth(),
                mDataAdapter.getImageData(id).getHeight(),
                boundWidth, boundHeight);

        item.getView().measure(
                MeasureSpec.makeMeasureSpec(
                        dim[0], MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(
                        dim[1], MeasureSpec.EXACTLY));
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

        if (mDataAdapter != null) {
            mDataAdapter.suggestViewSizeBound(boundWidth / 2, boundHeight / 2);
        }

        for (ViewItem item : mViewItem) {
            if (item != null) {
                measureViewItem(item, boundWidth, boundHeight);
            }
        }
        // Measure zoom view
        mZoomView.measure(
                MeasureSpec.makeMeasureSpec(
                        widthMeasureSpec, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(
                        heightMeasureSpec, MeasureSpec.EXACTLY));
    }

    @Override
    protected boolean fitSystemWindows(Rect insets) {
        // Since the camera preview needs this callback to layout the camera
        // controls correctly, we need to call super here.
        super.fitSystemWindows(insets);
        // After calling super, we need to return false because we have other
        // layouts such as bottom controls that needs this callback. The
        // framework behavior is to stop propagating this after the first
        // child returning true is found.
        return false;
    }

    private int findTheNearestView(int pointX) {

        int nearest = 0;
        // Find the first non-null ViewItem.
        while (nearest < BUFFER_SIZE
                && (mViewItem[nearest] == null || mViewItem[nearest].getLeftPosition() == -1)) {
            nearest++;
        }
        // No existing available ViewItem
        if (nearest == BUFFER_SIZE) {
            return -1;
        }
        int min = Math.abs(pointX - mViewItem[nearest].getCenterX());

        for (int itemID = nearest + 1; itemID < BUFFER_SIZE && mViewItem[itemID] != null; itemID++) {
            // Not measured yet.
            if (mViewItem[itemID].getLeftPosition() == -1)
                continue;

            int c = mViewItem[itemID].getCenterX();
            int dist = Math.abs(pointX - c);
            if (dist < min) {
                min = dist;
                nearest = itemID;
            }
        }
        return nearest;
    }

    private ViewItem buildItemFromData(int dataID) {
        ImageData data = mDataAdapter.getImageData(dataID);
        if (data == null) {
            return null;
        }
        data.prepare();
        View v = mDataAdapter.getView(mActivity, dataID);
        if (v == null) {
            return null;
        }
        ViewItem item = new ViewItem(dataID, v, mViewItemUpdateListener);
        v = item.getView();
        if (v != mCameraView) {
            addView(item.getView());
        } else {
            v.setVisibility(View.VISIBLE);
        }
        return item;
    }

    private void removeItem(int itemID) {
        if (itemID >= mViewItem.length || mViewItem[itemID] == null) {
            return;
        }

        ImageData data = mDataAdapter.getImageData(mViewItem[itemID].getId());
        checkForRemoval(data, mViewItem[itemID].getView());
        mViewItem[itemID] = null;
    }

    /**
     * We try to keep the one closest to the center of the screen at position
     * mCurrentItem.
     */
    private void stepIfNeeded() {
        if (!inFilmStrip() && !inFullScreen()) {
            // The good timing to step to the next view is when everything is
            // not in transition.
            return;
        }
        int nearest = findTheNearestView(mCenterX);
        // no change made.
        if (nearest == -1 || nearest == mCurrentItem)
            return;

        // Going to change the current item, notify the listener.
        if (mListener != null) {
            mListener.onCurrentDataChanged(mViewItem[mCurrentItem].getId(), false);
        }
        int adjust = nearest - mCurrentItem;
        if (adjust > 0) {
            for (int k = 0; k < adjust; k++) {
                removeItem(k);
            }
            for (int k = 0; k + adjust < BUFFER_SIZE; k++) {
                mViewItem[k] = mViewItem[k + adjust];
            }
            for (int k = BUFFER_SIZE - adjust; k < BUFFER_SIZE; k++) {
                mViewItem[k] = null;
                if (mViewItem[k - 1] != null) {
                    mViewItem[k] = buildItemFromData(mViewItem[k - 1].getId() + 1);
                }
            }
        } else {
            for (int k = BUFFER_SIZE - 1; k >= BUFFER_SIZE + adjust; k--) {
                removeItem(k);
            }
            for (int k = BUFFER_SIZE - 1; k + adjust >= 0; k--) {
                mViewItem[k] = mViewItem[k + adjust];
            }
            for (int k = -1 - adjust; k >= 0; k--) {
                mViewItem[k] = null;
                if (mViewItem[k + 1] != null) {
                    mViewItem[k] = buildItemFromData(mViewItem[k + 1].getId() - 1);
                }
            }
        }
        if (mListener != null) {
            mListener.onCurrentDataChanged(mViewItem[mCurrentItem].getId(), true);
        }
    }

    /** Don't go beyond the bound. */
    private void clampCenterX() {
        ViewItem curr = mViewItem[mCurrentItem];
        if (curr == null) {
            return;
        }

        boolean stopScroll = false;
        if (mDataAdapter.getTotalNumber() > 1 &&
                mDataAdapter.getImageData(0).getViewType() ==
                        ImageData.TYPE_STICKY_VIEW &&
                mStopAtFirstPhoto) {
            // Stop at the first photo which is the second ViewItem.
            if (curr.getId() == 1 && mCenterX < curr.getCenterX()) {
                mStopAtFirstPhoto = false;
                stopScroll = true;
            }
        } else  if (curr.getId() == 0 && mCenterX < curr.getCenterX()) {
            // Stop at the first ViewItem.
            stopScroll = true;
        } if (curr.getId() == mDataAdapter.getTotalNumber() - 1
                && mCenterX > curr.getCenterX()) {
            // Stop at the end.
            stopScroll = true;
        }

        if (stopScroll && mController.isScrolling()) {
            mCenterX = curr.getCenterX();
            mController.stopScrolling();
        }
    }

    private void adjustChildZOrder() {
        for (int i = BUFFER_SIZE - 1; i >= 0; i--) {
            if (mViewItem[i] == null)
                continue;
            bringChildToFront(mViewItem[i].getView());
        }
        // ZoomView is a special case to always be in the front.
        bringChildToFront(mZoomView);
    }

    /**
     * If the current photo is a photo sphere, this will launch the Photo Sphere
     * panorama viewer.
     */
    @Override
    public void onViewPhotoSphere() {
        ViewItem curr = mViewItem[mCurrentItem];
        if (curr != null) {
            mDataAdapter.getImageData(curr.getId()).viewPhotoSphere(mPanoramaViewHelper);
        }
    }

    @Override
    public void onEdit() {
        ImageData data = mDataAdapter.getImageData(getCurrentId());
        if (data == null || !(data instanceof LocalData)) {
            return;
        }
        mActivity.launchEditor((LocalData) data);
    }

    @Override
    public void onTinyPlanet() {
        ImageData data = mDataAdapter.getImageData(getCurrentId());
        if (data == null || !(data instanceof LocalData)) {
            return;
        }
        mActivity.launchTinyPlanetEditor((LocalData) data);
    }

    /**
     * @return The ID of the current item, or -1.
     */
    public int getCurrentId() {
        ViewItem current = mViewItem[mCurrentItem];
        if (current == null) {
            return -1;
        }
        return current.getId();
    }

    /**
     * Updates the visibility of the bottom controls depending on the current
     * data item.
     */
    private void updateBottomControls() {
        if (mBottomControls == null) {
            mBottomControls = (FilmstripBottomControls) ((View) getParent())
                    .findViewById(R.id.filmstrip_bottom_controls);
            mBottomControls.setListener(this);
        }

        final int requestId = getCurrentId();

        // Check if the item has changed since the last time we updated the
        // visibility status. Only then check of the current image is a photo
        // sphere.
        if (requestId == mLastItemId || requestId < 0) {
            return;
        }

        ImageData data = mDataAdapter.getImageData(requestId);

        // We can only edit photos, not videos.
        mBottomControls.setEditButtonVisibility(data.isPhoto());

        // If this is a photo sphere, show the button to view it. If it's a full
        // 360 photo sphere, show the tiny planet button.
        data.isPhotoSphere(mActivity, new PanoramaSupportCallback() {
            @Override
            public void panoramaInfoAvailable(final boolean isPanorama,
                    boolean isPanorama360) {
                // Make sure the returned data is for the current image.
                if (requestId == getCurrentId()) {
                    if (mListener != null) {
                        mListener.onCurrentDataChanged(requestId, true);
                    }
                    mBottomControls.setViewPhotoSphereButtonVisibility(isPanorama);
                    mBottomControls.setTinyPlanetButtonVisibility(isPanorama360);
                }
            }
        });
    }

    private void snapInCenter() {
        final ViewItem currentItem = mViewItem[mCurrentItem];
        if (currentItem == null || mController.isScrolling() || mIsUserScrolling) {
            return;
        }
        int currentViewCenter = currentItem.getCenterX();
        if (mCenterX != currentViewCenter) {
            int snapInTime = (int) (SNAP_IN_CENTER_TIME_MS
                    * ((float) Math.abs(mCenterX - currentViewCenter))
                    /  mDrawArea.width());
            mController.scrollToPosition(currentViewCenter,
                    snapInTime, false);
        }
        if (getCurrentViewType() == ImageData.TYPE_STICKY_VIEW
                && !mController.isScaling()
                && mScale != FULL_SCREEN_SCALE) {
            // Now going to full screen camera
            mController.goToFullScreen();
        }
    }

    /**
     * Translates the {@link ViewItem} on the left of the current one to match
     * the full-screen layout. In full-screen, we show only one {@link ViewItem}
     * which occupies the whole screen. The other left ones are put on the left
     * side in full scales.
     *
     * @param currItem The item ID of the current one to be translated.
     * @param drawAreaWidth The width of the current draw area.
     * @param scaleFraction A {@code float} between 0 and 1. 0 if the current
     *                      scale is {@link FILM_STRIP_SCALE}. 1 if the
     *                      current scale is {@link FULL_SCREEN_SCALE}.
     */
    private void translateLeftViewItem(
            int currItem, int drawAreaWidth, float scaleFraction) {
        if (currItem < 0 || currItem > BUFFER_SIZE - 1) {
            Log.e(TAG, "currItem id out of bound.");
            return;
        }

        final ViewItem curr = mViewItem[currItem];
        final ViewItem next = mViewItem[currItem + 1];
        if (curr == null || next == null) {
            Log.e(TAG, "Invalid view item.");
            return;
        }

        final int currCenterX = curr.getCenterX();
        final int nextCenterX = next.getCenterX();
        final int translate = (int) ((nextCenterX - drawAreaWidth
                    - currCenterX) * scaleFraction);

        curr.layoutIn(mDrawArea, mCenterX, mScale);
        curr.getView().setAlpha(1f);

        if (inFullScreen()) {
            curr.setTranslationX(translate * (mCenterX - currCenterX)
                    / (nextCenterX - currCenterX), mScale);
        } else {
            curr.setTranslationX(translate, mScale);
        }
    }

    /**
     * Fade out the {@link ViewItem} on the right of the current one in
     * full-screen layout.
     *
     * @param currItem The ID of the item to fade.
     */
    private void fadeAndScaleRightViewItem(int currItem) {
        if (currItem < 1 || currItem > BUFFER_SIZE) {
            Log.e(TAG, "currItem id out of bound.");
            return;
        }

        final ViewItem curr = mViewItem[currItem];
        final ViewItem prev = mViewItem[currItem - 1];
        if (curr == null || prev == null) {
            Log.e(TAG, "Invalid view item.");
            return;
        }

        final View currView = curr.getView();
        if (currItem > mCurrentItem + 1) {
            // Every item not right next to the mCurrentItem is invisible.
            currView.setVisibility(INVISIBLE);
            return;
        }
        final int prevCenterX = prev.getCenterX();
        if (mCenterX <= prevCenterX) {
            // Shortcut. If the position is at the center of the previous one,
            // set to invisible too.
            currView.setVisibility(INVISIBLE);
            return;
        }
        final int currCenterX = curr.getCenterX();
        final float fadeDownFraction =
                ((float) mCenterX - prevCenterX) / (currCenterX - prevCenterX);
        curr.layoutIn(mDrawArea, currCenterX,
                FILM_STRIP_SCALE + (1f - FILM_STRIP_SCALE) * fadeDownFraction);
        currView.setAlpha(fadeDownFraction);
        currView.setTranslationX(0);
        currView.setVisibility(VISIBLE);
    }

    private void layoutViewItems() {
        if (mViewItem[mCurrentItem] == null ||
                mDrawArea.width() == 0 ||
                mDrawArea.height() == 0) {
            return;
        }
        if (mAnchorPending) {
            mCenterX = mViewItem[mCurrentItem].getCenterX();
            mAnchorPending = false;
        }

        if (mController.hasNewGeometry()) {
            mCenterX = mController.getNewPosition();
            mScale = mController.getNewScale();
        }

        clampCenterX();

        /**
         * Transformed scale fraction between 0 and 1. 0 if the scale is
         * {@link FILM_STRIP_SCALE}. 1 if the scale is {@link FULL_SCREEN_SCALE}
         * .
         */
        final float scaleFraction = mViewAnimInterpolator.getInterpolation(
                (mScale - FILM_STRIP_SCALE) / (FULL_SCREEN_SCALE - FILM_STRIP_SCALE));
        final int fullScreenWidth = mDrawArea.width() + mViewGap;

        // Layout the current ViewItem first.
        if (scaleFraction == 1f) {
            if (mCenterX < mViewItem[mCurrentItem].getCenterX()) {
                // In full-screen and it's not the first one and mCenterX is on
                // the left of the center, we draw the current one to "fade down".
                fadeAndScaleRightViewItem(mCurrentItem);
            } else if(mCenterX > mViewItem[mCurrentItem].getCenterX()) {
                // In full-screen and it's not the last one and mCenterX is on
                // the right of the center, we draw the current one translated.
                translateLeftViewItem(mCurrentItem, fullScreenWidth, scaleFraction);
            } else {
                mViewItem[mCurrentItem].layoutIn(mDrawArea, mCenterX, mScale);
                mViewItem[mCurrentItem].setTranslationX(0f, mScale);
                mViewItem[mCurrentItem].getView().setAlpha(1f);
            }
        } else {
            // The normal filmstrip has no translation for the current item. If it has
            // translation before, gradually set it to zero.
            mViewItem[mCurrentItem].setTranslationX(
                    mViewItem[mCurrentItem].getScaledTranslationX(mScale) * scaleFraction,
                    mScale);
            mViewItem[mCurrentItem].layoutIn(mDrawArea, mCenterX, mScale);
        }

        // Layout the rest dependent on the current scale.

        // images on the left
        for (int itemID = mCurrentItem - 1; itemID >= 0; itemID--) {
            final ViewItem curr = mViewItem[itemID];
            if (curr == null) {
                break;
            }

            // First, layout relatively to the next one.
            final int currLeft = mViewItem[itemID + 1].getLeftPosition()
                    - curr.getView().getMeasuredWidth() - mViewGap;
            curr.setLeftPosition(currLeft);
            translateLeftViewItem(itemID, fullScreenWidth, scaleFraction);
        }

        // images on the right
        for (int itemID = mCurrentItem + 1; itemID < BUFFER_SIZE; itemID++) {
            ViewItem curr = mViewItem[itemID];
            if (curr == null) {
                continue;
            }

            // First, layout relatively to the previous one.
            ViewItem prev = mViewItem[itemID - 1];
            int currLeft =
                    prev.getLeftPosition() + prev.getView().getMeasuredWidth()
                    + mViewGap;
            curr.setLeftPosition(currLeft);
            curr.layoutIn(mDrawArea, mCenterX, mScale);
            View currView = curr.getView();
            if (scaleFraction == 1) {
                // It's in full-screen mode.
                fadeAndScaleRightViewItem(itemID);
            } else {
                if (currView.getVisibility() == INVISIBLE) {
                    currView.setVisibility(VISIBLE);
                }
                if (itemID == mCurrentItem + 1) {
                    currView.setAlpha(1f - scaleFraction);
                } else {
                    if (scaleFraction == 0f) {
                        currView.setAlpha(1f);
                    } else {
                        currView.setVisibility(INVISIBLE);
                    }
                }
                curr.setTranslationX(
                        (mViewItem[mCurrentItem].getLeftPosition() - currLeft)
                        * scaleFraction, mScale);
            }
        }

        stepIfNeeded();
        adjustChildZOrder();
        snapInCenter();
        updateBottomControls();
        mLastItemId = getCurrentId();

        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mDrawArea.left = l;
        mDrawArea.top = t;
        mDrawArea.right = r;
        mDrawArea.bottom = b;
        mZoomView.layout(mDrawArea.left, mDrawArea.top, mDrawArea.right, mDrawArea.bottom);
        // TODO: (tianliu) Need a more robust solution to decide when to re-layout
        // If in the middle of zooming, only re-layout when the layout has changed.
        if (!mController.isZoomStarted() || changed) {
            resetZoomView();
            layoutViewItems();
        }
    }

    /**
     * Clears the translation and scale that has been set on the view, cancels any loading
     * request for image partial decoding, and hides zoom view.
     * This is needed for when there is a layout change (e.g. when users re-enter the app,
     * or rotate the device, etc).
     */
    private void resetZoomView() {
        if (!mController.isZoomStarted()) {
            return;
        }
        ViewItem current = mViewItem[mCurrentItem];
        if (current == null) {
            return;
        }
        mScale = FULL_SCREEN_SCALE;
        current.resetTransform();
        mController.cancelLoadingZoomedImage();
        mZoomView.setVisibility(GONE);
        mController.setSurroundingViewsVisible(true);
    }

    // Keeps the view in the view hierarchy if it's camera preview.
    // Remove from the hierarchy otherwise.
    private void checkForRemoval(ImageData data, View v) {
        if (data.getViewType() != ImageData.TYPE_STICKY_VIEW) {
            removeView(v);
            data.recycle();
        } else {
            v.setVisibility(View.INVISIBLE);
            if (mCameraView != null && mCameraView != v) {
                removeView(mCameraView);
            }
            mCameraView = v;
        }
    }

    private void slideViewBack(ViewItem item) {
        item.animateTranslationX(
                0, GEOMETRY_ADJUST_TIME_MS, mViewAnimInterpolator);
        item.getView().animate()
                .alpha(1f)
                .setDuration(GEOMETRY_ADJUST_TIME_MS)
                .setInterpolator(mViewAnimInterpolator)
                .start();
    }

    private void animateItemRemoval(int dataID, final ImageData data) {
        int removedItem = findItemByDataID(dataID);

        // adjust the data id to be consistent
        for (int i = 0; i < BUFFER_SIZE; i++) {
            if (mViewItem[i] == null || mViewItem[i].getId() <= dataID) {
                continue;
            }
            mViewItem[i].setId(mViewItem[i].getId() - 1);
        }
        if (removedItem == -1) {
            return;
        }

        final View removedView = mViewItem[removedItem].getView();
        final int offsetX = removedView.getMeasuredWidth() + mViewGap;

        for (int i = removedItem + 1; i < BUFFER_SIZE; i++) {
            if (mViewItem[i] != null) {
                mViewItem[i].setLeftPosition(mViewItem[i].getLeftPosition() - offsetX);
            }
        }

        if (removedItem >= mCurrentItem
                && mViewItem[removedItem].getId() < mDataAdapter.getTotalNumber()) {
            // Fill the removed item by left shift when the current one or
            // anyone on the right is removed, and there's more data on the
            // right available.
            for (int i = removedItem; i < BUFFER_SIZE - 1; i++) {
                mViewItem[i] = mViewItem[i + 1];
            }

            // pull data out from the DataAdapter for the last one.
            int curr = BUFFER_SIZE - 1;
            int prev = curr - 1;
            if (mViewItem[prev] != null) {
                mViewItem[curr] = buildItemFromData(mViewItem[prev].getId() + 1);
            }

            // The animation part.
            if (inFullScreen()) {
                mViewItem[mCurrentItem].getView().setVisibility(VISIBLE);
                ViewItem nextItem = mViewItem[mCurrentItem + 1];
                if (nextItem != null) {
                    nextItem.getView().setVisibility(INVISIBLE);
                }
            }

            // Translate the views to their original places.
            for (int i = removedItem; i < BUFFER_SIZE; i++) {
                if (mViewItem[i] != null) {
                    mViewItem[i].setTranslationX(offsetX, mScale);
                }
            }

            // The end of the filmstrip might have been changed.
            // The mCenterX might be out of the bound.
            ViewItem currItem = mViewItem[mCurrentItem];
            if (currItem.getId() == mDataAdapter.getTotalNumber() - 1
                    && mCenterX > currItem.getCenterX()) {
                int adjustDiff = currItem.getCenterX() - mCenterX;
                mCenterX = currItem.getCenterX();
                for (int i = 0; i < BUFFER_SIZE; i++) {
                    if (mViewItem[i] != null) {
                        mViewItem[i].translateXBy(adjustDiff, mScale);
                    }
                }
            }
        } else {
            // fill the removed place by right shift
            mCenterX -= offsetX;

            for (int i = removedItem; i > 0; i--) {
                mViewItem[i] = mViewItem[i - 1];
            }

            // pull data out from the DataAdapter for the first one.
            int curr = 0;
            int next = curr + 1;
            if (mViewItem[next] != null) {
                mViewItem[curr] = buildItemFromData(mViewItem[next].getId() - 1);
            }

            // Translate the views to their original places.
            for (int i = removedItem; i >= 0; i--) {
                if (mViewItem[i] != null) {
                    mViewItem[i].setTranslationX(-offsetX, mScale);
                }
            }
        }

        // Now, slide every one back.
        for (int i = 0; i < BUFFER_SIZE; i++) {
            if (mViewItem[i] != null
                    && mViewItem[i].getScaledTranslationX(mScale) != 0f) {
                slideViewBack(mViewItem[i]);
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
                .setDuration(GEOMETRY_ADJUST_TIME_MS)
                .setListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        // Do nothing.
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        checkForRemoval(data, removedView);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        // Do nothing.
                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {
                        // Do nothing.
                    }
                })
                .start();
        layoutViewItems();
    }

    // returns -1 on failure.
    private int findItemByDataID(int dataID) {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            if (mViewItem[i] != null
                    && mViewItem[i].getId() == dataID) {
                return i;
            }
        }
        return -1;
    }

    private void updateInsertion(int dataID) {
        int insertedItem = findItemByDataID(dataID);
        if (insertedItem == -1) {
            // Not in the current item buffers. Check if it's inserted
            // at the end.
            if (dataID == mDataAdapter.getTotalNumber() - 1) {
                int prev = findItemByDataID(dataID - 1);
                if (prev >= 0 && prev < BUFFER_SIZE - 1) {
                    // The previous data is in the buffer and we still
                    // have room for the inserted data.
                    insertedItem = prev + 1;
                }
            }
        }

        // adjust the data id to be consistent
        for (int i = 0; i < BUFFER_SIZE; i++) {
            if (mViewItem[i] == null || mViewItem[i].getId() < dataID) {
                continue;
            }
            mViewItem[i].setId(mViewItem[i].getId() + 1);
        }
        if (insertedItem == -1) {
            return;
        }

        final ImageData data = mDataAdapter.getImageData(dataID);
        int[] dim = calculateChildDimension(
                data.getWidth(), data.getHeight(),
                getMeasuredWidth(), getMeasuredHeight());
        final int offsetX = dim[0] + mViewGap;
        ViewItem viewItem = buildItemFromData(dataID);

        if (insertedItem >= mCurrentItem) {
            if (insertedItem == mCurrentItem) {
                viewItem.setLeftPosition(mViewItem[mCurrentItem].getLeftPosition());
            }
            // Shift right to make rooms for newly inserted item.
            removeItem(BUFFER_SIZE - 1);
            for (int i = BUFFER_SIZE - 1; i > insertedItem; i--) {
                mViewItem[i] = mViewItem[i - 1];
                if (mViewItem[i] != null) {
                    mViewItem[i].setTranslationX(-offsetX, mScale);
                    slideViewBack(mViewItem[i]);
                }
            }
        } else {
            // Shift left. Put the inserted data on the left instead of the
            // found position.
            --insertedItem;
            if (insertedItem < 0) {
                return;
            }
            removeItem(0);
            for (int i = 1; i <= insertedItem; i++) {
                if (mViewItem[i] != null) {
                    mViewItem[i].setTranslationX(offsetX, mScale);
                    slideViewBack(mViewItem[i]);
                    mViewItem[i - 1] = mViewItem[i];
                }
            }
        }

        mViewItem[insertedItem] = viewItem;
        View insertedView = mViewItem[insertedItem].getView();
        insertedView.setAlpha(0f);
        insertedView.setTranslationY(getHeight() / 8);
        insertedView.animate()
                .alpha(1f)
                .translationY(0f)
                .setInterpolator(mViewAnimInterpolator)
                .setDuration(GEOMETRY_ADJUST_TIME_MS)
                .start();
        invalidate();
    }

    public void setDataAdapter(DataAdapter adapter) {
        mDataAdapter = adapter;
        mDataAdapter.suggestViewSizeBound(getMeasuredWidth(), getMeasuredHeight());
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
                if (mViewItem[mCurrentItem] == null) {
                    // empty now, simply do a reload.
                    reload();
                    return;
                }
                updateInsertion(dataID);
            }

            @Override
            public void onDataRemoved(int dataID, ImageData data) {
                animateItemRemoval(dataID, data);
            }
        });
    }

    public boolean inFilmStrip() {
        return (mScale == FILM_STRIP_SCALE);
    }

    public boolean inFullScreen() {
        return (mScale == FULL_SCREEN_SCALE);
    }

    public boolean inCameraFullscreen() {
        return isDataAtCenter(0) && inFullScreen()
                && (getCurrentViewType() == ImageData.TYPE_STICKY_VIEW);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!inFullScreen() || mController.isScrolling()) {
            return true;
        }

        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mCheckToIntercept = true;
            mDown = MotionEvent.obtain(ev);
            ViewItem viewItem = mViewItem[mCurrentItem];
            // Do not intercept touch if swipe is not enabled
            if (viewItem != null && !mDataAdapter.canSwipeInFullScreen(viewItem.getId())) {
                mCheckToIntercept = false;
            }
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
        mGestureRecognizer.onTouchEvent(ev);
        return true;
    }

    private void updateViewItem(int itemID) {
        ViewItem item = mViewItem[itemID];
        if (item == null) {
            Log.e(TAG, "trying to update an null item");
            return;
        }
        removeView(item.getView());
        ImageData data = mDataAdapter.getImageData(item.getId());
        data.recycle();

        ViewItem newItem = buildItemFromData(item.getId());
        if (newItem == null) {
            Log.e(TAG, "new item is null");
            // keep using the old data.
            data.prepare();
            addView(item.getView());
            return;
        }
        newItem.copyGeometry(item);
        mViewItem[itemID] = newItem;
    }

    /** Some of the data is changed. */
    private void update(DataAdapter.UpdateReporter reporter) {
        // No data yet.
        if (mViewItem[mCurrentItem] == null) {
            reload();
            return;
        }

        // Check the current one.
        ViewItem curr = mViewItem[mCurrentItem];
        int dataId = curr.getId();
        if (reporter.isDataRemoved(dataId)) {
            reload();
            return;
        }
        if (reporter.isDataUpdated(dataId)) {
            updateViewItem(mCurrentItem);
            final ImageData data = mDataAdapter.getImageData(dataId);
            int[] dim = calculateChildDimension(
                    data.getWidth(), data.getHeight(),
                    getMeasuredWidth(), getMeasuredHeight());
            mCenterX = curr.getLeftPosition() + dim[0] / 2;
        }

        // Check left
        for (int i = mCurrentItem - 1; i >= 0; i--) {
            curr = mViewItem[i];
            if (curr != null) {
                dataId = curr.getId();
                if (reporter.isDataRemoved(dataId) || reporter.isDataUpdated(dataId)) {
                    updateViewItem(i);
                }
            } else {
                ViewItem next = mViewItem[i + 1];
                if (next != null) {
                    mViewItem[i] = buildItemFromData(next.getId() - 1);
                }
            }
        }

        // Check right
        for (int i = mCurrentItem + 1; i < BUFFER_SIZE; i++) {
            curr = mViewItem[i];
            if (curr != null) {
                dataId = curr.getId();
                if (reporter.isDataRemoved(dataId) || reporter.isDataUpdated(dataId)) {
                    updateViewItem(i);
                }
            } else {
                ViewItem prev = mViewItem[i - 1];
                if (prev != null) {
                    mViewItem[i] = buildItemFromData(prev.getId() + 1);
                }
            }
        }
        // request a layout to find the measured width/height of the view first.
        requestLayout();
    }

    /**
     * The whole data might be totally different. Flush all and load from the
     * start. Filmstrip will be centered on the first item, i.e. the camera
     * preview.
     */
    private void reload() {
        // Remove all views from the mViewItem buffer, except the camera view.
        for (int i = 0; i < mViewItem.length; i++) {
            if (mViewItem[i] == null) {
                continue;
            }
            View v = mViewItem[i].getView();
            if (v != mCameraView) {
                removeView(v);
            }
        }

        // Clear out the mViewItems and rebuild with camera in the center.
        Arrays.fill(mViewItem, null);
        int dataNumber = mDataAdapter.getTotalNumber();
        if (dataNumber == 0) {
            return;
        }

        mViewItem[mCurrentItem] = buildItemFromData(0);
        mViewItem[mCurrentItem].setLeftPosition(0);
        if (mViewItem[mCurrentItem] == null) {
            return;
        }
        for (int i = mCurrentItem + 1; i < BUFFER_SIZE; i++) {
            mViewItem[i] = buildItemFromData(mViewItem[i - 1].getId() + 1);
            if (mViewItem[i] == null) {
                break;
            }
        }

        // Ensure that the views in mViewItem will layout the first in the
        // center of the display upon a reload.
        mCenterX = -1;

        layoutViewItems();

        mListener.onCurrentDataChanged(mViewItem[mCurrentItem].getId(), true);
    }

    private void promoteData(int itemID, int dataID) {
        if (mListener != null) {
            mListener.onDataPromoted(dataID);
        }
    }

    private void demoteData(int itemID, int dataID) {
        if (mListener != null) {
            mListener.onDataDemoted(dataID);
        }
    }

    /**
     * MyController controls all the geometry animations. It passively tells the
     * geometry information on demand.
     */
    private class MyController implements
            Controller,
            ValueAnimator.AnimatorUpdateListener,
            Animator.AnimatorListener {

        private final ValueAnimator mScaleAnimator;
        private ValueAnimator mZoomAnimator;
        private boolean mHasNewScale;
        private float mNewScale;
        private boolean mGeometryUpdateForced;

        private final Scroller mScroller;
        private boolean mHasNewPosition;
        private final DecelerateInterpolator mDecelerateInterpolator;
        private final TimeInterpolator mDecelerateAccelerateInterpolator =
                new TimeInterpolator() {
                    private final TimeInterpolator interpolator =
                            new AccelerateInterpolator();

                    @Override
                    public float getInterpolation(float v) {
                        float v2 = v * 2f;
                        return ((v2 < 1f) ?
                                mDecelerateInterpolator.getInterpolation(v2) / 2f :
                                interpolator.getInterpolation(v2 - 1f) / 2f + 0.5f);
                    }
                };

        private boolean mCanStopScroll;

        MyController(Context context) {
            mScroller = new Scroller(context);
            mHasNewPosition = false;
            mCanStopScroll = true;
            mHasNewScale = false;
            mGeometryUpdateForced = false;

            mScaleAnimator = new ValueAnimator();
            mScaleAnimator.addUpdateListener(MyController.this);
            mScaleAnimator.addListener(MyController.this);
            mDecelerateInterpolator = new DecelerateInterpolator(1.5f);
        }

        @Override
        public boolean isScrolling() {
            return !mScroller.isFinished();
        }

        @Override
        public boolean isScaling() {
            return mScaleAnimator.isRunning();
        }

        void forceGeometryUpdate() {
            mGeometryUpdateForced = true;
        }

        boolean hasNewGeometry() {
            boolean forceNewGeometry = mGeometryUpdateForced;
            mGeometryUpdateForced = false;
            mHasNewPosition = mScroller.computeScrollOffset();
            if (!mHasNewPosition) {
                mCanStopScroll = true;
            }
            // If the position is locked, then we always return true to force
            // the position value to use the locked value.
            return (mHasNewPosition || mHasNewScale || forceNewGeometry);
        }

        /**
         * Always call {@link #hasNewGeometry()} before getting the new scale
         * value.
         */
        float getNewScale() {
            if (!mHasNewScale) {
                return mScale;
            }
            mHasNewScale = false;
            return mNewScale;
        }

        /**
         * Always call {@link #hasNewGeometry()} before getting the new position
         * value.
         */
        int getNewPosition() {
            if (!mHasNewPosition) {
                return mCenterX;
            }
            return mScroller.getCurrX();
        }

        private int estimateMinX(int dataID, int leftPos, int viewWidth) {
            return leftPos - (dataID + 100) * (viewWidth + mViewGap);
        }

        private int estimateMaxX(int dataID, int leftPos, int viewWidth) {
            return leftPos
                    + (mDataAdapter.getTotalNumber() - dataID + 100)
                    * (viewWidth + mViewGap);
        }

        /** Zoom all the way in or out on the image at the given pivot point. */
        private void zoomAt(final ViewItem current, final float focusX, final float focusY) {
            // End previous zoom animation, if any
            if (mZoomAnimator != null) {
                mZoomAnimator.end();
            }
            // Calculate end scale
            final float maxScale = getCurrentDataMaxScale();
            final float endScale = mScale < maxScale - maxScale * TOLERANCE
                    ? maxScale : FULL_SCREEN_SCALE;

            mZoomAnimator = new ValueAnimator();
            mZoomAnimator.setFloatValues(mScale, endScale);
            mZoomAnimator.setDuration(ZOOM_ANIMATION_DURATION_MS);
            mZoomAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (mScale == FULL_SCREEN_SCALE) {
                        enterFullScreen();
                        setSurroundingViewsVisible(false);
                    }
                    cancelLoadingZoomedImage();
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    // Make sure animation ends up having the correct scale even
                    // if it is cancelled before it finishes
                    if (mScale != endScale) {
                        current.postScale(focusX, focusY, endScale/mScale, mDrawArea.width(),
                                mDrawArea.height());
                        mScale = endScale;
                    }

                    if (mScale == FULL_SCREEN_SCALE) {
                        setSurroundingViewsVisible(true);
                        mZoomView.setVisibility(GONE);
                        current.resetTransform();
                    } else {
                        mController.loadZoomedImage();
                    }
                    mZoomAnimator = null;
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
            if (mController.isScrolling()) {
                return;
            }
            mCenterX += deltaX;
            if (deltaX > 0) {
                mStopAtFirstPhoto = true;
            }
            invalidate();
        }

        @Override
        public void fling(float velocityX) {
            if (!stopScrolling()) {
                return;
            }
            ViewItem item = mViewItem[mCurrentItem];
            if (item == null) {
                return;
            }

            float scaledVelocityX = velocityX / mScale;
            if (inFullScreen() && getCurrentViewType() == ImageData.TYPE_STICKY_VIEW
                    && scaledVelocityX < 0) {
                // Swipe left in camera preview.
                goToFilmStrip();
            }

            int w = getWidth();
            // Estimation of possible length on the left. To ensure the
            // velocity doesn't become too slow eventually, we add a huge number
            // to the estimated maximum.
            int minX = estimateMinX(item.getId(), item.getLeftPosition(), w);
            // Estimation of possible length on the right. Likewise, exaggerate
            // the possible maximum too.
            int maxX = estimateMaxX(item.getId(), item.getLeftPosition(), w);
            mScroller.fling(mCenterX, 0, (int) -velocityX, 0, minX, maxX, 0, 0);

            layoutViewItems();
        }

        @Override
        public boolean stopScrolling() {
            if (!mCanStopScroll) {
                return false;
            }
            mScroller.forceFinished(true);
            mHasNewPosition = false;
            return true;
        }

        private void stopScale() {
            mScaleAnimator.cancel();
            mHasNewScale = false;
        }

        @Override
        public void scrollToPosition(int position, int duration, boolean interruptible) {
            if (!stopScrolling()) {
                return;
            }
            mCanStopScroll = interruptible;
            stopScrolling();
            mScroller.startScroll(mCenterX, 0, position - mCenterX,
                    0, duration);
            mHasNewPosition = true;
            invalidate();
        }

        @Override
        public boolean goToNextItem() {
            ViewItem nextItem = mViewItem[mCurrentItem + 1];
            if (nextItem == null) {
                return false;
            }
            stopScale();
            scrollToPosition(nextItem.getCenterX(), GEOMETRY_ADJUST_TIME_MS * 2, false);
            mScaleAnimator.setFloatValues(FULL_SCREEN_SCALE, FILM_STRIP_SCALE, FULL_SCREEN_SCALE);
            mScaleAnimator.setInterpolator(mDecelerateAccelerateInterpolator);
            mScaleAnimator.setDuration(GEOMETRY_ADJUST_TIME_MS * 2);
            mScaleAnimator.start();
            return true;
        }

        private void scaleTo(float scale, int duration) {
            if (mViewItem[mCurrentItem] == null) {
                return;
            }
            stopScale();
            mScaleAnimator.setDuration(duration);
            mScaleAnimator.setFloatValues(mScale, scale);
            mScaleAnimator.setInterpolator(mDecelerateInterpolator);
            mScaleAnimator.start();
            layoutViewItems();
        }

        @Override
        public void goToFilmStrip() {
            scaleTo(FILM_STRIP_SCALE, GEOMETRY_ADJUST_TIME_MS);

            mStopAtFirstPhoto = true;

            final ViewItem nextItem = mViewItem[mCurrentItem + 1];
            if (mViewItem[mCurrentItem].getId() == 0 &&
                    getCurrentViewType() == ImageData.TYPE_STICKY_VIEW &&
                    nextItem != null) {
                // Deal with the special case of swiping in camera preview.
                scrollToPosition(nextItem.getCenterX(), GEOMETRY_ADJUST_TIME_MS, false);
            }

            if (mListener != null) {
                mListener.onDataFullScreenChange(mViewItem[mCurrentItem].getId(), false);
                mBottomControls.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void goToFullScreen() {
            if (mViewItem[mCurrentItem] != null) {
                mController.scrollToPosition(mViewItem[mCurrentItem].getCenterX(),
                        GEOMETRY_ADJUST_TIME_MS, false);
            }
            enterFullScreen();

            if (inFullScreen()) {
                return;
            }
            scaleTo(1f, GEOMETRY_ADJUST_TIME_MS);
        }

        private void enterFullScreen() {
            if (mListener != null) {
                mListener.onDataFullScreenChange(mViewItem[mCurrentItem].getId(), true);
                mBottomControls.setVisibility(View.GONE);
            }
        }

        private void setSurroundingViewsVisible(boolean visible) {
            // Hide everything on the left
            // TODO: Need to find a better way to toggle the visibility of views around
            //       the current view.
            for (int i = 0; i < mCurrentItem; i++) {
                if (i == mCurrentItem || mViewItem[i] == null) {
                    continue;
                }
                mViewItem[i].getView().setVisibility(visible ? VISIBLE : INVISIBLE);
            }
        }

        private void leaveFullScreen() {
            if (mListener != null) {
                mListener.onDataFullScreenChange(mViewItem[mCurrentItem].getId(), false);
                mBottomControls.setVisibility(View.VISIBLE);
            }
        }

        private Uri getCurrentContentUri() {
            ViewItem curr = mViewItem[mCurrentItem];
            if (curr == null) {
                return Uri.EMPTY;
            }
            return mDataAdapter.getImageData(curr.getId()).getContentUri();
        }

        /**
         * Here we only support up to 1:1 image zoom (i.e. a 100% view of the
         * actual pixels). The max scale that we can apply on the view should
         * make the view same size as the image, in pixels.
         */
        private float getCurrentDataMaxScale() {
            ViewItem curr = mViewItem[mCurrentItem];
            ImageData imageData = mDataAdapter.getImageData(curr.getId());
            if (curr == null || !imageData
                    .isUIActionSupported(ImageData.ACTION_ZOOM)) {
                return FULL_SCREEN_SCALE;
            }
            float imageWidth = (float) imageData.getWidth();
            return imageWidth / (float) curr.getWidth();
        }

        private void loadZoomedImage() {
            if (!isZoomStarted()) {
                return;
            }
            ViewItem curr = mViewItem[mCurrentItem];
            if(curr == null || !mDataAdapter.getImageData(curr.getId())
                    .isUIActionSupported(ImageData.ACTION_ZOOM)) {
                return;
            }
            Uri uri = getCurrentContentUri();
            RectF viewRect = curr.getViewRect();
            if (uri == null || uri == Uri.EMPTY) {
                return;
            }
            mZoomView.loadBitmap(uri, viewRect);
        }

        private void cancelLoadingZoomedImage() {
            mZoomView.cancelPartialDecodingTask();
        }

        @Override
        public void goToFirstItem() {
            resetZoomView();
            // TODO: animate to camera if it is still in the mViewItem buffer
            // versus a full reload which will perform an immediate transition
            reload();
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            if (mViewItem[mCurrentItem] == null) {
                return;
            }
            mHasNewScale = true;
            mNewScale = (Float) animation.getAnimatedValue();
            layoutViewItems();
        }

        @Override
        public void onAnimationStart(Animator anim) {
            mHasNewScale = true;
        }

        @Override
        public void onAnimationEnd(Animator anim) {
            ViewItem item = mViewItem[mCurrentItem];
            if (item == null) {
                return;
            }
            if (mCenterX != item.getCenterX()) {
                if (inFilmStrip()) {
                    snapInCenter();
                }
            }
        }

        @Override
        public void onAnimationCancel(Animator anim) {
        }

        @Override
        public void onAnimationRepeat(Animator anim) {
        }

        public boolean isZoomStarted() {
            return mScale > FULL_SCREEN_SCALE;
        }

        public boolean isZoomAnimationRunning() {
            return mZoomAnimator != null && mZoomAnimator.isRunning();
        }
    }

    private class MyGestureReceiver implements FilmStripGestureRecognizer.Listener {
        // Indicating the current trend of scaling is up (>1) or down (<1).
        private float mScaleTrend;
        private float mMaxScale;

        @Override
        public boolean onSingleTapUp(float x, float y) {
            ViewItem centerItem = mViewItem[mCurrentItem];
            if (inFilmStrip()) {
                if (centerItem != null && centerItem.areaContains(x, y)) {
                    mController.goToFullScreen();
                    return true;
                }
            } else if (inFullScreen()) {
                int dataID = -1;
                if (centerItem != null) {
                    dataID = centerItem.getId();
                }
                boolean visible = mListener.onToggleActionBarVisibility(dataID);
                mBottomControls.setVisibility(visible ? View.VISIBLE : View.GONE);
                return true;
            }
            return false;
        }

        @Override
        public boolean onDoubleTap(float x, float y) {
            if (mScale < FULL_SCREEN_SCALE || inCameraFullscreen()) {
                return false;
            }
            ViewItem current = mViewItem[mCurrentItem];
            if (current == null) {
                return false;
            }
            if (mController.isScrolling()) {
                mController.stopScrolling();
            }
            mController.zoomAt(current, x, y);
            return false;
        }

        @Override
        public boolean onDown(float x, float y) {
            if (mController.isScrolling()) {
                mController.stopScrolling();
            }
            // A down event is usually followed by a gesture, we apply gesture on
            // the lower-res image during a gesture to ensure a responsive experience.
            // TODO: Delay this until gesture starts.
            if (mController.isZoomStarted()) {
                mController.cancelLoadingZoomedImage();
                mZoomView.setVisibility(GONE);
            }
            return true;
        }

        @Override
        public boolean onUp(float x, float y) {
            if (mController.isZoomAnimationRunning()) {
                return false;
            }
            if (mController.isZoomStarted()) {
                mController.loadZoomedImage();
                return true;
            }
            float halfH = getHeight() / 2;
            mIsUserScrolling = false;
            for (int i = 0; i < BUFFER_SIZE; i++) {
                if (mViewItem[i] == null) {
                    continue;
                }
                float transY = mViewItem[i].getScaledTranslationY(mScale);
                if (transY == 0) {
                    continue;
                }
                int id = mViewItem[i].getId();

                if (mDataAdapter.getImageData(id)
                        .isUIActionSupported(ImageData.ACTION_DEMOTE)
                        && transY > halfH) {
                    demoteData(i, id);
                } else if (mDataAdapter.getImageData(id)
                        .isUIActionSupported(ImageData.ACTION_PROMOTE)
                        && transY < -halfH) {
                    promoteData(i, id);
                } else {
                    // put the view back.
                    mViewItem[i].getView().animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(GEOMETRY_ADJUST_TIME_MS)
                            .start();
                }
            }
            snapInCenter();
            return false;
        }

        @Override
        public boolean onScroll(float x, float y, float dx, float dy) {
            if (mViewItem[mCurrentItem] == null) {
                return false;
            }
            // When image is zoomed in to be bigger than the screen
            if (mController.isZoomStarted()) {
                mController.cancelLoadingZoomedImage();
                ViewItem curr = mViewItem[mCurrentItem];
                float transX = curr.getTranslationX() - dx;
                float transY = curr.getTranslationY() - dy;
                curr.updateTransform(transX, transY, mScale, mScale, mDrawArea.width(),
                        mDrawArea.height());
                return true;
            }
            mIsUserScrolling = true;
            int deltaX = (int) (dx / mScale);
            if (inFilmStrip()) {
                if (Math.abs(dx) > Math.abs(dy)) {
                    mController.scroll(deltaX);
                } else {
                    // Vertical part. Promote or demote.
                    // int scaledDeltaY = (int) (dy * mScale);
                    int hit = 0;
                    Rect hitRect = new Rect();
                    for (; hit < BUFFER_SIZE; hit++) {
                        if (mViewItem[hit] == null) {
                            continue;
                        }
                        mViewItem[hit].getView().getHitRect(hitRect);
                        if (hitRect.contains((int) x, (int) y)) {
                            break;
                        }
                    }
                    if (hit == BUFFER_SIZE) {
                        return false;
                    }

                    ImageData data = mDataAdapter.getImageData(mViewItem[hit].getId());
                    float transY = mViewItem[hit].getScaledTranslationY(mScale) - dy / mScale;
                    if (!data.isUIActionSupported(ImageData.ACTION_DEMOTE) && transY > 0f) {
                        transY = 0f;
                    }
                    if (!data.isUIActionSupported(ImageData.ACTION_PROMOTE) && transY < 0f) {
                        transY = 0f;
                    }
                    mViewItem[hit].setTranslationY(transY, mScale);
                }
            } else if (inFullScreen()) {
                if (deltaX > 0 && getCurrentViewType() == ImageData.TYPE_STICKY_VIEW) {
                    mController.goToFilmStrip();
                }
                // Multiplied by 1.2 to make it more easy to swipe.
                mController.scroll((int) (deltaX * 1.2));
            }
            layoutViewItems();

            return true;
        }

        @Override
        public boolean onFling(float velocityX, float velocityY) {
            final ViewItem currItem = mViewItem[mCurrentItem];
            if (currItem == null) {
                return false;
            }
            if (Math.abs(velocityX) < Math.abs(velocityY)) {
                // ignore vertical fling.
                return true;
            }

            // In full-screen, fling of a velocity above a threshold should go to
            // the next/prev photos
            if (mScale == FULL_SCREEN_SCALE) {
                int currItemCenterX = currItem.getCenterX();
                if (velocityX > 0) {  // left
                    if (mCenterX > currItemCenterX) {
                        // The visually previous item is actually the current item.
                        mController.scrollToPosition(
                                currItemCenterX, GEOMETRY_ADJUST_TIME_MS, true);
                        return true;
                    }
                    ViewItem prevItem = mViewItem[mCurrentItem - 1];
                    if (prevItem == null) {
                        return false;
                    }
                    mController.scrollToPosition(
                            prevItem.getCenterX(), GEOMETRY_ADJUST_TIME_MS, true);
                } else {  // right
                    if (mCenterX < currItemCenterX) {
                        // The visually next item is actually the current item.
                        mController.scrollToPosition(
                                currItemCenterX, GEOMETRY_ADJUST_TIME_MS, true);
                        return true;
                    }
                    final ViewItem nextItem = mViewItem[mCurrentItem + 1];
                    if (nextItem == null) {
                        return false;
                    }
                    mController.scrollToPosition(
                            nextItem.getCenterX(), GEOMETRY_ADJUST_TIME_MS, true);
                }
            }

            if (mScale == FILM_STRIP_SCALE) {
                mController.fling(velocityX);
            }
            return true;
        }

        @Override
        public boolean onScaleBegin(float focusX, float focusY) {
            if (inCameraFullscreen()) {
                return false;
            }
            mScaleTrend = 1f;
            // If the image is smaller than screen size, we should allow to zoom
            // in to full screen size
            mMaxScale = Math.max(mController.getCurrentDataMaxScale(), FULL_SCREEN_SCALE);
            return true;
        }

        @Override
        public boolean onScale(float focusX, float focusY, float scale) {
            if (inCameraFullscreen()) {
                return false;
            }

            mScaleTrend = mScaleTrend * 0.3f + scale * 0.7f;
            float newScale = mScale * scale;
            if (mScale < FULL_SCREEN_SCALE && newScale < FULL_SCREEN_SCALE) {
                // Scaled view is smaller than or equal to screen size both before
                // and after scaling
                mScale = newScale;
                if (mScale <= FILM_STRIP_SCALE) {
                    mScale = FILM_STRIP_SCALE;
                }
                layoutViewItems();
            } else if (mScale < FULL_SCREEN_SCALE && newScale >= FULL_SCREEN_SCALE) {
                // Going from smaller than screen size to bigger than or equal to screen size
                mScale = FULL_SCREEN_SCALE;
                mController.enterFullScreen();
                layoutViewItems();
                mController.setSurroundingViewsVisible(false);
            } else if (mScale >= FULL_SCREEN_SCALE && newScale < FULL_SCREEN_SCALE) {
                // Going from bigger than or equal to screen size to smaller than screen size
                mScale = newScale;
                mController.leaveFullScreen();
                layoutViewItems();
                mController.setSurroundingViewsVisible(true);
            } else {
                // Scaled view bigger than or equal to screen size both before
                // and after scaling
                if (!mController.isZoomStarted()) {
                    mController.setSurroundingViewsVisible(false);
                }
                ViewItem curr = mViewItem[mCurrentItem];
                // Make sure the image is not overly scaled
                newScale = Math.min(newScale, mMaxScale);
                float postScale = newScale / mScale;
                curr.postScale(focusX, focusY, postScale, mDrawArea.width(), mDrawArea.height());
                mScale = newScale;
            }
            return true;
        }


        @Override
        public void onScaleEnd() {
            if (mScale > FULL_SCREEN_SCALE + TOLERANCE) {
                return;
            }
            mController.setSurroundingViewsVisible(true);
            if (mScale <= FILM_STRIP_SCALE + TOLERANCE) {
                mController.goToFilmStrip();
            } else if (mScaleTrend > 1f || mScale > FULL_SCREEN_SCALE - TOLERANCE) {
                if (mController.isZoomStarted()) {
                    mScale = FULL_SCREEN_SCALE;
                    resetZoomView();
                }
                mController.goToFullScreen();
            } else {
                mController.goToFilmStrip();
            }
            mScaleTrend = 1f;
        }
    }
}
