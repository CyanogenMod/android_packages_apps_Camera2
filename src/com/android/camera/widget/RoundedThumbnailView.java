/*
 * Copyright (C) 2014 The Android Open Source Project
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

import java.util.LinkedList;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.android.camera.debug.Log;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.CameraUtil;
import com.android.camera2.R;

/**
 * A view that shows a pop-out effect for a thumbnail image as the new capture indicator design for
 * Haleakala. When a photo is taken, this view will appear in the bottom right corner of the view
 * finder to indicate the capture is done.
 *
 * Thumbnail cropping:
 *   (1) 100% width and vertically centered for portrait.
 *   (2) 100% height and horizontally centered for landscape.
 *
 * General behavior spec: Hide the capture indicator by fading out using fast_out_linear_in (150ms):
 *   (1) User open filmstrip.
 *   (2) User switch module.
 *   (3) User switch front/back camera.
 *   (4) User close app.
 *
 * Visual spec:
 *   (1) A 12dp spacing between mode option overlay and thumbnail.
 *   (2) A circular mask that excludes the corners of the preview image.
 *   (3) A solid white layer that sits on top of the preview and is also masked by 2).
 *   (4) The preview thumbnail image.
 *   (5) A 'ripple' which is just a white circular stroke.
 *
 * Animation spec:
 * - For (2) only the scale animates, from 50%(24dp) to 114%(54dp) in 200ms then falls back to
 *   100%(48dp) in 200ms. Both steps use the same easing: fast_out_slow_in.
 * - For (3), change opacity from 50% to 0% over 150ms, easing is exponential.
 * - For (4), doesn't animate.
 * - For (5), starts animating after 100ms, when (1) is at its peak radius and all animations take
 *   200ms, using linear_out_slow in. Opacity goes from 40% to 0%, radius goes from 40dp to 70dp,
 *   stroke width goes from 5dp to 1dp.
 */
public class RoundedThumbnailView extends View {
    private static final Log.Tag TAG = new Log.Tag("RoundedThumbnailView");

    /**
     * Configurations for the thumbnail pop-out effect.
     */
    private static final long THUMBNAIL_STRETCH_DURATION_MS = 200;
    private static final long THUMBNAIL_SHRINK_DURATION_MS = 200;
    private static final float THUMBNAIL_REVEAL_CIRCLE_OPACITY_BEGIN = 0.5f;
    private static final float THUMBNAIL_REVEAL_CIRCLE_OPACITY_END = 0.0f;
    /**
     * Configurations for the ripple effect.
     */
    private static final long RIPPLE_DURATION_MS = 200;
    private static final float RIPPLE_OPACITY_BEGIN = 0.4f;
    private static final float RIPPLE_OPACITY_END = 0.0f;

    /**
     * Fields for view layout.
     */
    private float mThumbnailPadding;

    /**
     * Fields for the thumbnail pop-out effect.
     */
    // The duration of the stretch phase in thumbnail pop-out effect.
    private long mThumbnailStretchDurationMs;
    // The duration of the shrink phase in thumbnail pop-out effect.
    private long mThumbnailShrinkDurationMs;
    // The beginning diameter of the thumbnail for the stretch phase in thumbnail pop-out effect.
    private float mThumbnailStretchDiameterBegin;
    // The ending diameter of the thumbnail for the stretch phase in thumbnail pop-out effect.
    private float mThumbnailStretchDiameterEnd;
    // The beginning diameter of the thumbnail for the shrink phase in thumbnail pop-out effect.
    private float mThumbnailShrinkDiameterBegin;
    // The ending diameter of the thumbnail for the shrink phase in thumbnail pop-out effect.
    private float mThumbnailShrinkDiameterEnd;

    private AnimatorSet mThumbnailAnimatorSet;
    private float mCurrentThumbnailDiameter;
    private float mCurrentRevealCircleOpacity;

