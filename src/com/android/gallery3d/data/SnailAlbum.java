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

// This is a simple MediaSet which contains only one MediaItem -- a SnailItem.
public class SnailAlbum extends MediaSet {

    private MediaItem mItem;

    public SnailAlbum(Path path, MediaItem item) {
        super(path, nextVersionNumber());
        mItem = item;
    }

    @Override
    public int getMediaItemCount() {
        return 1;
    }

    @Override
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        ArrayList<MediaItem> result = new ArrayList<MediaItem>();

        // If [start, start+count) contains the index 0, return the item.
        if (start <= 0 && start + count > 0) {
            result.add(mItem);
        }

        return result;
    }

    @Override
    public boolean isLeafAlbum() {
        return true;
    }

    @Override
    public String getName() {
        return "SnailAlbum";
    }

    @Override
    public long reload() {
        return mDataVersion;
    }
}
