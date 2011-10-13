/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.widget.Scroller;

class PositionController {
    private static final String TAG = "PositionController";
    private long mAnimationStartTime = NO_ANIMATION;
    private static final long NO_ANIMATION = -1;
    private static final long LAST_ANIMATION = -2;

    private int mAnimationKind;
    private float mAnimationDuration;
    private final static int ANIM_KIND_SCROLL = 0;
    private final static int ANIM_KIND_SCALE = 1;
    private final static int ANIM_KIND_SNAPBACK = 2;
    private final static int ANIM_KIND_SLIDE = 3;
    private final static int ANIM_KIND_ZOOM = 4;
    private final static int ANIM_KIND_FLING = 5;

    // Animation time in milliseconds. The order must match ANIM_KIND_* above.
    private final static int ANIM_TIME[] = {
        0,    // ANIM_KIND_SCROLL
        50,   // ANIM_KIND_SCALE
        600,  // ANIM_KIND_SNAPBACK
        400,  // ANIM_KIND_SLIDE
        300,  // ANIM_KIND_ZOOM
        0,    // ANIM_KIND_FLING (the duration is calculated dynamically)
    };

    // We try to scale up the image to fill the screen. But in order not to
    // scale too much for small icons, we limit the max up-scaling factor here.
    private static final float SCALE_LIMIT = 4;

    private PhotoView mViewer;
    private EdgeView mEdgeView;
    private int mImageW, mImageH;
    private int mViewW, mViewH;

    // The X, Y are the coordinate on bitmap which shows on the center of
    // the view. We always keep the mCurrent{X,Y,Scale} sync with the actual
    // values used currently.
    private int mCurrentX, mFromX, mToX;
    private int mCurrentY, mFromY, mToY;
    private float mCurrentScale, mFromScale, mToScale;

    // The focus point of the scaling gesture (in bitmap coordinates).
    private int mFocusBitmapX;
    private int mFocusBitmapY;
    private boolean mInScale;

    // The minimum and maximum scale we allow.
    private float mScaleMin, mScaleMax = SCALE_LIMIT;

    // This is used by the fling animation
    private FlingScroller mScroller;

    // The bound of the stable region, see the comments above
    // calculateStableBound() for details.
    private int mBoundLeft, mBoundRight, mBoundTop, mBoundBottom;

    // Assume the image size is the same as view size before we know the actual
    // size of image.
    private boolean mUseViewSize = true;

    private RectF mTempRect = new RectF();
    private float[] mTempPoints = new float[8];

    public PositionController(PhotoView viewer, Context context,
            EdgeView edgeView) {
        mViewer = viewer;
        mEdgeView = edgeView;
        mScroller = new FlingScroller();
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

        // See the comment above translate() for details.
        mCurrentX = translate(mCurrentX, mImageW, width, ratio);
        mCurrentY = translate(mCurrentY, mImageH, height, ratio);
        mCurrentScale = mCurrentScale * ratio;

        mFromX = translate(mFromX, mImageW, width, ratio);
        mFromY = translate(mFromY, mImageH, height, ratio);
        mFromScale = mFromScale * ratio;

        mToX = translate(mToX, mImageW, width, ratio);
        mToY = translate(mToY, mImageH, height, ratio);
        mToScale = mToScale * ratio;

        mFocusBitmapX = translate(mFocusBitmapX, mImageW, width, ratio);
        mFocusBitmapY = translate(mFocusBitmapY, mImageH, height, ratio);

        mImageW = width;
        mImageH = height;

        mScaleMin = getMinimalScale(mImageW, mImageH);

        // Start animation from the saved position if we have one.
        Position position = mViewer.retrieveSavedPosition();
        if (position != null) {
            // The animation starts from 240 pixels and centers at the image
            // at the saved position.
            float scale = 240f / Math.min(width, height);
            mCurrentX = Math.round((mViewW / 2f - position.x) / scale) + mImageW / 2;
            mCurrentY = Math.round((mViewH / 2f - position.y) / scale) + mImageH / 2;
            mCurrentScale = scale;
            mViewer.openAnimationStarted();
            startSnapback();
        } else if (mAnimationStartTime == NO_ANIMATION) {
            mCurrentScale = Utils.clamp(mCurrentScale, mScaleMin, mScaleMax);
        }
        mViewer.setPosition(mCurrentX, mCurrentY, mCurrentScale);
    }

