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

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.camera.ShutterButton;
import com.android.camera.ui.PreviewOverlay;
import com.android.camera.ui.PreviewStatusListener;

import com.android.camera2.R;

/**
 * ModeOptionsOverlay is a FrameLayout which positions mode options in
 * in the bottom of the preview that is visible above the bottom bar.
 */
public class ModeOptionsOverlay extends FrameLayout
    implements PreviewStatusListener.PreviewAreaChangedListener,
               PreviewOverlay.OnPreviewTouchedListener,
               IndicatorIconController.OnIndicatorVisibilityChangedListener,
               ShutterButton.OnShutterButtonListener {

    private final static String TAG = "ModeOptionsOverlay";

    private static final int BOTTOMBAR_OPTIONS_TIMEOUT_MS = 2000;
    private final static int BOTTOM_RIGHT = Gravity.BOTTOM | Gravity.RIGHT;
    private final static int TOP_RIGHT = Gravity.TOP | Gravity.RIGHT;

    private int mPreviewWidth;
    private int mPreviewHeight;

    private ModeOptions mModeOptions;

    // The mode options toggle can be either a default image, or a
    // group of on screen indicators.
    private FrameLayout mModeOptionsToggle;
    // Default image for mode options toggle.
    private ImageView mThreeDots;
    // Group of on screen indicators for mode options toggle.
    private LinearLayout mIndicators;

    public ModeOptionsOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onFinishInflate() {
        mModeOptions = (ModeOptions) findViewById(R.id.mode_options);
        mModeOptions.setClickable(true);
        mModeOptions.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeModeOptions();
                }
            });

        mModeOptionsToggle = (FrameLayout) findViewById(R.id.mode_options_toggle);
        mModeOptionsToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mModeOptions.animateVisible();
            }
        });
        mModeOptions.setViewToShowHide(mModeOptionsToggle);

        mThreeDots = (ImageView) findViewById(R.id.three_dots);
        mIndicators = (LinearLayout) findViewById(R.id.indicator_icons);
        showCorrectToggleView(mThreeDots, mIndicators);
    }

    @Override
    public void onPreviewTouched(MotionEvent ev) {
        closeModeOptions();
    }

    @Override
    public void onIndicatorVisibilityChanged(View indicator) {
        showCorrectToggleView(mThreeDots, mIndicators);
    }

    @Override
    public void onShutterButtonClick() {
        closeModeOptions();
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
    }

    /**
     * Schedule (or re-schedule) the options menu to be closed after a number
     * of milliseconds.  If the options menu is already closed, nothing is
     * scheduled.
     */
    private void closeModeOptions() {
        // Check that the bottom bar options are visible.
        if (mModeOptions.isHidden()) {
            return;
        }
        mModeOptions.animateHidden();
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        checkOrientation(configuration.orientation);
    }

    @Override
    public void onPreviewAreaChanged(RectF previewArea) {
        mPreviewWidth = (int) previewArea.width();
        mPreviewHeight = (int) previewArea.height();
        setLayoutDimensions();
    }

    /**
     * The overlay takes its horizontal dimension from the preview.  The vertical
     * dimension of the overlay is determined by its parent layout, which has
     * knowledge of the bottom bar dimensions.
     */
    private void setLayoutDimensions() {
        if (mPreviewWidth == 0 || mPreviewHeight == 0) {
            return;
        }

        boolean isPortrait = Configuration.ORIENTATION_PORTRAIT
            == getResources().getConfiguration().orientation;

        ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) getLayoutParams();
        if (isPortrait) {
            params.width = mPreviewWidth;
        } else {
            params.height = mPreviewHeight;
        }
        setLayoutParams(params);
    }

    /**
     * Set the layout gravity of the child layout to be bottom or top right
     * depending on orientation.
     */
    private void checkOrientation(int orientation) {
        final boolean isPortrait = Configuration.ORIENTATION_PORTRAIT == orientation;

        FrameLayout.LayoutParams modeOptionsParams
            = (FrameLayout.LayoutParams) mModeOptions.getLayoutParams();
        FrameLayout.LayoutParams modeOptionsToggleParams
            = (FrameLayout.LayoutParams) mModeOptionsToggle.getLayoutParams();

        if (isPortrait) {
            modeOptionsParams.height = modeOptionsParams.width;
            modeOptionsParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            if (modeOptionsParams.gravity != Gravity.BOTTOM) {
                modeOptionsParams.gravity = Gravity.BOTTOM;
            }

            if (modeOptionsToggleParams.gravity != BOTTOM_RIGHT) {
                modeOptionsToggleParams.gravity = BOTTOM_RIGHT;
            }

            // Remove the rotation on the three dots.
            mThreeDots.setImageMatrix(new Matrix());
        } else if (!isPortrait) {
            modeOptionsParams.width = modeOptionsParams.height;
            modeOptionsParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            if (modeOptionsParams.gravity != Gravity.RIGHT) {
                modeOptionsParams.gravity = Gravity.RIGHT;
            }

            if (modeOptionsToggleParams.gravity != TOP_RIGHT) {
                modeOptionsToggleParams.gravity = TOP_RIGHT;
            }

            // Rotate the three dots 90 to match portrait.
            Matrix matrix = new Matrix();
            matrix.setRotate(90, mThreeDots.getWidth() / 2 , mThreeDots.getHeight() / 2);
            mThreeDots.setImageMatrix(matrix);
        }

        requestLayout();
    }

    /**
     * Show the correct toggle view: the default view if the group has
     * no visible children, otherwise the default view.
     */
    private void showCorrectToggleView(View defaultView, ViewGroup group) {
        if (getVisibleChildCount(group) > 0) {
            defaultView.setVisibility(View.GONE);
            group.setVisibility(View.VISIBLE);
        } else {
            group.setVisibility(View.GONE);
            defaultView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Get the number of a ViewGroup's visible children.
     */
    private int getVisibleChildCount(ViewGroup group) {
        int visible = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child != null && child.getVisibility() == View.VISIBLE) {
                visible++;
            }
        }
        return visible;
    }
}
