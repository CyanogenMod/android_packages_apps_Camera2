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
import com.android.gallery3d.photoeditor.filters.RotateFilter;

/**
 * An action handling rotate effect.
 */
public class RotateAction extends EffectAction {

    private static final float DEFAULT_ANGLE = 0.0f;
    private static final float DEFAULT_ROTATE_SPAN = 360.0f;

    private RotateFilter filter;
    private float rotateDegrees;
    private Runnable queuedRotationChange;
    private RotateView rotateView;

    public RotateAction(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void doBegin() {
        filter = new RotateFilter();

        rotateView = factory.createRotateView();
        rotateView.setOnRotateChangeListener(new RotateView.OnRotateChangeListener() {

            // Directly transform photo-view because running the rotate filter isn't fast enough.
            PhotoView photoView = (PhotoView) rotateView.getRootView().findViewById(
                    R.id.photo_view);

            @Override
            public void onAngleChanged(float degrees, boolean fromUser){
                if (fromUser) {
                    rotateDegrees = degrees;
                    updateRotateFilter(false);
                    transformPhotoView(degrees);
                }
            }

            @Override
            public void onStartTrackingTouch() {
                // no-op
            }

            @Override
            public void onStopTrackingTouch() {
                roundRotateDegrees();
                updateRotateFilter(false);
                transformPhotoView(rotateDegrees);
                rotateView.setRotatedAngle(rotateDegrees);
            }

            private void transformPhotoView(final float degrees) {
                // Remove the outdated rotation change before queuing a new one.
                if (queuedRotationChange != null) {
                    photoView.remove(queuedRotationChange);
                }
                queuedRotationChange = new Runnable() {

                    @Override
                    public void run() {
                        photoView.rotatePhoto(degrees);
                    }
                };
                photoView.queue(queuedRotationChange);
            }
        });
        rotateView.setRotatedAngle(DEFAULT_ANGLE);
        rotateView.setRotateSpan(DEFAULT_ROTATE_SPAN);
        rotateDegrees = 0;
        queuedRotationChange = null;
    }

    @Override
    public void doEnd() {
        rotateView.setOnRotateChangeListener(null);
        // Round the current rotation degrees in case rotation tracking has not stopped yet.
        roundRotateDegrees();
        updateRotateFilter(true);
    }

    /**
     * Rounds rotate degrees to multiples of 90 degrees.
     */
    private void roundRotateDegrees() {
        if (rotateDegrees % 90 != 0) {
            rotateDegrees = Math.round(rotateDegrees / 90) * 90;
        }
    }

    private void updateRotateFilter(boolean outputFilter) {
        filter.setAngle(rotateDegrees);
        notifyFilterChanged(filter, outputFilter);
    }
}
