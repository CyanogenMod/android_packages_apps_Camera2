/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.android.camera2.R;
import com.android.camera.ToggleImageButton;

/**
 * BottomBar swaps its width and height on rotation. In addition, it also changes
 * gravity and layout orientation based on the new orientation. Specifically, in
 * landscape it aligns to the right side of its parent and lays out its children
 * vertically, whereas in portrait, it stays at the bottom of the parent and has
 * a horizontal layout orientation.
 */
public class BottomBar extends FrameLayout
        implements PreviewStatusListener.PreviewAreaSizeChangedListener {
    private static final String TAG = "BottomBar";
    private int mWidth;
    private int mHeight;
    private float mOffsetShorterEdge;
    private float mOffsetLongerEdge;

    private final int mOptimalHeight;
    private boolean mOverLayBottomBar;

    private TopRightMostOverlay mSettingsOverlay;
    private TopRightWeightedLayout mSettingsLayout;
    private FrameLayout mCaptureLayout;
    private TopRightWeightedLayout mIntentLayout;

    public BottomBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        mOptimalHeight = getResources().getDimensionPixelSize(R.dimen.bottom_bar_height_optimal);
    }

    @Override
    public void onFinishInflate() {
        mSettingsOverlay
            = (TopRightMostOverlay) findViewById(R.id.bottombar_settings_overlay);
        mSettingsLayout
            = (TopRightWeightedLayout) findViewById(R.id.bottombar_settings);
        mCaptureLayout
            = (FrameLayout) findViewById(R.id.bottombar_capture);
        mIntentLayout
            = (TopRightWeightedLayout) findViewById(R.id.bottombar_intent);
    }

    /**
     * Initializes the bottom bar toggle for switching between
     * capture and the bottom bar settings toggles.
     */
    public void setupToggle(final boolean isCaptureIntent) {
        // Of type ToggleImageButton because ToggleButton
        // has a non-removable spacing for text on the right-hand side.
        ToggleImageButton toggle = (ToggleImageButton) findViewById(R.id.bottombar_settings_toggle);
        toggle.setState(0, false);
        toggle.setOnStateChangeListener(new ToggleImageButton.OnStateChangeListener() {
                @Override
                public void stateChanged(View view, boolean toSettings) {
                    if (toSettings) {
                        if (isCaptureIntent) {
                            hideIntentLayout();
                        }
                        transitionToSettings();
                    } else {
                        if (isCaptureIntent) {
                            transitionToIntentLayout();
                        } else {
                            transitionToCapture();
                        }
                    }
                }
            });
        mSettingsOverlay.setReferenceViewParent(mSettingsLayout);
    }

    /**
     * Hide the intent layout.  This is necessary for switching between
     * the intent capture layout and the bottom bar settings.
     */
    private void hideIntentLayout() {
        mIntentLayout.setVisibility(View.INVISIBLE);
    }

    /**
     * Perform a transition from the bottom bar capture layout to the
     * bottom bar settings layout.
     */
    private void transitionToCapture() {
        mSettingsLayout.setVisibility(View.INVISIBLE);
        mCaptureLayout.setVisibility(View.VISIBLE);
    }

    /**
     * Perform a transition from the bottom bar settings layout to the
     * bottom bar capture layout.
     */
    private void transitionToSettings() {
        mCaptureLayout.setVisibility(View.INVISIBLE);
        mSettingsLayout.setVisibility(View.VISIBLE);
    }

    /**
     * Perform a transition to the global intent layout.  The current
     * layout state of the bottom bar is irrelevant.
     */
    public void transitionToIntentLayout() {
        mCaptureLayout.setVisibility(View.VISIBLE);
        mSettingsLayout.setVisibility(View.INVISIBLE);
        mSettingsOverlay.setVisibility(View.VISIBLE);
        mIntentLayout.setVisibility(View.VISIBLE);

        View button;
        button = mIntentLayout.findViewById(R.id.done_button);
        button.setVisibility(View.INVISIBLE);
        button = mIntentLayout.findViewById(R.id.retake_button);
        button.setVisibility(View.INVISIBLE);
    }

    /**
     * Perform a transition to the global intent review layout.
     * The current layout state of the bottom bar is irrelevant.
     */
    public void transitionToIntentReviewLayout() {
        mCaptureLayout.setVisibility(View.INVISIBLE);
        mSettingsLayout.setVisibility(View.INVISIBLE);
        mSettingsOverlay.setVisibility(View.INVISIBLE);

        View button;
        button = mIntentLayout.findViewById(R.id.done_button);
        button.setVisibility(View.VISIBLE);
        button = mIntentLayout.findViewById(R.id.retake_button);
        button.setVisibility(View.VISIBLE);
        mIntentLayout.setVisibility(View.VISIBLE);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mWidth = MeasureSpec.getSize(widthMeasureSpec);
        mHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (mWidth == 0 || mHeight == 0) {
            return;
        }

        if (mOffsetShorterEdge != 0 && mOffsetLongerEdge != 0) {
            float previewAspectRatio =
                    mOffsetLongerEdge / mOffsetShorterEdge;
            if (previewAspectRatio < 1.0) {
                previewAspectRatio = 1.0f/previewAspectRatio;
            }
            float screenAspectRatio = (float) mWidth / (float) mHeight;
            if (screenAspectRatio < 1.0) {
                screenAspectRatio = 1.0f/screenAspectRatio;
            }
            if (previewAspectRatio >= screenAspectRatio) {
                mOverLayBottomBar = true;
                setAlpha(0.5f);
            } else {
                mOverLayBottomBar = false;
                setAlpha(1.0f);
            }
        }

        // Calculates the width and height needed for the bar.
        int barWidth, barHeight;
        if (mWidth > mHeight) {
            ((LayoutParams) getLayoutParams()).gravity = Gravity.RIGHT;
            if ((mOffsetLongerEdge == 0 && mOffsetShorterEdge == 0) || mOverLayBottomBar) {
                barWidth = mOptimalHeight;
                barHeight = mHeight;
            } else {
                barWidth = (int) (mWidth - mOffsetLongerEdge);
                barHeight = mHeight;
            }
        } else {
            ((LayoutParams) getLayoutParams()).gravity = Gravity.BOTTOM;
            if ((mOffsetLongerEdge == 0 && mOffsetShorterEdge == 0) || mOverLayBottomBar) {
                barWidth = mWidth;
                barHeight = mOptimalHeight;
            } else {
                barWidth = mWidth;
                barHeight = (int) (mHeight - mOffsetLongerEdge);
            }
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(barWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY));
    }

    private void adjustBottomBar(float scaledTextureWidth,
                                 float scaledTextureHeight) {
        setOffset(scaledTextureWidth, scaledTextureHeight);
    }

    @Override
    public void onPreviewAreaSizeChanged(float scaledTextureWidth,
                                         float scaledTextureHeight) {
        adjustBottomBar(scaledTextureWidth, scaledTextureHeight);
    }

    private void setOffset(float scaledTextureWidth, float scaledTextureHeight) {
        float offsetLongerEdge, offsetShorterEdge;
        if (scaledTextureHeight > scaledTextureWidth) {
            offsetLongerEdge = scaledTextureHeight;
            offsetShorterEdge = scaledTextureWidth;
        } else {
            offsetLongerEdge = scaledTextureWidth;
            offsetShorterEdge = scaledTextureHeight;
        }
        if (mOffsetLongerEdge != offsetLongerEdge || mOffsetShorterEdge != offsetShorterEdge) {
            mOffsetLongerEdge = offsetLongerEdge;
            mOffsetShorterEdge = offsetShorterEdge;
            requestLayout();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
    }

    // prevent touches on bottom bar (not its children)
    // from triggering a touch event on preview area
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }
}
