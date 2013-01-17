/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.gallery3d.util.Future;

import java.util.ArrayList;

// ComboAlbum combines multiple media sets into one. It lists all media items
// from the input albums.
// This only handles SubMediaSets, not MediaItems. (That's all we need now)
public class ComboAlbum extends MediaSet implements ContentListener {
    @SuppressWarnings("unused")
    private static final String TAG = "ComboAlbum";
    private final MediaSet[] mSets;
    private final boolean[] mDirtySets;
    private String mName;

    public ComboAlbum(Path path, MediaSet[] mediaSets, String name) {
        super(path, nextVersionNumber());
        mSets = mediaSets;
        mDirtySets = new boolean[mSets.length];
        for (MediaSet set : mSets) {
            set.addContentListener(this);
        }
        mName = name;
    }

    @Override
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        ArrayList<MediaItem> items = new ArrayList<MediaItem>();
        for (MediaSet set : mSets) {
            int size = set.getMediaItemCount();
            if (count < 1) break;
            if (start < size) {
                int fetchCount = (start + count <= size) ? count : size - start;
                ArrayList<MediaItem> fetchItems = set.getMediaItem(start, fetchCount);
                items.addAll(fetchItems);
                count -= fetchItems.size();
                start = 0;
            } else {
                start -= size;
            }
        }
        return items;
    }

    @Override
    public int getMediaItemCount() {
        int count = 0;
        for (MediaSet set : mSets) {
            count += set.getMediaItemCount();
        }
        return count;
    }

    @Override
    public boolean isLeafAlbum() {
        return true;
    }

    @Override
    public String getName() {
        return mName;
    }

    public void useNameOfChild(int i) {
        if (i < mSets.length) mName = mSets[i].getName();
    }

    @Override
    protected boolean isDirtyLocked() {
        boolean dirty = false;
        for (int i = 0; i < mSets.length; i++) {
            mDirtySets[i] = mSets[i].isDirtyLocked();
            dirty |= mDirtySets[i]
                    || mSets[i].getDataVersion() > getDataVersion();
        }
        return dirty;
    }

    @Override
    protected void load() throws InterruptedException {
        for (int i = 0, n = mSets.length; i < n; ++i) {
            if (mDirtySets[i]) {
                mDirtySets[i] = false;
                mSets[i].load();
            }
        }
    }

    @Override
    public void onContentDirty() {
        notifyContentChanged();
    }

    @Override
    public Future<Integer> requestSync(SyncListener listener) {
        return requestSyncOnMultipleSets(mSets, listener);
    }
}
