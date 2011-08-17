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

import com.android.gallery3d.util.Future;

import java.util.ArrayList;
import java.util.WeakHashMap;

// MediaSet is a directory-like data structure.
// It contains MediaItems and sub-MediaSets.
//
// The primary interface are:
// getMediaItemCount(), getMediaItem() and
// getSubMediaSetCount(), getSubMediaSet().
//
// getTotalMediaItemCount() returns the number of all MediaItems, including
// those in sub-MediaSets.
public abstract class MediaSet extends MediaObject {
    public static final int MEDIAITEM_BATCH_FETCH_COUNT = 500;
    public static final int INDEX_NOT_FOUND = -1;

    public MediaSet(Path path, long version) {
        super(path, version);
    }

    public int getMediaItemCount() {
        return 0;
    }

    // Returns the media items in the range [start, start + count).
    //
    // The number of media items returned may be less than the specified count
    // if there are not enough media items available. The number of
    // media items available may not be consistent with the return value of
    // getMediaItemCount() because the contents of database may have already
    // changed.
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        return new ArrayList<MediaItem>();
    }

    public int getSubMediaSetCount() {
        return 0;
    }

    public MediaSet getSubMediaSet(int index) {
        throw new IndexOutOfBoundsException();
    }

    public boolean isLeafAlbum() {
        return false;
    }

    public int getTotalMediaItemCount() {
        int total = getMediaItemCount();
        for (int i = 0, n = getSubMediaSetCount(); i < n; i++) {
            total += getSubMediaSet(i).getTotalMediaItemCount();
        }
        return total;
    }

    // TODO: we should have better implementation of sub classes
    public int getIndexOfItem(Path path, int hint) {
        // hint < 0 is handled below
        // first, try to find it around the hint
        int start = Math.max(0,
                hint - MEDIAITEM_BATCH_FETCH_COUNT / 2);
        ArrayList<MediaItem> list = getMediaItem(
                start, MEDIAITEM_BATCH_FETCH_COUNT);
        int index = getIndexOf(path, list);
        if (index != INDEX_NOT_FOUND) return start + index;

        // try to find it globally
        start = start == 0 ? MEDIAITEM_BATCH_FETCH_COUNT : 0;
        list = getMediaItem(start, MEDIAITEM_BATCH_FETCH_COUNT);
        while (true) {
            index = getIndexOf(path, list);
            if (index != INDEX_NOT_FOUND) return start + index;
            if (list.size() < MEDIAITEM_BATCH_FETCH_COUNT) return INDEX_NOT_FOUND;
            start += MEDIAITEM_BATCH_FETCH_COUNT;
            list = getMediaItem(start, MEDIAITEM_BATCH_FETCH_COUNT);
        }
    }

    protected int getIndexOf(Path path, ArrayList<MediaItem> list) {
        for (int i = 0, n = list.size(); i < n; ++i) {
            if (list.get(i).mPath == path) return i;
        }
        return INDEX_NOT_FOUND;
    }

    public abstract String getName();

    private WeakHashMap<ContentListener, Object> mListeners =
            new WeakHashMap<ContentListener, Object>();

    // NOTE: The MediaSet only keeps a weak reference to the listener. The
    // listener is automatically removed when there is no other reference to
    // the listener.
    public void addContentListener(ContentListener listener) {
        if (mListeners.containsKey(listener)) {
            throw new IllegalArgumentException();
        }
        mListeners.put(listener, null);
    }

    public void removeContentListener(ContentListener listener) {
        if (!mListeners.containsKey(listener)) {
            throw new IllegalArgumentException();
        }
        mListeners.remove(listener);
    }

    // This should be called by subclasses when the content is changed.
    public void notifyContentChanged() {
        for (ContentListener listener : mListeners.keySet()) {
            listener.onContentDirty();
        }
    }

    // Reload the content. Return the current data version. reload() should be called
    // in the same thread as getMediaItem(int, int) and getSubMediaSet(int).
    public abstract long reload();

    @Override
    public MediaDetails getDetails() {
        MediaDetails details = super.getDetails();
        details.addDetail(MediaDetails.INDEX_TITLE, getName());
        return details;
    }

    // Enumerate all media items in this media set (including the ones in sub
    // media sets), in an efficient order. ItemConsumer.consumer() will be
    // called for each media item with its index.
    public void enumerateMediaItems(ItemConsumer consumer) {
        enumerateMediaItems(consumer, 0);
    }

    public void enumerateTotalMediaItems(ItemConsumer consumer) {
        enumerateTotalMediaItems(consumer, 0);
    }

    public static interface ItemConsumer {
        void consume(int index, MediaItem item);
    }

    // The default implementation uses getMediaItem() for enumerateMediaItems().
    // Subclasses may override this and use more efficient implementations.
    // Returns the number of items enumerated.
    protected int enumerateMediaItems(ItemConsumer consumer, int startIndex) {
        int total = getMediaItemCount();
        int start = 0;
        while (start < total) {
            int count = Math.min(MEDIAITEM_BATCH_FETCH_COUNT, total - start);
            ArrayList<MediaItem> items = getMediaItem(start, count);
            for (int i = 0, n = items.size(); i < n; i++) {
                MediaItem item = items.get(i);
                consumer.consume(startIndex + start + i, item);
            }
            start += count;
        }
        return total;
    }

    // Recursively enumerate all media items under this set.
    // Returns the number of items enumerated.
    protected int enumerateTotalMediaItems(
            ItemConsumer consumer, int startIndex) {
        int start = 0;
        start += enumerateMediaItems(consumer, startIndex);
        int m = getSubMediaSetCount();
        for (int i = 0; i < m; i++) {
            start += getSubMediaSet(i).enumerateTotalMediaItems(
                    consumer, startIndex + start);
        }
        return start;
    }

    public Future<Void> requestSync() {
        return FUTURE_STUB;
    }

    private static final Future<Void> FUTURE_STUB = new Future<Void>() {
        @Override
        public void cancel() {}

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Void get() {
            return null;
        }

        @Override
        public void waitDone() {}
    };
}
