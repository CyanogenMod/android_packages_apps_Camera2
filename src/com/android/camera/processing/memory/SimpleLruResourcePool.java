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

package com.android.camera.processing.memory;

import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Simple resource based memory pool that can automatically return
 * items back to the memory pool when closed.
 */
@ThreadSafe
public abstract class SimpleLruResourcePool<TKey, TValue> implements LruResourcePool<TKey, TValue> {
    @GuardedBy("mLock")
    private final LruPool<TKey, TValue> mLruPool;

    private final Object mLock;

    public SimpleLruResourcePool(int lruSize) {
        Preconditions.checkArgument(lruSize > 0);

        mLock = new Object();
        mLruPool = new LruPool<>(lruSize);
    }

    @Override
    public Resource<TValue> acquire(TKey key) {
        TValue value;
        synchronized (mLock) {
            value = mLruPool.acquire(key);
        }

        // We may not reach a point where we have have a value to reuse,
        // create a new one.
        if(value == null) {
            value = create(key);
        }

        return new SynchronizedResource<>(this, key, value);
    }

    /**
     * Create a new value for a given key.
     */
    protected abstract TValue create(TKey key);

    /**
     * Recycle or reset a given value before it is added back to the pool,
     * by default, this does nothing.
     */
    protected TValue recycle(TKey key, TValue value) {
        return value;
    }

    /**
     * Returns an item to the LruPool.
     */
    private void release(TKey key, TValue value) {
        mLruPool.add(key, recycle(key, value));
    }

    /**
     * This is a closable resource that returns the underlying value to the pool
     * when the object is closed.
     */
    @ThreadSafe
    private static final class SynchronizedResource<TKey, TValue> implements Resource<TValue> {
        private final Object mLock;
        private final SimpleLruResourcePool<TKey, TValue> mPool;

        @GuardedBy("mLock")
        private TKey mKey;

        @GuardedBy("mLock")
        private TValue mValue;

        public SynchronizedResource(SimpleLruResourcePool<TKey, TValue> pool,
              TKey key, TValue value) {
            mPool = pool;
            mKey = key;
            mValue = value;

            mLock = new Object();
        }

        @Nullable
        @Override
        public TValue get() {
            synchronized (mLock) {
                if (mValue != null) {
                    return mValue;
                }
            }
            return null;
        }

        @Override
        public void close() {
            synchronized (mLock) {
                if (mValue != null) {
                    mPool.release(mKey, mValue);
                    mValue = null;
                    mKey = null;
                }
            }
        }
    }
}
