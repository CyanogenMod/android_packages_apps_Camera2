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

package com.android.camera.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import com.android.camera.app.CameraAppUI;
import com.android.camera2.R;

/**
 * This class manages the position change of the settings button in mode drawer.
 * It calculates its position based on the preview rect that is not covered by
 * bottom bar and the orientation of the layout.
 */
public class SettingsButton extends ImageView
    implements CameraAppUI.UncoveredPreviewAreaSizeChangedListener {

    private final int mButtonSize;
    private final int mButtonMargin;
    private final RectF mUncoveredPreviewArea = new RectF();
    private int mCurrentOrientation;
    private boolean mOrientationChanged;

    public SettingsButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mButtonSize = getResources().getDimensionPixelSize(R.dimen.mode_list_settings_icon_size);
        mButtonMargin = getResources().getDimensionPixelSize(
                R.dimen.mode_list_settings_icon_margin);
    }

    @Override
    public void uncoveredPreviewAreaChanged(RectF uncoveredPreviewArea) {
        mUncoveredPreviewArea.set(uncoveredPreviewArea);
        if (mOrientationChanged) {
            mOrientationChanged = false;
            adjustPosition();
        }
    }

    /**
     * Calculates the position of the button based on the current orientation and updated
     * preview area rect that is not covered by bottom bar.
     */
    private void adjustPosition() {
        mCurrentOrientation = getResources().getConfiguration().orientation;
        if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Align to the top right.
            setTranslationX(mUncoveredPreviewArea.right - mButtonMargin - mButtonSize);
            setTranslationY(mUncoveredPreviewArea.top + mButtonMargin);
        } else {
            // Align to the bottom right.
            setTranslationX(mUncoveredPreviewArea.right - mButtonMargin - mButtonSize);
            setTranslationY(mUncoveredPreviewArea.bottom - mButtonMargin - mButtonSize);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (config.orientation != mCurrentOrientation && getVisibility() == VISIBLE) {
            // Sets this flag to true, to postpone the position adjustment to when
            // the preview area size actually changes during/right after layout pass.
            mOrientationChanged = true;
        }
    }

    @Override
    public void onVisibilityChanged(View v, int visibility) {
        if (visibility == VISIBLE) {
            adjustPosition();
        }
    }
}
