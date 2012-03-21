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
import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.ui.PositionRepository.Position;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Message;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.animation.AccelerateInterpolator;

public class PhotoView extends GLView {
    @SuppressWarnings("unused")
    private static final String TAG = "PhotoView";

    public static final int INVALID_SIZE = -1;

    private static final int MSG_TRANSITION_COMPLETE = 1;
    private static final int MSG_SHOW_LOADING = 2;
    private static final int MSG_CANCEL_EXTRA_SCALING = 3;

    private static final long DELAY_SHOW_LOADING = 250; // 250ms;

    private static final int TRANS_NONE = 0;
    private static final int TRANS_SWITCH_NEXT = 3;
    private static final int TRANS_SWITCH_PREVIOUS = 4;

    public static final int TRANS_SLIDE_IN_RIGHT = 1;
    public static final int TRANS_SLIDE_IN_LEFT = 2;
    public static final int TRANS_OPEN_ANIMATION = 5;

    private static final int LOADING_INIT = 0;
    private static final int LOADING_TIMEOUT = 1;
    private static final int LOADING_COMPLETE = 2;
    private static final int LOADING_FAIL = 3;

    private static final int ENTRY_PREVIOUS = 0;
    private static final int ENTRY_NEXT = 1;

    private static final int IMAGE_GAP = 96;
    private static final int SWITCH_THRESHOLD = 256;
    private static final float SWIPE_THRESHOLD = 300f;

    private static final float DEFAULT_TEXT_SIZE = 20;
    private static float TRANSITION_SCALE_FACTOR = 0.74f;

    // Used to calculate the scaling factor for the fading animation.
    private ZInterpolator mScaleInterpolator = new ZInterpolator(0.5f);

    // Used to calculate the alpha factor for the fading animation.
    private AccelerateInterpolator mAlphaInterpolator =
            new AccelerateInterpolator(0.9f);

    public interface PhotoTapListener {
        public void onSingleTapUp(int x, int y);
    }

    // the previous/next image entries
    private final ScreenNailEntry mScreenNails[] = new ScreenNailEntry[2];

    private final ScaleGestureDetector mScaleDetector;
    private final GestureDetector mGestureDetector;
    private final DownUpDetector mDownUpDetector;

    private PhotoTapListener mPhotoTapListener;

    private final PositionController mPositionController;

    private Model mModel;
    private StringTexture mLoadingText;
    private StringTexture mNoThumbnailText;
    private int mTransitionMode = TRANS_NONE;
    private final TileImageView mTileView;
    private EdgeView mEdgeView;
    private Texture mVideoPlayIcon;

    private boolean mShowVideoPlayIcon;
    private ProgressSpinner mLoadingSpinner;

    private SynchronizedHandler mHandler;

    private int mLoadingState = LOADING_COMPLETE;

    private int mImageRotation;

    private Path mOpenedItemPath;
    private GalleryActivity mActivity;
    private Point mImageCenter = new Point();
    private boolean mCancelExtraScalingPending;

