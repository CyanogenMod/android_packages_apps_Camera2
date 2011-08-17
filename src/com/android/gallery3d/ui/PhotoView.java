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
import android.graphics.RectF;
import android.os.Message;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

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

    // We try to scale up the image to fill the screen. But in order not to
    // scale too much for small icons, we limit the max up-scaling factor here.
    private static final float SCALE_LIMIT = 4;

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
    private Texture mVideoPlayIcon;

    private boolean mShowVideoPlayIcon;
    private ProgressSpinner mLoadingSpinner;

    private SynchronizedHandler mHandler;

    private int mLoadingState = LOADING_COMPLETE;

    private RectF mTempRect = new RectF();
    private float[] mTempPoints = new float[8];

    private int mImageRotation;

    private Path mOpenedItemPath;
    private GalleryActivity mActivity;

    public PhotoView(GalleryActivity activity) {
        mActivity = activity;
        mTileView = new TileImageView(activity);
        addComponent(mTileView);
        Context context = activity.getAndroidContext();
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

        mPositionController = new PositionController(this);
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
        int inverseX = mPositionController.mImageW - centerX;
        int inverseY = mPositionController.mImageH - centerY;
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

    private RectF getImageBounds() {
        PositionController p = mPositionController;
        float points[] = mTempPoints;

        /*
         * (p0,p1)----------(p2,p3)
         *   |                  |
         *   |                  |
         * (p4,p5)----------(p6,p7)
         */
        points[0] = points[4] = -p.mCurrentX;
        points[1] = points[3] = -p.mCurrentY;
        points[2] = points[6] = p.mImageW - p.mCurrentX;
        points[5] = points[7] = p.mImageH - p.mCurrentY;

        RectF rect = mTempRect;
        rect.set(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);

        float scale = p.mCurrentScale;
        float offsetX = p.mViewW / 2;
        float offsetY = p.mViewH / 2;
        for (int i = 0; i < 4; ++i) {
            float x = points[i + i] * scale + offsetX;
            float y = points[i + i + 1] * scale + offsetY;
            if (x < rect.left) rect.left = x;
            if (x > rect.right) rect.right = x;
            if (y < rect.top) rect.top = y;
            if (y > rect.bottom) rect.bottom = y;
        }
        return rect;
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
        RectF bounds = getImageBounds();
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

    private static class PositionController {
        private long mAnimationStartTime = NO_ANIMATION;
        private static final long NO_ANIMATION = -1;
        private static final long LAST_ANIMATION = -2;

        // Animation time in milliseconds.
        private static final float ANIM_TIME_SCROLL = 0;
        private static final float ANIM_TIME_SCALE = 50;
        private static final float ANIM_TIME_SNAPBACK = 600;
        private static final float ANIM_TIME_SLIDE = 400;
        private static final float ANIM_TIME_ZOOM = 300;

        private int mAnimationKind;
        private final static int ANIM_KIND_SCROLL = 0;
        private final static int ANIM_KIND_SCALE = 1;
        private final static int ANIM_KIND_SNAPBACK = 2;
        private final static int ANIM_KIND_SLIDE = 3;
        private final static int ANIM_KIND_ZOOM = 4;

        private PhotoView mViewer;
        private int mImageW, mImageH;
        private int mViewW, mViewH;

        // The X, Y are the coordinate on bitmap which shows on the center of
        // the view. We always keep the mCurrent{X,Y,SCALE} sync with the actual
        // values used currently.
        private int mCurrentX, mFromX, mToX;
        private int mCurrentY, mFromY, mToY;
        private float mCurrentScale, mFromScale, mToScale;

        // The offsets from the center of the view to the user's focus point,
        // converted to the bitmap domain.
        private float mPrevOffsetX;
        private float mPrevOffsetY;
        private boolean mInScale;
        private boolean mUseViewSize = true;

        // The limits for position and scale.
        private float mScaleMin, mScaleMax = 4f;

        PositionController(PhotoView viewer) {
            mViewer = viewer;
        }

        public void setImageSize(int width, int height) {

            // If no image available, use view size.
            if (width == 0 || height == 0) {
                mUseViewSize = true;
                mImageW = mViewW;
                mImageH = mViewH;
                mCurrentX = mImageW / 2;
                mCurrentY = mImageH / 2;
                mCurrentScale = 1;
                mScaleMin = 1;
                mViewer.setPosition(mCurrentX, mCurrentY, mCurrentScale);
                return;
            }

            mUseViewSize = false;

            float ratio = Math.min(
                    (float) mImageW / width, (float) mImageH / height);

            mCurrentX = translate(mCurrentX, mImageW, width, ratio);
            mCurrentY = translate(mCurrentY, mImageH, height, ratio);
            mCurrentScale = mCurrentScale * ratio;

            mFromX = translate(mFromX, mImageW, width, ratio);
            mFromY = translate(mFromY, mImageH, height, ratio);
            mFromScale = mFromScale * ratio;

            mToX = translate(mToX, mImageW, width, ratio);
            mToY = translate(mToY, mImageH, height, ratio);
            mToScale = mToScale * ratio;

            mImageW = width;
            mImageH = height;

            mScaleMin = getMinimalScale(width, height, 0);

            // Scale the new image to fit into the old one
            if (mViewer.mOpenedItemPath != null) {
                Position position = PositionRepository
                        .getInstance(mViewer.mActivity).get(Long.valueOf(
                        System.identityHashCode(mViewer.mOpenedItemPath)));
                mViewer.mOpenedItemPath = null;
                if (position != null) {
                    float scale = 240f / Math.min(width, height);
                    mCurrentX = Math.round((mViewW / 2f - position.x) / scale) + mImageW / 2;
                    mCurrentY = Math.round((mViewH / 2f - position.y) / scale) + mImageH / 2;
                    mCurrentScale = scale;
                    mViewer.mTransitionMode = TRANS_OPEN_ANIMATION;
                    startSnapback();
                }
            } else if (mAnimationStartTime == NO_ANIMATION) {
                mCurrentScale = Utils.clamp(mCurrentScale, mScaleMin, mScaleMax);
            }
            mViewer.setPosition(mCurrentX, mCurrentY, mCurrentScale);
        }

        public void zoomIn(float tapX, float tapY, float targetScale) {
            if (targetScale > mScaleMax) targetScale = mScaleMax;
            float scale = mCurrentScale;
            float tempX = (tapX - mViewW / 2) / mCurrentScale + mCurrentX;
            float tempY = (tapY - mViewH / 2) / mCurrentScale + mCurrentY;

            // mCurrentX + (mViewW / 2) * (1 / targetScale) < mImageW
            // mCurrentX - (mViewW / 2) * (1 / targetScale) > 0
            float min = mViewW / 2.0f / targetScale;
            float max = mImageW  - mViewW / 2.0f / targetScale;
            int targetX = (int) Utils.clamp(tempX, min, max);

            min = mViewH / 2.0f / targetScale;
            max = mImageH  - mViewH / 2.0f / targetScale;
            int targetY = (int) Utils.clamp(tempY,  min, max);

            // If the width of the image is less then the view, center the image
            if (mImageW * targetScale < mViewW) targetX = mImageW / 2;
            if (mImageH * targetScale < mViewH) targetY = mImageH / 2;

            startAnimation(targetX, targetY, targetScale, ANIM_KIND_ZOOM);
        }

        public void resetToFullView() {
            startAnimation(mImageW / 2, mImageH / 2, mScaleMin, ANIM_KIND_ZOOM);
        }

        private float getMinimalScale(int w, int h, int rotation) {
            return Math.min(SCALE_LIMIT, ((rotation / 90) & 0x01) == 0
                    ? Math.min((float) mViewW / w, (float) mViewH / h)
                    : Math.min((float) mViewW / h, (float) mViewH / w));
        }

        private static int translate(int value, int size, int updateSize, float ratio) {
            return Math.round(
                    (value + (updateSize * ratio - size) / 2f) / ratio);
        }

        public void setViewSize(int viewW, int viewH) {
            boolean needLayout = mViewW == 0 || mViewH == 0;

            mViewW = viewW;
            mViewH = viewH;

            if (mUseViewSize) {
                mImageW = viewW;
                mImageH = viewH;
                mCurrentX = mImageW / 2;
                mCurrentY = mImageH / 2;
                mCurrentScale = 1;
                mViewer.setPosition(mCurrentX, mCurrentY, mCurrentScale);
            } else {
                boolean wasMinScale = (mCurrentScale == mScaleMin);
                mScaleMin = Math.min(SCALE_LIMIT, Math.min(
                        (float) viewW / mImageW, (float) viewH / mImageH));
                if (needLayout || mCurrentScale < mScaleMin || wasMinScale) {
                    mCurrentX = mImageW / 2;
                    mCurrentY = mImageH / 2;
                    mCurrentScale = mScaleMin;
                    mViewer.setPosition(mCurrentX, mCurrentY, mCurrentScale);
                }
            }
        }

        public void stopAnimation() {
            mAnimationStartTime = NO_ANIMATION;
        }

        public void skipAnimation() {
            if (mAnimationStartTime == NO_ANIMATION) return;
            mAnimationStartTime = NO_ANIMATION;
            mCurrentX = mToX;
            mCurrentY = mToY;
            mCurrentScale = mToScale;
        }

        public void scrollBy(float dx, float dy, int type) {
            startAnimation(getTargetX() + Math.round(dx / mCurrentScale),
                    getTargetY() + Math.round(dy / mCurrentScale),
                    mCurrentScale, type);
        }

        public void beginScale(float focusX, float focusY) {
            mInScale = true;
            mPrevOffsetX = (focusX - mViewW / 2f) / mCurrentScale;
            mPrevOffsetY = (focusY - mViewH / 2f) / mCurrentScale;
        }

        public void scaleBy(float s, float focusX, float focusY) {

            // The focus point should keep this position on the ImageView.
            // So, mCurrentX + mPrevOffsetX = mCurrentX' + offsetX.
            // mCurrentY + mPrevOffsetY = mCurrentY' + offsetY.
            float offsetX = (focusX - mViewW / 2f) / mCurrentScale;
            float offsetY = (focusY - mViewH / 2f) / mCurrentScale;

            startAnimation(getTargetX() - Math.round(offsetX - mPrevOffsetX),
                           getTargetY() - Math.round(offsetY - mPrevOffsetY),
                           getTargetScale() * s, ANIM_KIND_SCALE);
            mPrevOffsetX = offsetX;
            mPrevOffsetY = offsetY;
        }

        public void endScale() {
            mInScale = false;
            startSnapbackIfNeeded();
        }

        public void up() {
            startSnapback();
        }

        public void startSlideInAnimation(int fromX) {
            mFromX = Math.round(fromX + (mImageW - mViewW) / 2f);
            mFromY = Math.round(mImageH / 2f);
            mCurrentX = mFromX;
            mCurrentY = mFromY;
            startAnimation(mImageW / 2, mImageH / 2, mCurrentScale,
                    ANIM_KIND_SLIDE);
        }

        public void startHorizontalSlide(int distance) {
            scrollBy(distance, 0, ANIM_KIND_SLIDE);
        }

        private void startAnimation(
                int centerX, int centerY, float scale, int kind) {
            if (centerX == mCurrentX && centerY == mCurrentY
                    && scale == mCurrentScale) return;

            mFromX = mCurrentX;
            mFromY = mCurrentY;
            mFromScale = mCurrentScale;

            mToX = centerX;
            mToY = centerY;
            mToScale = Utils.clamp(scale, 0.6f * mScaleMin, 1.4f * mScaleMax);

            // If the scaled dimension is smaller than the view,
            // force it to be in the center.
            if (Math.floor(mImageH * mToScale) <= mViewH) {
                mToY = mImageH / 2;
            }

            mAnimationStartTime = SystemClock.uptimeMillis();
            mAnimationKind = kind;
            if (advanceAnimation()) mViewer.invalidate();
        }

        // Returns true if redraw is needed.
        public boolean advanceAnimation() {
            if (mAnimationStartTime == NO_ANIMATION) {
                return false;
            } else if (mAnimationStartTime == LAST_ANIMATION) {
                mAnimationStartTime = NO_ANIMATION;
                if (mViewer.mTransitionMode != TRANS_NONE) {
                    mViewer.mHandler.sendEmptyMessage(MSG_TRANSITION_COMPLETE);
                    return false;
                } else {
                    return startSnapbackIfNeeded();
                }
            }

            float animationTime;
            if (mAnimationKind == ANIM_KIND_SCROLL) {
                animationTime = ANIM_TIME_SCROLL;
            } else if (mAnimationKind == ANIM_KIND_SCALE) {
                animationTime = ANIM_TIME_SCALE;
            } else if (mAnimationKind == ANIM_KIND_SLIDE) {
                animationTime = ANIM_TIME_SLIDE;
            } else if (mAnimationKind == ANIM_KIND_ZOOM) {
                animationTime = ANIM_TIME_ZOOM;
            } else /* if (mAnimationKind == ANIM_KIND_SNAPBACK) */ {
                animationTime = ANIM_TIME_SNAPBACK;
            }

            float progress;
            if (animationTime == 0) {
                progress = 1;
            } else {
                long now = SystemClock.uptimeMillis();
                progress = (now - mAnimationStartTime) / animationTime;
            }

            if (progress >= 1) {
                progress = 1;
                mCurrentX = mToX;
                mCurrentY = mToY;
                mCurrentScale = mToScale;
                mAnimationStartTime = LAST_ANIMATION;
            } else {
                float f = 1 - progress;
                if (mAnimationKind == ANIM_KIND_SCROLL) {
                    progress = 1 - f;  // linear
                } else if (mAnimationKind == ANIM_KIND_SCALE) {
                    progress = 1 - f * f;  // quadratic
                } else /* if mAnimationKind is ANIM_KIND_SNAPBACK,
                            ANIM_KIND_ZOOM or ANIM_KIND_SLIDE */ {
                    progress = 1 - f * f * f * f * f; // x^5
                }
                linearInterpolate(progress);
            }
            mViewer.setPosition(mCurrentX, mCurrentY, mCurrentScale);
            return true;
        }

        private void linearInterpolate(float progress) {
            // To linearly interpolate the position, we have to translate the
            // coordinates. The meaning of the translated point (x, y) is the
            // coordinates of the center of the bitmap on the view component.
            float fromX = mViewW / 2f + (mImageW / 2f - mFromX) * mFromScale;
            float toX = mViewW / 2f + (mImageW / 2f - mToX) * mToScale;
            float currentX = fromX + progress * (toX - fromX);

            float fromY = mViewH / 2f + (mImageH / 2f - mFromY) * mFromScale;
            float toY = mViewH / 2f + (mImageH / 2f - mToY) * mToScale;
            float currentY = fromY + progress * (toY - fromY);

            mCurrentScale = mFromScale + progress * (mToScale - mFromScale);
            mCurrentX = Math.round(
                    mImageW / 2f + (mViewW / 2f - currentX) / mCurrentScale);
            mCurrentY = Math.round(
                    mImageH / 2f + (mViewH / 2f - currentY) / mCurrentScale);
        }

        // Returns true if redraw is needed.
        private boolean startSnapbackIfNeeded() {
            if (mAnimationStartTime != NO_ANIMATION) return false;
            if (mInScale) return false;
            if (mAnimationKind == ANIM_KIND_SCROLL && mViewer.isDown()) {
                return false;
            }
            return startSnapback();
        }

        public boolean startSnapback() {
            boolean needAnimation = false;
            int x = mCurrentX;
            int y = mCurrentY;
            float scale = mCurrentScale;

            if (mCurrentScale < mScaleMin || mCurrentScale > mScaleMax) {
                needAnimation = true;
                scale = Utils.clamp(mCurrentScale, mScaleMin, mScaleMax);
            }

            // The number of pixels when the edge is aligned.
            int left = (int) Math.ceil(mViewW / (2 * scale));
            int right = mImageW - left;
            int top = (int) Math.ceil(mViewH / (2 * scale));
            int bottom = mImageH - top;

            if (mImageW * scale > mViewW) {
                if (mCurrentX < left) {
                    needAnimation = true;
                    x = left;
                } else if (mCurrentX > right) {
                    needAnimation = true;
                    x = right;
                }
            } else if (mCurrentX != mImageW / 2) {
                needAnimation = true;
                x = mImageW / 2;
            }

            if (mImageH * scale > mViewH) {
                if (mCurrentY < top) {
                    needAnimation = true;
                    y = top;
                } else if (mCurrentY > bottom) {
                    needAnimation = true;
                    y = bottom;
                }
            } else if (mCurrentY != mImageH / 2) {
                needAnimation = true;
                y = mImageH / 2;
            }

            if (needAnimation) {
                startAnimation(x, y, scale, ANIM_KIND_SNAPBACK);
            }

            return needAnimation;
        }

        private float getTargetScale() {
            if (mAnimationStartTime == NO_ANIMATION
                    || mAnimationKind == ANIM_KIND_SNAPBACK) return mCurrentScale;
            return mToScale;
        }

        private int getTargetX() {
            if (mAnimationStartTime == NO_ANIMATION
                    || mAnimationKind == ANIM_KIND_SNAPBACK) return mCurrentX;
            return mToX;
        }

        private int getTargetY() {
            if (mAnimationStartTime == NO_ANIMATION
                    || mAnimationKind == ANIM_KIND_SNAPBACK) return mCurrentY;
            return mToY;
        }
    }

    @Override
    protected void render(GLCanvas canvas) {
        PositionController p = mPositionController;

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

            if (prevNail.mVisible) prevNail.draw(canvas);
            if (nextNail.mVisible) nextNail.draw(canvas);
        }

        // Draw the progress spinner and the text below it
        //
        // (x, y) is where we put the center of the spinner.
        // s is the size of the video play icon, and we use s to layout text
        // because we want to keep the text at the same place when the video
        // play icon is shown instead of the spinner.
        int w = getWidth();
        int h = getHeight();
        int x = Math.round(getImageBounds().centerX());
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

        if (mPositionController.advanceAnimation()) invalidate();
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

    private static boolean isAlmostEquals(float a, float b) {
        float diff = a - b;
        return (diff < 0 ? -diff : diff) < 0.02f;
    }

    private boolean swipeImages(float velocity) {
        if (mTransitionMode != TRANS_NONE
                && mTransitionMode != TRANS_SWITCH_NEXT
                && mTransitionMode != TRANS_SWITCH_PREVIOUS) return false;

        ScreenNailEntry next = mScreenNails[ENTRY_NEXT];
        ScreenNailEntry prev = mScreenNails[ENTRY_PREVIOUS];

        int width = getWidth();

        // If the edge of the current photo is visible and the sweeping velocity
        // exceed the threshold, switch to next / previous image
        PositionController controller = mPositionController;
        if (isAlmostEquals(controller.mCurrentScale, controller.mScaleMin)) {
            if (velocity < -SWIPE_THRESHOLD) {
                stopCurrentSwipingIfNeeded();
                if (next.isEnabled()) {
                    mTransitionMode = TRANS_SWITCH_NEXT;
                    controller.startHorizontalSlide(next.mOffsetX - width / 2);
                    return true;
                }
                return false;
            }
            if (velocity > SWIPE_THRESHOLD) {
                stopCurrentSwipingIfNeeded();
                if (prev.isEnabled()) {
                    mTransitionMode = TRANS_SWITCH_PREVIOUS;
                    controller.startHorizontalSlide(prev.mOffsetX - width / 2);
                    return true;
                }
                return false;
            }
        }

        if (mTransitionMode != TRANS_NONE) return false;

        // Decide whether to swiping to the next/prev image in the zoom-in case
        RectF bounds = getImageBounds();
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
            mPositionController.scrollBy(
                    dx, dy, PositionController.ANIM_KIND_SCROLL);
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
            mIgnoreUpEvent = true;
            if (!swipeImages(velocityX) && mTransitionMode == TRANS_NONE) {
                mPositionController.up();
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (mTransitionMode != TRANS_NONE) return true;
            PositionController controller = mPositionController;
            float scale = controller.mCurrentScale;
            // onDoubleTap happened on the second ACTION_DOWN.
            // We need to ignore the next UP event.
            mIgnoreUpEvent = true;
            if (scale <= 1.0f || isAlmostEquals(scale, controller.mScaleMin)) {
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
            mPositionController.scaleBy(scale,
                    detector.getFocusX(), detector.getFocusY());
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
            swipeImages(0);
        }
    }

    public void notifyOnNewImage() {
        mPositionController.setImageSize(0, 0);
    }

    public void startSlideInAnimation(int direction) {
        PositionController a = mPositionController;
        a.stopAnimation();
        switch (direction) {
            case TRANS_SLIDE_IN_LEFT: {
                mTransitionMode = TRANS_SLIDE_IN_LEFT;
                a.startSlideInAnimation(a.mViewW);
                break;
            }
            case TRANS_SLIDE_IN_RIGHT: {
                mTransitionMode = TRANS_SLIDE_IN_RIGHT;
                a.startSlideInAnimation(-a.mViewW);
                break;
            }
            default: throw new IllegalArgumentException(String.valueOf(direction));
        }
    }

    private class MyDownUpListener implements DownUpDetector.DownUpListener {
        public void onDown(MotionEvent e) {
        }

        public void onUp(MotionEvent e) {
            if (mIgnoreUpEvent) {
                mIgnoreUpEvent = false;
                return;
            }
            if (!swipeImages(0) && mTransitionMode == TRANS_NONE) {
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

    private boolean isDown() {
        return mDownUpDetector.isDown();
    }

    public static interface Model extends TileImageView.Model {
        public void next();
        public void previous();
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
            float s = mPositionController.getMinimalScale(width, height, mRotation);
            mDrawWidth = Math.round(width * s);
            mDrawHeight = Math.round(height * s);
        }

        public boolean isEnabled() {
            return mEnabled;
        }

        public void draw(GLCanvas canvas) {
            int x = mOffsetX;
            int y = getHeight() / 2;

            if (mTexture != null) {
                if (mRotation != 0) {
                    canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
                    canvas.translate(x, y, 0);
                    canvas.rotate(mRotation, 0, 0, 1); //mRotation
                    canvas.translate(-x, -y, 0);
                }
                mTexture.draw(canvas, x - mDrawWidth / 2, y - mDrawHeight / 2,
                        mDrawWidth, mDrawHeight);
                if (mRotation != 0) {
                    canvas.restore();
                }
            }
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
}
