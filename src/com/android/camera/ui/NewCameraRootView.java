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

import com.android.camera.Util;
import com.android.gallery3d.R;

public class NewCameraRootView extends FrameLayout
    implements RotatableLayout.RotationListener {

    private int mOffset = 0;
    public NewCameraRootView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected boolean fitSystemWindows(Rect insets) {
        super.fitSystemWindows(insets);
        // insets include status bar, navigation bar, etc
        // In this case, we are only concerned with the size of nav bar
        if (mOffset > 0) return true;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        if (insets.bottom > 0) {
            mOffset = insets.bottom;
        } else if (insets.right > 0) {
            mOffset = insets.right;
        }
        Configuration config = getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
            lp.setMargins(0, 0, 0, mOffset);
        } else if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            lp.setMargins(0, 0, mOffset, 0);
        }
        CameraControls controls = (CameraControls) findViewById(R.id.camera_controls);
        if (controls != null) {
            controls.setRotationListener(this);
            controls.adjustControlsToRightPosition();
        }
        return true;
    }

    public void cameraModuleChanged() {
        CameraControls controls = (CameraControls) findViewById(R.id.camera_controls);
        if (controls != null) {
            controls.setRotationListener(this);
            controls.adjustControlsToRightPosition();
        }
    }

    @Override
    public void onRotation(int rotation) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        int b = lp.bottomMargin;
        int t = lp.topMargin;
        int l = lp.leftMargin;
        int r = lp.rightMargin;
        rotation = (rotation + 360) % 360;
        if (rotation == 90) {
            lp.setMargins(b, l, t, r);
        } else if (rotation == 270) {
            lp.setMargins(t, r, b, l);
        } else if (rotation == 180) {
            lp.setMargins(r, b, l, t);
        }
    }
}
