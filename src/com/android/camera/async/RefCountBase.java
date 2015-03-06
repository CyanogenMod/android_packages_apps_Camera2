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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

/**
 * Wraps an object with reference counting. When the reference count goes to 0
 * for the first time, the object is closed.
 */
public class RefCountBase<T extends SafeCloseable> implements SafeCloseable {
    private final Object mLock;
    private final T mObject;
    private int mRefCount;
    private boolean mObjectClosed;

    public RefCountBase(T object) {
        this(object, 1);
    }

    public RefCountBase(T object, int initialReferenceCount) {
        Preconditions.checkState(
                initialReferenceCount > 0, "initialReferenceCount is not greater than 0.");
        mLock = new Object();
        mObject = object;
        mRefCount = initialReferenceCount;
        mObjectClosed = false;
    }

    public void addRef() {
        synchronized (mLock) {
            Preconditions.checkState(!mObjectClosed,
                    "addRef on an object which has been closed.");
            mRefCount++;
        }
    }

    public T get() {
        return mObject;
    }

    @Override
    public void close() {
        synchronized (mLock) {
            // A SafeCloseable must tolerate multiple calls to close().
            if (mObjectClosed) {
                return;
            }
            mRefCount--;
            if (mRefCount > 0) {
                return;
            }
            mObjectClosed = true;
        }
        // Do this outside of the mLock critical section for speed and to avoid
        // deadlock.
        mObject.close();
    }

    @VisibleForTesting
    public int getRefCount() {
        synchronized (mLock) {
            return mRefCount;
        }
    }
}
