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

package com.android.camera.async;

import android.os.Handler;
import android.os.HandlerThread;

/**
 * Creates a new Handler thread which can be safely destroyed when the object is
 * closed.
 */
public class CloseableHandlerThread implements SafeCloseable {
    private final HandlerThread mThread;
    private final Handler mHandler;

    public CloseableHandlerThread(String threadName) {
        mThread = new HandlerThread(threadName);
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
    }

    public Handler get() {
        return mHandler;
    }

    @Override
    public void close() {
        mThread.quitSafely();
    }
}
