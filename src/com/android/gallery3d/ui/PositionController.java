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

class PositionController {
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

    // We try to scale up the image to fill the screen. But in order not to
    // scale too much for small icons, we limit the max up-scaling factor here.
    private static final float SCALE_LIMIT = 4;

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

    private RectF mTempRect = new RectF();
    private float[] mTempPoints = new float[8];

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
        Position position = mViewer.retrieveOldPosition();
        if (position != null) {
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

    public float getMinimalScale(int w, int h, int rotation) {
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

    public void startSlideInAnimation(int direction) {
        int fromX = (direction == PhotoView.TRANS_SLIDE_IN_LEFT) ?
                mViewW : -mViewW;
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

    public void startScroll(float dx, float dy) {
        scrollBy(dx, dy, ANIM_KIND_SCROLL);
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
            if (mViewer.isInTransition()) {
                mViewer.notifyTransitionComplete();
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
}
