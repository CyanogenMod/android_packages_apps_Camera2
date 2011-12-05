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

import com.android.gallery3d.photoeditor.PhotoView;
import com.android.gallery3d.photoeditor.filters.RotateFilter;

/**
 * An action handling rotate effect.
 */
public class RotateAction extends EffectAction {

    private static final float DEFAULT_ANGLE = 0.0f;
    private static final float DEFAULT_ROTATE_SPAN = 360.0f;

    public RotateAction(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void prepare() {
        // Disable outputting rotated results and directly rotate photo-view for animations.
        final RotateFilter filter = new RotateFilter();
        disableFilterOutput();

        final RotateView rotateView = toolKit.addRotateView();
        rotateView.setOnRotateChangeListener(new RotateView.OnRotateChangeListener() {

            float rotateDegrees;
            Runnable queuedTransform;
            PhotoView photoView = toolKit.getPhotoView();

            @Override
            public void onAngleChanged(float degrees, boolean fromUser) {
                if (fromUser) {
                    rotateDegrees = degrees;
                    transformPhotoView(rotateDegrees);
                    notifyChanged(filter);
                }
            }

            @Override
            public void onStartTrackingTouch() {
                // no-op
            }

            @Override
            public void onStopTrackingTouch() {
                // Round rotate degrees to multiples of 90 degrees.
                if (rotateDegrees % 90 != 0) {
                    rotateDegrees = Math.round(rotateDegrees / 90) * 90;
                }
                transformPhotoView(rotateDegrees);
                rotateView.setRotatedAngle(rotateDegrees);
                filter.setAngle(rotateDegrees);
                notifyChanged(filter);
            }

            private void transformPhotoView(final float degrees) {
                // Remove the outdated rotation change before queuing a new one.
                if (queuedTransform != null) {
                    photoView.remove(queuedTransform);
                }
                queuedTransform = new Runnable() {

                    @Override
                    public void run() {
                        photoView.rotatePhoto(degrees);
                    }
                };
                photoView.queue(queuedTransform);
            }
        });
        rotateView.setRotatedAngle(DEFAULT_ANGLE);
        rotateView.setRotateSpan(DEFAULT_ROTATE_SPAN);
    }
}
