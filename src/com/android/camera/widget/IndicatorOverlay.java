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
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.camera.ui.PreviewStatusListener;

/**
 * IndicatorOverlay is a FrameLayout which positions indicator icons in
 * in the bottom right corner of the preview that is visible
 * above the bottom bar.  This overlay takes its horizontal dimension from
 * the preview, and its vertical dimension relative to the bottom bar.
 */
public class IndicatorOverlay extends FrameLayout
    implements PreviewStatusListener.PreviewAreaSizeChangedListener {

    private final static String TAG = "IndicatorOverlay";

    private int mPreviewWidth;
    private int mPreviewHeight;

    public IndicatorOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
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
    public void setLayoutDimensions() {
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
}
