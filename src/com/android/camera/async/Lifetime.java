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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Enables handling the shut-down of components in a structured way.
 * <p>
 * Lifetimes are nestable sets of {@link SafeCloseable}s useful for guaranteeing
 * that resources, such as threads, files, hardware devices, etc., are properly
 * closed when necessary.
 * <p>
 * Child lifetimes are closed when their parent is closed, or when they are
 * closed directly, whichever comes first. Objects added to a particular
 * lifetime will only ever be closed once by that lifetime.
 * </p>
 */
public class Lifetime implements SafeCloseable {
    /**
     * The parent, or null if there is no parent lifetime.
     */
    private final Lifetime mParent;
    private final Object mLock;
    private final Set<SafeCloseable> mCloseables;
    private boolean mClosed;

    public Lifetime() {
        mLock = new Object();
        mCloseables = new HashSet<SafeCloseable>();
        mParent = null;
        mClosed = false;
    }

    public Lifetime(Lifetime parent) {
        mLock = new Object();
        mCloseables = new HashSet<SafeCloseable>();
        mParent = parent;
        mClosed = false;
        parent.mCloseables.add(this);
    }

    /**
     * Adds the given object to this lifetime and returns it.
     */
    public <T extends SafeCloseable> T add(T closeable) {
        boolean needToClose = false;
        synchronized (mLock) {
            if (mClosed) {
                needToClose = true;
            } else {
                mCloseables.add(closeable);
            }
        }
        if (needToClose) {
            closeable.close();
        }
        return closeable;
    }

    @Override
    public void close() {
        List<SafeCloseable> toClose = new ArrayList<SafeCloseable>();
        synchronized (mLock) {
            if (mClosed) {
                return;
            }
            mClosed = true;
            // Remove from parent to avoid leaking memory if a long-lasting
            // lifetime has lots of shorter-lived lifetimes created and
            // destroyed repeatedly.
            if (mParent != null) {
                mParent.mCloseables.remove(this);
            }
            toClose.addAll(mCloseables);
            mCloseables.clear();
        }
        // Invoke close() outside the critical section
        for (SafeCloseable closeable : toClose) {
            closeable.close();
        }
    }
}
