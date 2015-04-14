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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

import javax.annotation.concurrent.GuardedBy;

/**
 * LruPool that will evict items from the pool after reaching maximum size in
 * order of Least Recently Used (LRU). This code is based on the Android
 * Lru implementation but removes the hard requirement that keys must only
 * exist once. Different values may be returned for the same key, and there is
 * no guarantee that adding and then immediately acquiring the same key will
 * return the same value instance.
 *
 * The size of the pool is generally equal to the number of items, but can be
 * reconfigured by a subclass to be proportional to some other computed value.
 *
 * This class has multiple moving parts and should only be use to track and
 * reuse objects that are expensive to create or re-create.
 *
 * WARNING:
 * {@link #acquire(TKey)} is currently linear time, pending a better
 * implementation.
 *
 * TODO: Build a constant time acquire(TKey) method implementation.
 *
 */
public class LruPool<TKey, TValue> {
    public static class Configuration<TKey, TValue> {
        /**
         * Called for entries that have been evicted or removed. This method is
         * invoked when a value is evicted to make space, but NOT when an item is
         * removed via {@link #acquire}. The default
         * implementation does nothing.
         *
         * <p>The method is called without synchronization: other threads may
         * access the cache while this method is executing.
         */
        void entryEvicted(TKey key, TValue value) { }

        /**
         * Called after a cache miss to compute a value for the corresponding key.
         * Returns the computed value or null if no value can be computed. The
         * default implementation returns null.
         *
         * <p>The method is called without synchronization: other threads may
         * access the cache while this method is executing.
         */
        TValue create(TKey key) {
            return null;
        }

        /**
         * Returns the size of the entry for {@code key} and {@code value} in
         * user-defined units.  The default implementation returns 1 so that size
         * is the number of entries and max size is the maximum number of entries.
         *
         * <p>An entry's size must not change while it is in the cache.
         */
        int sizeOf(TKey key, TValue value) {
            return 1;
        }
    }

    private final Object mLock;

    /**
     * Maintains an ordered list of keys by "most recently added". Duplicate
     * keys can exist in the list.
     */
    @GuardedBy("mLock")
    private final LinkedList<TKey> mLruKeyList;

    /**
     * Maintains individual pools for each distinct key type.
     */
    @GuardedBy("mLock")
    private final HashMap<TKey, Queue<TValue>> mValuePool;
    private final Configuration<TKey, TValue> mConfiguration;

    private final int mMaxSize;

    /**
     * Size may be configured to represent quantities other than the number of
     * items in the pool. By default, it represents the number of items
     * in the pool.
     */
    @GuardedBy("mLock")
    private int mSize;

    /**
     * Creates and sets the size of the Lru Pool
     *
     * @param maxSize Sets the size of the Lru Pool.
     */
    public LruPool(int maxSize) {
        this(maxSize, new Configuration<TKey, TValue>());
    }

    public LruPool(int maxSize, Configuration<TKey, TValue> configuration) {
        Preconditions.checkArgument(maxSize > 0, "maxSize must be > 0.");

        mMaxSize = maxSize;
        mConfiguration = configuration;

        mLock = new Object();
        mLruKeyList = new LinkedList<>();
        mValuePool = new HashMap<>();
    }

    /**
     * Acquire a value from the pool, or attempt to create a new one if the create
     * method is overridden. If an item cannot be retrieved or created, this method
     * will return null.
     *
     * WARNING:
     * This implementation is currently linear time, pending a better
     * implementation.
     *
     * TODO: Build a constant time acquire(TKey) method implementation.
     *
     * @param key the type of object to retrieve from the pool.
     * @return a value or null if none exists or can be created.
     */
    public final TValue acquire(TKey key) {
        Preconditions.checkNotNull(key);

        // We must remove the item we acquire from the list
        TValue value;

        synchronized (mLock) {
            if (mLruKeyList.removeLastOccurrence(key)) {
                value = mValuePool.get(key).remove();
                mSize -= checkedSizeOf(key, value);
            } else {
                value = mConfiguration.create(key);
            }
        }

        return value;
    }

    /**
     * Add a new or previously existing value to the pool. The most recently added
     * item will be placed at the top of the Lru list, and will trim existing items
     * off the list, if the list exceeds the maximum size.
     *
     * @param key the type of object to add to the pool.
     * @param value the object to add into the pool.
     */
    public final void add(TKey key, TValue value) {
        Preconditions.checkNotNull(key);
        Preconditions.checkNotNull(value);

        synchronized (mLock) {
            final Queue<TValue> pool;

            mLruKeyList.push(key);
            if (!mValuePool.containsKey(key)) {
                pool = new LinkedList<>();
                mValuePool.put(key, pool);
            } else {
                pool = mValuePool.get(key);
            }
            pool.add(value);
            mSize += checkedSizeOf(key, value);

            unsafeTrimToSize(mMaxSize);
        }
    }

    /**
     * Remove the oldest entries until the total of remaining entries is at or
     * below the configured size.
     *
     * @param trimToSize the maximum size of the cache before returning. May
     *                   be -1 to evict even 0-sized elements.
     */
    public final void trimToSize(int trimToSize) {
        synchronized (mLock) {
            unsafeTrimToSize(trimToSize);
        }
    }

    /**
     * For pools that do not override {@link Configuration#sizeOf}, this
     * returns the number of items in the pool. For custom sizes, this returns
     * the sum of the sizes of the entries in this pool.
     */
    public final int getSize() {
        synchronized (mLock) {
            return mSize;
        }
    }

    /**
     * For pools that do not override {@link Configuration#sizeOf}, this
     * returns the maximum number of entries in the pool. For all other pools,
     * this returns the maximum sum of the sizes of the entries in this pool.
     */
    public final int getMaxSize() {
        return mMaxSize;
    }

    @GuardedBy("mLock")
    private void unsafeTrimToSize(int trimToSize) {
        while (mSize > trimToSize && !mLruKeyList.isEmpty()) {
            TKey key = mLruKeyList.removeLast();
            if (key == null) {
                break;
            }

            Queue<TValue> pool = mValuePool.get(key);
            TValue value = pool.remove();

            if (pool.size() <= 0) {
                mValuePool.remove(key);
            }

            mSize = mSize - checkedSizeOf(key, value);
            mConfiguration.entryEvicted(key, value);
        }

        if (mSize < 0 || (mLruKeyList.isEmpty() && mSize != 0)) {
            throw new IllegalStateException("LruPool.sizeOf() is reporting "
                  + "inconsistent results!");
        }
    }

    private int checkedSizeOf(TKey key, TValue value) {
        int result = mConfiguration.sizeOf(key, value);
        Preconditions.checkArgument(result >= 0, "Size was < 0.");
        return result;
    }
}
