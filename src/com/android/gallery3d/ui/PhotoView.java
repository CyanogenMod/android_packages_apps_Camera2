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
    private static final int PLACEHOLDER_COLOR = 0xFF222222;

    public static final int INVALID_SIZE = -1;
    public static final long INVALID_DATA_VERSION =
            MediaObject.INVALID_DATA_VERSION;

    public static class Size {
        public int width;
        public int height;
    }

    public interface Model extends TileImageView.Model {
        public void next();
        public void previous();

        // Returns the size for the specified picture. If the size information is
        // not avaiable, width = height = 0.
        public void getImageSize(int offset, Size size);

        // Returns the rotation for the specified picture.
        public int getImageRotation(int offset);

        // This amends the getScreenNail() method of TileImageView.Model to get
        // ScreenNail at previous (negative offset) or next (positive offset)
        // positions. Returns null if the specified ScreenNail is unavailable.
        public ScreenNail getScreenNail(int offset);

        // Set this to true if we need the model to provide full images.
        public void setNeedFullImage(boolean enabled);
    }

    public interface PhotoTapListener {
        public void onSingleTapUp(int x, int y);
    }

    private static final int MSG_SHOW_LOADING = 1;
    private static final int MSG_CANCEL_EXTRA_SCALING = 2;
    private static final int MSG_SWITCH_FOCUS = 3;
    private static final int MSG_CAPTURE_ANIMATION_DONE = 4;

    private static final long DELAY_SHOW_LOADING = 250; // 250ms;

    private static final int LOADING_INIT = 0;
    private static final int LOADING_TIMEOUT = 1;
    private static final int LOADING_COMPLETE = 2;
    private static final int LOADING_FAIL = 3;

    private static final int MOVE_THRESHOLD = 256;
    private static final float SWIPE_THRESHOLD = 300f;

    private static final float DEFAULT_TEXT_SIZE = 20;
    private static float TRANSITION_SCALE_FACTOR = 0.74f;

    // whether we want to apply card deck effect in page mode.
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

    // [mPrevBound, mNextBound] is the range of index for all pictures in the
    // model, if we assume the index of current focused picture is 0.  So if
    // there are some previous pictures, mPrevBound < 0, and if there are some
    // next pictures, mNextBound > 0.
    private int mPrevBound;
    private int mNextBound;

    // This variable prevents us doing snapback until its values goes to 0. This
    // happens if the user gesture is still in progress or we are in a capture
    // animation.
    // HOLD_TOUCH_DOWN_FROM_CAMERA is an extra flag set together with
    // HOLD_TOUCH_DOWN if the touch down starts from camera preview.
    private int mHolding;
    private static final int HOLD_TOUCH_DOWN = 1;
    private static final int HOLD_TOUCH_DOWN_FROM_CAMERA = 2;
    private static final int HOLD_CAPTURE_ANIMATION = 4;

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
                    public boolean isHolding() {
                        // We want the film mode change happen as soon as
                        // possible even if the touch is still down.
                        if ((mHolding & HOLD_TOUCH_DOWN_FROM_CAMERA) != 0) {
                            return false;
                        } else {
                            return mHolding != 0;
                        }
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
                case MSG_CAPTURE_ANIMATION_DONE: {
                    captureAnimationDone();
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

    public void notifyDataChange(long[] versions, int prevBound, int nextBound) {
        mPrevBound = prevBound;
        mNextBound = nextBound;

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
        mPositionController.moveBox(mFromIndex, mPrevBound < 0, mNextBound > 0);

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
        boolean isCamera();  // whether the picture is a camera preview
    };

    private boolean isCameraScreenNail(ScreenNail s) {
        return s != null && !(s instanceof BitmapScreenNail);
    }

    class FullPicture implements Picture {
        private int mRotation;
        private boolean mIsCamera;
        private boolean mWasCenter;

        public void FullPicture(TileImageView tileView) {
            mTileView = tileView;
        }

        @Override
        public void reload() {
            // mImageWidth and mImageHeight will get updated
            mTileView.notifyModelInvalidated();
            mTileView.setAlpha(1.0f);

            mRotation = mModel.getImageRotation(0);
            int w = mTileView.mImageWidth;
            int h = mTileView.mImageHeight;
            mPositionController.setImageSize(0,
                    getRotated(mRotation, w, h),
                    getRotated(mRotation, h, w));

            setScreenNail(mModel.getScreenNail(0));
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

            // We want to have following transitions:
            // (1) Move camera preview out of its place: switch to film mode
            // (2) Move camera preview into its place: switch to page mode
            // The extra mWasCenter check makes sure (1) does not apply if in
            // page mode, we move _to_ the camera preview from another picture.
            if ((mHolding & ~(HOLD_TOUCH_DOWN | HOLD_TOUCH_DOWN_FROM_CAMERA)) == 0) {
                if (mWasCenter && !isCenter && mIsCamera && !mFilmMode) {
                    setFilmMode(true);
                } else if (mIsCamera && isCenter && mFilmMode) {
                    setFilmMode(false);
                }
            }
            mWasCenter = isCenter;
        }

        @Override
        public void setScreenNail(ScreenNail s) {
            mIsCamera = isCameraScreenNail(s);
            mTileView.setScreenNail(s);
        }

        @Override
        public boolean isCamera() {
            return mIsCamera;
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

            boolean wantsCardEffect = CARD_EFFECT && !mFilmMode
                && !mPictures.get(-1).isCamera();
            if (wantsCardEffect) {
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
        private int mRotation;
        private ScreenNail mScreenNail;
        private Size mSize = new Size();
        private boolean mIsCamera;

        public ScreenNailPicture(int index) {
            mIndex = index;
        }

        @Override
        public void reload() {
            setScreenNail(mModel.getScreenNail(mIndex));
        }

        @Override
        public void draw(GLCanvas canvas, Rect r) {
            if (mScreenNail == null) {
                // Draw a placeholder rectange if there will be a picture in
                // this position.
                if (mIndex >= mPrevBound && mIndex <= mNextBound) {
                    canvas.fillRect(r.left, r.top, r.width(), r.height(),
                            PLACEHOLDER_COLOR);
                }
                return;
            }
            if (r.left >= getWidth() || r.right <= 0 ||
                    r.top >= getHeight() || r.bottom <= 0) {
                mScreenNail.noDraw();
                return;
            }

            boolean wantsCardEffect = CARD_EFFECT && !mFilmMode
                && (mIndex > 0) && !mPictures.get(0).isCamera();

            int w = getWidth();
            int drawW = getRotated(mRotation, r.width(), r.height());
            int drawH = getRotated(mRotation, r.height(), r.width());
            int cx = wantsCardEffect ? w / 2 : r.centerX();
            int cy = r.centerY();
            int flags = GLCanvas.SAVE_FLAG_MATRIX;

            if (wantsCardEffect) flags |= GLCanvas.SAVE_FLAG_ALPHA;
            canvas.save(flags);
            canvas.translate(cx, cy);
            if (wantsCardEffect) {
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
            if (mScreenNail == s) return;
            mScreenNail = s;
            mIsCamera = isCameraScreenNail(s);
            mRotation = mModel.getImageRotation(mIndex);

            int w = 0, h = 0;
            if (mScreenNail != null) {
                w = s.getWidth();
                h = s.getHeight();
            } else if (mModel != null) {
                // If we don't have ScreenNail available, we can still try to
                // get the size information of it.
                mModel.getImageSize(mIndex, mSize);
                w = mSize.width;
                h = mSize.height;
            }

            if (w != 0 && h != 0)  {
                mPositionController.setImageSize(mIndex,
                        getRotated(mRotation, w, h),
                        getRotated(mRotation, h, w));
            }
        }

        @Override
        public boolean isCamera() {
            return mIsCamera;
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
            mHolding |= HOLD_TOUCH_DOWN;
            if (mPictures.get(0).isCamera()) {
                mHolding |= HOLD_TOUCH_DOWN_FROM_CAMERA;
            }
        }

        @Override
        public void onUp() {
            mHolding &= ~(HOLD_TOUCH_DOWN | HOLD_TOUCH_DOWN_FROM_CAMERA);
            mEdgeView.onRelease();

            if (mIgnoreUpEvent) {
                mIgnoreUpEvent = false;
                return;
            }

            snapback();
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
        if (mHolding != 0) return;
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

        if (curr.left > center && mPrevBound < 0) {
            Rect prev = mPositionController.getPosition(-1);
            int currDist = curr.left - center;
            int prevDist = center - prev.right;
            if (prevDist < currDist) {
                return -1;
            }
        } else if (curr.right < center && mNextBound > 0) {
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

    private void snapback() {
        if (mHolding != 0) return;
        if (!snapToNeighborImage()) {
            mPositionController.snapback();
        }
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
        if (mNextBound <= 0) return false;
        switchToNextImage();
        mPositionController.startHorizontalSlide();
        return true;
    }

    private boolean slideToPrevPicture() {
        if (mPrevBound >= 0) return false;
        switchToPrevImage();
        mPositionController.startHorizontalSlide();
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
    //  Capture Animation
    ////////////////////////////////////////////////////////////////////////////

    public boolean switchWithCaptureAnimation(int offset) {
        GLRoot root = getGLRoot();
        root.lockRenderThread();
        try {
            return switchWithCaptureAnimationLocked(offset);
        } finally {
            root.unlockRenderThread();
        }
    }

    private boolean switchWithCaptureAnimationLocked(int offset) {
        if (mFilmMode) return false;
        if (mHolding != 0) return true;
        if (offset == 1) {
            if (mNextBound <= 0) return false;
            switchToNextImage();
            mPositionController.startCaptureAnimationSlide(-1);
        } else if (offset == -1) {
            if (mPrevBound >= 0) return false;
            switchToPrevImage();
            mPositionController.startCaptureAnimationSlide(1);
        } else {
            return false;
        }
        mHolding |= HOLD_CAPTURE_ANIMATION;
        mHandler.sendEmptyMessageDelayed(MSG_CAPTURE_ANIMATION_DONE, 800);
        return true;
    }

    private void captureAnimationDone() {
        mHolding &= ~HOLD_CAPTURE_ANIMATION;
        snapback();
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
