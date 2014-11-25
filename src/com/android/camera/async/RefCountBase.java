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
 * Wraps an object with reference counting. When the reference count goes to 0
 * for the first time, the object is closed.
 */
public class RefCountBase<T extends SafeCloseable> implements SafeCloseable {
    private final Object mLock;
    private final T mObject;
    private int mRefCount;

    public RefCountBase(T object, int initialReferenceCount) {
        mLock = new Object();
        mObject = object;
        mRefCount = initialReferenceCount;
    }

    public void addRef() {
        synchronized (mLock) {
            if (mRefCount <= 0) {
                return;
            }
            mRefCount++;
        }
    }

    public T get() {
        return mObject;
    }

    @Override
    public void close() {
        synchronized (mLock) {
            if (mRefCount <= 0) {
                return;
            }
            mRefCount--;
            if (mRefCount > 0) {
                return;
            }
        }
        // Do this outside of the mLock critical section for speed and to avoid
        // deadlock.
        mObject.close();
    }
}
