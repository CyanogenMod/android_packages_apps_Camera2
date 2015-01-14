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

import javax.annotation.Nonnull;

/**
 * Wraps an {@link com.android.camera.async.Updatable} by filtering out
 * duplicate updates.
 */
public class FilteredUpdatable<T> implements Updatable<T> {
    private final Updatable<T> mUpdatable;
    private final Object mLock;
    private boolean mValueSet;
    private T mLatestValue;

    public FilteredUpdatable(Updatable<T> updatable) {
        mUpdatable = updatable;
        mLock = new Object();
        mValueSet = false;
        mLatestValue = null;
    }

    @Override
    public void update(@Nonnull T t) {
        synchronized (mLock) {
            if (!mValueSet) {
                setNewValue(t);
            } else {
                if (t == null && mLatestValue != null) {
                    setNewValue(t);
                } else if (t != null) {
                    if (!t.equals(mLatestValue)) {
                        setNewValue(t);
                    }
                }
            }
        }
    }

    private void setNewValue(T value) {
        synchronized (mLock) {
            mUpdatable.update(value);
            mLatestValue = value;
            mValueSet = true;
        }
    }
}
