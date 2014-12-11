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

import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;

import com.android.camera.async.Updatable;
import com.android.camera.one.v2.core.ResponseListener;

/**
 * A {@link ResponseListener} which listens for a particular metadata key.
 */
public class MetadataResponseListener<V> extends ResponseListener {
    private final Updatable<V> mUpdatable;
    private final CaptureResult.Key<V> mKey;

    /**
     * @param key The key associated with the value for which to listen to
     *            changes.
     */
    public MetadataResponseListener(CaptureResult.Key<V> key, Updatable<V> updatable) {
        mKey = key;
        mUpdatable = updatable;
    }

    @Override
    public void onProgressed(CaptureResult partialResult) {
        V newValue = partialResult.get(mKey);
        if (newValue != null) {
            mUpdatable.update(newValue);
        }
    }

    @Override
    public void onCompleted(TotalCaptureResult totalCaptureResult) {
        V newValue = totalCaptureResult.get(mKey);
        if (newValue != null) {
            mUpdatable.update(newValue);
        }
    }
}
