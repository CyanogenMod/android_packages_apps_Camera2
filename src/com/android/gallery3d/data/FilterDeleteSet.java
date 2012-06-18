/*
 * Copyright (C) 2012 The Android Open Source Project
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

import java.util.ArrayList;

// FilterDeleteSet filters a base MediaSet to remove a deletion item. The user
// can use the following method to change the deletion item:
//
// void setDeletion(Path path, int index);
//
// If the path is null, there is no deletion item.
public class FilterDeleteSet extends MediaSet implements ContentListener {
    private static final String TAG = "FilterDeleteSet";

    private final MediaSet mBaseSet;
    private Path mDeletionPath;
    private int mDeletionIndexHint;
    private boolean mNewDeletionSettingPending = false;

    // This is set to true or false in reload(), so we know if the given
    // mDelectionPath is still in the mBaseSet, and if so we can adjust the
    // index and items.
    private boolean mDeletionInEffect;
    private int mDeletionIndex;

    public FilterDeleteSet(Path path, MediaSet baseSet) {
        super(path, INVALID_DATA_VERSION);
        mBaseSet = baseSet;
        mBaseSet.addContentListener(this);
    }

    @Override
    public String getName() {
        return mBaseSet.getName();
    }

    @Override
    public int getMediaItemCount() {
        if (mDeletionInEffect) {
            return mBaseSet.getMediaItemCount() - 1;
        } else {
            return mBaseSet.getMediaItemCount();
        }
    }

    @Override
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        if (!mDeletionInEffect || mDeletionIndex >= start + count) {
            return mBaseSet.getMediaItem(start, count);
        }
        if (mDeletionIndex < start) {
            return mBaseSet.getMediaItem(start + 1, count);
        }
        ArrayList<MediaItem> base = mBaseSet.getMediaItem(start, count + 1);
        base.remove(mDeletionIndex - start);
        return base;
    }

    @Override
    public long reload() {
        boolean newData = mBaseSet.reload() > mDataVersion;
        if (!newData && !mNewDeletionSettingPending) return mDataVersion;
        mNewDeletionSettingPending = false;
        mDeletionInEffect = false;
        if (mDeletionPath != null) {
            // See if mDeletionPath can be found in the MediaSet. We don't want
            // to search the whole mBaseSet, so we just search a small window
            // that is close the the index hint.
            int n = mBaseSet.getMediaItemCount();
            int from = Math.max(mDeletionIndexHint - 5, 0);
            int to = Math.min(mDeletionIndexHint + 5, n);
            ArrayList<MediaItem> items = mBaseSet.getMediaItem(from, to - from);
            for (int i = 0; i < items.size(); i++) {
                MediaItem item = items.get(i);
                if (item != null && item.getPath() == mDeletionPath) {
                    mDeletionInEffect = true;
                    mDeletionIndex = i + from;
                }
            }
            // We cannot find this path. Set it to null to avoid further search.
            if (!mDeletionInEffect) {
                mDeletionPath = null;
            }
        }
        mDataVersion = nextVersionNumber();
        return mDataVersion;
    }

    @Override
    public void onContentDirty() {
        notifyContentChanged();
    }

    public void setDeletion(Path path, int indexHint) {
        mDeletionPath = path;
        mDeletionIndexHint = indexHint;
        mNewDeletionSettingPending = true;
        notifyContentChanged();
    }
}