    public void zoomIn(float tapX, float tapY, float targetScale) {
        if (targetScale > mScaleMax) targetScale = mScaleMax;

        // Convert the tap position to image coordinate
        int tempX = Math.round((tapX - mViewW / 2) / mCurrentScale + mCurrentX);
        int tempY = Math.round((tapY - mViewH / 2) / mCurrentScale + mCurrentY);

        calculateStableBound(targetScale);
        int targetX = Utils.clamp(tempX, mBoundLeft, mBoundRight);
        int targetY = Utils.clamp(tempY, mBoundTop, mBoundBottom);

        startAnimation(targetX, targetY, targetScale, ANIM_KIND_ZOOM);
    }

    public void resetToFullView() {
        startAnimation(mImageW / 2, mImageH / 2, mScaleMin, ANIM_KIND_ZOOM);
    }

    public float getMinimalScale(int w, int h) {
        return Math.min(SCALE_LIMIT,
                Math.min((float) mViewW / w, (float) mViewH / h));
    }

    // Translate a coordinate on bitmap if the bitmap size changes.
    // If the aspect ratio doesn't change, it's easy:
    //
    //         r  = w / w' (= h / h')
    //         x' = x / r
    //         y' = y / r
    //
    // However the aspect ratio may change. That happens when the user slides
    // a image before it's loaded, we don't know the actual aspect ratio, so
    // we will assume one. When we receive the actual bitmap size, we need to
    // translate the coordinate from the old bitmap into the new bitmap.
    //
    // What we want to do is center the bitmap at the original position.
    //
    //         ...+--+...
    //         .  |  |  .
    //         .  |  |  .
    //         ...+--+...
    //
    // First we scale down the new bitmap by a factor r = min(w/w', h/h').
    // Overlay it onto the original bitmap. Now (0, 0) of the old bitmap maps
    // to (-(w-w'*r)/2 / r, -(h-h'*r)/2 / r) in the new bitmap. So (x, y) of
    // the old bitmap maps to (x', y') in the new bitmap, where
    //         x' = (x-(w-w'*r)/2) / r = w'/2 + (x-w/2)/r
    //         y' = (y-(h-h'*r)/2) / r = h'/2 + (y-h/2)/r
    private static int translate(int value, int size, int newSize, float ratio) {
        return Math.round(newSize / 2f + (value - size / 2f) / ratio);
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
            mScaleMin = 1;
            mViewer.setPosition(mCurrentX, mCurrentY, mCurrentScale);
            return;
        }

        // In most cases we want to keep the scaling factor intact when the
        // view size changes. The cases we want to reset the scaling factor
        // (to fit the view if possible) are (1) the scaling factor is too
        // small for the new view size (2) the scaling factor has not been
        // changed by the user.
        boolean wasMinScale = (mCurrentScale == mScaleMin);
        mScaleMin = getMinimalScale(mImageW, mImageH);

