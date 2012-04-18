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
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Message;
import android.view.MotionEvent;
import android.view.animation.AccelerateInterpolator;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.util.RangeArray;
import com.android.gallery3d.util.RangeBoolArray;

import java.util.Arrays;

public class PhotoView extends GLView {
    @SuppressWarnings("unused")
    private static final String TAG = "PhotoView";

    public static final int INVALID_SIZE = -1;
    public static final long INVALID_DATA_VERSION =
            MediaObject.INVALID_DATA_VERSION;

    public static interface Model extends TileImageView.Model {
        public void next();
        public void previous();
        public int getImageRotation();

        // This amends the getScreenNail() method of TileImageView.Model to get
        // ScreenNail at previous (negative offset) or next (positive offset)
        // positions. Returns null if the specified ScreenNail is unavailable.
        public ScreenNail getScreenNail(int offset);
        public void setNeedFullImage(boolean enabled);
    }

    public interface PhotoTapListener {
        public void onSingleTapUp(int x, int y);
    }

    private static final int MSG_SHOW_LOADING = 1;
    private static final int MSG_CANCEL_EXTRA_SCALING = 2;
    private static final int MSG_SWITCH_FOCUS = 3;

    private static final long DELAY_SHOW_LOADING = 250; // 250ms;

    private static final int LOADING_INIT = 0;
    private static final int LOADING_TIMEOUT = 1;
    private static final int LOADING_COMPLETE = 2;
    private static final int LOADING_FAIL = 3;

    private static final int MOVE_THRESHOLD = 256;
    private static final float SWIPE_THRESHOLD = 300f;

    private static final float DEFAULT_TEXT_SIZE = 20;
    private static float TRANSITION_SCALE_FACTOR = 0.74f;
    private static final boolean CARD_EFFECT = true;

    // Used to calculate the scaling factor for the fading animation.
    private ZInterpolator mScaleInterpolator = new ZInterpolator(0.5f);

    // Used to calculate the alpha factor for the fading animation.
    private AccelerateInterpolator mAlphaInterpolator =
            new AccelerateInterpolator(0.9f);

    // We keep this many previous ScreenNails. (also this many next ScreenNails)
    public static final int SCREEN_NAIL_MAX = 3;

    // The picture entries, the valid index is from -SCREEN_NAIL_MAX to
    // SCREEN_NAIL_MAX.
    private final RangeArray<Picture> mPictures =
            new RangeArray<Picture>(-SCREEN_NAIL_MAX, SCREEN_NAIL_MAX);

    private final long mDataVersion[] = new long[2 * SCREEN_NAIL_MAX + 1];
    private final int mFromIndex[] = new int[2 * SCREEN_NAIL_MAX + 1];

    private final GestureRecognizer mGestureRecognizer;

    private PhotoTapListener mPhotoTapListener;

    private final PositionController mPositionController;

    private Model mModel;
    private StringTexture mLoadingText;
    private StringTexture mNoThumbnailText;
    private TileImageView mTileView;
    private EdgeView mEdgeView;
    private Texture mVideoPlayIcon;

    private boolean mShowVideoPlayIcon;
    private ProgressSpinner mLoadingSpinner;

    private SynchronizedHandler mHandler;

    private int mLoadingState = LOADING_COMPLETE;

    private Point mImageCenter = new Point();
    private boolean mCancelExtraScalingPending;
    private boolean mFilmMode = false;

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

        mHandler = new MyHandler(activity.getGLRoot());

        mGestureRecognizer = new GestureRecognizer(
                context, new MyGestureListener());