    /**
     * Fields for the ripple effect.
     */
    // The start delay of the ripple effect.
    private long mRippleStartDelayMs;
    // The duration of the ripple effect.
    private long mRippleDurationMs;
    // The beginning diameter of the ripple ring.
    private float mRippleRingDiameterBegin;
    // The ending diameter of the ripple ring.
    private float mRippleRingDiameterEnd;
    // The beginning thickness of the ripple ring.
    private float mRippleRingThicknessBegin;
    // The ending thickness of the ripple ring.
    private float mRippleRingThicknessEnd;
    // A lazily loaded animator for the ripple effect.
    private ValueAnimator mRippleAnimator;
    // The current ripple ring diameter which is updated by the ripple animator and used by
    // onDraw().
    private float mCurrentRippleRingDiameter;
    // The current ripple ring thickness which is updated by the ripple animator and used by
    // onDraw().
    private float mCurrentRippleRingThickness;
    // The current ripple ring opacity which is updated by the ripple animator and used byonDraw().
    private float mCurrentRippleRingOpacity;

    // The waiting queue for all pending reveal requests. The latest request should be in the end of
    // the queue.
    private LinkedList<RevealRequest> mRevealRequestWaitQueue = new LinkedList<>();

    // The currently running reveal request.
    private RevealRequest mActiveRevealRequest;

    // The latest finished reveal request. Its thumbnail will be shown until a newer one replace it.
    private RevealRequest mFinishedRevealRequest;

    /**
     * Constructs a RoundedThumbnailView.
     */
    public RoundedThumbnailView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Make the view clickable.
        setClickable(true);

        // TODO: Adjust layout when mode option overlay is visible.
        mThumbnailPadding = getResources().getDimension(R.dimen.rounded_thumbnail_padding);

        // Load thumbnail pop-out effect constants.
        mThumbnailStretchDurationMs = THUMBNAIL_STRETCH_DURATION_MS;
        mThumbnailShrinkDurationMs = THUMBNAIL_SHRINK_DURATION_MS;
        mThumbnailStretchDiameterBegin =
                getResources().getDimension(R.dimen.rounded_thumbnail_diameter_min);
        mThumbnailStretchDiameterEnd =
                getResources().getDimension(R.dimen.rounded_thumbnail_diameter_max);
        mThumbnailShrinkDiameterBegin = mThumbnailStretchDiameterEnd;
        mThumbnailShrinkDiameterEnd =
                getResources().getDimension(R.dimen.rounded_thumbnail_diameter_normal);
        // Load ripple effect constants.
        float startDelayRatio = 0.5f;
        mRippleStartDelayMs = (long) (mThumbnailStretchDurationMs * startDelayRatio);
        mRippleDurationMs = RIPPLE_DURATION_MS;
        mRippleRingDiameterEnd =
                getResources().getDimension(R.dimen.rounded_thumbnail_ripple_ring_diameter_max);
        mRippleRingDiameterBegin =
                getResources().getDimension(R.dimen.rounded_thumbnail_ripple_ring_diameter_min);
        mRippleRingThicknessBegin =
                getResources().getDimension(R.dimen.rounded_thumbnail_ripple_ring_thick_max);
        mRippleRingThicknessEnd =
                getResources().getDimension(R.dimen.rounded_thumbnail_ripple_ring_thick_min);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Ignore the spec since the size should be fixed.
        int desiredSize = (int) mRippleRingDiameterEnd;
        setMeasuredDimension(desiredSize, desiredSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float centerX = canvas.getWidth() / 2;
        float centerY = canvas.getHeight() / 2;
        RectF viewBound =
                new RectF(0, 0, mRippleRingDiameterEnd, mRippleRingDiameterEnd);

        // Draw the thumbnail of latest finished reveal request.
        if (mFinishedRevealRequest != null) {
            Paint thumbnailPaint = mFinishedRevealRequest.getThumbnailPaint();
            if (thumbnailPaint != null) {
                // Draw the old thumbnail with the final diameter.
                float scaleRatio = mThumbnailShrinkDiameterEnd / mRippleRingDiameterEnd;

                canvas.save();
                canvas.scale(scaleRatio, scaleRatio, centerX, centerY);
                canvas.drawRoundRect(
                        viewBound,
                        centerX,
                        centerY,
                        thumbnailPaint);
                canvas.restore();
            }
        }

        // Draw animated parts (thumbnail and ripple) if there exists a reveal request.
        if (mActiveRevealRequest != null) {
            // Draw ripple ring first or the ring will cover thumbnail.
            if (mCurrentRippleRingThickness > 0) {
                // Draw the ripple ring.
                Paint ripplePaint = new Paint();
                ripplePaint.setAntiAlias(true);
                ripplePaint.setStrokeWidth(mCurrentRippleRingThickness);
                ripplePaint.setColor(Color.WHITE);
                ripplePaint.setAlpha((int) (mCurrentRippleRingOpacity * 255));
                ripplePaint.setStyle(Paint.Style.STROKE);

                canvas.save();
                canvas.drawCircle(centerX, centerY, mCurrentRippleRingDiameter / 2, ripplePaint);
                canvas.restore();
            }

            // Achieve the animation effect by scaling the transformation matrix.
            float scaleRatio = mCurrentThumbnailDiameter / mRippleRingDiameterEnd;

            canvas.save();
            canvas.scale(scaleRatio, scaleRatio, centerX, centerY);

            // Draw the new popping up thumbnail.
            Paint thumbnailPaint = mActiveRevealRequest.getThumbnailPaint();
            if (thumbnailPaint != null) {
                canvas.drawRoundRect(
                        viewBound,
                        centerX,
                        centerY,
                        thumbnailPaint);

            }

            // Draw the reveal while circle.
            Paint revealCirclePaint = new Paint();
            revealCirclePaint.setAntiAlias(true);
            revealCirclePaint.setColor(Color.WHITE);
            revealCirclePaint.setAlpha((int) (mCurrentRevealCircleOpacity * 255));
            revealCirclePaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(centerX, centerY,
                    mRippleRingDiameterEnd / 2, revealCirclePaint);

            canvas.restore();
        }
    }

