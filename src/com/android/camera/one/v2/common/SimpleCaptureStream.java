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

package com.android.camera.one.v2.common;

import android.view.Surface;

import com.android.camera.async.BufferQueue;
import com.android.camera.one.v2.core.CaptureStream;

/**
 * A {@link CaptureStream} which just registers a {@link Surface} without
 * providing any filtering of the images output to it.
 */
public class SimpleCaptureStream implements CaptureStream {
    private final Surface mSurface;

    public SimpleCaptureStream(Surface surface) {
        mSurface = surface;
    }

    @Override
    public Surface bind(BufferQueue<Long> timestamps) throws InterruptedException {
        // Close the stream to avoid leaking the timestamp for every image of a
        // repeating request.
        timestamps.close();
        return mSurface;
    }
}
