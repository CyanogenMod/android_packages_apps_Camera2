/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"),
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

package com.android.camera.cameradevice;

import android.os.SystemClock;

import com.android.camera.debug.Log;

class CameraStateHolder {
    private static final Log.Tag TAG = new Log.Tag("CameraStateHolder");

    /** Camera states **/
    // These states are defined bitwise so we can easily to specify a set of
    // states together.
    public static final int CAMERA_UNOPENED = 1;
    public static final int CAMERA_IDLE = 1 << 1;
    public static final int CAMERA_UNLOCKED = 1 << 2;
    public static final int CAMERA_CAPTURING = 1 << 3;
    public static final int CAMERA_FOCUSING = 1 << 4;

    private int mState;

    public CameraStateHolder() {
        setState(CAMERA_UNOPENED);
    }

    public CameraStateHolder(int state) {
        setState(state);
    }

    public synchronized void setState(int state) {
        mState = state;
        this.notifyAll();
    }

    public synchronized int getState() {
        return mState;
    }

    private interface ConditionChecker {
        /**
         * @return Whether the condition holds.
         */
        boolean success();
    }

    /**
     * A helper method used by {@link #waitToAvoidStates(int)} and
     * {@link #waitForStates(int)}. This method will wait until the
     * condition is successful.
     *
     * @param stateChecker The state checker to be used.
     * @param timeoutMs The timeout limit in milliseconds.
     * @return {@code false} if the wait is interrupted or timeout limit is
     *         reached.
     */
    private boolean waitForCondition(ConditionChecker stateChecker,
            long timeoutMs) {
        long timeBound = SystemClock.uptimeMillis() + timeoutMs;
        synchronized (this) {
            while (!stateChecker.success()) {
                try {
                    this.wait(timeoutMs);
                } catch (InterruptedException ex) {
                    if (SystemClock.uptimeMillis() > timeBound) {
                        // Timeout.
                        Log.w(TAG, "Timeout waiting.");
                    }
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Block the current thread until the state becomes one of the
     * specified.
     *
     * @param states Expected states.
     * @return {@code false} if the wait is interrupted or timeout limit is
     *         reached.
     */
    public boolean waitForStates(final int states) {
        return waitForCondition(new ConditionChecker() {
            @Override
            public boolean success() {
                return (states | mState) == states;
            }
        }, CameraManager.CAMERA_OPERATION_TIMEOUT_MS);
    }

    /**
     * Block the current thread until the state becomes NOT one of the
     * specified.
     *
     * @param states States to avoid.
     * @return {@code false} if the wait is interrupted or timeout limit is
     *         reached.
     */
    public boolean waitToAvoidStates(final int states) {
        return waitForCondition(new ConditionChecker() {
            @Override
            public boolean success() {
                return (states & mState) == 0;
            }
        }, CameraManager.CAMERA_OPERATION_TIMEOUT_MS);
    }
}