    /**
     * Calculates the desired layout of capture indicator.
     *
     * @param parentRect The bound of the view which contains capture indicator.
     * @param uncoveredPreviewRect The uncovered preview bound which contains mode option
     *                             overlay and capture indicator.
     * @return the desired view bound for capture indicator.
     */
    public RectF getDesiredLayout(RectF parentRect, RectF uncoveredPreviewRect) {
        float parentViewWidth = parentRect.right - parentRect.left;
        float x = 0;
        float y = 0;

        // The view bound is based on the maximal ripple ring diameter. This is the diff of maximal
        // ripple ring radius and the final thumbnail radius.
        float radius_diff_max_normal = (mRippleRingDiameterEnd - mThumbnailShrinkDiameterEnd) / 2;
        float modeSwitchThreeDotsDiameter = mThumbnailShrinkDiameterEnd;
        float modeSwitchThreeDotsBottomPadding = mThumbnailPadding;

        int orientation = getResources().getConfiguration().orientation;
        int rotation = CameraUtil.getDisplayRotation(getContext());
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            // The view finder of 16:9 aspect ratio might have a black padding.
            float previewRightEdgeGap =
                    parentRect.right - uncoveredPreviewRect.right;
            x = parentViewWidth - previewRightEdgeGap - mThumbnailPadding -
                    mThumbnailShrinkDiameterEnd - radius_diff_max_normal;
            y = uncoveredPreviewRect.bottom;
            y -= modeSwitchThreeDotsBottomPadding + modeSwitchThreeDotsDiameter +
                    mThumbnailPadding + mThumbnailShrinkDiameterEnd + radius_diff_max_normal;
        }
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            float previewTopEdgeGap = uncoveredPreviewRect.top;
            x = uncoveredPreviewRect.right;
            x -= modeSwitchThreeDotsBottomPadding + modeSwitchThreeDotsDiameter +
                    mThumbnailPadding + mThumbnailShrinkDiameterEnd + radius_diff_max_normal;
            y = previewTopEdgeGap + mThumbnailPadding - radius_diff_max_normal;
        }
        return new RectF(x, y, x + mRippleRingDiameterEnd, y + mRippleRingDiameterEnd);
    }

    /**
     * Starts the thumbnail revealing animation.
     *
     * @param accessibilityString An accessibility String to be announced during the revealing
     *                            animation.
     */
    public void startRevealThumbnailAnimation(String accessibilityString) {
        // Create a new request.
        RevealRequest latestRevealRequest =
                new RevealRequest(getMeasuredWidth(), accessibilityString);
        mRevealRequestWaitQueue.addLast(latestRevealRequest);
        // Process the next request.
        processNextRevealRequest();
    }

    /**
     * Updates the thumbnail image.
     *
     * @param thumbnailBitmap The thumbnail image to be shown.
     */
    public void setThumbnail(final Bitmap thumbnailBitmap) {
        if (mRevealRequestWaitQueue.isEmpty()) {
            if (mActiveRevealRequest != null) {
                mActiveRevealRequest.setThumbnailBitmap(thumbnailBitmap);
            }
        } else {
            // Update the thumbnail in the latest reveal request.
            RevealRequest latestRevealRequest = mRevealRequestWaitQueue.peekLast();
            latestRevealRequest.setThumbnailBitmap(thumbnailBitmap);
        }
    }

    /**
     * Hide the thumbnail.
     */
    public void hideThumbnail() {
        // Make this view invisible.
        setVisibility(GONE);

        // Stop currently running animators.
        if (mThumbnailAnimatorSet != null && mThumbnailAnimatorSet.isRunning()) {
            mThumbnailAnimatorSet.end();
        }
        if (mRippleAnimator != null && mRippleAnimator.isRunning()) {
            mRippleAnimator.end();
        }
        // Remove all pending reveal requests.
        mRevealRequestWaitQueue.clear();
        mActiveRevealRequest = null;
        mFinishedRevealRequest = null;
    }

    /**
     * Pick the next request in the reveal request queue and start a reveal animation for the
     * request.
     */
    private void processNextRevealRequest() {
        // Do nothing if the queue is empty.
        if (mRevealRequestWaitQueue.isEmpty()) {
            return;
        }
        // Do nothing if the active request is still running.
        if (mActiveRevealRequest != null) {
            return;
        }

        // Pick the first request in the queue and make it active.
        mActiveRevealRequest = mRevealRequestWaitQueue.peekFirst();
        mRevealRequestWaitQueue.removeFirst();

        // Make this view visible.
        setVisibility(VISIBLE);

        // Lazily load the thumbnail animator.
        if (mThumbnailAnimatorSet == null) {
            Interpolator stretchInterpolator;
            if (ApiHelper.isLOrHigher()) {
                // Both phases use fast_out_flow_in interpolator.
                stretchInterpolator = AnimationUtils.loadInterpolator(
                        getContext(), android.R.interpolator.fast_out_slow_in);
            } else {
                stretchInterpolator = new AccelerateDecelerateInterpolator();
            }

            // The first phase of thumbnail animation. Stretch the thumbnail to the maximal size.
            ValueAnimator stretchAnimator = ValueAnimator.ofFloat(
                    mThumbnailStretchDiameterBegin, mThumbnailStretchDiameterEnd);
            stretchAnimator.setDuration(mThumbnailStretchDurationMs);
            stretchAnimator.setInterpolator(stretchInterpolator);
            stretchAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    mCurrentThumbnailDiameter = (Float) valueAnimator.getAnimatedValue();
                    float fraction = valueAnimator.getAnimatedFraction();
                    float opacityDiff = THUMBNAIL_REVEAL_CIRCLE_OPACITY_END -
                            THUMBNAIL_REVEAL_CIRCLE_OPACITY_BEGIN;
                    mCurrentRevealCircleOpacity =
                            THUMBNAIL_REVEAL_CIRCLE_OPACITY_BEGIN + fraction * opacityDiff;
                    invalidate();
                }
            });

            // The second phase of thumbnail animation. Shrink the thumbnail to the final size.
            Interpolator shrinkInterpolator = stretchInterpolator;
            ValueAnimator shrinkAnimator = ValueAnimator.ofFloat(
                    mThumbnailShrinkDiameterBegin, mThumbnailShrinkDiameterEnd);
            shrinkAnimator.setDuration(mThumbnailShrinkDurationMs);
            shrinkAnimator.setInterpolator(shrinkInterpolator);
            shrinkAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    mCurrentThumbnailDiameter = (Float) valueAnimator.getAnimatedValue();
                    invalidate();
                }
            });

            // The stretch and shrink animators play sequentially.
            mThumbnailAnimatorSet = new AnimatorSet();
            mThumbnailAnimatorSet.playSequentially(stretchAnimator, shrinkAnimator);
            mThumbnailAnimatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // Mark the thumbnail animation is finished.
                    mActiveRevealRequest.finishThumbnailAnimation();
                    // Process the next reveal request if both thumbnail animation and ripple
                    // animation are both finished.
                    if (mActiveRevealRequest.isFinished()) {
                        mFinishedRevealRequest = mActiveRevealRequest;
                        mActiveRevealRequest = null;
                        processNextRevealRequest();
                    }
                }
            });
        }
        // Start thumbnail animation immediately.
        mThumbnailAnimatorSet.start();

        // Lazily load the ripple animator.
        if (mRippleAnimator == null) {

            // Ripple effect uses linear_out_slow_in interpolator.
            Interpolator rippleInterpolator;
            if (ApiHelper.isLOrHigher()) {
                // Both phases use fast_out_flow_in interpolator.
                rippleInterpolator = AnimationUtils.loadInterpolator(
                        getContext(), android.R.interpolator.linear_out_slow_in);
            } else {
                rippleInterpolator = new DecelerateInterpolator();
            }

            // When start shrinking the thumbnail, a ripple effect is triggered at the same time.
            mRippleAnimator =
                    ValueAnimator.ofFloat(mRippleRingDiameterBegin, mRippleRingDiameterEnd);
            mRippleAnimator.setDuration(mRippleDurationMs);
            mRippleAnimator.setInterpolator(rippleInterpolator);
            mRippleAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mActiveRevealRequest.finishRippleAnimation();
                    if (mActiveRevealRequest.isFinished()) {
                        mFinishedRevealRequest = mActiveRevealRequest;
                        mActiveRevealRequest = null;
                        processNextRevealRequest();
                    }
                }
            });
            mRippleAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    mCurrentRippleRingDiameter = (Float) valueAnimator.getAnimatedValue();
                    float fraction = valueAnimator.getAnimatedFraction();
                    mCurrentRippleRingThickness = mRippleRingThicknessBegin +
                            fraction * (mRippleRingThicknessEnd - mRippleRingThicknessBegin);
                    mCurrentRippleRingOpacity = RIPPLE_OPACITY_BEGIN +
                            fraction * (RIPPLE_OPACITY_END - RIPPLE_OPACITY_BEGIN);
                    invalidate();
                }
            });
        }
        // Start ripple animation after delay.
        mRippleAnimator.setStartDelay(mRippleStartDelayMs);
        mRippleAnimator.start();

        // Announce the accessibility string.
        announceForAccessibility(mActiveRevealRequest.getAccessibilityString());
    }

    /**
     * Encapsulates necessary information for a complete thumbnail reveal animation.
     */
    private static class RevealRequest {
        // The size of the thumbnail.
        private float mViewSize;

        // The accessibility string.
        private String mAccessibilityString;

        // The original full-size image bitmap.
        private Bitmap mOriginalBitmap;

        // The cached Paint object to draw the thumbnail.
        private Paint mThumbnailPaint;

        // The flag to indicate if thumbnail animation of this request is full-filled.
        private boolean mThumbnailAnimationFinished;

        // The flag to indicate if ripple animation of this request is full-filled.
        private boolean mRippleAnimationFinished;

        /**
         * Constructs a reveal request. Use setThumbnailBitmap() to specify a source bitmap for the
         * thumbnail.
         *
         * @param viewSize The size of the capture indicator view.
         * @param accessibilityString The accessibility string of the request.
         */
        public RevealRequest(float viewSize, String accessibilityString) {
            mAccessibilityString = accessibilityString;
            mViewSize = viewSize;
        }

        /**
         * Returns the accessibility string.
         *
         * @return the accessibility string.
         */
        public String getAccessibilityString() {
            return mAccessibilityString;
        }

        /**
         * Returns the paint object which can be used to draw the thumbnail on a Canvas.
         *
         * @return the paint object which can be used to draw the thumbnail on a Canvas.
         */
        public Paint getThumbnailPaint() {
            // Lazy loading the thumbnail paint object.
            if (mThumbnailPaint == null) {
                // Can't create a paint object until the thumbnail bitmap is available.
                if (mOriginalBitmap == null) {
                    return null;
                }
                // The original bitmap should be a square shape.
                if (mOriginalBitmap.getWidth() != mOriginalBitmap.getHeight()) {
                    return null;
                }

                // Create a bitmap shader for the paint.
                BitmapShader shader = new BitmapShader(
                        mOriginalBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                if (mOriginalBitmap.getWidth() != mViewSize) {
                    // Create a transformation matrix for the bitmap shader if the size is not
                    // matched.
                    RectF srcRect = new RectF(
                            0.0f, 0.0f, mOriginalBitmap.getWidth(), mOriginalBitmap.getHeight());
                    RectF dstRect = new RectF(0.0f, 0.0f, mViewSize, mViewSize);
                    Matrix shaderMatrix = new Matrix();
                    shaderMatrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.FILL);
                    shader.setLocalMatrix(shaderMatrix);
                }

                // Create the paint for drawing the thumbnail in a circle.
                mThumbnailPaint = new Paint();
                mThumbnailPaint.setAntiAlias(true);
                mThumbnailPaint.setShader(shader);
            }
            return mThumbnailPaint;
        }

        /**
         * Checks if the request is full-filled.
         *
         * @return True if both thumbnail animation and ripple animation are finished
         */
        public boolean isFinished() {
            return mThumbnailAnimationFinished && mRippleAnimationFinished;
        }

        /**
         * Marks the thumbnail animation is finished.
         */
        public void finishThumbnailAnimation() {
            mThumbnailAnimationFinished = true;
        }

        /**
         * Marks the ripple animation is finished.
         */
        public void finishRippleAnimation() {
            mRippleAnimationFinished = true;
        }

        /**
         * Updates the thumbnail image.
         *
         * @param thumbnailBitmap The thumbnail image to be shown.
         */
        public void setThumbnailBitmap(Bitmap thumbnailBitmap) {
            mOriginalBitmap = thumbnailBitmap;
            // Crop the image if it is not square.
            if (mOriginalBitmap.getWidth() != mOriginalBitmap.getHeight()) {
                mOriginalBitmap = cropCenterBitmap(mOriginalBitmap);
            }
        }

        /**
         * Obtains a square bitmap by cropping the center of a bitmap. If the given image is
         * portrait, the cropped image keeps 100% original width and vertically centered to the
         * original image. If the given image is landscape, the cropped image keeps 100% original
         * height and horizontally centered to the original image.
         *
         * @param srcBitmap the bitmap image to be cropped in the center.
         * @return a result square bitmap.
         */
        private Bitmap cropCenterBitmap(Bitmap srcBitmap) {
            int srcWidth = srcBitmap.getWidth();
            int srcHeight = srcBitmap.getHeight();
            Bitmap dstBitmap;
            if (srcWidth >= srcHeight) {
                dstBitmap = Bitmap.createBitmap(
                        srcBitmap, srcWidth / 2 - srcHeight / 2, 0, srcHeight, srcHeight);
            } else {
                dstBitmap = Bitmap.createBitmap(
                        srcBitmap, 0, srcHeight / 2 - srcWidth / 2, srcWidth, srcWidth);
            }
            return dstBitmap;
        }
    }
}