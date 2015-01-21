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
 * A guarding profiler creates new guarded profiles that
 * will only write output messages if the profile time
 * exceeds the threshold.
 */
public class GuardingProfiler implements Profiler {
    private static final int DEFAULT_GUARD_DURATION_MILLIS = 15;
    private final Writer mGuardWriter;
    private final Writer mVerboseWriter;
    private final int mMaxDurationMillis;

    /** Create a new GuardingProfiler */
    public GuardingProfiler(Writer writer, Writer verbose) {
        this(writer, verbose, DEFAULT_GUARD_DURATION_MILLIS);
    }

    /** Create a new GuardingProfiler with a given max duration. */
    public GuardingProfiler(Writer writer, Writer verbose, int maxDurationMillis) {
        mGuardWriter = writer;
        mVerboseWriter = verbose;
        mMaxDurationMillis = maxDurationMillis;
    }

    @Override
    public Profile create(String name) {
        return new GuardingProfile(mGuardWriter, mVerboseWriter, name,
              mMaxDurationMillis);
    }

    /** Start a new profile, but override the maxDuration */
    public Profile create(String name, int maxDurationMillis) {
        return new GuardingProfile(mGuardWriter, mVerboseWriter, name,
              maxDurationMillis);
    }
}
