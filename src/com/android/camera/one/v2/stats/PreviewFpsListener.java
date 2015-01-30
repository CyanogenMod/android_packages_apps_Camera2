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

package com.android.camera.one.v2.stats;

import com.android.camera.async.Updatables;
import com.android.camera.async.Updatable;
import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.one.v2.core.ResponseListener;

/**
 * A {@link ResponseListener} which provides a stream of averaged fps.
 */
public class PreviewFpsListener extends ResponseListener {
    private static final Tag TAG = new Tag("PreviewFPS");
    private static final float EXISTING_VALUE_WEIGHT = .9f;
    private static final float WARNING_THRESHOLD_SECONDS = 0.1f;
    private static final double NANO_TO_SECOND_DENOMINATOR =  1000000000.0;

    private final Updatable<Float> mFpsListener;

    private long mLastFrameTimeNanos = 0;
    private double mFrameDuration = 1.0 / 30.0f;
    private double mFpsValue = 30.0f;

    public PreviewFpsListener() {
        this(Updatables.<Float>getNoOp());
    }

    public PreviewFpsListener(Updatable<Float> fpsListener) {
        mFpsListener = fpsListener;
    }

    @Override
    public void onStarted(long timestampNanos) {
        if(mLastFrameTimeNanos == 0) {
            mLastFrameTimeNanos = timestampNanos;
            return;
        }

        long elapsedNanos = (timestampNanos - mLastFrameTimeNanos);
        double elapsedSeconds = (double)elapsedNanos / NANO_TO_SECOND_DENOMINATOR;

        // TODO: Consider warning when a frame takes x percent longer than
        // the current frame rate or if the frame rate drops below some value.
        if(elapsedSeconds > WARNING_THRESHOLD_SECONDS) {
            Log.e(TAG, String.format(
                "Elapsed time from previous start was over %.2f millis. "
                      + "%.2f millis total, %.4f avg fps.",
                  WARNING_THRESHOLD_SECONDS * 1000,
                  elapsedSeconds * 1000,
                  mFpsValue));
        }

        mFrameDuration = mFrameDuration * EXISTING_VALUE_WEIGHT +
              (1.0f-EXISTING_VALUE_WEIGHT) * elapsedSeconds;

        mFpsValue = 1.0 / mFrameDuration;
        mLastFrameTimeNanos = timestampNanos;

        mFpsListener.update((float) mFpsValue);
    }
}
