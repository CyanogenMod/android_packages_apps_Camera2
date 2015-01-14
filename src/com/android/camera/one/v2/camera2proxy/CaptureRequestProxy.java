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
import android.os.Parcel;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Wraps {@link CaptureRequest}
 */
public class CaptureRequestProxy {
    private final CaptureRequest mRequest;

    public CaptureRequestProxy(@Nonnull CaptureRequest request) {
        mRequest = request;
    }

    @Nullable
    public <T> T get(@Nonnull CaptureRequest.Key<T> key) {
        return mRequest.get(key);
    }

    @Nonnull
    public List<CaptureRequest.Key<?>> getKeys() {
        return mRequest.getKeys();
    }

    @Nullable
    public Object getTag() {
        return mRequest.getTag();
    }

    public boolean equals(Object other) {
        return mRequest.equals(other);
    }

    public int hashCode() {
        return mRequest.hashCode();
    }

    public int describeContents() {
        return mRequest.describeContents();
    }

    public void writeToParcel(Parcel dest, int flags) {
        mRequest.writeToParcel(dest, flags);
    }
}
