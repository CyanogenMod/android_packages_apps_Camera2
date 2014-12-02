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

import com.android.camera.util.Callback;

/**
 * Note: This interface, alone, does not provide a means of guaranteeing which
 * thread the callback will be invoked on. Use
 * {@link com.android.camera.async.ConcurrentState}, {@link BufferQueue}, or
 * {@link java.util.concurrent.Future} instead to guarantee thread-safety.
 */
public interface Listenable<T> extends SafeCloseable {
    /**
     * Sets the callback, removing any existing callback first.
     */
    public void setCallback(Callback<T> callback);

    /**
     * Removes any existing callback.
     */
    @Override
    public void close();
}
