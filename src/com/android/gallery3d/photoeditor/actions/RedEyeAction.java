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
import android.graphics.PointF;
import android.util.AttributeSet;

import com.android.gallery3d.photoeditor.filters.RedEyeFilter;

/**
 * An action handling red-eye removal.
 */
public class RedEyeAction extends EffectAction {

    private TouchView touchView;

    public RedEyeAction(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void doBegin() {
        final RedEyeFilter filter = new RedEyeFilter();

        touchView = factory.createTouchView();
        touchView.setSingleTapListener(new TouchView.SingleTapListener() {

            @Override
            public void onSingleTap(PointF point) {
                filter.addRedEyePosition(point);
                notifyFilterChanged(filter, true);
            }
        });
    }

    @Override
    public void doEnd() {
        touchView.setSingleTapListener(null);
    }
}
