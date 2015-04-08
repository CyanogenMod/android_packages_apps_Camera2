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
 * Creates new handlers backed by threads with a specified lifetime.
 */
public class HandlerFactory {
    /**
     * @param lifetime The lifetime of the associated handler's thread.
     * @param threadName The name to assign to the created thread.
     * @return A handler backed by a new thread.
     */
    public Handler create(Lifetime lifetime, String threadName) {
        final HandlerThread thread = new HandlerThread(threadName);
        thread.start();

        lifetime.add(new SafeCloseable() {
            @Override
            public void close() {
                thread.quitSafely();
            }
        });

        return new Handler(thread.getLooper());
    }

    /**
     * @param lifetime The lifetime of the associated handler's thread.
     * @param threadName The name to assign to the created thread.
     * @param javaThreadPriority The Java thread priority to use for this thread.
     * @return A handler backed by a new thread.
     */
    public Handler create(Lifetime lifetime, String threadName, int javaThreadPriority) {
        final HandlerThread thread = new HandlerThread(threadName);
        thread.start();
        thread.setPriority(javaThreadPriority);

        lifetime.add(new SafeCloseable() {
            @Override
            public void close() {
                thread.quitSafely();
            }
        });

        return new Handler(thread.getLooper());
    }
}
