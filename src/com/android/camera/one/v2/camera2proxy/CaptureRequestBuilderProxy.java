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

package com.android.camera.one.v2.camera2proxy;

import android.hardware.camera2.CaptureRequest;
import android.view.Surface;

/**
 * Wraps {@link android.hardware.camera2.CaptureRequest.Builder} with a mockable
 * interface.
 */
public class CaptureRequestBuilderProxy {
    private final CaptureRequest.Builder mBuilder;

    public CaptureRequestBuilderProxy(CaptureRequest.Builder builder) {
        mBuilder = builder;
    }

    /**
     * See {@link CaptureRequest.Builder#addTarget}.
     */
    public void addTarget(Surface outputTarget) {
        mBuilder.addTarget(outputTarget);
    }

    /**
     * See {@link CaptureRequest.Builder#build}.
     */
    public CaptureRequest build() {
        return mBuilder.build();
    }

    /**
     * See {@link CaptureRequest.Builder#get}.
     */
    public <T> T get(CaptureRequest.Key<T> key) throws IllegalArgumentException {
        return mBuilder.get(key);
    }

    /**
     * See {@link CaptureRequest.Builder#removeTarget}.
     */
    public void removeTarget(Surface outputTarget) {
        mBuilder.removeTarget(outputTarget);
    }

    /**
     * See {@link CaptureRequest.Builder#set}.
     */
    public <T> void set(CaptureRequest.Key<T> key, T value) {
        mBuilder.set(key, value);
    }

    /**
     * See {@link CaptureRequest.Builder#setTag}.
     */
    public void setTag(Object tag) {
        mBuilder.setTag(tag);
    }
}