        if (needLayout || mCurrentScale < mScaleMin || wasMinScale) {
            mCurrentX = mImageW / 2;
            mCurrentY = mImageH / 2;
            mCurrentScale = mScaleMin;
            mViewer.setPosition(mCurrentX, mCurrentY, mCurrentScale);
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

    public void beginScale(float focusX, float focusY) {
        mInScale = true;
        mFocusBitmapX = Math.round(mCurrentX +
                (focusX - mViewW / 2f) / mCurrentScale);
        mFocusBitmapY = Math.round(mCurrentY +
                (focusY - mViewH / 2f) / mCurrentScale);
    }

    public void scaleBy(float s, float focusX, float focusY) {

        // We want to keep the focus point (on the bitmap) the same as when
        // we begin the scale guesture, that is,
        //
        // mCurrentX' + (focusX - mViewW / 2f) / scale = mFocusBitmapX
        //
        s *= getTargetScale();
        int x = Math.round(mFocusBitmapX - (focusX - mViewW / 2f) / s);
        int y = Math.round(mFocusBitmapY - (focusY - mViewH / 2f) / s);

        startAnimation(x, y, s, ANIM_KIND_SCALE);
    }

    public void endScale() {
        mInScale = false;
        startSnapbackIfNeeded();
    }

    public float getCurrentScale() {
        return mCurrentScale;
    }

    public boolean isAtMinimalScale() {
        return isAlmostEquals(mCurrentScale, mScaleMin);
    }

    private static boolean isAlmostEquals(float a, float b) {
        float diff = a - b;
        return (diff < 0 ? -diff : diff) < 0.02f;
    }

    public void up() {
        startSnapback();
    }

    //             |<--| (1/2) * mImageW
    // +-------+-------+-------+
    // |       |       |       |
    // |       |   o   |       |
    // |       |       |       |
    // +-------+-------+-------+
    // |<----------| (3/2) * mImageW
    // Slide in the image from left or right.
    // Precondition: mCurrentScale = 1 (mView{W|H} == mImage{W|H}).
    // Sliding from left:  mCurrentX = (1/2) * mImageW
    //              right: mCurrentX = (3/2) * mImageW
    public void startSlideInAnimation(int direction) {
        int fromX = (direction == PhotoView.TRANS_SLIDE_IN_LEFT) ?
                mImageW / 2 : 3 * mImageW / 2;
        mFromX = Math.round(fromX);
        mFromY = Math.round(mImageH / 2f);
        mCurrentX = mFromX;
        mCurrentY = mFromY;
        startAnimation(
                mImageW / 2, mImageH / 2, mCurrentScale, ANIM_KIND_SLIDE);
    }

    public void startHorizontalSlide(int distance) {
        scrollBy(distance, 0, ANIM_KIND_SLIDE);
    }

    private void scrollBy(float dx, float dy, int type) {
        startAnimation(getTargetX() + Math.round(dx / mCurrentScale),
                getTargetY() + Math.round(dy / mCurrentScale),
                mCurrentScale, type);
    }

    public void startScroll(float dx, float dy, boolean hasNext,
            boolean hasPrev) {
        int x = getTargetX() + Math.round(dx / mCurrentScale);
        int y = getTargetY() + Math.round(dy / mCurrentScale);

        calculateStableBound(mCurrentScale);

        // Vertical direction: If we have space to move in the vertical
        // direction, we show the edge effect when scrolling reaches the edge.
        if (mBoundTop != mBoundBottom) {
            if (y < mBoundTop) {
                mEdgeView.onPull(mBoundTop - y, EdgeView.TOP);
            } else if (y > mBoundBottom) {
                mEdgeView.onPull(y - mBoundBottom, EdgeView.BOTTOM);
            }
        }

        y = Utils.clamp(y, mBoundTop, mBoundBottom);

        // Horizontal direction: we show the edge effect when the scrolling
        // tries to go left of the first image or go right of the last image.
        if (!hasPrev && x < mBoundLeft) {
            int pixels = Math.round((mBoundLeft - x) * mCurrentScale);
            mEdgeView.onPull(pixels, EdgeView.LEFT);
            x = mBoundLeft;
        } else if (!hasNext && x > mBoundRight) {
            int pixels = Math.round((x - mBoundRight) * mCurrentScale);
            mEdgeView.onPull(pixels, EdgeView.RIGHT);
            x = mBoundRight;
        }

        startAnimation(x, y, mCurrentScale, ANIM_KIND_SCROLL);
    }

    public boolean fling(float velocityX, float velocityY) {
        // We only want to do fling when the picture is zoomed-in.
        if (mImageW * mCurrentScale <= mViewW &&
            mImageH * mCurrentScale <= mViewH) {
            return false;
        }

        calculateStableBound(mCurrentScale);
        mScroller.fling(mCurrentX, mCurrentY,
                Math.round(-velocityX / mCurrentScale),
                Math.round(-velocityY / mCurrentScale),
                mBoundLeft, mBoundRight, mBoundTop, mBoundBottom);
        int targetX = mScroller.getFinalX();
        int targetY = mScroller.getFinalY();
        mAnimationDuration = mScroller.getDuration();
        startAnimation(targetX, targetY, mCurrentScale, ANIM_KIND_FLING);
        return true;
    }

    private void startAnimation(
            int targetX, int targetY, float scale, int kind) {
        if (targetX == mCurrentX && targetY == mCurrentY
                && scale == mCurrentScale) return;

        mFromX = mCurrentX;
        mFromY = mCurrentY;
        mFromScale = mCurrentScale;

        mToX = targetX;
        mToY = targetY;
        mToScale = Utils.clamp(scale, 0.6f * mScaleMin, 1.4f * mScaleMax);

        // If the scaled height is smaller than the view height,
        // force it to be in the center.
        // (We do for height only, not width, because the user may
        // want to scroll to the previous/next image.)
        if (Math.floor(mImageH * mToScale) <= mViewH) {
            mToY = mImageH / 2;
        }

        mAnimationStartTime = SystemClock.uptimeMillis();
        mAnimationKind = kind;
        if (mAnimationKind != ANIM_KIND_FLING) {
            mAnimationDuration = ANIM_TIME[mAnimationKind];
        }
        if (advanceAnimation()) mViewer.invalidate();
    }

    // Returns true if redraw is needed.
    public boolean advanceAnimation() {
        if (mAnimationStartTime == NO_ANIMATION) {
            return false;
        } else if (mAnimationStartTime == LAST_ANIMATION) {
            mAnimationStartTime = NO_ANIMATION;
            if (mViewer.isInTransition()) {
                mViewer.notifyTransitionComplete();
                return false;
            } else {
                return startSnapbackIfNeeded();
            }
        }

        long now = SystemClock.uptimeMillis();
        float progress;
        if (mAnimationDuration == 0) {
            progress = 1;
        } else {
            progress = (now - mAnimationStartTime) / mAnimationDuration;
        }

        if (progress >= 1) {
            progress = 1;
            mCurrentX = mToX;
            mCurrentY = mToY;
            mCurrentScale = mToScale;
            mAnimationStartTime = LAST_ANIMATION;
        } else {
            float f = 1 - progress;
            switch (mAnimationKind) {
                case ANIM_KIND_SCROLL:
                case ANIM_KIND_FLING:
                    progress = 1 - f;  // linear
                    break;
                case ANIM_KIND_SCALE:
                    progress = 1 - f * f;  // quadratic
                    break;
                case ANIM_KIND_SNAPBACK:
                case ANIM_KIND_ZOOM:
                case ANIM_KIND_SLIDE:
                    progress = 1 - f * f * f * f * f; // x^5
                    break;
            }
            if (mAnimationKind == ANIM_KIND_FLING) {
                flingInterpolate(progress);
            } else {
                linearInterpolate(progress);
            }
        }
        mViewer.setPosition(mCurrentX, mCurrentY, mCurrentScale);
        return true;
    }

    private void flingInterpolate(float progress) {
        mScroller.computeScrollOffset(progress);
        int oldX = mCurrentX;
        int oldY = mCurrentY;
        mCurrentX = mScroller.getCurrX();
        mCurrentY = mScroller.getCurrY();

        // Check if we hit the edges; show edge effects if we do.
        if (oldX > mBoundLeft && mCurrentX == mBoundLeft) {
            int v = Math.round(-mScroller.getCurrVelocityX() * mCurrentScale);
            mEdgeView.onAbsorb(v, EdgeView.LEFT);
        } else if (oldX < mBoundRight && mCurrentX == mBoundRight) {
            int v = Math.round(mScroller.getCurrVelocityX() * mCurrentScale);
            mEdgeView.onAbsorb(v, EdgeView.RIGHT);
        }

        if (oldY > mBoundTop && mCurrentY == mBoundTop) {
            int v = Math.round(-mScroller.getCurrVelocityY() * mCurrentScale);
            mEdgeView.onAbsorb(v, EdgeView.TOP);
        } else if (oldY < mBoundBottom && mCurrentY == mBoundBottom) {
            int v = Math.round(mScroller.getCurrVelocityY() * mCurrentScale);
            mEdgeView.onAbsorb(v, EdgeView.BOTTOM);
        }
    }

    // Interpolates mCurrent{X,Y,Scale} given the progress in [0, 1].
    private void linearInterpolate(float progress) {
        // To linearly interpolate the position on view coordinates, we do the
        // following steps:
        // (1) convert a bitmap position (x, y) to view coordinates:
        //     from: (x - mFromX) * mFromScale + mViewW / 2
        //     to: (x - mToX) * mToScale + mViewW / 2
        // (2) interpolate between the "from" and "to" coordinates:
        //     (x - mFromX) * mFromScale * (1 - p) + (x - mToX) * mToScale * p
        //     + mViewW / 2
        //     should be equal to
        //     (x - mCurrentX) * mCurrentScale + mViewW / 2
        // (3) The x-related terms in the above equation can be removed because
        //     mFromScale * (1 - p) + ToScale * p = mCurrentScale
        // (4) Solve for mCurrentX, we have mCurrentX =
        // (mFromX * mFromScale * (1 - p) + mToX * mToScale * p) / mCurrentScale
        float fromX = mFromX * mFromScale;
        float toX = mToX * mToScale;
        float currentX = fromX + progress * (toX - fromX);

        float fromY = mFromY * mFromScale;
        float toY = mToY * mToScale;
        float currentY = fromY + progress * (toY - fromY);

        mCurrentScale = mFromScale + progress * (mToScale - mFromScale);
        mCurrentX = Math.round(currentX / mCurrentScale);
        mCurrentY = Math.round(currentY / mCurrentScale);
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
        float scale = mCurrentScale;

        if (mCurrentScale < mScaleMin || mCurrentScale > mScaleMax) {
            needAnimation = true;
            scale = Utils.clamp(mCurrentScale, mScaleMin, mScaleMax);
        }

        calculateStableBound(scale);
        int x = Utils.clamp(mCurrentX, mBoundLeft, mBoundRight);
        int y = Utils.clamp(mCurrentY, mBoundTop, mBoundBottom);

        if (mCurrentX != x || mCurrentY != y || mCurrentScale != scale) {
            needAnimation = true;
        }

        if (needAnimation) {
            startAnimation(x, y, scale, ANIM_KIND_SNAPBACK);
        }

        return needAnimation;
    }

    // Calculates the stable region of mCurrent{X/Y}, where "stable" means
    //
    // (1) If the dimension of scaled image >= view dimension, we will not
    // see black region outside the image (at that dimension).
    // (2) If the dimension of scaled image < view dimension, we will center
    // the scaled image.
    //
    // We might temporarily go out of this stable during user interaction,
    // but will "snap back" after user stops interaction.
    //
    // The results are stored in mBound{Left/Right/Top/Bottom}.
    //
    private void calculateStableBound(float scale) {
        // The number of pixels between the center of the view
        // and the edge when the edge is aligned.
        mBoundLeft = (int) Math.ceil(mViewW / (2 * scale));
        mBoundRight = mImageW - mBoundLeft;
        mBoundTop = (int) Math.ceil(mViewH / (2 * scale));
        mBoundBottom = mImageH - mBoundTop;

        // If the scaled height is smaller than the view height,
        // force it to be in the center.
        if (Math.floor(mImageH * scale) <= mViewH) {
            mBoundTop = mBoundBottom = mImageH / 2;
        }

        // Same for width
        if (Math.floor(mImageW * scale) <= mViewW) {
            mBoundLeft = mBoundRight = mImageW / 2;
        }
    }

    private boolean useCurrentValueAsTarget() {
        return mAnimationStartTime == NO_ANIMATION ||
                mAnimationKind == ANIM_KIND_SNAPBACK ||
                mAnimationKind == ANIM_KIND_FLING;
    }

    private float getTargetScale() {
        return useCurrentValueAsTarget() ? mCurrentScale : mToScale;
    }

    private int getTargetX() {
        return useCurrentValueAsTarget() ? mCurrentX : mToX;
    }

    private int getTargetY() {
        return useCurrentValueAsTarget() ? mCurrentY : mToY;
    }

    public RectF getImageBounds() {
        float points[] = mTempPoints;

        /*
         * (p0,p1)----------(p2,p3)
         *   |                  |
         *   |                  |
         * (p4,p5)----------(p6,p7)
         */
        points[0] = points[4] = -mCurrentX;
        points[1] = points[3] = -mCurrentY;
        points[2] = points[6] = mImageW - mCurrentX;
        points[5] = points[7] = mImageH - mCurrentY;

        RectF rect = mTempRect;
        rect.set(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);

        float scale = mCurrentScale;
        float offsetX = mViewW / 2;
        float offsetY = mViewH / 2;
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

    public int getImageWidth() {
        return mImageW;
    }

    public int getImageHeight() {
        return mImageH;
    }

    public boolean isAtLeftEdge() {
        calculateStableBound(mCurrentScale);
        return mCurrentX <= mBoundLeft;
    }

    public boolean isAtRightEdge() {
        calculateStableBound(mCurrentScale);
        return mCurrentX >= mBoundRight;
    }
}
