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

/**
 * An interface for state which can be polled for the latest value.
 */
public interface Pollable<T> {
    /**
     * Indicates that no value has been set yet.
     */
    public static class NoValueSetException extends Exception {
    }

    /**
     * Polls for the latest value.
     *
     * @return The latest state, or defaultValue if no state has been set yet.
     */
    public T get(T defaultValue);

    /**
     * Polls for the latest value.
     *
     * @return The latest state.
     * @throws NoValueSetException If no value has been set yet.
     */
    public T get() throws NoValueSetException;
}
