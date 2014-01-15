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
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.android.camera.ui.PreviewStatusListener;

import com.android.camera2.R;

/**
 * IndicatorOverlay is a FrameLayout which positions indicator icons in
 * in the bottom right corner of the preview that is visible
 * above the bottom bar.  This overlay takes its horizontal dimension from
 * the preview, and its vertical dimension relative to the bottom bar.
 */
public class IndicatorOverlay extends FrameLayout
    implements PreviewStatusListener.PreviewAreaSizeChangedListener {

    private final static String TAG = "IndicatorOverlay";
    private final static int BOTTOM_RIGHT = Gravity.BOTTOM | Gravity.RIGHT;
    private final static int TOP_RIGHT = Gravity.TOP | Gravity.RIGHT;

    private int mPreviewWidth;
    private int mPreviewHeight;

    private LinearLayout mIndicatorOverlayIcons;

    public IndicatorOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onFinishInflate() {
        mIndicatorOverlayIcons = (LinearLayout) findViewById(R.id.indicator_overlay_icons);
        Configuration configuration = getContext().getResources().getConfiguration();
        checkOrientation(configuration.orientation);
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        checkOrientation(configuration.orientation);
    }

    @Override
    public void onPreviewAreaSizeChanged(RectF previewArea) {
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
        FrameLayout.LayoutParams params
            = (FrameLayout.LayoutParams) mIndicatorOverlayIcons.getLayoutParams();

        if (isPortrait && params.gravity != BOTTOM_RIGHT) {
            params.gravity = BOTTOM_RIGHT;
            requestLayout();
        } else if (!isPortrait && params.gravity != TOP_RIGHT) {
            params.gravity = TOP_RIGHT;
            requestLayout();
        }
    }
}
