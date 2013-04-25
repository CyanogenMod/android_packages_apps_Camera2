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
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.android.camera.Util;
import com.android.gallery3d.R;

public class CameraRootView extends RelativeLayout
    implements RotatableLayout.RotationListener {

    private int mTopMargin = 0;
    private int mBottomMargin = 0;
    private int mLeftMargin = 0;
    private int mRightMargin = 0;
    private int mOffset = 0;
    public CameraRootView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Layout the window as if we did not need navigation bar
        setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    protected boolean fitSystemWindows(Rect insets) {
        super.fitSystemWindows(insets);
        // insets include status bar, navigation bar, etc
        // In this case, we are only concerned with the size of nav bar
        if (mOffset > 0) {
            // Add margin if necessary to the view to ensure nothing is covered
            // by navigation bar
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
            int right, bottom;
            if (insets.right > 0) {
                // navigation bar on the right
                right = mRightMargin > 0 ? 0 : insets.right;
                bottom = 0;
            } else {
                // navigation bar on the bottom
                bottom = mBottomMargin > 0 ? 0 : insets.bottom;
                right = 0;
            }
            lp.setMargins(mLeftMargin, mTopMargin, mRightMargin + right, mBottomMargin + bottom);
            return true;
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        if (insets.bottom > 0) {
            mOffset = insets.bottom;
        } else if (insets.right > 0) {
            mOffset = insets.right;
        }
        Configuration config = getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
            mBottomMargin = mOffset;
        } else if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mRightMargin = mOffset;
        }
        lp.setMargins( mLeftMargin, mTopMargin, mRightMargin, mBottomMargin);
        CameraControls controls = (CameraControls) findViewById(R.id.camera_controls);
        if (controls != null) {
            controls.setRotationListener(this);
            controls.adjustControlsToRightPosition();
        }
        return true;
    }

    @Override
    public void onRotation(int rotation) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        int b = mBottomMargin;
        int t = mTopMargin;
        int l = mLeftMargin;
        int r = mRightMargin;
        rotation = (rotation + 360) % 360;
        if (rotation == 90) {
            lp.setMargins(b, l, t, r);
        } else if (rotation == 270) {
            lp.setMargins(t, r, b, l);
        } else if (rotation == 180) {
            lp.setMargins(r, b, l, t);
        }
        mLeftMargin = lp.leftMargin;
        mTopMargin = lp.topMargin;
        mRightMargin = lp.rightMargin;
        mBottomMargin = lp.bottomMargin;
    }
}
