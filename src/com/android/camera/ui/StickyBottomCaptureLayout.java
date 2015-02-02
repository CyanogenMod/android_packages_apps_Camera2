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

package com.android.camera.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.view.animation.Interpolator;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.android.camera.CaptureLayoutHelper;
import com.android.camera.debug.Log;
import com.android.camera.ui.motion.InterpolatorHelper;
import com.android.camera.widget.ModeOptions;
import com.android.camera.widget.ModeOptionsOverlay;
import com.android.camera.widget.RoundedThumbnailView;
import com.android.camera2.R;

/**
 * The goal of this class is to ensure mode options and capture indicator is
 * always laid out to the left of or above bottom bar in landscape or portrait
 * respectively. All the other children in this view group can be expected to
 * be laid out the same way as they are in a normal FrameLayout.
 */
public class StickyBottomCaptureLayout extends FrameLayout {

    private final static Log.Tag TAG = new Log.Tag("StickyBotCapLayout");
    private RoundedThumbnailView mRoundedThumbnailView;
    private ModeOptionsOverlay mModeOptionsOverlay;
    private View mBottomBar;
    private CaptureLayoutHelper mCaptureLayoutHelper = null;

    private ModeOptions.Listener mModeOptionsListener = new ModeOptions.Listener() {
        @Override
        public void onBeginToShowModeOptions() {
            final PointF thumbnailViewPosition = getRoundedThumbnailPosition(
                    mCaptureLayoutHelper.getUncoveredPreviewRect(),
                    false,
                    mModeOptionsOverlay.getModeOptionsToggleWidth());
            final int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                animateCaptureIndicatorToY(thumbnailViewPosition.y);
            } else {
                animateCaptureIndicatorToX(thumbnailViewPosition.x);
            }
        }

