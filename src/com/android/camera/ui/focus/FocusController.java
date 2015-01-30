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

import android.os.Handler;

import com.android.camera.debug.Log.Tag;
import com.android.camera.one.OneCamera.FocusDistanceListener;

/**
 * The focus controller interacts with the focus ring UI element.
 */
public class FocusController implements FocusDistanceListener {
    private static final Tag TAG = new Tag("FocusController");

    private final FocusRing mFocusRing;
    private final Handler mHandler;
    private final FocusSound mFocusSound;

    public FocusController(FocusRing focusRing, FocusSound focusSound, Handler handler) {
        mFocusRing = focusRing;
        mHandler = handler;
        mFocusSound = focusSound;
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
        mHandler.post(new Runnable() {
            @Override
            public void run() {
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
        mHandler.post(new Runnable() {
            @Override
            public void run() {
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
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mFocusRing.stopFocusAnimations();
            }
        });
    }

    @Override
    public void onFocusDistance(final float diopter, final boolean isActive) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isActive || mFocusRing.isPassiveFocusRunning() ||
                      mFocusRing.isActiveFocusRunning()) {
                    mFocusRing.setFocusDiopter(diopter);
                }
            }
        });
    }
}
