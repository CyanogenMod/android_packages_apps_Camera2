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
import android.widget.LinearLayout;

/**
 * RotatableLinearLayout maintains a vertical LinearLayout in portrait
 * and a horizontal LinearLayout in landscape.
 */
public class RotatableLinearLayout extends LinearLayout {

    public RotatableLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            setOrientation(LinearLayout.HORIZONTAL);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);

        final boolean isHorizontal = LinearLayout.HORIZONTAL == getOrientation();
        final boolean isPortrait
            = Configuration.ORIENTATION_PORTRAIT == configuration.orientation;
        if (isPortrait && isHorizontal) {
            setOrientation(LinearLayout.VERTICAL);
        } else if (!isPortrait && !isHorizontal) {
            setOrientation(LinearLayout.HORIZONTAL);
        }
    }
}