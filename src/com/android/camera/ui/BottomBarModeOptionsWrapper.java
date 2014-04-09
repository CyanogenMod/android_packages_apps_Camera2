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
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.camera2.R;

/**
 * The goal of this class is to ensure mode options is always laid out to
 * the left of or above bottom bar in landscape or portrait respectively.
 * All the other children in this view group can be expected to be laid out
 * the same way as they are in a normal FrameLayout.
 */
public class BottomBarModeOptionsWrapper extends FrameLayout {

    private View mModeOptionsOverlay;
    private View mBottomBar;

    public BottomBarModeOptionsWrapper(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onFinishInflate() {
        mModeOptionsOverlay = findViewById(R.id.mode_options_overlay);
        mBottomBar = findViewById(R.id.bottom_bar);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int bottomBarWidth = mBottomBar.getMeasuredWidth();
        final int bottomBarHeight = mBottomBar.getMeasuredHeight();
        right -= left;
        bottom -= top;
        left = 0;
        top = 0;
        super.onLayout(changed, left, top, right, bottom);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Lay out mode options to the left of bottom bar and in the vertical
            // center of the parent view.
            int centerY = (top + bottom) / 2;
            mModeOptionsOverlay.layout(left, centerY - bottomBarHeight / 2,
                    right - bottomBarWidth, centerY + bottomBarHeight / 2);
        } else {
            // Lay out mode options above the bottom bar and in the horizontal center.
            int centerX = (left + right) / 2;
            mModeOptionsOverlay.layout(centerX - bottomBarWidth / 2, top,
                    centerX + bottomBarWidth / 2, bottom - bottomBarHeight);
        }
    }
}