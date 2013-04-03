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

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout.LayoutParams;

import com.android.camera.Util;
import com.android.gallery3d.R;

public class CameraControls extends RotatableLayout
{
    private View mBackgroundView;
    public CameraControls(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CameraControls(Context context) {
        super(context);
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        adjustBackground();
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mBackgroundView = findViewById(R.id.blocker);
    }

    // In reverse landscape and reverse portrait, camera controls will be laid out
    // on the wrong side of the screen. We need to make adjustment to move the controls
    // to the USB side
    public void adjustControlsToRightPosition() {
        Configuration config = getResources().getConfiguration();
        int orientation = Util.getDisplayRotation((Activity) getContext());
        if (orientation == 270 && config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            flipChildren();
        }
        if (orientation == 180 && config.orientation == Configuration.ORIENTATION_PORTRAIT) {
            flipChildren();
        }
        adjustBackground();
    }

    private void adjustBackground() {
        // remove current drawable and reset rotation
        mBackgroundView.setBackgroundDrawable(null);
        mBackgroundView.setRotationX(0);
        mBackgroundView.setRotationY(0);
        // if the switcher background is top aligned we need to flip the background
        // drawable vertically; if left aligned, flip horizontally
        int gravity = ((LayoutParams) mBackgroundView.getLayoutParams()).gravity;
        if ((gravity & Gravity.TOP) == Gravity.TOP) {
            mBackgroundView.setRotationX(180);
        } else if ((gravity & Gravity.LEFT) == Gravity.LEFT) {
            mBackgroundView.setRotationY(180);
        }
        mBackgroundView.setBackgroundResource(R.drawable.switcher_bg);
    }
}
