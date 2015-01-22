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
package com.android.camera.debug;

public class LogHelper {
    private static class Singleton {
        private static final LogHelper INSTANCE = new LogHelper();
    }

    public static LogHelper instance() {
        return Singleton.INSTANCE;
    }

    /**
     * Return a valid log level from {@link android.util.Log} to override
     * the system log level. Return 0 to instead defer to system log level.
     */
    public int getOverrideLevel() {
        return 0;
    }
}