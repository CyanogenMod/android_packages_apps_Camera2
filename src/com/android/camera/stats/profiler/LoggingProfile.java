/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.camera.stats.profiler;

/**
 * A logging profile writes out all events to the provided
 * writer and uses the standard message formatting.
 */
public class LoggingProfile extends ProfileBase {
    private final Writer mWriter;

    /** Create a new LoggingProfile */
    public LoggingProfile(Writer writer, String name) {
        super(name);

        mWriter = writer;
    }

    @Override
    protected void onStart() {
        mWriter.write(format(0.0, "BEGIN"));
    }

    @Override
    protected void onMark(double totalMillis, double lastMillis, String reason) {
        mWriter.write(format(totalMillis, "MARK", lastMillis, reason));
    }

    @Override
    protected void onStop(double totalMillis, double lastMillis) {
        mWriter.write(format(totalMillis, "END"));
    }

    @Override
    protected void onStop(double totalMillis, double lastMillis, String reason) {
        mWriter.write(format(totalMillis, "END", lastMillis, reason));
    }
}
