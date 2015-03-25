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

package com.android.camera.ui.focus;

import android.graphics.RectF;

import com.android.camera.async.MainThread;
import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.one.OneCamera.FocusDistanceListener;
import com.android.camera.ui.motion.LinearScale;

/**
 * The focus controller interacts with the focus ring UI element.
 */
public class FocusController implements FocusDistanceListener {
    private static final Tag TAG = new Tag("FocusController");

    private final FocusRing mFocusRing;
    private final FocusSound mFocusSound;
    private final MainThread mMainThread;

    public FocusController(FocusRing focusRing, FocusSound focusSound, MainThread mainThread) {
        mFocusRing = focusRing;
        mFocusSound = focusSound;
        mMainThread = mainThread;
    }

    /**
     * Show a passive focus animation at the center of the active area.
     * This will likely be different than the view bounds due to varying image
     * ratios and dimensions.
     */
    public void showPassiveFocusAtCenter() {
        mMainThread.execute(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "Running showPassiveFocusAtCenter()");
                mFocusRing.startPassiveFocus();
                mFocusRing.centerFocusLocation();
            }
        });
    }

    /**
     * Show a passive focus animation at the given viewX and viewY position.
     * This is usually indicates the camera subsystem kicked off an auto-focus
     * at the given screen position.
     *
     * @param viewX the view's x coordinate
     * @param viewY the view's y coordinate
     */
    public void showPassiveFocusAt(final int viewX, final int viewY) {
        mMainThread.execute(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "Running showPassiveFocusAt(" + viewX + ", " + viewY + ")");
                mFocusRing.startPassiveFocus();
                mFocusRing.setFocusLocation(viewX, viewY);
            }
        });
    }

    /**
     * Show an active focus animation at the given viewX and viewY position.
     * This is normally initiated by the user touching the screen at a given
     * point.
     *
     * @param viewX the view's x coordinate
     * @param viewY the view's y coordinate
     */
    public void showActiveFocusAt(final int viewX, final int viewY) {
        mMainThread.execute(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "showActiveFocusAt(" + viewX + ", " + viewY + ")");
                mFocusRing.startActiveFocus();
                mFocusRing.setFocusLocation(viewX, viewY);

                // TODO: Enable focus sound when better audio controls exist.
                // mFocusSound.play();
            }
        });
    }

    /**
     * Stop any currently executing focus animation.
     */
    public void clearFocusIndicator() {
        mMainThread.execute(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "clearFocusIndicator()");
                mFocusRing.stopFocusAnimations();
            }
        });
    }

    /**
     * Computing the correct location for the focus ring requires knowing
     * the screen position and size of the preview area so the drawing
     * operations can be clipped correctly.
     */
    public void configurePreviewDimensions(final RectF previewArea) {
        mMainThread.execute(new Runnable() {
            @Override
            public void run() {
               Log.v(TAG, "configurePreviewDimensions(" + previewArea + ")");
               mFocusRing.configurePreviewDimensions(previewArea);
            }
        });
    }

    /**
     * Set the radius of the focus ring as a radius between 0 and 1.
     * This will map to the min and max values computed for the UI.
     */
    public void setFocusRatio(final float ratio) {
        mMainThread.execute(new Runnable() {
            @Override
            public void run() {
                if (mFocusRing.isPassiveFocusRunning() ||
                      mFocusRing.isActiveFocusRunning()) {
                    mFocusRing.setRadiusRatio(ratio);
                }
            }
        });
    }

    @Override
    public void onFocusDistance(float lensDistance, LinearScale lensRange) {
        if (lensRange.isInDomain(lensDistance)) {
            setFocusRatio(lensRange.scale(lensDistance));
        }
    }
}
