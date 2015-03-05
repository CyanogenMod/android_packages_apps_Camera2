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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
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
import android.view.animation.Interpolator;

import com.android.camera.async.MainThread;
import com.android.camera.debug.Log;
import com.android.camera.ui.motion.InterpolatorHelper;
import com.android.camera.util.ApiHelper;
import com.android.camera2.R;
import com.google.common.base.Optional;

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

     // Configurations for the thumbnail pop-out effect.
    private static final long THUMBNAIL_STRETCH_DURATION_MS = 200;
    private static final long THUMBNAIL_SHRINK_DURATION_MS = 200;
    private static final float THUMBNAIL_REVEAL_CIRCLE_OPACITY_BEGIN = 0.5f;
    private static final float THUMBNAIL_REVEAL_CIRCLE_OPACITY_END = 0.0f;

    // Configurations for the ripple effect.
    private static final long RIPPLE_DURATION_MS = 200;
    private static final float RIPPLE_OPACITY_BEGIN = 0.4f;
    private static final float RIPPLE_OPACITY_END = 0.0f;

    // Configurations for the hit-state effect.
    private static final float HIT_STATE_CIRCLE_OPACITY_HIDDEN = -1.0f;
    private static final float HIT_STATE_CIRCLE_OPACITY_BEGIN = 0.7f;
    private static final float HIT_STATE_CIRCLE_OPACITY_END = 0.0f;
    private static final long HIT_STATE_DURATION_MS = 150;

    /** Defines call events. */
    public interface Callback {
        public void onHitStateFinished();
    }

    /** The registered callback. */
    private Optional<Callback> mCallback;

    // Fields for view layout.
    private float mThumbnailPadding;
    private RectF mViewRect;

    // Fields for the thumbnail pop-out effect.
    /** The animators to move the thumbnail. */
    private AnimatorSet mThumbnailAnimatorSet;
    /** The current diameter for the thumbnail image. */
    private float mCurrentThumbnailDiameter;
    /** The current reveal circle opacity. */
    private float mCurrentRevealCircleOpacity;
    /** The duration of the stretch phase in thumbnail pop-out effect. */
    private long mThumbnailStretchDurationMs;
    /** The duration of the shrink phase in thumbnail pop-out effect. */
    private long mThumbnailShrinkDurationMs;
    /**
     * The beginning diameter of the thumbnail for the stretch phase in
     * thumbnail pop-out effect.
     */
    private float mThumbnailStretchDiameterBegin;
    /**
     * The ending diameter of the thumbnail for the stretch phase in thumbnail
     * pop-out effect.
     */
    private float mThumbnailStretchDiameterEnd;
    /**
     * The beginning diameter of the thumbnail for the shrink phase in thumbnail
     * pop-out effect.
     */
    private float mThumbnailShrinkDiameterBegin;
    /**
     * The ending diameter of the thumbnail for the shrink phase in thumbnail
     * pop-out effect.
     */
    private float mThumbnailShrinkDiameterEnd;
    /** Paint object for the reveal circle. */
    private final Paint mRevealCirclePaint;

    // Fields for the ripple effect.
    /** The start delay of the ripple effect. */
    private long mRippleStartDelayMs;
    /** The duration of the ripple effect. */
    private long mRippleDurationMs;
    /** The beginning diameter of the ripple ring. */
    private float mRippleRingDiameterBegin;
    /** The ending diameter of the ripple ring. */
    private float mRippleRingDiameterEnd;
    /** The beginning thickness of the ripple ring. */
    private float mRippleRingThicknessBegin;
    /** The ending thickness of the ripple ring. */
    private float mRippleRingThicknessEnd;
    /** A lazily loaded animator for the ripple effect. */
    private ValueAnimator mRippleAnimator;
    /**
     * The current ripple ring diameter which is updated by the ripple animator
     * and used by onDraw().
     */
    private float mCurrentRippleRingDiameter;
    /**
     * The current ripple ring thickness which is updated by the ripple animator
     * and used by onDraw().
     */
    private float mCurrentRippleRingThickness;
    /**
     * The current ripple ring opacity which is updated by the ripple animator
     * and used by onDraw().
     */
    private float mCurrentRippleRingOpacity;
    /** The paint used for drawing the ripple effect. */
    private final Paint mRipplePaint;

    // Fields for the hit state effect.
    /** The paint to draw hit state circle. */
    private final Paint mHitStateCirclePaint;
    /**
     * The current hit state circle opacity (0.0 - 1.0) which is updated by the
     * hit state animator. If -1, the hit state circle won't be drawn.
     */
    private float mCurrentHitStateCircleOpacity;

    /**
     * The pending reveal request. This is created when start is called, but is
     * not drawn until the thumbnail is available. Once the bitmap is available
     * it is swapped into the foreground request.
     */
    private RevealRequest mPendingRequest;

    /** The currently animating reveal request. */
    private RevealRequest mForegroundRequest;

    /**
     * The latest finished reveal request. Its thumbnail will be shown until
     * a newer one replace it.
     */
    private RevealRequest mBackgroundRequest;

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // Trigger the hit state animation. Fade out the hit state white
            // circle by changing the alpha.
            final ValueAnimator hitStateAnimator = ValueAnimator.ofFloat(
                    HIT_STATE_CIRCLE_OPACITY_BEGIN, HIT_STATE_CIRCLE_OPACITY_END);
            hitStateAnimator.setDuration(HIT_STATE_DURATION_MS);
            hitStateAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            hitStateAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    mCurrentHitStateCircleOpacity = (Float) valueAnimator.getAnimatedValue();
                    invalidate();
                }
            });
            hitStateAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    mCurrentHitStateCircleOpacity = HIT_STATE_CIRCLE_OPACITY_HIDDEN;
                    if (mCallback.isPresent()) {
                        mCallback.get().onHitStateFinished();
                    }
                }
            });
            hitStateAnimator.start();
        }
    };

    /**
     * Constructs a RoundedThumbnailView.
     */
    public RoundedThumbnailView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mCallback = Optional.absent();

        // Make the view clickable.
        setClickable(true);
        setOnClickListener(mOnClickListener);

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

        mViewRect = new RectF(0, 0, mRippleRingDiameterEnd, mRippleRingDiameterEnd);

        mRippleRingDiameterBegin =
                getResources().getDimension(R.dimen.rounded_thumbnail_ripple_ring_diameter_min);
        mRippleRingThicknessBegin =
                getResources().getDimension(R.dimen.rounded_thumbnail_ripple_ring_thick_max);
        mRippleRingThicknessEnd =
                getResources().getDimension(R.dimen.rounded_thumbnail_ripple_ring_thick_min);

        mCurrentHitStateCircleOpacity = HIT_STATE_CIRCLE_OPACITY_HIDDEN;
        // Draw the reveal while circle.
        mHitStateCirclePaint = new Paint();
        mHitStateCirclePaint.setAntiAlias(true);
        mHitStateCirclePaint.setColor(Color.WHITE);
        mHitStateCirclePaint.setStyle(Paint.Style.FILL);

        mRipplePaint = new Paint();
        mRipplePaint.setAntiAlias(true);
        mRipplePaint.setColor(Color.WHITE);
        mRipplePaint.setStyle(Paint.Style.STROKE);

        mRevealCirclePaint = new Paint();
        mRevealCirclePaint.setAntiAlias(true);
        mRevealCirclePaint.setColor(Color.WHITE);
        mRevealCirclePaint.setStyle(Paint.Style.FILL);
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

        final float centerX = canvas.getWidth() / 2;
        final float centerY = canvas.getHeight() / 2;

        final float viewDiameter = mRippleRingDiameterEnd;
        final float finalDiameter = mThumbnailShrinkDiameterEnd;

        canvas.clipRect(mViewRect);

        // Draw the thumbnail of latest finished reveal request.
        if (mBackgroundRequest != null) {
            Paint thumbnailPaint = mBackgroundRequest.getThumbnailPaint();
            if (thumbnailPaint != null) {
                // Draw the old thumbnail with the final diameter.
                float scaleRatio = finalDiameter / viewDiameter;

                canvas.save();
                canvas.scale(scaleRatio, scaleRatio, centerX, centerY);
                canvas.drawRoundRect(
                        mViewRect,
                        centerX,
                        centerY,
                        thumbnailPaint);
                canvas.restore();
            }
        }

        // Draw animated parts (thumbnail and ripple) if there exists a reveal request.
        if (mForegroundRequest != null) {
            // Draw ripple ring first or the ring will cover thumbnail.
            if (mCurrentRippleRingThickness > 0) {
                // Draw the ripple ring.
                mRipplePaint.setAlpha((int) (mCurrentRippleRingOpacity * 255));
                mRipplePaint.setStrokeWidth(mCurrentRippleRingThickness);

                canvas.save();
                canvas.drawCircle(centerX, centerY, mCurrentRippleRingDiameter / 2, mRipplePaint);
                canvas.restore();
            }

            // Achieve the animation effect by scaling the transformation matrix.
            float scaleRatio = mCurrentThumbnailDiameter / mRippleRingDiameterEnd;

            canvas.save();
            canvas.scale(scaleRatio, scaleRatio, centerX, centerY);

            // Draw the new popping up thumbnail.
            Paint thumbnailPaint = mForegroundRequest.getThumbnailPaint();
            if (thumbnailPaint != null) {
                canvas.drawRoundRect(
                        mViewRect,
                        centerX,
                        centerY,
                        thumbnailPaint);
            }

            // Draw the reveal while circle.
            mRevealCirclePaint.setAlpha((int) (mCurrentRevealCircleOpacity * 255));
            canvas.drawCircle(centerX, centerY,
                    mRippleRingDiameterEnd / 2, mRevealCirclePaint);

            canvas.restore();
        }

        // Draw hit state circle if necessary.
        if (mCurrentHitStateCircleOpacity != HIT_STATE_CIRCLE_OPACITY_HIDDEN) {
            canvas.save();
            final float scaleRatio = finalDiameter / viewDiameter;
            canvas.scale(scaleRatio, scaleRatio, centerX, centerY);

            // Draw the hit state while circle.
            mHitStateCirclePaint.setAlpha((int) (mCurrentHitStateCircleOpacity * 255));
            canvas.drawCircle(centerX, centerY,
                    mRippleRingDiameterEnd / 2, mHitStateCirclePaint);
            canvas.restore();
        }
    }

    /**
     * Sets the callback.
     *
     * @param callback The callback to be set.
     */
    public void setCallback(Callback callback) {
        mCallback = Optional.of(callback);
    }

    /**
     * Gets the padding size with mode options and preview edges.
     *
     * @return The padding size with mode options and preview edges.
     */
    public float getThumbnailPadding() {
        return mThumbnailPadding;
    }

    /**
     * Gets the diameter of the thumbnail image after the revealing animation.
     *
     * @return The diameter of the thumbnail image after the revealing animation.
     */
    public float getThumbnailFinalDiameter() {
        return mThumbnailShrinkDiameterEnd;
    }

    /**
     * Starts the thumbnail revealing animation.
     *
     * @param accessibilityString An accessibility String to be announced during the revealing
     *                            animation.
     */
    public void startRevealThumbnailAnimation(String accessibilityString) {
        MainThread.checkMainThread();
        // Create a new request.
        mPendingRequest = new RevealRequest(getMeasuredWidth(), accessibilityString);
    }

    /**
     * Updates the thumbnail image.
     *
     * @param thumbnailBitmap The thumbnail image to be shown.
     * @param rotation The orientation of the image in degrees.
     */
    public void setThumbnail(final Bitmap thumbnailBitmap, final int rotation) {
        MainThread.checkMainThread();

        if(mPendingRequest != null) {
            mPendingRequest.setThumbnailBitmap(thumbnailBitmap, rotation);

            runPendingRequestAnimation();
        } else {
            Log.e(TAG, "Pending thumb was null!");
        }
    }

    /**
     * Hide the thumbnail.
     */
    public void hideThumbnail() {
        MainThread.checkMainThread();
        // Make this view invisible.
        setVisibility(GONE);

        clearAnimations();

        // Remove all pending reveal requests.
        mPendingRequest = null;
        mForegroundRequest = null;
        mBackgroundRequest = null;
    }

    /**
     * Stop currently running animators.
     */
    private void clearAnimations() {
        // Stop currently running animators.
        if (mThumbnailAnimatorSet != null && mThumbnailAnimatorSet.isRunning()) {
            mThumbnailAnimatorSet.removeAllListeners();
            mThumbnailAnimatorSet.cancel();
            // Release the animator so that a new instance will be created and
            // its listeners properly reconnected.  Fix for b/19034435
            mThumbnailAnimatorSet = null;
        }

        if (mRippleAnimator != null && mRippleAnimator.isRunning()) {
            mRippleAnimator.removeAllListeners();
            mRippleAnimator.cancel();
            // Release the animator so that a new instance will be created and
            // its listeners properly reconnected.  Fix for b/19034435
            mRippleAnimator = null;
        }
    }

    /**
     * Set the foreground request to the background, complete it, and run the
     * animation for the pending thumbnail.
     */
    private void runPendingRequestAnimation() {
        // Shift foreground to background, and pending to foreground.
        if (mForegroundRequest != null) {
            mBackgroundRequest = mForegroundRequest;
            mBackgroundRequest.finishRippleAnimation();
            mBackgroundRequest.finishThumbnailAnimation();
        }

        mForegroundRequest = mPendingRequest;
        mPendingRequest = null;

        // Make this view visible.
        setVisibility(VISIBLE);

        // Ensure there are no running animations.
        clearAnimations();

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
                if (mForegroundRequest != null) {
                    // Mark the thumbnail animation as finished.
                    mForegroundRequest.finishThumbnailAnimation();
                    processRevealRequests();
                }
            }
        });

        // Start thumbnail animation immediately.
        mThumbnailAnimatorSet.start();

        // Lazily load the ripple animator.
        // Ripple effect uses linear_out_slow_in interpolator.
        Interpolator rippleInterpolator =
              InterpolatorHelper.getLinearOutSlowInInterpolator(getContext());

        // When start shrinking the thumbnail, a ripple effect is triggered at the same time.
        mRippleAnimator =
              ValueAnimator.ofFloat(mRippleRingDiameterBegin, mRippleRingDiameterEnd);
        mRippleAnimator.setDuration(mRippleDurationMs);
        mRippleAnimator.setInterpolator(rippleInterpolator);
        mRippleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mForegroundRequest != null) {
                    mForegroundRequest.finishRippleAnimation();
                    processRevealRequests();
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

        // Start ripple animation after delay.
        mRippleAnimator.setStartDelay(mRippleStartDelayMs);
        mRippleAnimator.start();

        // Announce the accessibility string.
        announceForAccessibility(mForegroundRequest.getAccessibilityString());
    }

    private void processRevealRequests() {
        if(mForegroundRequest != null && mForegroundRequest.isFinished()) {
            mBackgroundRequest = mForegroundRequest;
            mForegroundRequest = null;
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return true;
    }

    /**
     * Encapsulates necessary information for a complete thumbnail reveal animation.
     */
    private static class RevealRequest {
        // The size of the thumbnail.
        private float mViewSize;

        // The accessibility string.
        private String mAccessibilityString;

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
            return mThumbnailPaint;
        }

        /**
         * Used to precompute the thumbnail paint from the given source bitmap.
         */
        private void precomputeThumbnailPaint(Bitmap srcBitmap, int rotation) {
            // Lazy loading the thumbnail paint object.
            if (mThumbnailPaint == null) {
                // Can't create a paint object until the thumbnail bitmap is available.
                if (srcBitmap == null) {
                    return;
                }
                // The original bitmap should be a square shape.
                if (srcBitmap.getWidth() != srcBitmap.getHeight()) {
                    return;
                }

                // Create a bitmap shader for the paint.
                BitmapShader shader = new BitmapShader(
                      srcBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                if (srcBitmap.getWidth() != mViewSize) {
                    // Create a transformation matrix for the bitmap shader if the size is not
                    // matched.
                    RectF srcRect = new RectF(
                          0.0f, 0.0f, srcBitmap.getWidth(), srcBitmap.getHeight());
                    RectF dstRect = new RectF(0.0f, 0.0f, mViewSize, mViewSize);

                    Matrix shaderMatrix = new Matrix();

                    // Scale the shader to fit the destination view size.
                    shaderMatrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.FILL);

                    // Rotate the image around the given source rect point.
                    shaderMatrix.preRotate(rotation,
                          srcRect.width() / 2,
                          srcRect.height() / 2);

                    shader.setLocalMatrix(shaderMatrix);
                }

                // Create the paint for drawing the thumbnail in a circle.
                mThumbnailPaint = new Paint();
                mThumbnailPaint.setAntiAlias(true);
                mThumbnailPaint.setShader(shader);
            }
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
         * @param rotation The orientation of the image in degrees.
         */
        public void setThumbnailBitmap(Bitmap thumbnailBitmap, int rotation) {
            Bitmap originalBitmap = thumbnailBitmap;
            // Crop the image if it is not square.
            if (originalBitmap.getWidth() != originalBitmap.getHeight()) {
                originalBitmap = cropCenterBitmap(originalBitmap);
            }

            precomputeThumbnailPaint(originalBitmap, rotation);
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
