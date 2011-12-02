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
import com.android.gallery3d.photoeditor.filters.FlipFilter;

/**
 * An action handling flip effect.
 */
public class FlipAction extends EffectAction {

    private static final float DEFAULT_ANGLE = 0.0f;
    private static final float DEFAULT_FLIP_SPAN = 180.0f;

    public FlipAction(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void prepare() {
        // Disable outputting flipped results and directly flip photo-view for animations.
        final FlipFilter filter = new FlipFilter();
        disableFilterOutput();

        final FlipView flipView = toolKit.addFlipView();
        flipView.setOnFlipChangeListener(new FlipView.OnFlipChangeListener() {

            float horizontalDegrees;
            float verticalDegrees;
            Runnable queuedTransform;
            PhotoView photoView = toolKit.getPhotoView();

            @Override
            public void onAngleChanged(float horizontalDegrees, float verticalDegrees,
                    boolean fromUser) {
                if (fromUser) {
                    this.horizontalDegrees = horizontalDegrees;
                    this.verticalDegrees = verticalDegrees;
                    transformPhotoView(horizontalDegrees, verticalDegrees);
                    notifyChanged(filter);
                }
            }

            @Override
            public void onStartTrackingTouch() {
                // no-op
            }

            @Override
            public void onStopTrackingTouch() {
                // Round flip degrees to multiples of 180 degrees.
                horizontalDegrees = roundTo180(horizontalDegrees);
                verticalDegrees = roundTo180(verticalDegrees);
                transformPhotoView(horizontalDegrees, verticalDegrees);
                flipView.setFlippedAngles(horizontalDegrees, verticalDegrees);

                // Flip the filter according to the flipped directions of flip-view.
                filter.setFlip(((int) horizontalDegrees / 180) % 2 != 0,
                        ((int) verticalDegrees / 180) % 2 != 0);
                notifyChanged(filter);
            }

            private float roundTo180(float degrees) {
                if (degrees % 180 != 0) {
                    degrees = Math.round(degrees / 180) * 180;
                }
                return degrees;
            }

            private void transformPhotoView(final float horizontalDegrees,
                    final float verticalDegrees) {
                // Remove the outdated flip change before queuing a new one.
                if (queuedTransform != null) {
                    photoView.remove(queuedTransform);
                }
                queuedTransform = new Runnable() {

                    @Override
                    public void run() {
                        photoView.flipPhoto(horizontalDegrees, verticalDegrees);
                    }
                };
                photoView.queue(queuedTransform);
            }
        });
        flipView.setFlippedAngles(DEFAULT_ANGLE, DEFAULT_ANGLE);
        flipView.setFlipSpan(DEFAULT_FLIP_SPAN);
    }
}