    public PhotoView(GalleryActivity activity) {
        mActivity = activity;
        mTileView = new TileImageView(activity);
        addComponent(mTileView);
        Context context = activity.getAndroidContext();
        mEdgeView = new EdgeView(context);
        addComponent(mEdgeView);
        mLoadingSpinner = new ProgressSpinner(context);
        mLoadingText = StringTexture.newInstance(
                context.getString(R.string.loading),
                DEFAULT_TEXT_SIZE, Color.WHITE);
        mNoThumbnailText = StringTexture.newInstance(
                context.getString(R.string.no_thumbnail),
                DEFAULT_TEXT_SIZE, Color.WHITE);

        mHandler = new SynchronizedHandler(activity.getGLRoot()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_TRANSITION_COMPLETE: {
                        onTransitionComplete();
                        break;
                    }
                    case MSG_SHOW_LOADING: {
                        if (mLoadingState == LOADING_INIT) {
                            // We don't need the opening animation
                            mOpenedItemPath = null;

                            mLoadingSpinner.startAnimation();
                            mLoadingState = LOADING_TIMEOUT;
                            invalidate();
                        }
                        break;
                    }
                    case MSG_CANCEL_EXTRA_SCALING: {
                        cancelScaleGesture();
                        mPositionController.setExtraScalingRange(false);
                        mCancelExtraScalingPending = false;
                        break;
                    }
                    default: throw new AssertionError(message.what);
                }
            }
        };

        mGestureDetector = new GestureDetector(context,
                new MyGestureListener(), null, true /* ignoreMultitouch */);
        mScaleDetector = new ScaleGestureDetector(context, new MyScaleListener());
        mDownUpDetector = new DownUpDetector(new MyDownUpListener());

        for (int i = 0, n = mScreenNails.length; i < n; ++i) {
            mScreenNails[i] = new ScreenNailEntry();
        }

        mPositionController = new PositionController(this, context, mEdgeView);
        mVideoPlayIcon = new ResourceTexture(context, R.drawable.ic_control_play);
    }


    public void setModel(Model model) {
        if (mModel == model) return;
        mModel = model;
        mTileView.setModel(model);
        if (model != null) notifyOnNewImage();
    }

    public void setPhotoTapListener(PhotoTapListener listener) {
        mPhotoTapListener = listener;
    }

    private void setTileViewPosition(int centerX, int centerY, float scale) {
        TileImageView t = mTileView;

        // Calculate the move-out progress value.
        RectF bounds = mPositionController.getImageBounds();
        int left = Math.round(bounds.left);
        int right = Math.round(bounds.right);
        int width = getWidth();
        float progress = calculateMoveOutProgress(left, right, width);
        progress = Utils.clamp(progress, -1f, 1f);

        // We only want to apply the fading animation if the scrolling movement
        // is to the right.
        if (progress < 0) {
            if (right - left < width) {
                // If the picture is narrower than the view, keep it at the center
                // of the view.
                centerX = mPositionController.getImageWidth() / 2;
            } else {
                // If the picture is wider than the view (it's zoomed-in), keep
                // the left edge of the object align the the left edge of the view.
                centerX = Math.round(width / 2f / scale);
            }
            scale *= getScrollScale(progress);
            t.setAlpha(getScrollAlpha(progress));
        }

        // set the position of the tile view
        int inverseX = mPositionController.getImageWidth() - centerX;
        int inverseY = mPositionController.getImageHeight() - centerY;
        int rotation = mImageRotation;
        switch (rotation) {
            case 0: t.setPosition(centerX, centerY, scale, 0); break;
            case 90: t.setPosition(centerY, inverseX, scale, 90); break;
            case 180: t.setPosition(inverseX, inverseY, scale, 180); break;
            case 270: t.setPosition(inverseY, centerX, scale, 270); break;
            default: throw new IllegalArgumentException(String.valueOf(rotation));
        }
    }

    public void setPosition(int centerX, int centerY, float scale) {
        setTileViewPosition(centerX, centerY, scale);
        layoutScreenNails();
    }

    private void updateScreenNailEntry(int which, ImageData data) {
        if (mTransitionMode == TRANS_SWITCH_NEXT
                || mTransitionMode == TRANS_SWITCH_PREVIOUS) {
            // ignore screen nail updating during switching
            return;
        }
        ScreenNailEntry entry = mScreenNails[which];
        if (data == null) {
            entry.set(false, null, 0);
        } else {
            entry.set(true, data.bitmap, data.rotation);
        }
    }

    // -1 previous, 0 current, 1 next
    public void notifyImageInvalidated(int which) {
        switch (which) {
            case -1: {
                updateScreenNailEntry(
                        ENTRY_PREVIOUS, mModel.getPreviousImage());
                layoutScreenNails();
                invalidate();
                break;
            }
            case 1: {
                updateScreenNailEntry(ENTRY_NEXT, mModel.getNextImage());
                layoutScreenNails();
                invalidate();
                break;
            }
            case 0: {
                // mImageWidth and mImageHeight will get updated
                mTileView.notifyModelInvalidated();
                mTileView.setAlpha(1.0f);

                mImageRotation = mModel.getImageRotation();
                if (((mImageRotation / 90) & 1) == 0) {
                    mPositionController.setImageSize(
                            mTileView.mImageWidth, mTileView.mImageHeight);
                } else {
                    mPositionController.setImageSize(
                            mTileView.mImageHeight, mTileView.mImageWidth);
                }
                updateLoadingState();
                break;
            }
        }
    }

    private void updateLoadingState() {
        // Possible transitions of mLoadingState:
        //        INIT --> TIMEOUT, COMPLETE, FAIL
        //     TIMEOUT --> COMPLETE, FAIL, INIT
        //    COMPLETE --> INIT
        //        FAIL --> INIT
        if (mModel.getLevelCount() != 0 || mModel.getBackupImage() != null) {
            mHandler.removeMessages(MSG_SHOW_LOADING);
            mLoadingState = LOADING_COMPLETE;
        } else if (mModel.isFailedToLoad()) {
            mHandler.removeMessages(MSG_SHOW_LOADING);
            mLoadingState = LOADING_FAIL;
        } else if (mLoadingState != LOADING_INIT) {
            mLoadingState = LOADING_INIT;
            mHandler.removeMessages(MSG_SHOW_LOADING);
            mHandler.sendEmptyMessageDelayed(
                    MSG_SHOW_LOADING, DELAY_SHOW_LOADING);
        }
    }

    public void notifyModelInvalidated() {
        if (mModel == null) {
            updateScreenNailEntry(ENTRY_PREVIOUS, null);
            updateScreenNailEntry(ENTRY_NEXT, null);
        } else {
            updateScreenNailEntry(ENTRY_PREVIOUS, mModel.getPreviousImage());
            updateScreenNailEntry(ENTRY_NEXT, mModel.getNextImage());
        }
        layoutScreenNails();

        if (mModel == null) {
            mTileView.notifyModelInvalidated();
            mTileView.setAlpha(1.0f);
            mImageRotation = 0;
            mPositionController.setImageSize(0, 0);
            updateLoadingState();
        } else {
            notifyImageInvalidated(0);
        }
    }

    @Override
    protected boolean onTouch(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        mScaleDetector.onTouchEvent(event);
        mDownUpDetector.onTouchEvent(event);
        return true;
    }

    @Override
    protected void onLayout(
            boolean changeSize, int left, int top, int right, int bottom) {
        mTileView.layout(left, top, right, bottom);
        mEdgeView.layout(left, top, right, bottom);
        if (changeSize) {
            mPositionController.setViewSize(getWidth(), getHeight());
            for (ScreenNailEntry entry : mScreenNails) {
                entry.updateDrawingSize();
            }
        }
    }

    private static int gapToSide(int imageWidth, int viewWidth) {
        return Math.max(0, (viewWidth - imageWidth) / 2);
    }

    /*
     * Here is how we layout the screen nails
     *
     *  previous            current           next
     *  ___________       ________________     __________
     * |  _______  |     |   __________   |   |  ______  |
     * | |       | |     |  |   right->|  |   | |      | |
     * | |       |<-------->|<--left   |  |   | |      | |
     * | |_______| |  |  |  |__________|  |   | |______| |
     * |___________|  |  |________________|   |__________|
     *                |  <--> gapToSide()
     *                |
     * IMAGE_GAP + Max(previous.gapToSide(), current.gapToSide)
     */
    private void layoutScreenNails() {
        int width = getWidth();
        int height = getHeight();

        // Use the image width in AC, since we may fake the size if the
        // image is unavailable
        RectF bounds = mPositionController.getImageBounds();
        int left = Math.round(bounds.left);
        int right = Math.round(bounds.right);
        int gap = gapToSide(right - left, width);

        // layout the previous image
        ScreenNailEntry entry = mScreenNails[ENTRY_PREVIOUS];

        if (entry.isEnabled()) {
            entry.layoutRightEdgeAt(left - (
                    IMAGE_GAP + Math.max(gap, entry.gapToSide())));
        }

        // layout the next image
        entry = mScreenNails[ENTRY_NEXT];
        if (entry.isEnabled()) {
            entry.layoutLeftEdgeAt(right + (
                    IMAGE_GAP + Math.max(gap, entry.gapToSide())));
        }
    }

    @Override
    protected void render(GLCanvas canvas) {
        PositionController p = mPositionController;
        boolean drawScreenNail = (mTransitionMode != TRANS_SLIDE_IN_LEFT
                && mTransitionMode != TRANS_SLIDE_IN_RIGHT
                && mTransitionMode != TRANS_OPEN_ANIMATION);

        // Draw the next photo
        if (drawScreenNail) {
            ScreenNailEntry nextNail = mScreenNails[ENTRY_NEXT];
            if (nextNail.mVisible) nextNail.draw(canvas, true);
        }

        // Draw the current photo
        if (mLoadingState == LOADING_COMPLETE) {
            super.render(canvas);
        }

        // If the photo is loaded, draw the message/icon at the center of it,
        // otherwise draw the message/icon at the center of the view.
        if (mLoadingState == LOADING_COMPLETE) {
            mTileView.getImageCenter(mImageCenter);
            renderMessage(canvas, mImageCenter.x, mImageCenter.y);
        } else {
            renderMessage(canvas, getWidth() / 2, getHeight() / 2);
        }

        // Draw the previous photo
        if (drawScreenNail) {
            ScreenNailEntry prevNail = mScreenNails[ENTRY_PREVIOUS];
            if (prevNail.mVisible) prevNail.draw(canvas, false);
        }

        if (mPositionController.advanceAnimation()) invalidate();
    }

    private void renderMessage(GLCanvas canvas, int x, int y) {
        // Draw the progress spinner and the text below it
        //
        // (x, y) is where we put the center of the spinner.
        // s is the size of the video play icon, and we use s to layout text
        // because we want to keep the text at the same place when the video
        // play icon is shown instead of the spinner.
        int w = getWidth();
        int h = getHeight();
        int s = Math.min(getWidth(), getHeight()) / 6;

        if (mLoadingState == LOADING_TIMEOUT) {
            StringTexture m = mLoadingText;
            ProgressSpinner r = mLoadingSpinner;
            r.draw(canvas, x - r.getWidth() / 2, y - r.getHeight() / 2);
            m.draw(canvas, x - m.getWidth() / 2, y + s / 2 + 5);
            invalidate(); // we need to keep the spinner rotating
        } else if (mLoadingState == LOADING_FAIL) {
            StringTexture m = mNoThumbnailText;
            m.draw(canvas, x - m.getWidth() / 2, y + s / 2 + 5);
        }

        // Draw the video play icon (in the place where the spinner was)
        if (mShowVideoPlayIcon
                && mLoadingState != LOADING_INIT
                && mLoadingState != LOADING_TIMEOUT) {
            mVideoPlayIcon.draw(canvas, x - s / 2, y - s / 2, s, s);
        }
    }

    private void stopCurrentSwipingIfNeeded() {
        // Enable fast sweeping
        if (mTransitionMode == TRANS_SWITCH_NEXT) {
            mTransitionMode = TRANS_NONE;
            mPositionController.stopAnimation();
            switchToNextImage();
        } else if (mTransitionMode == TRANS_SWITCH_PREVIOUS) {
            mTransitionMode = TRANS_NONE;
            mPositionController.stopAnimation();
            switchToPreviousImage();
        }
    }

    private boolean swipeImages(float velocity) {
        if (mTransitionMode != TRANS_NONE
                && mTransitionMode != TRANS_SWITCH_NEXT
                && mTransitionMode != TRANS_SWITCH_PREVIOUS) return false;

        ScreenNailEntry next = mScreenNails[ENTRY_NEXT];
        ScreenNailEntry prev = mScreenNails[ENTRY_PREVIOUS];

        int width = getWidth();

        // If we are at the edge of the current photo and the sweeping velocity
        // exceeds the threshold, switch to next / previous image.
        PositionController controller = mPositionController;
        boolean isMinimal = controller.isAtMinimalScale();

        if (velocity < -SWIPE_THRESHOLD &&
                (isMinimal || controller.isAtRightEdge())) {
            stopCurrentSwipingIfNeeded();
            if (next.isEnabled()) {
                mTransitionMode = TRANS_SWITCH_NEXT;
                controller.startHorizontalSlide(next.mOffsetX - width / 2);
                return true;
            }
        } else if (velocity > SWIPE_THRESHOLD &&
                (isMinimal || controller.isAtLeftEdge())) {
            stopCurrentSwipingIfNeeded();
            if (prev.isEnabled()) {
                mTransitionMode = TRANS_SWITCH_PREVIOUS;
                controller.startHorizontalSlide(prev.mOffsetX - width / 2);
                return true;
            }
        }

        return false;
    }

    public boolean snapToNeighborImage() {
        if (mTransitionMode != TRANS_NONE) return false;

        ScreenNailEntry next = mScreenNails[ENTRY_NEXT];
        ScreenNailEntry prev = mScreenNails[ENTRY_PREVIOUS];

        int width = getWidth();
        PositionController controller = mPositionController;

        RectF bounds = controller.getImageBounds();
        int left = Math.round(bounds.left);
        int right = Math.round(bounds.right);
        int threshold = SWITCH_THRESHOLD + gapToSide(right - left, width);

        // If we have moved the picture a lot, switching.
        if (next.isEnabled() && threshold < width - right) {
            mTransitionMode = TRANS_SWITCH_NEXT;
            controller.startHorizontalSlide(next.mOffsetX - width / 2);
            return true;
        }
        if (prev.isEnabled() && threshold < left) {
            mTransitionMode = TRANS_SWITCH_PREVIOUS;
            controller.startHorizontalSlide(prev.mOffsetX - width / 2);
            return true;
        }

        return false;
    }

    private boolean mIgnoreUpEvent = false;

    private class MyGestureListener
            extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(
                MotionEvent e1, MotionEvent e2, float dx, float dy) {
            if (mTransitionMode != TRANS_NONE) return true;

            ScreenNailEntry next = mScreenNails[ENTRY_NEXT];
            ScreenNailEntry prev = mScreenNails[ENTRY_PREVIOUS];

            mPositionController.startScroll(dx, dy, next.isEnabled(),
                    prev.isEnabled());
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (mPhotoTapListener != null) {
                mPhotoTapListener.onSingleTapUp((int) e.getX(), (int) e.getY());
            }
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                float velocityY) {
            if (swipeImages(velocityX)) {
                mIgnoreUpEvent = true;
            } else if (mTransitionMode != TRANS_NONE) {
                // do nothing
            } else if (mPositionController.fling(velocityX, velocityY)) {
                mIgnoreUpEvent = true;
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (mTransitionMode != TRANS_NONE) return true;
            PositionController controller = mPositionController;
            float scale = controller.getCurrentScale();
            // onDoubleTap happened on the second ACTION_DOWN.
            // We need to ignore the next UP event.
            mIgnoreUpEvent = true;
            if (scale <= 1.0f || controller.isAtMinimalScale()) {
                controller.zoomIn(
                        e.getX(), e.getY(), Math.max(1.5f, scale * 1.5f));
            } else {
                controller.resetToFullView();
            }
            return true;
        }
    }

    private class MyScaleListener
            extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scale = detector.getScaleFactor();
            if (Float.isNaN(scale) || Float.isInfinite(scale)
                    || mTransitionMode != TRANS_NONE) return true;
            boolean outOfRange = mPositionController.scaleBy(scale,
                    detector.getFocusX(), detector.getFocusY());
            if (outOfRange) {
                if (!mCancelExtraScalingPending) {
                    mHandler.sendEmptyMessageDelayed(
                            MSG_CANCEL_EXTRA_SCALING, 700);
                    mPositionController.setExtraScalingRange(true);
                    mCancelExtraScalingPending = true;
                }
            } else {
                if (mCancelExtraScalingPending) {
                    mHandler.removeMessages(MSG_CANCEL_EXTRA_SCALING);
                    mPositionController.setExtraScalingRange(false);
                    mCancelExtraScalingPending = false;
                }
            }
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            if (mTransitionMode != TRANS_NONE) return false;
            mPositionController.beginScale(
                detector.getFocusX(), detector.getFocusY());
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            mPositionController.endScale();
            snapToNeighborImage();
        }
    }

    private void cancelScaleGesture() {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancelEvent = MotionEvent.obtain(
                now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        mScaleDetector.onTouchEvent(cancelEvent);
        cancelEvent.recycle();
    }

    public boolean jumpTo(int index) {
        if (mTransitionMode != TRANS_NONE) return false;
        mModel.jumpTo(index);
        return true;
    }

    public void notifyOnNewImage() {
        mPositionController.setImageSize(0, 0);
    }

    public void startSlideInAnimation(int direction) {
        PositionController a = mPositionController;
        a.stopAnimation();
        switch (direction) {
            case TRANS_SLIDE_IN_LEFT:
            case TRANS_SLIDE_IN_RIGHT: {
                mTransitionMode = direction;
                a.startSlideInAnimation(direction);
                break;
            }
            default: throw new IllegalArgumentException(String.valueOf(direction));
        }
    }

    private class MyDownUpListener implements DownUpDetector.DownUpListener {
        public void onDown(MotionEvent e) {
        }

        public void onUp(MotionEvent e) {
            mEdgeView.onRelease();

            if (mIgnoreUpEvent) {
                mIgnoreUpEvent = false;
                return;
            }
            if (!snapToNeighborImage() && mTransitionMode == TRANS_NONE) {
                mPositionController.up();
            }
        }
    }

    private void switchToNextImage() {
        // We update the texture here directly to prevent texture uploading.
        ScreenNailEntry prevNail = mScreenNails[ENTRY_PREVIOUS];
        ScreenNailEntry nextNail = mScreenNails[ENTRY_NEXT];
        mTileView.invalidateTiles();
        if (prevNail.mTexture != null) prevNail.mTexture.recycle();
        prevNail.mTexture = mTileView.mBackupImage;
        mTileView.mBackupImage = nextNail.mTexture;
        nextNail.mTexture = null;
        mModel.next();
    }

    private void switchToPreviousImage() {
        // We update the texture here directly to prevent texture uploading.
        ScreenNailEntry prevNail = mScreenNails[ENTRY_PREVIOUS];
        ScreenNailEntry nextNail = mScreenNails[ENTRY_NEXT];
        mTileView.invalidateTiles();
        if (nextNail.mTexture != null) nextNail.mTexture.recycle();
        nextNail.mTexture = mTileView.mBackupImage;
        mTileView.mBackupImage = prevNail.mTexture;
        nextNail.mTexture = null;
        mModel.previous();
    }

    public void notifyTransitionComplete() {
        mHandler.sendEmptyMessage(MSG_TRANSITION_COMPLETE);
    }

    private void onTransitionComplete() {
        int mode = mTransitionMode;
        mTransitionMode = TRANS_NONE;

        if (mModel == null) return;
        if (mode == TRANS_SWITCH_NEXT) {
            switchToNextImage();
        } else if (mode == TRANS_SWITCH_PREVIOUS) {
            switchToPreviousImage();
        }
    }

    public boolean isDown() {
        return mDownUpDetector.isDown();
    }

    public static interface Model extends TileImageView.Model {
        public void next();
        public void previous();
        public void jumpTo(int index);
        public int getImageRotation();

        // Return null if the specified image is unavailable.
        public ImageData getNextImage();
        public ImageData getPreviousImage();
    }

    public static class ImageData {
        public int rotation;
        public Bitmap bitmap;

        public ImageData(Bitmap bitmap, int rotation) {
            this.bitmap = bitmap;
            this.rotation = rotation;
        }
    }

    private static int getRotated(int degree, int original, int theother) {
        return ((degree / 90) & 1) == 0 ? original : theother;
    }

    private class ScreenNailEntry {
        private boolean mVisible;
        private boolean mEnabled;

        private int mRotation;
        private int mDrawWidth;
        private int mDrawHeight;
        private int mOffsetX;

        private BitmapTexture mTexture;

        public void set(boolean enabled, Bitmap bitmap, int rotation) {
            mEnabled = enabled;
            mRotation = rotation;
            if (bitmap == null) {
                if (mTexture != null) mTexture.recycle();
                mTexture = null;
            } else {
                if (mTexture != null) {
                    if (mTexture.getBitmap() != bitmap) {
                        mTexture.recycle();
                        mTexture = new BitmapTexture(bitmap);
                    }
                } else {
                    mTexture = new BitmapTexture(bitmap);
                }
                updateDrawingSize();
            }
        }

        public void layoutRightEdgeAt(int x) {
            mVisible = x > 0;
            mOffsetX = x - getRotated(
                    mRotation, mDrawWidth, mDrawHeight) / 2;
        }

        public void layoutLeftEdgeAt(int x) {
            mVisible = x < getWidth();
            mOffsetX = x + getRotated(
                    mRotation, mDrawWidth, mDrawHeight) / 2;
        }

        public int gapToSide() {
            return ((mRotation / 90) & 1) != 0
                    ? PhotoView.gapToSide(mDrawHeight, getWidth())
                    : PhotoView.gapToSide(mDrawWidth, getWidth());
        }

        public void updateDrawingSize() {
            if (mTexture == null) return;

            int width = mTexture.getWidth();
            int height = mTexture.getHeight();

            // Calculate the initial scale that will used by PositionController
            // (usually fit-to-screen)
            float s = ((mRotation / 90) & 0x01) == 0
                    ? mPositionController.getMinimalScale(width, height)
                    : mPositionController.getMinimalScale(height, width);

            mDrawWidth = Math.round(width * s);
            mDrawHeight = Math.round(height * s);
        }

        public boolean isEnabled() {
            return mEnabled;
        }

        public void draw(GLCanvas canvas, boolean applyFadingAnimation) {
            if (mTexture == null) return;

            int w = getWidth();
            int x = applyFadingAnimation ? w / 2 : mOffsetX;
            int y = getHeight() / 2;
            int flags = GLCanvas.SAVE_FLAG_MATRIX;

            if (applyFadingAnimation) flags |= GLCanvas.SAVE_FLAG_ALPHA;
            canvas.save(flags);
            canvas.translate(x, y, 0);
            if (applyFadingAnimation) {
                float progress = (float) (x - mOffsetX) / w;
                float alpha = getScrollAlpha(progress);
                float scale = getScrollScale(progress);
                canvas.multiplyAlpha(alpha);
                canvas.scale(scale, scale, 1);
            }
            if (mRotation != 0) {
                canvas.rotate(mRotation, 0, 0, 1);
            }
            canvas.translate(-x, -y, 0);
            mTexture.draw(canvas, x - mDrawWidth / 2, y - mDrawHeight / 2,
                    mDrawWidth, mDrawHeight);
            canvas.restore();
        }
    }

    // Returns the scrolling progress value for an object moving out of a
    // view. The progress value measures how much the object has moving out of
    // the view. The object currently displays in [left, right), and the view is
    // at [0, viewWidth].
    //
    // The returned value is negative when the object is moving right, and
    // positive when the object is moving left. The value goes to -1 or 1 when
    // the object just moves out of the view completely. The value is 0 if the
    // object currently fills the view.
    private static float calculateMoveOutProgress(int left, int right,
            int viewWidth) {
        // w = object width
        // viewWidth = view width
        int w = right - left;

        // If the object width is smaller than the view width,
        //      |....view....|
        //                   |<-->|      progress = -1 when left = viewWidth
        // |<-->|                        progress = 1 when left = -w
        // So progress = 1 - 2 * (left + w) / (viewWidth + w)
        if (w < viewWidth) {
            return 1f - 2f * (left + w) / (viewWidth + w);
        }

        // If the object width is larger than the view width,
        //             |..view..|
        //                      |<--------->| progress = -1 when left = viewWidth
        //             |<--------->|          progress = 0 between left = 0
        //          |<--------->|                          and right = viewWidth
        // |<--------->|                      progress = 1 when right = 0
        if (left > 0) {
            return -left / (float) viewWidth;
        }

        if (right < viewWidth) {
            return (viewWidth - right) / (float) viewWidth;
        }

        return 0;
    }

    // Maps a scrolling progress value to the alpha factor in the fading
    // animation.
    private float getScrollAlpha(float scrollProgress) {
        return scrollProgress < 0 ? mAlphaInterpolator.getInterpolation(
                     1 - Math.abs(scrollProgress)) : 1.0f;
    }

    // Maps a scrolling progress value to the scaling factor in the fading
    // animation.
    private float getScrollScale(float scrollProgress) {
        float interpolatedProgress = mScaleInterpolator.getInterpolation(
                Math.abs(scrollProgress));
        float scale = (1 - interpolatedProgress) +
                interpolatedProgress * TRANSITION_SCALE_FACTOR;
        return scale;
    }


    // This interpolator emulates the rate at which the perceived scale of an
    // object changes as its distance from a camera increases. When this
    // interpolator is applied to a scale animation on a view, it evokes the
    // sense that the object is shrinking due to moving away from the camera.
    private static class ZInterpolator {
        private float focalLength;

        public ZInterpolator(float foc) {
            focalLength = foc;
        }

        public float getInterpolation(float input) {
            return (1.0f - focalLength / (focalLength + input)) /
                (1.0f - focalLength / (focalLength + 1.0f));
        }
    }

    public void pause() {
        mPositionController.skipAnimation();
        mTransitionMode = TRANS_NONE;
        mTileView.freeTextures();
        for (ScreenNailEntry entry : mScreenNails) {
            entry.set(false, null, 0);
        }
    }

    public void resume() {
        mTileView.prepareTextures();
    }

    public void setOpenedItem(Path itemPath) {
        mOpenedItemPath = itemPath;
    }

    public void showVideoPlayIcon(boolean show) {
        mShowVideoPlayIcon = show;
    }

    // Returns the position saved by the previous page.
    public Position retrieveSavedPosition() {
        if (mOpenedItemPath != null) {
            Position position = PositionRepository
                    .getInstance(mActivity).get(Long.valueOf(
                    System.identityHashCode(mOpenedItemPath)));
            mOpenedItemPath = null;
            return position;
        }
        return null;
    }

    public void openAnimationStarted() {
        mTransitionMode = TRANS_OPEN_ANIMATION;
    }

    public boolean isInTransition() {
        return mTransitionMode != TRANS_NONE;
    }
}
