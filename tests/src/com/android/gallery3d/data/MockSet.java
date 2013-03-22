/*
 * Copyright (C) 2010 The Android Open Source Project
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

public class MockSet extends MediaSet {
    ArrayList<MediaItem> mItems = new ArrayList<MediaItem>();
    ArrayList<MediaSet> mSets = new ArrayList<MediaSet>();
    Path mItemPath;

    public MockSet(Path path, DataManager dataManager) {
        super(path, nextVersionNumber());
        mItemPath = Path.fromString("/mock/item");
    }

    public MockSet(Path path, DataManager dataManager,
            int items, int item_id_start) {
        this(path, dataManager);
        for (int i = 0; i < items; i++) {
            Path childPath = mItemPath.getChild(item_id_start + i);
            mItems.add(new MockItem(childPath));
        }
    }

    public void addMediaSet(MediaSet sub) {
        mSets.add(sub);
    }

    @Override
    public int getMediaItemCount() {
        return mItems.size();
    }

    @Override
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        ArrayList<MediaItem> result = new ArrayList<MediaItem>();
        int end = Math.min(start + count, mItems.size());

        for (int i = start; i < end; i++) {
            result.add(mItems.get(i));
        }
        return result;
    }

    @Override
    public int getSubMediaSetCount() {
        return mSets.size();
    }

    @Override
    public MediaSet getSubMediaSet(int index) {
        return mSets.get(index);
    }

    @Override
    public int getTotalMediaItemCount() {
        int result = mItems.size();
        for (MediaSet s : mSets) {
            result += s.getTotalMediaItemCount();
        }
        return result;
    }

    @Override
    public String getName() {
        return "Set " + mPath;
    }

    @Override
    public long reload() {
        return 0;
    }
}