        mPositionController = new PositionController(context,
                new PositionController.Listener() {
                    public void invalidate() {
                        PhotoView.this.invalidate();
                    }
                    public boolean isDown() {
                        return mGestureRecognizer.isDown();
                    }
                    public void onPull(int offset, int direction) {
                        mEdgeView.onPull(offset, direction);
                    }
                    public void onRelease() {
                        mEdgeView.onRelease();
                    }
                    public void onAbsorb(int velocity, int direction) {
                        mEdgeView.onAbsorb(velocity, direction);
                    }
                });
        mVideoPlayIcon = new ResourceTexture(context, R.drawable.ic_control_play);
        Arrays.fill(mDataVersion, INVALID_DATA_VERSION);
        for (int i = -SCREEN_NAIL_MAX; i <= SCREEN_NAIL_MAX; i++) {
            if (i == 0) {
                mPictures.put(i, new FullPicture());
            } else {
                mPictures.put(i, new ScreenNailPicture(i));
            }
        }
    }

    public void setModel(Model model) {
        mModel = model;
        mTileView.setModel(mModel);
    }

    class MyHandler extends SynchronizedHandler {
        public MyHandler(GLRoot root) {
            super(root);
        }

        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case MSG_SHOW_LOADING: {
                    if (mLoadingState == LOADING_INIT) {
                        // We don't need the opening animation
                        mPositionController.setOpenAnimationRect(null);

                        mLoadingSpinner.startAnimation();
                        mLoadingState = LOADING_TIMEOUT;
                        invalidate();
                    }
                    break;
                }
                case MSG_CANCEL_EXTRA_SCALING: {
                    mGestureRecognizer.cancelScale();
                    mPositionController.setExtraScalingRange(false);
                    mCancelExtraScalingPending = false;
                    break;
                }
                case MSG_SWITCH_FOCUS: {
                    switchFocus();
                    break;
                }
                default: throw new AssertionError(message.what);
            }
        }
    };

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
            mPositionController.setOpenAnimationRect(null);
        } else if (mLoadingState != LOADING_INIT) {
            mLoadingState = LOADING_INIT;
            mHandler.removeMessages(MSG_SHOW_LOADING);
            mHandler.sendEmptyMessageDelayed(
                    MSG_SHOW_LOADING, DELAY_SHOW_LOADING);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Data/Image change notifications
    ////////////////////////////////////////////////////////////////////////////

    public void notifyDataChange(long[] versions) {
        // Check if the data version actually changed.
        boolean changed = false;
        int N = 2 * SCREEN_NAIL_MAX + 1;
        for (int i = 0; i < N; i++) {
            if (versions[i] != mDataVersion[i]) {
                changed = true;
                break;
            }
        }
        if (!changed) return;

        // Create the mFromIndex array, which records the index where the picture
        // come from. The value Integer.MAX_VALUE means it's a new picture.
        for (int i = 0; i < N; i++) {
            long v = versions[i];
            if (v == INVALID_DATA_VERSION) {
                mFromIndex[i] = Integer.MAX_VALUE;
                continue;
            }

            // Try to find the same version number in the old array
            int j;
            for (j = 0; j < N; j++) {
                if (mDataVersion[j] == v) {
                    break;
                }
            }
            mFromIndex[i] = (j < N) ? j - SCREEN_NAIL_MAX : Integer.MAX_VALUE;
        }

        // Copy the new data version
        for (int i = 0; i < N; i++) {
            mDataVersion[i] = versions[i];
        }

        // Move the boxes
        mPositionController.moveBox(mFromIndex);

        // Update the ScreenNails.
        for (int i = -SCREEN_NAIL_MAX; i <= SCREEN_NAIL_MAX; i++) {
            mPictures.get(i).reload();
        }

        invalidate();
    }

    public void notifyImageChange(int index) {
        mPictures.get(index).reload();
        invalidate();
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Pictures
    ////////////////////////////////////////////////////////////////////////////

    private interface Picture {
        void reload();
        void draw(GLCanvas canvas, Rect r);
        void setScreenNail(ScreenNail s);
        boolean isEnabled();
    };

    class FullPicture implements Picture {
        private int mRotation;

        // This is a temporary hack to switch mode when entering/leaving camera.
        private volatile boolean mIsNonBitmap;

        public void FullPicture(TileImageView tileView) {
            mTileView = tileView;
        }

        @Override
        public void reload() {
            // mImageWidth and mImageHeight will get updated
            mTileView.notifyModelInvalidated();
            if (CARD_EFFECT) mTileView.setAlpha(1.0f);

            if (mModel == null) {
                mRotation = 0;
                mPositionController.setImageSize(0, 0, 0);
            } else {
                mRotation = mModel.getImageRotation();
                int w = mTileView.mImageWidth;
                int h = mTileView.mImageHeight;
                mPositionController.setImageSize(0,
                        getRotated(mRotation, w, h),
                        getRotated(mRotation, h, w));
            }
            setScreenNail(mModel == null ? null : mModel.getScreenNail(0));
            updateLoadingState();
        }

        @Override
        public void draw(GLCanvas canvas, Rect r) {
            if (mLoadingState == LOADING_COMPLETE) {
                setTileViewPosition(r);
                PhotoView.super.render(canvas);
            }
            renderMessage(canvas, r.centerX(), r.centerY());

            boolean isCenter = r.centerX() == getWidth() / 2;
            if (mIsNonBitmap && !isCenter && !mFilmMode) {
                setFilmMode(true);
            } else if (mIsNonBitmap && isCenter && mFilmMode) {
                setFilmMode(false);
            }
        }

        @Override
        public void setScreenNail(ScreenNail s) {
            mIsNonBitmap = (s != null && !(s instanceof BitmapScreenNail));
            mTileView.setScreenNail(s);
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        private void setTileViewPosition(Rect r) {
            TileImageView t = mTileView;

            // Find out the bitmap coordinates of the center of the view
            int imageW = mPositionController.getImageWidth();
            int imageH = mPositionController.getImageHeight();
            float scale = mPositionController.getImageScale();
            int viewW = getWidth();
            int viewH = getHeight();
            int centerX = (int) (imageW / 2f +
                    (viewW / 2f - r.exactCenterX()) / scale + 0.5f);
            int centerY = (int) (imageH / 2f +
                    (viewH / 2f - r.exactCenterY()) / scale + 0.5f);

            if (CARD_EFFECT && !mFilmMode) {
                // Calculate the move-out progress value.
                int left = r.left;
                int right = r.right;
                float progress = calculateMoveOutProgress(left, right, viewW);
                progress = Utils.clamp(progress, -1f, 1f);

                // We only want to apply the fading animation if the scrolling
                // movement is to the right.
                if (progress < 0) {
                    if (right - left < viewW) {
                        // If the picture is narrower than the view, keep it at
                        // the center of the view.
                        centerX = imageW / 2;
                    } else {
                        // If the picture is wider than the view (it's
                        // zoomed-in), keep the left edge of the object align
                        // the the left edge of the view.
                        centerX = Math.round(viewW / 2f / scale);
                    }
                    scale *= getScrollScale(progress);
                    t.setAlpha(getScrollAlpha(progress));
                }
            }

            // set the position of the tile view
            int inverseX = imageW - centerX;
            int inverseY = imageH - centerY;
            int rotation = mRotation;
            switch (rotation) {
                case 0: t.setPosition(centerX, centerY, scale, 0); break;
                case 90: t.setPosition(centerY, inverseX, scale, 90); break;
                case 180: t.setPosition(inverseX, inverseY, scale, 180); break;
                case 270: t.setPosition(inverseY, centerX, scale, 270); break;
                default:
                    throw new IllegalArgumentException(String.valueOf(rotation));
            }
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
                ProgressSpinner p = mLoadingSpinner;
                p.draw(canvas, x - p.getWidth() / 2, y - p.getHeight() / 2);
                m.draw(canvas, x - m.getWidth() / 2, y + s / 2 + 5);
                invalidate(); // we need to keep the spinner rotating
            } else if (mLoadingState == LOADING_FAIL) {
                StringTexture m = mNoThumbnailText;
                m.draw(canvas, x - m.getWidth() / 2, y + s / 2 + 5);
            }

            // Draw a debug indicator showing which picture has focus (index ==
            // 0).
            // canvas.fillRect(x - 10, y - 10, 20, 20, 0x80FF00FF);

            // Draw the video play icon (in the place where the spinner was)
            if (mShowVideoPlayIcon
                    && mLoadingState != LOADING_INIT
                    && mLoadingState != LOADING_TIMEOUT) {
                mVideoPlayIcon.draw(canvas, x - s / 2, y - s / 2, s, s);
            }
        }
    }

    private class ScreenNailPicture implements Picture {
        private int mIndex;
        private boolean mEnabled;
        private int mRotation;
        private ScreenNail mScreenNail;

        public ScreenNailPicture(int index) {
            mIndex = index;
        }

        @Override
        public void reload() {
            setScreenNail(mModel == null ? null : mModel.getScreenNail(mIndex));
        }

        @Override
        public void draw(GLCanvas canvas, Rect r) {
            if (mScreenNail == null) {
                return;
            }
            if (r.left >= getWidth() || r.right <= 0 ||
                    r.top >= getHeight() || r.bottom <= 0) {
                mScreenNail.noDraw();
                return;
            }

            boolean applyFadingAnimation =
                CARD_EFFECT && mIndex > 0 && !mFilmMode;

            int w = getWidth();
            int drawW = getRotated(mRotation, r.width(), r.height());
            int drawH = getRotated(mRotation, r.height(), r.width());
            int cx = applyFadingAnimation ? w / 2 : r.centerX();
            int cy = r.centerY();
            int flags = GLCanvas.SAVE_FLAG_MATRIX;

            if (applyFadingAnimation) flags |= GLCanvas.SAVE_FLAG_ALPHA;
            canvas.save(flags);
            canvas.translate(cx, cy);
            if (applyFadingAnimation) {
                float progress = (float) (w / 2 - r.centerX()) / w;
                progress = Utils.clamp(progress, -1, 1);
                float alpha = getScrollAlpha(progress);
                float scale = getScrollScale(progress);
                canvas.multiplyAlpha(alpha);
                canvas.scale(scale, scale, 1);
            }
            if (mRotation != 0) {
                canvas.rotate(mRotation, 0, 0, 1);
            }
            mScreenNail.draw(canvas, -drawW / 2, -drawH / 2, drawW, drawH);
            canvas.restore();
        }

        @Override
        public void setScreenNail(ScreenNail s) {
            mEnabled = (s != null);
            if (mScreenNail == s) return;
            mScreenNail = s;
            if (mScreenNail != null) {
                mRotation = mScreenNail.getRotation();
            }
            if (mScreenNail != null) {
                int w = s.getWidth();
                int h = s.getHeight();
                mPositionController.setImageSize(mIndex,
                        getRotated(mRotation, w, h),
                        getRotated(mRotation, h, w));
            }
        }

        @Override
        public boolean isEnabled() {
            return mEnabled;
        }
    }

    private static int getRotated(int degree, int original, int theother) {
        return (degree % 180 == 0) ? original : theother;
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Gestures Handling
    ////////////////////////////////////////////////////////////////////////////

    @Override
    protected boolean onTouch(MotionEvent event) {
        mGestureRecognizer.onTouchEvent(event);
        return true;
    }

    private class MyGestureListener implements GestureRecognizer.Listener {
        private boolean mIgnoreUpEvent = false;
        // If we can change mode for this scale gesture.
        private boolean mCanChangeMode;
        // If we have changed the mode in this scaling gesture.
        private boolean mModeChanged;

        @Override
        public boolean onSingleTapUp(float x, float y) {
            if (mFilmMode) {
                setFilmMode(false);
                return true;
            }

            if (mPhotoTapListener != null) {
                mPhotoTapListener.onSingleTapUp((int) x, (int) y);
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(float x, float y) {
            PositionController controller = mPositionController;
            float scale = controller.getImageScale();
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
            mPositionController.startScroll(-dx, -dy);
            return true;
        }

        @Override
        public boolean onFling(float velocityX, float velocityY) {
            if (swipeImages(velocityX, velocityY)) {
                mIgnoreUpEvent = true;
            } else if (mPositionController.fling(velocityX, velocityY)) {
                mIgnoreUpEvent = true;
            }
            return true;
        }

        @Override
        public boolean onScaleBegin(float focusX, float focusY) {
            mPositionController.beginScale(focusX, focusY);
            // We can change mode if we are in film mode, or we are in page
            // mode and at minimal scale.
            mCanChangeMode = mFilmMode
                    || mPositionController.isAtMinimalScale();
            mModeChanged = false;
            return true;
        }

        @Override
        public boolean onScale(float focusX, float focusY, float scale) {
            if (Float.isNaN(scale) || Float.isInfinite(scale)) return false;
            int outOfRange = mPositionController.scaleBy(scale, focusX, focusY);

            // We allow only one mode change in a scaling gesture.
            if (mCanChangeMode && !mModeChanged) {
                if ((outOfRange < 0 && !mFilmMode) ||
                        (outOfRange > 0 && mFilmMode)) {
                    setFilmMode(!mFilmMode);
                    mModeChanged = true;
                    return true;
                }
           }

            if (outOfRange != 0 && !mModeChanged) {
                startExtraScalingIfNeeded();
            } else {
                stopExtraScalingIfNeeded();
            }
            return true;
        }

        private void startExtraScalingIfNeeded() {
            if (!mCancelExtraScalingPending) {
                mHandler.sendEmptyMessageDelayed(
                        MSG_CANCEL_EXTRA_SCALING, 700);
                mPositionController.setExtraScalingRange(true);
                mCancelExtraScalingPending = true;
            }
        }

        private void stopExtraScalingIfNeeded() {
            if (mCancelExtraScalingPending) {
                mHandler.removeMessages(MSG_CANCEL_EXTRA_SCALING);
                mPositionController.setExtraScalingRange(false);
                mCancelExtraScalingPending = false;
            }
        }

        @Override
        public void onScaleEnd() {
            mPositionController.endScale();
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

            if (!snapToNeighborImage()) {
                mPositionController.up();
            }
        }
    }

    private void setFilmMode(boolean enabled) {
        if (mFilmMode == enabled) return;
        mFilmMode = enabled;
        mPositionController.setFilmMode(mFilmMode);
        mModel.setNeedFullImage(!enabled);
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Framework events
    ////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onLayout(
            boolean changeSize, int left, int top, int right, int bottom) {
        mTileView.layout(left, top, right, bottom);
        mEdgeView.layout(left, top, right, bottom);
        if (changeSize) {
            mPositionController.setViewSize(getWidth(), getHeight());
        }
    }

    public void pause() {
        mPositionController.skipAnimation();
        mTileView.freeTextures();
        for (int i = -SCREEN_NAIL_MAX; i <= SCREEN_NAIL_MAX; i++) {
            mPictures.get(i).setScreenNail(null);
        }
    }

    public void resume() {
        mTileView.prepareTextures();
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Rendering
    ////////////////////////////////////////////////////////////////////////////

    @Override
    protected void render(GLCanvas canvas) {
        // Draw next photos
        for (int i = 1; i <= SCREEN_NAIL_MAX; i++) {
            Rect r = mPositionController.getPosition(i);
            mPictures.get(i).draw(canvas, r);
            // In page mode, we draw only one next photo.
            if (!mFilmMode) break;
        }

        // Draw current photo
        mPictures.get(0).draw(canvas, mPositionController.getPosition(0));

        // Draw previous photos
        for (int i = -1; i >= -SCREEN_NAIL_MAX; i--) {
            Rect r = mPositionController.getPosition(i);
            mPictures.get(i).draw(canvas, r);
            // In page mode, we draw only one previous photo.
            if (!mFilmMode) break;
        }

        mPositionController.advanceAnimation();
        checkFocusSwitching();
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Film mode focus switching
    ////////////////////////////////////////////////////////////////////////////

    // Runs in GL thread.
    private void checkFocusSwitching() {
        if (!mFilmMode) return;
        if (mHandler.hasMessages(MSG_SWITCH_FOCUS)) return;
        if (switchPosition() != 0) {
            mHandler.sendEmptyMessage(MSG_SWITCH_FOCUS);
        }
    }

    // Runs in main thread.
    private void switchFocus() {
        if (mGestureRecognizer.isDown()) return;
        switch (switchPosition()) {
            case -1:
                switchToPrevImage();
                break;
            case 1:
                switchToNextImage();
                break;
        }
    }

    // Returns -1 if we should switch focus to the previous picture, +1 if we
    // should switch to the next, 0 otherwise.
    private int switchPosition() {
        Rect curr = mPositionController.getPosition(0);
        int center = getWidth() / 2;

        if (curr.left > center && mPictures.get(-1).isEnabled()) {
            Rect prev = mPositionController.getPosition(-1);
            int currDist = curr.left - center;
            int prevDist = center - prev.right;
            if (prevDist < currDist) {
                return -1;
            }
        } else if (curr.right < center && mPictures.get(1).isEnabled()) {
            Rect next = mPositionController.getPosition(1);
            int currDist = center - curr.right;
            int nextDist = next.left - center;
            if (nextDist < currDist) {
                return 1;
            }
        }

        return 0;
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Page mode focus switching
    //
    //  We slide image to the next one or the previous one in two cases: 1: If
    //  the user did a fling gesture with enough velocity.  2 If the user has
    //  moved the picture a lot.
    ////////////////////////////////////////////////////////////////////////////

    private boolean swipeImages(float velocityX, float velocityY) {
        if (mFilmMode) return false;

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
        // exceeds the threshold, slide to the next / previous image.
        if (velocityX < -SWIPE_THRESHOLD && (isMinimal
                || (edges & PositionController.IMAGE_AT_RIGHT_EDGE) != 0)) {
            return slideToNextPicture();
        } else if (velocityX > SWIPE_THRESHOLD && (isMinimal
                || (edges & PositionController.IMAGE_AT_LEFT_EDGE) != 0)) {
            return slideToPrevPicture();
        }

        return false;
    }

    private boolean snapToNeighborImage() {
        if (mFilmMode) return false;

        Rect r = mPositionController.getPosition(0);
        int viewW = getWidth();
        int threshold = MOVE_THRESHOLD + gapToSide(r.width(), viewW);

        // If we have moved the picture a lot, switching.
        if (viewW - r.right > threshold) {
            return slideToNextPicture();
        } else if (r.left > threshold) {
            return slideToPrevPicture();
        }

        return false;
    }

    private boolean slideToNextPicture() {
        Picture next = mPictures.get(1);
        if (!next.isEnabled()) return false;
        int currentX = mPositionController.getPosition(1).centerX();
        int targetX = getWidth() / 2;
        mPositionController.startHorizontalSlide(targetX - currentX);
        switchToNextImage();
        return true;
    }

    private boolean slideToPrevPicture() {
        Picture prev = mPictures.get(-1);
        if (!prev.isEnabled()) return false;
        int currentX = mPositionController.getPosition(-1).centerX();
        int targetX = getWidth() / 2;
        mPositionController.startHorizontalSlide(targetX - currentX);
        switchToPrevImage();
        return true;
    }

    private static int gapToSide(int imageWidth, int viewWidth) {
        return Math.max(0, (viewWidth - imageWidth) / 2);
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Focus switching
    ////////////////////////////////////////////////////////////////////////////

    private void switchToNextImage() {
        mModel.next();
    }

    private void switchToPrevImage() {
        mModel.previous();
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Opening Animation
    ////////////////////////////////////////////////////////////////////////////

    public void setOpenAnimationRect(Rect rect) {
        mPositionController.setOpenAnimationRect(rect);
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Card deck effect calculation
    ////////////////////////////////////////////////////////////////////////////

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

    ////////////////////////////////////////////////////////////////////////////
    //  Simple public utilities
    ////////////////////////////////////////////////////////////////////////////

    public void setPhotoTapListener(PhotoTapListener listener) {
        mPhotoTapListener = listener;
    }

    public void showVideoPlayIcon(boolean show) {
        mShowVideoPlayIcon = show;
    }
}
