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
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Message;
import android.view.MotionEvent;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryActivity;

public class PhotoView extends GLView {
    @SuppressWarnings("unused")
    private static final String TAG = "PhotoView";

    public static final int INVALID_SIZE = -1;

    private static final int MSG_TRANSITION_COMPLETE = 1;
    private static final int MSG_SHOW_LOADING = 2;

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

    public interface PhotoTapListener {
        public void onSingleTapUp(int x, int y);
    }

    // the previous/next image entries
    private final ScreenNailEntry mScreenNails[] = new ScreenNailEntry[2];

    private final GestureRecognizer mGestureRecognizer;

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

    private Rect mOpenAnimationRect;

    public PhotoView(GalleryActivity activity) {
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
                            mOpenAnimationRect = null;

                            mLoadingSpinner.startAnimation();
                            mLoadingState = LOADING_TIMEOUT;
                            invalidate();
                        }
                        break;
                    }
                    default: throw new AssertionError(message.what);
                }
            }
        };

        mGestureRecognizer = new GestureRecognizer(
                context, new MyGestureListener());

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

    private boolean setTileViewPosition(int centerX, int centerY, float scale) {
        int inverseX = mPositionController.getImageWidth() - centerX;
        int inverseY = mPositionController.getImageHeight() - centerY;
        TileImageView t = mTileView;
        int rotation = mImageRotation;
        switch (rotation) {
            case 0: return t.setPosition(centerX, centerY, scale, 0);
            case 90: return t.setPosition(centerY, inverseX, scale, 90);
            case 180: return t.setPosition(inverseX, inverseY, scale, 180);
            case 270: return t.setPosition(inverseY, centerX, scale, 270);
            default: throw new IllegalArgumentException(String.valueOf(rotation));
        }
    }

    public void setPosition(int centerX, int centerY, float scale) {
        if (setTileViewPosition(centerX, centerY, scale)) {
            layoutScreenNails();
        }
    }

    private void updateScreenNailEntry(int which, ScreenNail screenNail) {
        if (mTransitionMode == TRANS_SWITCH_NEXT
                || mTransitionMode == TRANS_SWITCH_PREVIOUS) {
            // ignore screen nail updating during switching
            return;
        }
        ScreenNailEntry entry = mScreenNails[which];
        entry.set(screenNail);
    }

    // -1 previous, 0 current, 1 next
    public void notifyImageInvalidated(int which) {
        switch (which) {
            case -1: {
                updateScreenNailEntry(
                        ENTRY_PREVIOUS, mModel.getPrevScreenNail());
                layoutScreenNails();
                invalidate();
                break;
            }
            case 1: {
                updateScreenNailEntry(ENTRY_NEXT, mModel.getNextScreenNail());
                layoutScreenNails();
                invalidate();
                break;
            }
            case 0: {
                // mImageWidth and mImageHeight will get updated
                mTileView.notifyModelInvalidated();

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
        if (mModel.getLevelCount() != 0 || mModel.getScreenNail() != null) {
            mHandler.removeMessages(MSG_SHOW_LOADING);
            mLoadingState = LOADING_COMPLETE;
        } else if (mModel.isFailedToLoad()) {
            mHandler.removeMessages(MSG_SHOW_LOADING);
            mLoadingState = LOADING_FAIL;
            // We don't want the opening animation after loading failure
            mOpenAnimationRect = null;
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
            updateScreenNailEntry(ENTRY_PREVIOUS, mModel.getPrevScreenNail());
            updateScreenNailEntry(ENTRY_NEXT, mModel.getNextScreenNail());
        }
        layoutScreenNails();

        if (mModel == null) {
            mTileView.notifyModelInvalidated();
            mImageRotation = 0;
            mPositionController.setImageSize(0, 0);
            updateLoadingState();
        } else {
            notifyImageInvalidated(0);
        }
    }

    @Override
    protected boolean onTouch(MotionEvent event) {
        mGestureRecognizer.onTouchEvent(event);
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
        // Draw the current photo
        if (mLoadingState == LOADING_COMPLETE) {
            super.render(canvas);
        }

        // Draw the previous and the next photo
        if (mTransitionMode != TRANS_SLIDE_IN_LEFT
                && mTransitionMode != TRANS_SLIDE_IN_RIGHT
                && mTransitionMode != TRANS_OPEN_ANIMATION) {
            ScreenNailEntry prevNail = mScreenNails[ENTRY_PREVIOUS];
            ScreenNailEntry nextNail = mScreenNails[ENTRY_NEXT];

            prevNail.draw(canvas);
            nextNail.draw(canvas);
        }

        // Draw the progress spinner and the text below it
        //
        // (x, y) is where we put the center of the spinner.
        // s is the size of the video play icon, and we use s to layout text
        // because we want to keep the text at the same place when the video
        // play icon is shown instead of the spinner.
        int w = getWidth();
        int h = getHeight();
        int x = Math.round(mPositionController.getImageBounds().centerX());
        int y = h / 2;
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

        mPositionController.advanceAnimation();
    }

    private void stopCurrentSwipingIfNeeded() {
        // Enable fast swiping
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

    private boolean swipeImages(float velocityX, float velocityY) {
        if (mTransitionMode != TRANS_NONE
                && mTransitionMode != TRANS_SWITCH_NEXT
                && mTransitionMode != TRANS_SWITCH_PREVIOUS) return false;

        // Avoid swiping images if we're possibly flinging to view the
        // zoomed in picture vertically.
        PositionController controller = mPositionController;
        boolean isMinimal = controller.isAtMinimalScale();
        int edges = controller.getImageAtEdges();
        if (!isMinimal && Math.abs(velocityY) > Math.abs(velocityX))
            if ((edges & PositionController.IMAGE_AT_TOP_EDGE) == 0
                    || (edges & PositionController.IMAGE_AT_BOTTOM_EDGE) == 0)
                return false;

        // If we are at the edge of the current photo and the sweeping velocity
        // exceeds the threshold, switch to next / previous image.
        int halfWidth = getWidth() / 2;
        if (velocityX < -SWIPE_THRESHOLD && (isMinimal
                || (edges & PositionController.IMAGE_AT_RIGHT_EDGE) != 0)) {
            stopCurrentSwipingIfNeeded();
            ScreenNailEntry next = mScreenNails[ENTRY_NEXT];
            if (next.isEnabled()) {
                mTransitionMode = TRANS_SWITCH_NEXT;
                controller.startHorizontalSlide(next.mOffsetX - halfWidth);
                return true;
            }
        } else if (velocityX > SWIPE_THRESHOLD && (isMinimal
                || (edges & PositionController.IMAGE_AT_LEFT_EDGE) != 0)) {
            stopCurrentSwipingIfNeeded();
            ScreenNailEntry prev = mScreenNails[ENTRY_PREVIOUS];
            if (prev.isEnabled()) {
                mTransitionMode = TRANS_SWITCH_PREVIOUS;
                controller.startHorizontalSlide(prev.mOffsetX - halfWidth);
                return true;
            }
        }

        return false;
    }

    private boolean snapToNeighborImage() {
        if (mTransitionMode != TRANS_NONE) return false;

        PositionController controller = mPositionController;
        RectF bounds = controller.getImageBounds();
        int left = Math.round(bounds.left);
        int right = Math.round(bounds.right);
        int width = getWidth();
        int threshold = SWITCH_THRESHOLD + gapToSide(right - left, width);

        // If we have moved the picture a lot, switching.
        ScreenNailEntry next = mScreenNails[ENTRY_NEXT];
        if (next.isEnabled() && threshold < width - right) {
            mTransitionMode = TRANS_SWITCH_NEXT;
            controller.startHorizontalSlide(next.mOffsetX - width / 2);
            return true;
        }
        ScreenNailEntry prev = mScreenNails[ENTRY_PREVIOUS];
        if (prev.isEnabled() && threshold < left) {
            mTransitionMode = TRANS_SWITCH_PREVIOUS;
            controller.startHorizontalSlide(prev.mOffsetX - width / 2);
            return true;
        }

        return false;
    }

    private class MyGestureListener implements GestureRecognizer.Listener {
        private boolean mIgnoreUpEvent = false;

        @Override
        public boolean onSingleTapUp(float x, float y) {
            if (mPhotoTapListener != null) {
                mPhotoTapListener.onSingleTapUp((int) x, (int) y);
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(float x, float y) {
            if (mTransitionMode != TRANS_NONE) return true;
            PositionController controller = mPositionController;
            float scale = controller.getCurrentScale();
            // onDoubleTap happened on the second ACTION_DOWN.
            // We need to ignore the next UP event.
            mIgnoreUpEvent = true;
            if (scale <= 1.0f || controller.isAtMinimalScale()) {
                controller.zoomIn(x, y, Math.max(1.5f, scale * 1.5f));
            } else {
                controller.resetToFullView();
            }
            return true;
        }

        @Override
        public boolean onScroll(float dx, float dy) {
            if (mTransitionMode != TRANS_NONE) return true;

            ScreenNailEntry next = mScreenNails[ENTRY_NEXT];
            ScreenNailEntry prev = mScreenNails[ENTRY_PREVIOUS];

            mPositionController.startScroll(dx, dy, next.isEnabled(),
                    prev.isEnabled());
            return true;
        }

        @Override
        public boolean onFling(float velocityX, float velocityY) {
            if (swipeImages(velocityX, velocityY)) {
                mIgnoreUpEvent = true;
            } else if (mTransitionMode != TRANS_NONE) {
                // do nothing
            } else if (mPositionController.fling(velocityX, velocityY)) {
                mIgnoreUpEvent = true;
            }
            return true;
        }

        @Override
        public boolean onScaleBegin(float focusX, float focusY) {
            if (mTransitionMode != TRANS_NONE) return false;
            mPositionController.beginScale(focusX, focusY);
            return true;
        }

        @Override
        public boolean onScale(float focusX, float focusY, float scale) {
            if (Float.isNaN(scale) || Float.isInfinite(scale)
                    || mTransitionMode != TRANS_NONE) return true;
            mPositionController.scaleBy(scale, focusX, focusY);
            return true;
        }

        @Override
        public void onScaleEnd() {
            mPositionController.endScale();
            snapToNeighborImage();
        }

        @Override
        public void onDown() {
        }

        @Override
        public void onUp() {
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

    private void switchToNextImage() {
        // We update the texture here directly to prevent texture uploading.
        ScreenNailEntry prevNail = mScreenNails[ENTRY_PREVIOUS];
        ScreenNailEntry nextNail = mScreenNails[ENTRY_NEXT];
        mTileView.invalidateTiles();
        if (prevNail.mScreenNail != null) prevNail.mScreenNail.recycle();
        prevNail.set(mTileView.mScreenNail);
        mTileView.updateScreenNail(nextNail.mScreenNail);
        nextNail.set(null);
        mModel.next();
    }

    private void switchToPreviousImage() {
        // We update the texture here directly to prevent texture uploading.
        ScreenNailEntry prevNail = mScreenNails[ENTRY_PREVIOUS];
        ScreenNailEntry nextNail = mScreenNails[ENTRY_NEXT];
        mTileView.invalidateTiles();
        if (nextNail.mScreenNail != null) nextNail.mScreenNail.recycle();
        nextNail.set(mTileView.mScreenNail);
        mTileView.updateScreenNail(prevNail.mScreenNail);
        nextNail.set(null);
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
        return mGestureRecognizer.isDown();
    }

    public static interface Model extends TileImageView.Model {
        public void next();
        public void previous();
        public void jumpTo(int index);
        public int getImageRotation();

        // Return null if the specified image is unavailable.
        public ScreenNail getNextScreenNail();
        public ScreenNail getPrevScreenNail();
    }

    private static int getRotated(int degree, int original, int theother) {
        return ((degree / 90) & 1) == 0 ? original : theother;
    }

    private class ScreenNailEntry {
        private boolean mVisible;
        private boolean mEnabled;

        private int mDrawWidth;
        private int mDrawHeight;
        private int mOffsetX;
        private int mRotation;

        private ScreenNail mScreenNail;

        public void set(ScreenNail screenNail) {
            mEnabled = (screenNail != null);
            if (mScreenNail == screenNail) return;
            if (mScreenNail != null) mScreenNail.recycle();
            mScreenNail = screenNail;
            if (mScreenNail != null) {
                mRotation = mScreenNail.getRotation();
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
            if (mScreenNail == null) return;

            int width = mScreenNail.getWidth();
            int height = mScreenNail.getHeight();

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

        public void draw(GLCanvas canvas) {
            if (mScreenNail == null) return;
            if (!mVisible) {
                mScreenNail.disableDraw();
                return;
            }

            int x = mOffsetX;
            int y = getHeight() / 2;

            if (mRotation != 0) {
                canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
                canvas.translate(x, y);
                canvas.rotate(mRotation, 0, 0, 1);
                canvas.translate(-x, -y);
            }

            mScreenNail.draw(canvas, x - mDrawWidth / 2, y - mDrawHeight / 2,
                    mDrawWidth, mDrawHeight);

            if (mRotation != 0) {
                canvas.restore();
            }
        }
    }

    public void pause() {
        mPositionController.skipAnimation();
        mTransitionMode = TRANS_NONE;
        mTileView.freeTextures();
        for (ScreenNailEntry entry : mScreenNails) {
            entry.set(null);
        }
    }

    public void resume() {
        mTileView.prepareTextures();
    }

    public void setOpenAnimationRect(Rect rect) {
        mOpenAnimationRect = rect;
    }

    public void showVideoPlayIcon(boolean show) {
        mShowVideoPlayIcon = show;
    }

    // Returns the opening animation rectangle saved by the previous page.
    public Rect retrieveOpenAnimationRect() {
        Rect r = mOpenAnimationRect;
        mOpenAnimationRect = null;
        return r;
    }

    public void openAnimationStarted() {
        mTransitionMode = TRANS_OPEN_ANIMATION;
    }

    public boolean isInTransition() {
        return mTransitionMode != TRANS_NONE;
    }
}
