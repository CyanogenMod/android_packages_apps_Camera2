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
 * Wraps {@link ConcurrentState} with {@link #setCallback} semantics which
 * overwrite any existing callback.
 */
public class Listenable<T> implements SafeCloseable {
    private final ConcurrentState<T> mState;
    private final MainThread mMainThread;
    private final Object mLock;
    private boolean mClosed;
    private SafeCloseable mExistingCallbackHandle;

    public Listenable(ConcurrentState<T> state, MainThread mainThread) {
        mState = state;
        mMainThread = mainThread;
        mLock = new Object();
        mClosed = false;
        mExistingCallbackHandle = null;
    }

    /**
     * Sets the callback, removing any existing callback first.
     */
    public void setCallback(final Callback<T> callback) {
        synchronized (mLock) {
            if (mClosed) {
                return;
            }
            if (mExistingCallbackHandle != null) {
                // Unregister any existing callback
                mExistingCallbackHandle.close();
            }
            mExistingCallbackHandle = mState.addCallback(new Runnable() {
                @Override
                public void run() {
                    callback.onCallback(mState.get());
                }
            }, mMainThread);
        }
    }

    /**
     * Removes any existing callbacks.
     */
    public void clear() {
        synchronized (mLock) {
            mClosed = true;
            if (mExistingCallbackHandle != null) {
                // Unregister any existing callback
                mExistingCallbackHandle.close();
            }
        }
    }

    /**
     * Removes any existing callbacks.  Once closed, no more callbacks will be registered.
     */
    @Override
    public void close() {
        synchronized (mLock) {
            mClosed = true;
            clear();
        }
    }
}
