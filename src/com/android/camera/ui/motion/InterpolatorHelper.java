/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.camera.ui.motion;

import android.content.Context;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.android.camera.util.ApiHelper;

import javax.annotation.Nonnull;

public class InterpolatorHelper {
    private static Interpolator LINEAR_OUT_SLOW_IN = null;

    @Nonnull
    public static Interpolator getLinearOutSlowInInterpolator(final @Nonnull Context context) {
        if (LINEAR_OUT_SLOW_IN != null) {
            return LINEAR_OUT_SLOW_IN;
        }

        if (ApiHelper.isLOrHigher()) {
            LINEAR_OUT_SLOW_IN = AnimationUtils.loadInterpolator(
                    context, android.R.interpolator.linear_out_slow_in);
        } else {
            LINEAR_OUT_SLOW_IN = new DecelerateInterpolator();
        }
        return LINEAR_OUT_SLOW_IN;
    }
}
