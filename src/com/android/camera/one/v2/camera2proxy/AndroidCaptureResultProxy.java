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

package com.android.camera.one.v2.camera2proxy;

import android.hardware.camera2.CaptureResult;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Wraps a {@link android.hardware.camera2.CaptureResult} as a
 * {@link CaptureResultProxy}.
 */
@ParametersAreNonnullByDefault
public class AndroidCaptureResultProxy implements CaptureResultProxy {
    final CaptureResult mCaptureResult;

    public AndroidCaptureResultProxy(CaptureResult captureResult) {
        mCaptureResult = captureResult;
    }

    @Nullable
    public <T> T get(CaptureResult.Key<T> key) {
        return mCaptureResult.get(key);
    }

    @Nonnull
    public List<CaptureResult.Key<?>> getKeys() {
        return mCaptureResult.getKeys();
    }

    @Nonnull
    public CaptureRequestProxy getRequest() {
        return new CaptureRequestProxy(mCaptureResult.getRequest());
    }

    public long getFrameNumber() {
        return mCaptureResult.getFrameNumber();
    }

    public int getSequenceId() {
        return mCaptureResult.getSequenceId();
    }
}
