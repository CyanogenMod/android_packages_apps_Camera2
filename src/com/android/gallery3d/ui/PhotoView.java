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
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Message;
import android.view.MotionEvent;
import android.view.animation.AccelerateInterpolator;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.util.RangeArray;

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
        public int getCurrentIndex();
        public void moveTo(int index);

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

        // Returns true if the item is the Camera preview.
        public boolean isCamera(int offset);

        // Returns true if the item is a Video.
        public boolean isVideo(int offset);
    }

    public interface Listener {
        public void onSingleTapUp(int x, int y);
        public void lockOrientation();
        public void unlockOrientation();
        public void onFullScreenChanged(boolean full);
        public void onActionBarAllowed(boolean allowed);
    }

    // Here is a graph showing the places we need to lock/unlock device
    // orientation:
    //
    //           +------------+ A  +------------+
    // Page mode |   Camera   |<---|   Photo    |
    //           |  [locked]  |--->| [unlocked] |
    //           +------------+  B +------------+
    //                ^                  ^
    //                | C                | D
    //           +------------+    +------------+
    //           |   Camera   |    |   Photo    |
    // Film mode |    [*]     |    |    [*]     |
    //           +------------+    +------------+
    //
    // In Page mode, we want to lock in Camera because we don't want the system
    // rotation animation. We also want to unlock in Photo because we want to
    // show the system action bar in the right place.
    //
    // We don't show action bar in Film mode, so it's fine for it to be locked
    // or unlocked in Film mode.
    //
    // There are four transitions we need to check if we need to
    // lock/unlock. Marked as A to D above and in the code.

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
    private static final int ICON_RATIO = 6;

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

    private final MyGestureListener mGestureListener;
    private final GestureRecognizer mGestureRecognizer;
    private final PositionController mPositionController;

    private Listener mListener;
    private Model mModel;
    private StringTexture mLoadingText;
    private StringTexture mNoThumbnailText;
    private TileImageView mTileView;
    private EdgeView mEdgeView;
    private Texture mVideoPlayIcon;

    private ProgressSpinner mLoadingSpinner;

    private SynchronizedHandler mHandler;

    private int mLoadingState = LOADING_COMPLETE;

    private Point mImageCenter = new Point();
    private boolean mCancelExtraScalingPending;
    private boolean mFilmMode = false;
    private int mDisplayRotation = 0;
    private int mCompensation = 0;
    private boolean mFullScreen = true;
    private Rect mCameraRelativeFrame = new Rect();
    private Rect mCameraRect = new Rect();

    // [mPrevBound, mNextBound] is the range of index for all pictures in the
    // model, if we assume the index of current focused picture is 0.  So if
    // there are some previous pictures, mPrevBound < 0, and if there are some
    // next pictures, mNextBound > 0.
    private int mPrevBound;
    private int mNextBound;

    // This variable prevents us doing snapback until its values goes to 0. This
    // happens if the user gesture is still in progress or we are in a capture
    // animation.
    private int mHolding;
    private static final int HOLD_TOUCH_DOWN = 1;
    private static final int HOLD_CAPTURE_ANIMATION = 2;

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

        mGestureListener = new MyGestureListener();
        mGestureRecognizer = new GestureRecognizer(context, mGestureListener);

        mPositionController = new PositionController(context,
                new PositionController.Listener() {
                    public void invalidate() {
                        PhotoView.this.invalidate();
                    }
                    public boolean isHolding() {
                        return mHolding != 0;
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
                    // message.arg1 is the offset parameter passed to
                    // switchWithCaptureAnimation().
                    captureAnimationDone(message.arg1);
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

    public void notifyDataChange(int[] fromIndex, int prevBound, int nextBound) {
        mPrevBound = prevBound;
        mNextBound = nextBound;

        // Move the boxes
        mPositionController.moveBox(fromIndex, mPrevBound < 0, mNextBound > 0,
                mModel.isCamera(0));

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

    @Override
    protected void onLayout(
            boolean changeSize, int left, int top, int right, int bottom) {
        int w = right - left;
        int h = bottom - top;
        mTileView.layout(0, 0, w, h);
        mEdgeView.layout(0, 0, w, h);

        GLRoot root = getGLRoot();
        int displayRotation = root.getDisplayRotation();
        int compensation = root.getCompensation();
        if (mDisplayRotation != displayRotation
                || mCompensation != compensation) {
            mDisplayRotation = displayRotation;
            mCompensation = compensation;

            // We need to change the size and rotation of the Camera ScreenNail,
            // but we don't want it to animate because the size doen't actually
            // change in the eye of the user.
            for (int i = -SCREEN_NAIL_MAX; i <= SCREEN_NAIL_MAX; i++) {
                Picture p = mPictures.get(i);
                if (p.isCamera()) {
                    p.updateSize(true);
                }
            }
        }

        updateConstrainedFrame();
        if (changeSize) {
            mPositionController.setViewSize(getWidth(), getHeight());
        }
    }

    // Update the constrained frame due to layout change.
    private void updateConstrainedFrame() {
        // Get the width and height in framework orientation because the given
        // mCameraRelativeFrame is in that coordinates.
        int w = getWidth();
        int h = getHeight();
        if (mCompensation % 180 != 0) {
            int tmp = w;
            w = h;
            h = tmp;
        }
        int l = mCameraRelativeFrame.left;
        int t = mCameraRelativeFrame.top;
        int r = mCameraRelativeFrame.right;
        int b = mCameraRelativeFrame.bottom;

        // Now convert it to the coordinates we are using.
        switch (mCompensation) {
            case 0: mCameraRect.set(l, t, r, b); break;
            case 90: mCameraRect.set(h - b, l, h - t, r); break;
            case 180: mCameraRect.set(w - r, h - b, w - l, h - t); break;
            case 270: mCameraRect.set(t, w - r, b, w - l); break;
        }

        Log.d(TAG, "compensation = " + mCompensation
                + ", CameraRelativeFrame = " + mCameraRelativeFrame
                + ", mCameraRect = " + mCameraRect);
        mPositionController.setConstrainedFrame(mCameraRect);
    }

    public void setCameraRelativeFrame(Rect frame) {
        mCameraRelativeFrame.set(frame);
        updateConstrainedFrame();
    }

    // Returns the rotation we need to do to the camera texture before drawing
    // it to the canvas, assuming the camera texture is correct when the device
    // is in its natural orientation.
    private int getCameraRotation() {
        return (mCompensation - mDisplayRotation + 360) % 360;
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Pictures
    ////////////////////////////////////////////////////////////////////////////

    private interface Picture {
        void reload();
        void draw(GLCanvas canvas, Rect r);
        void setScreenNail(ScreenNail s);
        boolean isCamera();  // whether the picture is a camera preview
        void updateSize(boolean force);  // called when mCompensation changes
    };

    class FullPicture implements Picture {
        private int mRotation;
        private boolean mIsCamera;
        private boolean mIsVideo;
        private boolean mWasCameraCenter;

        public void FullPicture(TileImageView tileView) {
            mTileView = tileView;
        }

        @Override
        public void reload() {
            // mImageWidth and mImageHeight will get updated
            mTileView.notifyModelInvalidated();

            mIsCamera = mModel.isCamera(0);
            mIsVideo = mModel.isVideo(0);
            setScreenNail(mModel.getScreenNail(0));
            updateSize(false);
            updateLoadingState();
        }

        @Override
        public void updateSize(boolean force) {
            if (mIsCamera) {
                mRotation = getCameraRotation();
            } else {
                mRotation = mModel.getImageRotation(0);
            }

            int w = mTileView.mImageWidth;
            int h = mTileView.mImageHeight;
            mPositionController.setImageSize(0,
                    getRotated(mRotation, w, h),
                    getRotated(mRotation, h, w),
                    force);
        }

        @Override
        public void draw(GLCanvas canvas, Rect r) {
            boolean isCenter = mPositionController.isCenter();

            if (mLoadingState == LOADING_COMPLETE) {
                drawTileView(canvas, r);
            }
            renderMessage(canvas, r.centerX(), r.centerY());

            if (mIsCamera) {
                boolean full = !mFilmMode && isCenter
                        && mPositionController.isAtMinimalScale();
                if (full != mFullScreen) {
                    mFullScreen = full;
                    mListener.onFullScreenChanged(full);
                }
            }

            // We want to have the following transitions:
            // (1) Move camera preview out of its place: switch to film mode
            // (2) Move camera preview into its place: switch to page mode
            // The extra mWasCenter check makes sure (1) does not apply if in
            // page mode, we move _to_ the camera preview from another picture.

            // Holdings except touch-down prevent the transitions.
            if ((mHolding & ~HOLD_TOUCH_DOWN) != 0) return;

            boolean isCameraCenter = mIsCamera && isCenter;

            if (mWasCameraCenter && mIsCamera && !isCenter && !mFilmMode) {
                // Temporary disabled to de-emphasize filmstrip.
                // setFilmMode(true);
            } else if (!mWasCameraCenter && isCameraCenter && mFilmMode) {
                setFilmMode(false);
            }

            if (isCenter && !mFilmMode) {
                if (mIsCamera) {
                    // move into camera, lock
                    mListener.lockOrientation();  // Transition A
                } else {
                    // move out of camera, unlock
                    mListener.unlockOrientation();  // Transition B
                }
            }

            mWasCameraCenter = isCameraCenter;
        }

        @Override
        public void setScreenNail(ScreenNail s) {
            mTileView.setScreenNail(s);
        }

        @Override
        public boolean isCamera() {
            return mIsCamera;
        }

        private void drawTileView(GLCanvas canvas, Rect r) {
            float imageScale = mPositionController.getImageScale();
            int viewW = getWidth();
            int viewH = getHeight();
            float cx = r.exactCenterX();
            float cy = r.exactCenterY();
            float scale = 1f;  // the scaling factor due to card effect

            canvas.save(GLCanvas.SAVE_FLAG_MATRIX | GLCanvas.SAVE_FLAG_ALPHA);
            float filmRatio = mPositionController.getFilmRatio();
            boolean wantsCardEffect = CARD_EFFECT && !mIsCamera
                    && filmRatio != 1f && !mPictures.get(-1).isCamera();
            if (wantsCardEffect) {
                // Calculate the move-out progress value.
                int left = r.left;
                int right = r.right;
                float progress = calculateMoveOutProgress(left, right, viewW);
                progress = Utils.clamp(progress, -1f, 1f);

                // We only want to apply the fading animation if the scrolling
                // movement is to the right.
                if (progress < 0) {
                    scale = getScrollScale(progress);
                    float alpha = getScrollAlpha(progress);
                    scale = interpolate(filmRatio, scale, 1f);
                    alpha = interpolate(filmRatio, alpha, 1f);

                    imageScale *= scale;
                    canvas.multiplyAlpha(alpha);

                    float cxPage;  // the cx value in page mode
                    if (right - left <= viewW) {
                        // If the picture is narrower than the view, keep it at
                        // the center of the view.
                        cxPage = viewW / 2f;
                    } else {
                        // If the picture is wider than the view (it's
                        // zoomed-in), keep the left edge of the object align
                        // the the left edge of the view.
                        cxPage = (right - left) * scale / 2f;
                    }
                    cx = interpolate(filmRatio, cxPage, cx);
                }
            }

            // Draw the tile view.
            setTileViewPosition(cx, cy, viewW, viewH, imageScale);
            PhotoView.super.render(canvas);

            // Draw the play video icon.
            if (mIsVideo) {
                canvas.translate((int) (cx + 0.5f), (int) (cy + 0.5f));
                int s = (int) (scale * Math.min(r.width(), r.height()) + 0.5f);
                drawVideoPlayIcon(canvas, s);
            }

            canvas.restore();
        }

        // Set the position of the tile view
        private void setTileViewPosition(float cx, float cy,
                int viewW, int viewH, float scale) {
            // Find out the bitmap coordinates of the center of the view
            int imageW = mPositionController.getImageWidth();
            int imageH = mPositionController.getImageHeight();
            int centerX = (int) (imageW / 2f + (viewW / 2f - cx) / scale + 0.5f);
            int centerY = (int) (imageH / 2f + (viewH / 2f - cy) / scale + 0.5f);

            int inverseX = imageW - centerX;
            int inverseY = imageH - centerY;
            int x, y;
            switch (mRotation) {
                case 0: x = centerX; y = centerY; break;
                case 90: x = centerY; y = inverseX; break;
                case 180: x = inverseX; y = inverseY; break;
                case 270: x = inverseY; y = centerX; break;
                default:
                    throw new RuntimeException(String.valueOf(mRotation));
            }
            mTileView.setPosition(x, y, scale, mRotation);
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
            int s = Math.min(w, h) / ICON_RATIO;

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
        }
    }

    private class ScreenNailPicture implements Picture {
        private int mIndex;
        private int mRotation;
        private ScreenNail mScreenNail;
        private Size mSize = new Size();
        private boolean mIsCamera;
        private boolean mIsVideo;

        public ScreenNailPicture(int index) {
            mIndex = index;
        }

        @Override
        public void reload() {
            mIsCamera = mModel.isCamera(mIndex);
            mIsVideo = mModel.isVideo(mIndex);
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

            if (mIsCamera && mFullScreen != false) {
                mFullScreen = false;
                mListener.onFullScreenChanged(false);
            }

            float filmRatio = mPositionController.getFilmRatio();
            boolean wantsCardEffect = CARD_EFFECT && mIndex > 0
                    && filmRatio != 1f && !mPictures.get(0).isCamera();
            int w = getWidth();
            int cx = wantsCardEffect
                    ? (int) (interpolate(filmRatio, w / 2, r.centerX()) + 0.5f)
                    : r.centerX();
            int cy = r.centerY();
            canvas.save(GLCanvas.SAVE_FLAG_MATRIX | GLCanvas.SAVE_FLAG_ALPHA);
            canvas.translate(cx, cy);
            if (wantsCardEffect) {
                float progress = (float) (w / 2 - r.centerX()) / w;
                progress = Utils.clamp(progress, -1, 1);
                float alpha = getScrollAlpha(progress);
                float scale = getScrollScale(progress);
                alpha = interpolate(filmRatio, alpha, 1f);
                scale = interpolate(filmRatio, scale, 1f);
                canvas.multiplyAlpha(alpha);
                canvas.scale(scale, scale, 1);
            }
            if (mRotation != 0) {
                canvas.rotate(mRotation, 0, 0, 1);
            }
            int drawW = getRotated(mRotation, r.width(), r.height());
            int drawH = getRotated(mRotation, r.height(), r.width());
            mScreenNail.draw(canvas, -drawW / 2, -drawH / 2, drawW, drawH);
            if (mIsVideo) drawVideoPlayIcon(canvas, Math.min(drawW, drawH));
            canvas.restore();
        }

        @Override
        public void setScreenNail(ScreenNail s) {
            if (mScreenNail == s) return;
            mScreenNail = s;
            updateSize(false);
        }

        @Override
        public void updateSize(boolean force) {
            if (mIsCamera) {
                mRotation = getCameraRotation();
            } else {
                mRotation = mModel.getImageRotation(mIndex);
            }

            int w = 0, h = 0;
            if (mScreenNail != null) {
                w = mScreenNail.getWidth();
                h = mScreenNail.getHeight();
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
                        getRotated(mRotation, h, w),
                        force);
            }
        }

        @Override
        public boolean isCamera() {
            return mIsCamera;
        }
    }

    // Draw the video play icon (in the place where the spinner was)
    private void drawVideoPlayIcon(GLCanvas canvas, int side) {
        int s = side / ICON_RATIO;
        // Draw the video play icon at the center
        mVideoPlayIcon.draw(canvas, -s / 2, -s / 2, s, s);
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
        // If we have changed the film mode in this scaling gesture.
        private boolean mModeChanged;
        // If this scaling gesture should be ignored.
        private boolean mIgnoreScalingGesture;
        // whether the down action happened while the view is scrolling.
        private boolean mDownInScrolling;
        // If we should ignore all gestures other than onSingleTapUp.
        private boolean mIgnoreSwipingGesture;

        @Override
        public boolean onSingleTapUp(float x, float y) {
            // We do this in addition to onUp() because we want the snapback of
            // setFilmMode to happen.
            mHolding &= ~HOLD_TOUCH_DOWN;

            if (mFilmMode && !mDownInScrolling) {
                switchToHitPicture((int) (x + 0.5f), (int) (y + 0.5f));
                setFilmMode(false);
                mIgnoreUpEvent = true;
                return true;
            }

            if (mListener != null) {
                // Do the inverse transform of the touch coordinates.
                Matrix m = getGLRoot().getCompensationMatrix();
                Matrix inv = new Matrix();
                m.invert(inv);
                float[] pts = new float[] {x, y};
                inv.mapPoints(pts);
                mListener.onSingleTapUp((int) (pts[0] + 0.5f), (int) (pts[1] + 0.5f));
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(float x, float y) {
            if (mIgnoreSwipingGesture) return true;
            if (mPictures.get(0).isCamera()) return false;
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
            if (mIgnoreSwipingGesture) return true;
            mPositionController.startScroll(-dx, -dy);
            return true;
        }

        @Override
        public boolean onFling(float velocityX, float velocityY) {
            if (mIgnoreSwipingGesture) return true;
            if (swipeImages(velocityX, velocityY)) {
                mIgnoreUpEvent = true;
            } else if (mPositionController.fling(velocityX, velocityY)) {
                mIgnoreUpEvent = true;
            }
            return true;
        }

        @Override
        public boolean onScaleBegin(float focusX, float focusY) {
            if (mIgnoreSwipingGesture) return true;
            // We ignore the scaling gesture if it is a camera preview.
            mIgnoreScalingGesture = mPictures.get(0).isCamera();
            if (mIgnoreScalingGesture) {
                return true;
            }
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
            if (mIgnoreSwipingGesture) return true;
            if (mIgnoreScalingGesture) return true;
            if (mModeChanged) return true;
            if (Float.isNaN(scale) || Float.isInfinite(scale)) return false;

            // We wait for the scale change accumulated to a large enough change
            // before reacting to it. Otherwise we may mistakenly treat a
            // zoom-in gesture as zoom-out or vice versa.
            if (scale > 0.99f && scale < 1.01f) return false;

            int outOfRange = mPositionController.scaleBy(scale, focusX, focusY);

            // If mode changes, we treat this scaling gesture has ended.
            if (mCanChangeMode) {
                if ((outOfRange < 0 && !mFilmMode) ||
                        (outOfRange > 0 && mFilmMode)) {
                    stopExtraScalingIfNeeded();

                    // Removing the touch down flag allows snapback to happen
                    // for film mode change.
                    mHolding &= ~HOLD_TOUCH_DOWN;
                    setFilmMode(!mFilmMode);

                    // We need to call onScaleEnd() before setting mModeChanged
                    // to true.
                    onScaleEnd();
                    mModeChanged = true;
                    return true;
                }
           }

            if (outOfRange != 0) {
                startExtraScalingIfNeeded();
            } else {
                stopExtraScalingIfNeeded();
            }
            return true;
        }

        @Override
        public void onScaleEnd() {
            if (mIgnoreSwipingGesture) return;
            if (mIgnoreScalingGesture) return;
            if (mModeChanged) return;
            mPositionController.endScale();
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
        public void onDown() {
            if (mIgnoreSwipingGesture) return;

            mHolding |= HOLD_TOUCH_DOWN;

            if (mFilmMode && mPositionController.isScrolling()) {
                mDownInScrolling = true;
                mPositionController.stopScrolling();
            } else {
                mDownInScrolling = false;
            }
        }

        @Override
        public void onUp() {
            if (mIgnoreSwipingGesture) return;

            mHolding &= ~HOLD_TOUCH_DOWN;
            mEdgeView.onRelease();

            if (mIgnoreUpEvent) {
                mIgnoreUpEvent = false;
                return;
            }

            snapback();
        }

        public void setSwipingEnabled(boolean enabled) {
            mIgnoreSwipingGesture = !enabled;
        }
    }

    public void setSwipingEnabled(boolean enabled) {
        mGestureListener.setSwipingEnabled(enabled);
    }

    private void setFilmMode(boolean enabled) {
        if (mFilmMode == enabled) return;
        mFilmMode = enabled;
        mPositionController.setFilmMode(mFilmMode);
        mModel.setNeedFullImage(!enabled);
        mListener.onActionBarAllowed(!enabled);

        // If we leave filmstrip mode, we should lock/unlock
        if (!enabled) {
            if (mPictures.get(0).isCamera()) {
                mListener.lockOrientation();  // Transition C
            } else {
                mListener.unlockOrientation();  // Transition D
            }
        }
    }

    public boolean getFilmMode() {
        return mFilmMode;
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Framework events
    ////////////////////////////////////////////////////////////////////////////

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

    // move to the camera preview and show controls after resume
    public void resetToFirstPicture() {
        mModel.moveTo(0);
        setFilmMode(false);
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Rendering
    ////////////////////////////////////////////////////////////////////////////

    @Override
    protected void render(GLCanvas canvas) {
        float filmRatio = mPositionController.getFilmRatio();

        // Draw next photos. In page mode, we draw only one next photo.
        int lastPhoto = (filmRatio == 0f) ? 1 : SCREEN_NAIL_MAX;
        for (int i = lastPhoto; i > 0; i--) {
            Rect r = mPositionController.getPosition(i);
            mPictures.get(i).draw(canvas, r);
        }

        // Draw current photo
        mPictures.get(0).draw(canvas, mPositionController.getPosition(0));

        // Draw previous photos. In page mode, we draw only one previous photo.
        lastPhoto = (filmRatio == 0f) ? -1: -SCREEN_NAIL_MAX;
        for (int i = -1; i >= lastPhoto; i--) {
            Rect r = mPositionController.getPosition(i);
            mPictures.get(i).draw(canvas, r);
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

    // Switch to the previous or next picture if the hit position is inside
    // one of their boxes. This runs in main thread.
    private void switchToHitPicture(int x, int y) {
        if (mPrevBound < 0) {
            Rect r = mPositionController.getPosition(-1);
            if (r.right >= x) {
                slideToPrevPicture();
                return;
            }
        }

        if (mNextBound > 0) {
            Rect r = mPositionController.getPosition(1);
            if (r.left <= x) {
                slideToNextPicture();
                return;
            }
        }
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
        mModel.moveTo(mModel.getCurrentIndex() + 1);
    }

    private void switchToPrevImage() {
        mModel.moveTo(mModel.getCurrentIndex() - 1);
    }

    private void switchToFirstImage() {
        mModel.moveTo(0);
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
        if (mHolding != 0) return true;
        if (offset == 1) {
            if (mNextBound <= 0) return false;
            // Temporary disable action bar until the capture animation is done.
            if (!mFilmMode) mListener.onActionBarAllowed(false);
            switchToNextImage();
            mPositionController.startCaptureAnimationSlide(-1);
        } else if (offset == -1) {
            if (mPrevBound >= 0) return false;
            switchToFirstImage();
            mPositionController.startCaptureAnimationSlide(1);
        } else {
            return false;
        }
        mHolding |= HOLD_CAPTURE_ANIMATION;
        Message m = mHandler.obtainMessage(MSG_CAPTURE_ANIMATION_DONE, offset, 0);
        mHandler.sendMessageDelayed(m, 800);
        return true;
    }

    private void captureAnimationDone(int offset) {
        mHolding &= ~HOLD_CAPTURE_ANIMATION;
        if (offset == 1) {
            // move out of camera, unlock
            if (!mFilmMode) {
                // Now the capture animation is done, enable the action bar.
                mListener.onActionBarAllowed(true);
            }
        }
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
        //          |<-->|               progress = 0 when left = viewWidth / 2 - w / 2
        // |<-->|                        progress = 1 when left = -w
        if (w < viewWidth) {
            int zx = viewWidth / 2 - w / 2;
            if (left > zx) {
                return -(left - zx) / (float) (viewWidth - zx);  // progress = (0, -1]
            } else {
                return (left - zx) / (float) (-w - zx);  // progress = [0, 1]
            }
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

    // Returns an interpolated value for the page/film transition.
    // When ratio = 0, the result is from.
    // When ratio = 1, the result is to.
    private static float interpolate(float ratio, float from, float to) {
        return from + (to - from) * ratio * ratio;
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Simple public utilities
    ////////////////////////////////////////////////////////////////////////////

    public void setListener(Listener listener) {
        mListener = listener;
    }
}
