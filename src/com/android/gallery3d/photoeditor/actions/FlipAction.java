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

import com.android.gallery3d.R;
import com.android.gallery3d.photoeditor.PhotoView;
import com.android.gallery3d.photoeditor.filters.FlipFilter;

/**
 * An action handling flip effect.
 */
public class FlipAction extends EffectAction {

    private static final float DEFAULT_ANGLE = 0.0f;
    private static final float DEFAULT_FLIP_SPAN = 180.0f;

    private FlipFilter filter;
    private float horizontalFlipDegrees;
    private float verticalFlipDegrees;
    private Runnable queuedFlipChange;
    private FlipView flipView;

    public FlipAction(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void doBegin() {
        filter = new FlipFilter();

        flipView = factory.createFlipView();
        flipView.setOnFlipChangeListener(new FlipView.OnFlipChangeListener() {

            // Directly transform photo-view because running the flip filter isn't fast enough.
            PhotoView photoView = (PhotoView) flipView.getRootView().findViewById(
                    R.id.photo_view);

            @Override
            public void onAngleChanged(float horizontalDegrees, float verticalDegrees,
                    boolean fromUser) {
                if (fromUser) {
                    horizontalFlipDegrees = horizontalDegrees;
                    verticalFlipDegrees = verticalDegrees;
                    updateFlipFilter(false);
                    transformPhotoView(horizontalDegrees, verticalDegrees);
                }
            }

            @Override
            public void onStartTrackingTouch() {
                // no-op
            }

            @Override
            public void onStopTrackingTouch() {
                roundFlipDegrees();
                updateFlipFilter(false);
                transformPhotoView(horizontalFlipDegrees, verticalFlipDegrees);
                flipView.setFlippedAngles(horizontalFlipDegrees, verticalFlipDegrees);
            }

            private void transformPhotoView(final float horizontalDegrees,
                    final float verticalDegrees) {
                // Remove the outdated flip change before queuing a new one.
                if (queuedFlipChange != null) {
                    photoView.remove(queuedFlipChange);
                }
                queuedFlipChange = new Runnable() {

                    @Override
                    public void run() {
                        photoView.flipPhoto(horizontalDegrees, verticalDegrees);
                    }
                };
                photoView.queue(queuedFlipChange);
            }
        });
        flipView.setFlippedAngles(DEFAULT_ANGLE, DEFAULT_ANGLE);
        flipView.setFlipSpan(DEFAULT_FLIP_SPAN);
        horizontalFlipDegrees = 0;
        verticalFlipDegrees = 0;
        queuedFlipChange = null;
    }

    @Override
    public void doEnd() {
        flipView.setOnFlipChangeListener(null);
        // Round the current flip degrees in case flip tracking has not stopped yet.
        roundFlipDegrees();
        updateFlipFilter(true);
    }

    /**
     * Rounds flip degrees to multiples of 180 degrees.
     */
    private void roundFlipDegrees() {
        if (horizontalFlipDegrees % 180 != 0) {
            horizontalFlipDegrees = Math.round(horizontalFlipDegrees / 180) * 180;
        }
        if (verticalFlipDegrees % 180 != 0) {
            verticalFlipDegrees = Math.round(verticalFlipDegrees / 180) * 180;
        }
    }

    private void updateFlipFilter(boolean outputFilter) {
        // Flip the filter if the flipped degrees are at the opposite directions.
        filter.setFlip(((int) horizontalFlipDegrees / 180) % 2 != 0,
                ((int) verticalFlipDegrees / 180) % 2 != 0);
        notifyFilterChanged(filter, outputFilter);
    }
}
