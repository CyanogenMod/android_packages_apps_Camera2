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

import com.android.camera.async.MainThread;

/**
 * Basic profiler that will compute start, end and "time since last event"
 * values and pass them along to the subclass. This also provides
 * standard formatting methods for messages to keep them consistent.
 */
public abstract class ProfileBase implements Profile {
    private final String mName;

    private long mStartNanos;
    private long mLastMark;

    /** Create a new profile for a given name. */
    public ProfileBase(String name) {
        mName = name;
    }

    @Override
    public final Profile start() {
        mStartNanos = System.nanoTime();
        mLastMark = mStartNanos;
        onStart();

        return this;
    }

    @Override
    public final void mark() {
        mLastMark = System.nanoTime();
        // In most cases this will only be used to reset the lastMark time.
    }

    @Override
    public final void mark(String reason) {
        long time = System.nanoTime();
        onMark(getTotalMillis(time), getTimeFromLastMillis(time), reason);
        mLastMark = time;
    }

    @Override
    public final void stop() {
        long time = System.nanoTime();
        onStop(getTotalMillis(time), getTimeFromLastMillis(time));
        mLastMark = time;
    }

    @Override
    public final void stop(String reason) {
        long time = System.nanoTime();
        onStop(getTotalMillis(time), getTimeFromLastMillis(time), reason);
        mLastMark = time;
    }

    /**
     * Format a simple message with the total elapsed time and a simple event.
     */
    protected final String format(double totalMillis, String event) {
        return String.format("[%7sms]%s %-6s %s",
              String.format("%.3f", totalMillis),
              MainThread.isMainThread() ? "[ui]" : "",
              event + ":",
              mName);
    }

    /**
     * Format a simple message with the total elapsed time, a simple event,
     * and a string reason at the end.
     */
    protected final String format(double totalMillis, String event, String reason) {
        return String.format("[%7sms]%s %-6s %s - %s",
              String.format("%.3f", totalMillis),
              MainThread.isMainThread() ? "[ui]" : "",
              event + ":",
              mName,
              reason);
    }

    /**
     * Format a simple message with the total elapsed time, a simple event,
     * a time since last event, and a string reason.
     */
    protected final String format(double totalMillis, String event, double lastMillis, String reason) {
        return String.format("[%7sms]%s %-6s %s - [%6sms] %s",
              String.format("%.3f", totalMillis),
              MainThread.isMainThread() ? "[ui]" : "",
              event + ":",
              mName,
              String.format("%.3f", lastMillis),
              reason);
    }

    /**
     * Called when start() is called.
     */
    protected void onStart() { }

    /**
     * Called when mark() is called with computed total and time
     * since last event values in milliseconds.
     */
    protected void onMark(double totalMillis, double lastMillis, String reason) { }

    /**
     * Called when stop() is called with computed total and time
     * since last event values in milliseconds.
     */
    protected void onStop(double totalMillis, double lastMillis) { }

    /**
     * Called when stop() is called with computed total and time
     * since last event values in milliseconds. Inclues the stop reason.
     */
    protected void onStop(double totalMillis, double lastMillis, String reason) { }

    private double getTotalMillis(long timeNanos) {
        return nanoToMillis(timeNanos - mStartNanos);
    }

    private double getTimeFromLastMillis(long timeNanos) {
        return nanoToMillis(timeNanos - mLastMark);
    }

    private double nanoToMillis(long timeNanos) {
        return (double)(timeNanos) / 1000000.0;
    }
}
