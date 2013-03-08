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

package com.android.photos.shims;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.drawable.Drawable;

import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.MediaSet.SyncListener;
import com.android.gallery3d.util.Future;
import com.android.photos.data.AlbumSetLoader;
import com.android.photos.drawables.DrawableFactory;

import java.util.ArrayList;

/**
 * Returns all MediaSets in a MediaSet, wrapping them in a cursor to appear
 * like a AlbumSetLoader.
 */
public class MediaSetLoader extends AsyncTaskLoader<Cursor> implements DrawableFactory<Cursor>{

    private static final SyncListener sNullListener = new SyncListener() {
        @Override
        public void onSyncDone(MediaSet mediaSet, int resultCode) {
        }
    };

    private final MediaSet mMediaSet;
    private Future<Integer> mSyncTask = null;
    private ContentListener mObserver = new ContentListener() {
        @Override
        public void onContentDirty() {
            onContentChanged();
        }
    };

    private ArrayList<MediaItem> mCoverItems;

    public MediaSetLoader(Context context) {
        super(context);
        DataManager dm = DataManager.from(context);
        String path = dm.getTopSetPath(DataManager.INCLUDE_ALL);
        mMediaSet = dm.getMediaSet(path);
    }

    public MediaSetLoader(Context context, String path) {
        super(context);
        mMediaSet = DataManager.from(getContext()).getMediaSet(path);
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();
        mMediaSet.addContentListener(mObserver);
        mSyncTask = mMediaSet.requestSync(sNullListener);
        forceLoad();
    }

    @Override
    protected boolean onCancelLoad() {
        if (mSyncTask != null) {
            mSyncTask.cancel();
            mSyncTask = null;
        }
        return super.onCancelLoad();
    }

    @Override
    protected void onStopLoading() {
        super.onStopLoading();
        cancelLoad();
        mMediaSet.removeContentListener(mObserver);
    }

    @Override
    protected void onReset() {
        super.onReset();
        onStopLoading();
    }

    @Override
    public Cursor loadInBackground() {
        mMediaSet.loadIfDirty();
        final MatrixCursor cursor = new MatrixCursor(AlbumSetLoader.PROJECTION);
        final Object[] row = new Object[AlbumSetLoader.PROJECTION.length];
        int count = mMediaSet.getSubMediaSetCount();
        ArrayList<MediaItem> coverItems = new ArrayList<MediaItem>(count);
        for (int i = 0; i < count; i++) {
            MediaSet m = mMediaSet.getSubMediaSet(i);
            m.loadIfDirty();
            row[AlbumSetLoader.INDEX_ID] = i;
            row[AlbumSetLoader.INDEX_TITLE] = m.getName();
            row[AlbumSetLoader.INDEX_COUNT] = m.getMediaItemCount();
            MediaItem coverItem = m.getCoverMediaItem();
            if (coverItem != null) {
                row[AlbumSetLoader.INDEX_TIMESTAMP] = coverItem.getDateInMs();
            }
            coverItems.add(coverItem);
            cursor.addRow(row);
        }
        synchronized (mMediaSet) {
            mCoverItems = coverItems;
        }
        return cursor;
    }

    @Override
    public Drawable drawableForItem(Cursor item, Drawable recycle) {
        BitmapJobDrawable drawable = null;
        if (recycle == null || !(recycle instanceof BitmapJobDrawable)) {
            drawable = new BitmapJobDrawable();
        } else {
            drawable = (BitmapJobDrawable) recycle;
        }
        int index = item.getInt(AlbumSetLoader.INDEX_ID);
        drawable.setMediaItem(mCoverItems.get(index));
        return drawable;
    }

    public static int getThumbnailSize() {
        return MediaItem.getTargetSize(MediaItem.TYPE_MICROTHUMBNAIL);
    }
}
