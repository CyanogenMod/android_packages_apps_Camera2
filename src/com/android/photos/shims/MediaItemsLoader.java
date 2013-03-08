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
import android.provider.MediaStore.Files.FileColumns;
import android.util.SparseArray;

import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.MediaSet.ItemConsumer;
import com.android.gallery3d.data.MediaSet.SyncListener;
import com.android.gallery3d.util.Future;
import com.android.photos.data.PhotoSetLoader;
import com.android.photos.drawables.DrawableFactory;

/**
 * Returns all MediaItems in a MediaSet, wrapping them in a cursor to appear
 * like a PhotoSetLoader
 */
public class MediaItemsLoader extends AsyncTaskLoader<Cursor> implements DrawableFactory<Cursor> {

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
    private SparseArray<MediaItem> mMediaItems;

    public MediaItemsLoader(Context context) {
        super(context);
        DataManager dm = DataManager.from(context);
        String path = dm.getTopSetPath(DataManager.INCLUDE_ALL);
        mMediaSet = dm.getMediaSet(path);
    }

    public MediaItemsLoader(Context context, String parentPath) {
        super(context);
        mMediaSet = DataManager.from(getContext()).getMediaSet(parentPath);
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
        final MatrixCursor cursor = new MatrixCursor(PhotoSetLoader.PROJECTION);
        final Object[] row = new Object[PhotoSetLoader.PROJECTION.length];
        final SparseArray<MediaItem> mediaItems = new SparseArray<MediaItem>();
        mMediaSet.enumerateTotalMediaItems(new ItemConsumer() {
            @Override
            public void consume(int index, MediaItem item) {
                row[PhotoSetLoader.INDEX_ID] = index;
                row[PhotoSetLoader.INDEX_DATA] = item.getContentUri().toString();
                row[PhotoSetLoader.INDEX_DATE_ADDED] = item.getDateInMs();
                row[PhotoSetLoader.INDEX_HEIGHT] = item.getHeight();
                row[PhotoSetLoader.INDEX_WIDTH] = item.getWidth();
                row[PhotoSetLoader.INDEX_WIDTH] = item.getWidth();
                int rawMediaType = item.getMediaType();
                int mappedMediaType = FileColumns.MEDIA_TYPE_NONE;
                if (rawMediaType == MediaItem.MEDIA_TYPE_IMAGE) {
                    mappedMediaType = FileColumns.MEDIA_TYPE_IMAGE;
                } else if (rawMediaType == MediaItem.MEDIA_TYPE_VIDEO) {
                    mappedMediaType = FileColumns.MEDIA_TYPE_VIDEO;
                }
                row[PhotoSetLoader.INDEX_MEDIA_TYPE] = mappedMediaType;
                cursor.addRow(row);
                mediaItems.append(index, item);
            }
        });
        synchronized (mMediaSet) {
            mMediaItems = mediaItems;
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
        int index = item.getInt(PhotoSetLoader.INDEX_ID);
        drawable.setMediaItem(mMediaItems.get(index));
        return drawable;
    }

    public static int getThumbnailSize() {
        return MediaItem.getTargetSize(MediaItem.TYPE_MICROTHUMBNAIL);
    }

}
