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
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.camera.Util;

/* RotatableLayout rotates itself as well as all its children when orientation
 * changes. Specifically, when going from portrait to landscape, camera
 * controls move from the bottom of the screen to right side of the screen
 * (i.e. counter clockwise). Similarly, when the screen changes to portrait, we
 * need to move the controls from right side to the bottom of the screen, which
 * is a clockwise rotation.
 */

public class RotatableLayout extends FrameLayout {

    private static final String TAG = "RotatableLayout";
    private int mPrevRotation;
    public RotatableLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public RotatableLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RotatableLayout(Context context) {
        super(context);
    }

    @Override
    public void onFinishInflate() { // get initial orientation
        mPrevRotation = Util.getDisplayRotation((Activity) getContext());
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        // Change the size of the layout
        ViewGroup.LayoutParams lp = getLayoutParams();
        int width = lp.width;
        int height = lp.height;
        lp.height = width;
        lp.width = height;
        setLayoutParams(lp);
        // rotate all the children
        int rotation = Util.getDisplayRotation((Activity) getContext());
        boolean clockwise = isClockWiseRotation(mPrevRotation, rotation);
        mPrevRotation = rotation;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            rotate(child, clockwise);
        }
    }

    public static boolean isClockWiseRotation(int prevRotation, int currentRotation) {
        if (prevRotation == (currentRotation + 90) % 360) {
            return true;
        }
        return false;
    }

    public static void rotate(View view, boolean isClockwise) {
        if (isClockwise) {
            rotateClockwise(view);
        } else {
            rotateCounterClockwise(view);
        }
    }

    private static boolean contains(int value, int mask) {
        return (value & mask) == mask;
    }

    public static void rotateClockwise(View view) {
        if (view == null) return;
        LayoutParams lp = (LayoutParams) view.getLayoutParams();
        int gravity = lp.gravity;
        int ngravity = 0;
        // rotate gravity
        if (contains(gravity, Gravity.LEFT)) {
            ngravity |= Gravity.TOP;
        }
        if (contains(gravity, Gravity.RIGHT)) {
            ngravity |= Gravity.BOTTOM;
        }
        if (contains(gravity, Gravity.TOP)) {
            ngravity |= Gravity.RIGHT;
        }
        if (contains(gravity, Gravity.BOTTOM)) {
            ngravity |= Gravity.LEFT;
        }
        if (contains(gravity, Gravity.CENTER)) {
            ngravity |= Gravity.CENTER;
        }
        if (contains(gravity, Gravity.CENTER_HORIZONTAL)) {
            ngravity |= Gravity.CENTER_VERTICAL;
        }
        if (contains(gravity, Gravity.CENTER_VERTICAL)) {
            ngravity |= Gravity.CENTER_HORIZONTAL;
        }
        lp.gravity = ngravity;
        int ml = lp.leftMargin;
        int mr = lp.rightMargin;
        int mt = lp.topMargin;
        int mb = lp.bottomMargin;
        lp.leftMargin = mb;
        lp.rightMargin = mt;
        lp.topMargin = ml;
        lp.bottomMargin = mr;
        int width = lp.width;
        int height = lp.height;
        lp.width = height;
        lp.height = width;
        view.setLayoutParams(lp);
    }

    public static void rotateCounterClockwise(View view) {
        if (view == null) return;
        LayoutParams lp = (LayoutParams) view.getLayoutParams();
        int gravity = lp.gravity;
        int ngravity = 0;
        // change gravity
        if (contains(gravity, Gravity.RIGHT)) {
            ngravity |= Gravity.TOP;
        }
        if (contains(gravity, Gravity.LEFT)) {
            ngravity |= Gravity.BOTTOM;
        }
        if (contains(gravity, Gravity.TOP)) {
            ngravity |= Gravity.LEFT;
        }
        if (contains(gravity, Gravity.BOTTOM)) {
            ngravity |= Gravity.RIGHT;
        }
        if (contains(gravity, Gravity.CENTER)) {
            ngravity |= Gravity.CENTER;
        }
        if (contains(gravity, Gravity.CENTER_HORIZONTAL)) {
            ngravity |= Gravity.CENTER_VERTICAL;
        }
        if (contains(gravity, Gravity.CENTER_VERTICAL)) {
            ngravity |= Gravity.CENTER_HORIZONTAL;
        }
        lp.gravity = ngravity;
        int ml = lp.leftMargin;
        int mr = lp.rightMargin;
        int mt = lp.topMargin;
        int mb = lp.bottomMargin;
        lp.leftMargin = mt;
        lp.rightMargin = mb;
        lp.topMargin = mr;
        lp.bottomMargin = ml;
        int width = lp.width;
        int height = lp.height;
        lp.width = height;
        lp.height = width;
        view.setLayoutParams(lp);
    }
}