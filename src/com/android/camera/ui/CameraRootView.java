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

public class CameraRootView extends RelativeLayout {

    private int mTopMargin = 0;
    private int mBottomMargin = 0;
    private int mLeftMargin = 0;
    private int mRightMargin = 0;
    private int mOffset = 0;
    private Rect mCurrentInsets;
    public CameraRootView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Layout the window as if we did not need navigation bar
        setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    protected boolean fitSystemWindows(Rect insets) {
        super.fitSystemWindows(insets);
        mCurrentInsets = insets;
        // insets include status bar, navigation bar, etc
        // In this case, we are only concerned with the size of nav bar
        if (mOffset > 0) return true;

        if (insets.bottom > 0) {
            mOffset = insets.bottom;
        } else if (insets.right > 0) {
            mOffset = insets.right;
        }
        return true;
    }

    public void onLayout(boolean changed, int l, int t, int r, int b) {
        int rotation = Util.getDisplayRotation((Activity) getContext());
        // all the layout code assumes camera device orientation to be portrait
        // adjust rotation for landscape
        int orientation = getResources().getConfiguration().orientation;
        int camOrientation = (rotation % 180 == 0) ? Configuration.ORIENTATION_PORTRAIT
                : Configuration.ORIENTATION_LANDSCAPE;
        if (camOrientation != orientation) {
            rotation = (rotation + 90) % 360;
        }
        // calculate margins
        int left = 0;
        int right = 0;
        int bottom = 0;
        int top = 0;
        switch (rotation) {
            case 0:
                bottom += mOffset;
                break;
            case 90:
                right += mOffset;
                break;
            case 180:
                top += mOffset;
                break;
            case 270:
                left += mOffset;
                break;
        }
        if (mCurrentInsets.right > 0) {
            // navigation bar on the right
            right = right > 0 ? right : mCurrentInsets.right;
        } else {
            // navigation bar on the bottom
            bottom = bottom > 0 ? bottom : mCurrentInsets.bottom;
        }
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v instanceof CameraControls) {
                // Lay out camera controls to fill the short side of the screen
                // so that they stay in place during rotation
                if (rotation % 180 == 0) {
                    v.layout(l, t + top, r, b - bottom);
                } else {
                    v.layout(l + left, t, r - right, b);
                }
            } else {
                v.layout(l + left, t + top, r - right, b - bottom);
            }
        }
    }
}
