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

package com.android.camera.ui.motion;

import android.os.SystemClock;

/**
 * Wraps the SystemClock static time methods so they can be exercised in tests.
 */
public abstract class AnimationClock {

    public abstract long getTimeMillis();

    /**
     * Forwards calls to SystemClock.uptimeMillis() since it is the most consistent clock for
     * animations.
     */
    public static class SystemTimeClock extends AnimationClock {

        @Override
        public long getTimeMillis() {
            return SystemClock.uptimeMillis();
        }
    }
}
