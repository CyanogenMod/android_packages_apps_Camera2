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
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import com.android.camera.Util;
import com.android.gallery3d.R;

public class CameraControls extends RotatableLayout {

    private static final String TAG = "CAM_Controls";

    private View mBackgroundView;
    private View mShutter;
    private View mSwitcher;
    private View mMenu;
    private View mIndicators;

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
        mSwitcher = findViewById(R.id.camera_switcher);
        mShutter = findViewById(R.id.shutter_button);
        mMenu = findViewById(R.id.menu);
        mIndicators = findViewById(R.id.on_screen_indicators);
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        int orientation = getResources().getConfiguration().orientation;
        int rotation = Util.getDisplayRotation((Activity) getContext());
        rotation = correctRotation(rotation, orientation);
        super.onLayout(changed, l, t, r, b);
        Rect shutter = new Rect();
        center(mShutter, l, t, r, b, orientation, rotation, shutter);
        center(mBackgroundView, l, t, r, b, orientation, rotation, new Rect());
        toLeft(mSwitcher, l, t, r, b, orientation, rotation, shutter);
        toRight(mMenu, l, t, r, b, orientation, rotation, shutter);
        toRight(mIndicators, l, t, r, b, orientation, rotation, shutter);
    }

    private int correctRotation(int rotation, int orientation) {
        // all the layout code assumes camera device orientation to be portrait
        // adjust rotation for landscape
        int camOrientation = (rotation % 180 == 0) ? Configuration.ORIENTATION_PORTRAIT
                : Configuration.ORIENTATION_LANDSCAPE;
        if (camOrientation != orientation) {
            return (rotation + 90) % 360;
        }
        return rotation;
    }
    private void center(View v, int l, int t, int r, int b, int orientation, int rotation, Rect result) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        int tw = lp.leftMargin + v.getMeasuredWidth() + lp.rightMargin;
        int th = lp.topMargin + v.getMeasuredHeight() + lp.bottomMargin;
        switch (rotation) {
        case 0:
            // phone portrait; controls bottom
            result.left = (r - l) / 2 - tw / 2 + lp.leftMargin;
            result.right = (r - l) / 2 + tw / 2 - lp.rightMargin;
            result.bottom = b - lp.bottomMargin;
            result.top = b - th + lp.topMargin;
            break;
        case 90:
            // phone landscape: controls right
            result.right = r - lp.rightMargin;
            result.left = r - tw + lp.leftMargin;
            result.top = (b - t) / 2 - th / 2 + lp.topMargin;
            result.bottom = (b - t) / 2 + th / 2 - lp.bottomMargin;
            break;
        case 180:
            // phone upside down: controls top
            result.left = (r - l) / 2 - tw / 2 + lp.leftMargin;
            result.right = (r - l) / 2 + tw / 2 - lp.rightMargin;
            result.top = t + lp.topMargin;
            result.bottom = t + th - lp.bottomMargin;
            break;
        case 270:
            // reverse landscape: controls left
            result.left = l + lp.leftMargin;
            result.right = l + tw - lp.rightMargin;
            result.top = (b - t) / 2 - th / 2 + lp.topMargin;
            result.bottom = (b - t) / 2 + th / 2 - lp.bottomMargin;
            break;
        }
        v.layout(result.left, result.top, result.right, result.bottom);
    }

    private void toLeft(View v, int l, int t, int r, int b, int orientation, int rotation, Rect anchor) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        int tw = lp.leftMargin + v.getMeasuredWidth() + lp.rightMargin;
        int th = lp.topMargin + v.getMeasuredHeight() + lp.bottomMargin;
        Rect result = new Rect();
        switch (rotation) {
        case 0:
            // portrait, to left of anchor at bottom
            result.right = anchor.left - lp.rightMargin;
            result.left = anchor.left - tw + lp.leftMargin;
            result.bottom = b - lp.bottomMargin;
            result.top = b - th + lp.topMargin;
            break;
        case 90:
            // phone landscape: below anchor on right
            result.right = r - lp.rightMargin;
            result.left = r - tw + lp.leftMargin;
            result.top = anchor.bottom + lp.topMargin;
            result.bottom = anchor.bottom + th - lp.bottomMargin;
            break;
        case 180:
            // phone upside down: right of anchor at top
            result.left = anchor.right + lp.leftMargin;
            result.right = anchor.right + tw - lp.rightMargin;
            result.top = t + lp.topMargin;
            result.bottom = t + th - lp.bottomMargin;
            break;
        case 270:
            // reverse landscape: above anchor on left
            result.left = l + lp.leftMargin;
            result.right = l + tw - lp.rightMargin;
            result.bottom = anchor.top - lp.bottomMargin;
            result.top = anchor.top - th + lp.topMargin;
            break;
        }
        v.layout(result.left, result.top, result.right, result.bottom);
    }

    private void toRight(View v, int l, int t, int r, int b, int orientation, int rotation, Rect anchor) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        int tw = lp.leftMargin + v.getMeasuredWidth() + lp.rightMargin;
        int th = lp.topMargin + v.getMeasuredHeight() + lp.bottomMargin;
        Rect result = new Rect();
        switch (rotation) {
        case 0:
            // portrait, right of anchor at bottom
            result.left = anchor.right + lp.leftMargin;
            result.right = anchor.right + tw - lp.rightMargin;
            result.bottom = b - lp.bottomMargin;
            result.top = b - th + lp.topMargin;
            break;
        case 90:
            // phone landscape: above anchor on right
            result.right = r - lp.rightMargin;
            result.left = r - tw + lp.leftMargin;
            result.bottom = anchor.top - lp.bottomMargin;
            result.top = anchor.top - th + lp.topMargin;
            break;
        case 180:
            // phone upside down: left of anchor at top
            result.right = anchor.left - lp.rightMargin;
            result.left = anchor.left - tw + lp.leftMargin;
            result.top = t + lp.topMargin;
            result.bottom = t + th - lp.bottomMargin;
            break;
        case 270:
            // reverse landscape: below anchor on left
            result.left = l + lp.leftMargin;
            result.right = l + tw - lp.rightMargin;
            result.top = anchor.bottom + lp.topMargin;
            result.bottom = anchor.bottom + th - lp.bottomMargin;
            break;
        }
        v.layout(result.left, result.top, result.right, result.bottom);
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
