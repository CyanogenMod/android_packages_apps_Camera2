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

/*
 * This is a simple view that has a gradient background. The background
 * needs to rotate when orientation changes, so that the side of the drawable
 * that is dark is always aligned to the side of the screen, and the side that is
 * closer to the center of the screen is transparent.
 * */
public class SwitcherBackgroundView extends View
{
    public SwitcherBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBackgroundResource(R.drawable.switcher_bg);
    }

    public SwitcherBackgroundView(Context context) {
        super(context);
        setBackgroundResource(R.drawable.switcher_bg);
    }
    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        // remove current drawable and reset rotation
        setBackgroundDrawable(null);
        setRotationX(0);
        setRotationY(0);
        // if the switcher background is top aligned we need to flip the background
        // drawable vertically; if left aligned, flip horizontally
        int gravity = ((LayoutParams) getLayoutParams()).gravity;
        if ((gravity & Gravity.TOP) == Gravity.TOP) {
            setRotationX(180);
        } else if ((gravity & Gravity.LEFT) == Gravity.LEFT) {
            setRotationY(180);
        }
        setBackgroundResource(R.drawable.switcher_bg);
    }
}