        @Override
        public void onBeginToHideModeOptions() {
            final PointF thumbnailViewPosition = getRoundedThumbnailPosition(
                    mCaptureLayoutHelper.getUncoveredPreviewRect(),
                    true,
                    mModeOptionsOverlay.getModeOptionsToggleWidth());
            final int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                animateCaptureIndicatorToY(thumbnailViewPosition.y);
            } else {
                animateCaptureIndicatorToX(thumbnailViewPosition.x);
            }
        }
    };

    public StickyBottomCaptureLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onFinishInflate() {
        mRoundedThumbnailView = (RoundedThumbnailView) findViewById(R.id.rounded_thumbnail_view);
        mModeOptionsOverlay = (ModeOptionsOverlay) findViewById(R.id.mode_options_overlay);
        mModeOptionsOverlay.setModeOptionsListener(mModeOptionsListener);
        mBottomBar = findViewById(R.id.bottom_bar);
    }

    /**
     * Sets a capture layout helper to query layout rect from.
     */
    public void setCaptureLayoutHelper(CaptureLayoutHelper helper) {
        mCaptureLayoutHelper = helper;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mCaptureLayoutHelper == null) {
            Log.e(TAG, "Capture layout helper needs to be set first.");
            return;
        }
        // Layout mode options overlay.
        RectF uncoveredPreviewRect = mCaptureLayoutHelper.getUncoveredPreviewRect();
        mModeOptionsOverlay.layout((int) uncoveredPreviewRect.left, (int) uncoveredPreviewRect.top,
                (int) uncoveredPreviewRect.right, (int) uncoveredPreviewRect.bottom);

        // Layout capture indicator.
        PointF roundedThumbnailViewPosition = getRoundedThumbnailPosition(
                uncoveredPreviewRect,
                mModeOptionsOverlay.isModeOptionsHidden(),
                mModeOptionsOverlay.getModeOptionsToggleWidth());
        mRoundedThumbnailView.layout(
                (int) roundedThumbnailViewPosition.x,
                (int) roundedThumbnailViewPosition.y,
                (int) roundedThumbnailViewPosition.x + mRoundedThumbnailView.getMeasuredWidth(),
                (int) roundedThumbnailViewPosition.y + mRoundedThumbnailView.getMeasuredHeight());

        // Layout bottom bar.
        RectF bottomBarRect = mCaptureLayoutHelper.getBottomBarRect();
        mBottomBar.layout((int) bottomBarRect.left, (int) bottomBarRect.top,
                (int) bottomBarRect.right, (int) bottomBarRect.bottom);
    }

    /**
     * Calculates the desired layout of capture indicator.
     *
     * @param uncoveredPreviewRect The uncovered preview bound which contains mode option
     *                             overlay and capture indicator.
     * @param isModeOptionsHidden Whether the mode options button are hidden.
     * @param modeOptionsToggleWidth The width of mode options toggle (three dots button).
     * @return the desired view bound for capture indicator.
     */
    private PointF getRoundedThumbnailPosition(
            RectF uncoveredPreviewRect, boolean isModeOptionsHidden, float modeOptionsToggleWidth) {
        final float threeDotsButtonDiameter =
                getResources().getDimension(R.dimen.option_button_circle_size);
        final float threeDotsButtonPadding =
                getResources().getDimension(R.dimen.mode_options_toggle_padding);
        final float modeOptionsHeight = getResources().getDimension(R.dimen.mode_options_height);

        final float roundedThumbnailViewSize = mRoundedThumbnailView.getMeasuredWidth();
        final float roundedThumbnailFinalSize = mRoundedThumbnailView.getThumbnailFinalDiameter();
        final float roundedThumbnailViewPadding = mRoundedThumbnailView.getThumbnailPadding();

        // The view bound is based on the maximal ripple ring diameter. This is the diff of maximal
        // ripple ring radius and the final thumbnail radius.
        final float radiusDiffBetweenViewAndThumbnail =
                (roundedThumbnailViewSize - roundedThumbnailFinalSize) / 2.0f;
        final float distanceFromModeOptions = roundedThumbnailViewPadding +
                roundedThumbnailFinalSize + radiusDiffBetweenViewAndThumbnail;

        final int orientation = getResources().getConfiguration().orientation;

        float x = 0;
        float y = 0;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            // The view finder of 16:9 aspect ratio might have a black padding.
            x = uncoveredPreviewRect.right - distanceFromModeOptions;

            y = uncoveredPreviewRect.bottom;
            if (isModeOptionsHidden) {
                y -= threeDotsButtonPadding + threeDotsButtonDiameter;
            } else {
                y -= modeOptionsHeight;
            }
            y -= distanceFromModeOptions;
        }
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (isModeOptionsHidden) {
                x = uncoveredPreviewRect.right - threeDotsButtonPadding - modeOptionsToggleWidth;
            } else {
                x = uncoveredPreviewRect.right - modeOptionsHeight;
            }
            x -= distanceFromModeOptions;
            y = uncoveredPreviewRect.top + roundedThumbnailViewPadding -
                    radiusDiffBetweenViewAndThumbnail;
        }
        return new PointF(x, y);
    }

    private void animateCaptureIndicatorToX(float x) {
        final Interpolator interpolator =
                InterpolatorHelper.getLinearOutSlowInInterpolator(getContext());
        mRoundedThumbnailView.animate()
                .setDuration(ModeOptions.PADDING_ANIMATION_TIME)
                .setInterpolator(interpolator)
                .x(x)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mRoundedThumbnailView.setTranslationX(0.0f);
                        requestLayout();
                    }
                });
    }

    private void animateCaptureIndicatorToY(float y) {
        final Interpolator interpolator =
                InterpolatorHelper.getLinearOutSlowInInterpolator(getContext());
        mRoundedThumbnailView.animate()
                .setDuration(ModeOptions.PADDING_ANIMATION_TIME)
                .setInterpolator(interpolator)
                .y(y)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mRoundedThumbnailView.setTranslationY(0.0f);
                        requestLayout();
                    }
                });
    }
}
