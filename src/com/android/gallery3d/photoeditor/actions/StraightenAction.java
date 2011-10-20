/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.photoeditor.actions;

import android.content.Context;
import android.util.AttributeSet;

import com.android.gallery3d.photoeditor.filters.StraightenFilter;

/**
 * An action handling straighten effect.
 */
public class StraightenAction extends EffectAction {

    private static final float DEFAULT_ANGLE = 0.0f;
    private static final float DEFAULT_ROTATE_SPAN = StraightenFilter.MAX_DEGREES * 2;

    private RotateView rotateView;

    public StraightenAction(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void doBegin() {
        final StraightenFilter filter = new StraightenFilter();

        rotateView = factory.createRotateView();
        rotateView.setOnRotateChangeListener(new RotateView.OnRotateChangeListener() {

            @Override
            public void onAngleChanged(float degrees, boolean fromUser){
                if (fromUser) {
                    filter.setAngle(degrees);
                    notifyFilterChanged(filter, true);
                }
            }

            @Override
            public void onStartTrackingTouch() {
                // no-op
            }

            @Override
            public void onStopTrackingTouch() {
                // no-op
            }
        });
        rotateView.setDrawGrids(true);
        rotateView.setRotatedAngle(DEFAULT_ANGLE);
        rotateView.setRotateSpan(DEFAULT_ROTATE_SPAN);
    }

    @Override
    public void doEnd() {
        rotateView.setOnRotateChangeListener(null);
    }
}
