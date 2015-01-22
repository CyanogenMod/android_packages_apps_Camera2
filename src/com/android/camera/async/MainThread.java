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

import static com.google.common.base.Preconditions.checkState;

import android.os.Handler;
import android.os.Looper;

import com.google.common.annotations.VisibleForTesting;

import javax.annotation.Nonnull;

public class MainThread extends HandlerExecutor {
    private MainThread(Handler handler) {
        super(handler);
    }

    public static MainThread create() {
        return new MainThread(new Handler(Looper.getMainLooper()));
    }

    /**
     * Caches whether or not the current thread is the main thread.
     */
    private static final ThreadLocal<Boolean> sIsMainThread = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Looper.getMainLooper().getThread() == Thread.currentThread();
        }
    };

    /**
     * Asserts that the current thread is the main thread.
     */
    public static void checkMainThread() {
        checkState(sIsMainThread.get(), "Not main thread.");
    }

    /**
     * Returns true if the method is run on the main android thread.
     */
    public static boolean isMainThread() {
        return sIsMainThread.get();
    }

    /**
     * Returns a fake MainThreadExecutor which executes immediately.
     */
    @VisibleForTesting
    public static MainThread createFakeForTesting() {
        return new MainThread(null) {
            @Override
            public void execute(@Nonnull Runnable runnable) {
                //
                sIsMainThread.set(true);
                try {
                    runnable.run();
                } finally {
                    sIsMainThread.set(false);
                }
            }
        };
    }
}
