/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.gallery3d.data;

public abstract class MediaSetWrapper extends MediaSet {

    private MediaSet mWrappedSet;
    private boolean mWrappedIsDirty;

    public MediaSetWrapper(MediaSet wrappedSet, Path path, long version) {
        super(path, version);
        mWrappedSet = wrappedSet;
    }

    @Override
    protected boolean isDirtyLocked() {
        mWrappedIsDirty = mWrappedSet.isDirtyLocked();
        return mWrappedIsDirty;
    }

    @Override
    protected void load() throws InterruptedException {
        if (mWrappedIsDirty) {
            mWrappedSet.load();
        }
    }

    @Override
    public long getDataVersion() {
        return mWrappedSet.getDataVersion();
    }

}
