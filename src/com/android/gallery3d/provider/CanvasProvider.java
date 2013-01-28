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

package com.android.gallery3d.provider;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.util.Log;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.MediaSet.SyncListener;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.ThreadPool.CancelListener;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.google.android.canvas.data.Cluster;
import com.google.android.canvas.provider.CanvasContract;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CanvasProvider extends ContentProvider {

    private static final String TAG = "GalleryCanvasProvider";

    private static final String AUTHORITY = "com.android.gallery3d.provider.CanvasProvider";

    public static Uri NOTIFY_CHANGED_URI = Uri.parse("content://" + AUTHORITY);

    private static final String PATH_IMAGE = "image";

    private static final String PATH_LAUNCHER = "launcher";
    private static final String PATH_LAUNCHER_ITEM = PATH_LAUNCHER + "/"
            + CanvasContract.PATH_LAUNCHER_ITEM;
    private static final String PATH_BROWSE = "browse";
    private static final String PATH_BROWSE_HEADERS = PATH_BROWSE + "/"
            + CanvasContract.PATH_BROWSE_HEADERS;

    private static final int LAUNCHER = 1;
    private static final int LAUNCHER_ITEMS = 2;
    private static final int LAUNCHER_ITEM_ID = 3;
    private static final int BROWSE_HEADERS = 4;
    private static final int BROWSE = 5;
    private static final int IMAGE = 6;

    private static final Uri BROWSER_ROOT_URI = Uri.parse("content://" + AUTHORITY + "/" + PATH_BROWSE);

    private static final UriMatcher sUriMatcher = new UriMatcher(
            UriMatcher.NO_MATCH);

    static {
        sUriMatcher.addURI(AUTHORITY, PATH_LAUNCHER, LAUNCHER);
        sUriMatcher.addURI(AUTHORITY, PATH_LAUNCHER_ITEM, LAUNCHER_ITEMS);
        sUriMatcher.addURI(AUTHORITY, PATH_LAUNCHER_ITEM + "/#",
                LAUNCHER_ITEM_ID);
        sUriMatcher.addURI(AUTHORITY, PATH_BROWSE_HEADERS, BROWSE_HEADERS);
        sUriMatcher.addURI(AUTHORITY, PATH_BROWSE + "/#", BROWSE);
        sUriMatcher.addURI(AUTHORITY, PATH_IMAGE + "/*", IMAGE);
    }

    private static final HashMap<String, Integer> LAUNCHER_COLUMN_CASES = new HashMap<String, Integer>();
    private static final String[] LAUNCHER_PROJECTION_ALL;

    private static final int LAUNCHER_CASE_ID = 0;
    private static final int LAUNCHER_CASE_COUNT = 1;
    private static final int LAUNCHER_CASE_NAME = 2;
    private static final int LAUNCHER_CASE_IMPORTANCE = 3;
    private static final int LAUNCHER_CASE_DISPLAY_NAME = 4;
    private static final int LAUNCHER_CASE_VISIBLE_COUNT = 5;
    private static final int LAUNCHER_CASE_CROP_ALLOWED = 6;
    private static final int LAUNCHER_CASE_CACHE_TIME = 7;
    private static final int LAUNCHER_CASE_INTENT_URI = 8;

    static {
        LAUNCHER_COLUMN_CASES
                .put(CanvasContract.Launcher._ID, LAUNCHER_CASE_ID);
        LAUNCHER_COLUMN_CASES.put(CanvasContract.Launcher._COUNT,
                LAUNCHER_CASE_COUNT);
        LAUNCHER_COLUMN_CASES.put(CanvasContract.Launcher.NAME,
                LAUNCHER_CASE_NAME);
        LAUNCHER_COLUMN_CASES.put(CanvasContract.Launcher.IMPORTANCE,
                LAUNCHER_CASE_IMPORTANCE);
        LAUNCHER_COLUMN_CASES.put(CanvasContract.Launcher.DISPLAY_NAME,
                LAUNCHER_CASE_DISPLAY_NAME);
        LAUNCHER_COLUMN_CASES.put(CanvasContract.Launcher.VISIBLE_COUNT,
                LAUNCHER_CASE_VISIBLE_COUNT);
        LAUNCHER_COLUMN_CASES.put(CanvasContract.Launcher.IMAGE_CROP_ALLOWED,
                LAUNCHER_CASE_CROP_ALLOWED);
        LAUNCHER_COLUMN_CASES.put(CanvasContract.Launcher.CACHE_TIME_MS,
                LAUNCHER_CASE_CACHE_TIME);
        LAUNCHER_COLUMN_CASES.put(CanvasContract.Launcher.INTENT_URI,
                LAUNCHER_CASE_INTENT_URI);

        LAUNCHER_PROJECTION_ALL = LAUNCHER_COLUMN_CASES.keySet().toArray(
                new String[] {});
    }

    private static final HashMap<String, Integer> CLUSTER_COLUMN_CASES = new HashMap<String, Integer>();
    private static final String[] CLUSTER_PROJECTION_ALL;

    private static final int CLUSTER_CASE_ID = 0;
    private static final int CLUSTER_CASE_COUNT = 1;
    private static final int CLUSTER_CASE_PARENT_ID = 2;
    private static final int CLUSTER_CASE_IMAGE_URI = 3;

    static {
        CLUSTER_COLUMN_CASES.put(CanvasContract.LauncherItem._ID,
                CLUSTER_CASE_ID);
        CLUSTER_COLUMN_CASES.put(CanvasContract.LauncherItem._COUNT,
                CLUSTER_CASE_COUNT);
        CLUSTER_COLUMN_CASES.put(CanvasContract.LauncherItem.PARENT_ID,
                CLUSTER_CASE_PARENT_ID);
        CLUSTER_COLUMN_CASES.put(CanvasContract.LauncherItem.IMAGE_URI,
                CLUSTER_CASE_IMAGE_URI);

        CLUSTER_PROJECTION_ALL = CLUSTER_COLUMN_CASES.keySet().toArray(
                new String[] {});
    }

    private static final HashMap<String, Integer> BROWSE_HEADER_COLUMN_CASES = new HashMap<String, Integer>();
    private static final String[] BROWSE_HEADER_PROJECTION_ALL;

    private static final int BROWSE_HEADER_CASE_ID = 0;
    private static final int BROWSE_HEADER_CASE_COUNT = 1;
    private static final int BROWSE_HEADER_CASE_NAME = 2;
    private static final int BROWSE_HEADER_CASE_DISPLAY_NAME = 3;
    private static final int BROWSE_HEADER_CASE_ICON_URI = 4;
    private static final int BROWSE_HEADER_CASE_BADGE_URI = 5;
    private static final int BROWSE_HEADER_CASE_COLOR_HINT = 6;
    private static final int BROWSE_HEADER_CASE_TEXT_COLOR_HINT = 7;
    private static final int BROWSE_HEADER_CASE_BG_IMAGE_URI = 8;
    private static final int BROWSE_HEADER_CASE_EXPAND_GROUP = 9;
    private static final int BROWSE_HEADER_CASE_WRAP = 10;
    private static final int BROWSE_HEADER_CASE_DEFAULT_ITEM_WIDTH = 11;
    private static final int BROWSE_HEADER_CASE_DEFAULT_ITEM_HEIGHT = 12;

    static {
        BROWSE_HEADER_COLUMN_CASES.put(CanvasContract.BrowseHeaders._ID,
                BROWSE_HEADER_CASE_ID);
        BROWSE_HEADER_COLUMN_CASES.put(CanvasContract.BrowseHeaders._COUNT,
                BROWSE_HEADER_CASE_COUNT);
        BROWSE_HEADER_COLUMN_CASES.put(CanvasContract.BrowseHeaders.NAME,
                BROWSE_HEADER_CASE_NAME);
        BROWSE_HEADER_COLUMN_CASES.put(
                CanvasContract.BrowseHeaders.DISPLAY_NAME,
                BROWSE_HEADER_CASE_DISPLAY_NAME);
        BROWSE_HEADER_COLUMN_CASES.put(CanvasContract.BrowseHeaders.ICON_URI,
                BROWSE_HEADER_CASE_ICON_URI);
        BROWSE_HEADER_COLUMN_CASES.put(CanvasContract.BrowseHeaders.BADGE_URI,
                BROWSE_HEADER_CASE_BADGE_URI);
        BROWSE_HEADER_COLUMN_CASES.put(CanvasContract.BrowseHeaders.COLOR_HINT,
                BROWSE_HEADER_CASE_COLOR_HINT);
        BROWSE_HEADER_COLUMN_CASES.put(
                CanvasContract.BrowseHeaders.TEXT_COLOR_HINT,
                BROWSE_HEADER_CASE_TEXT_COLOR_HINT);
        BROWSE_HEADER_COLUMN_CASES.put(
                CanvasContract.BrowseHeaders.BG_IMAGE_URI,
                BROWSE_HEADER_CASE_BG_IMAGE_URI);
        BROWSE_HEADER_COLUMN_CASES.put(
                CanvasContract.BrowseHeaders.EXPAND_GROUP,
                BROWSE_HEADER_CASE_EXPAND_GROUP);
        BROWSE_HEADER_COLUMN_CASES.put(CanvasContract.BrowseHeaders.WRAP_ITEMS,
                BROWSE_HEADER_CASE_WRAP);
        BROWSE_HEADER_COLUMN_CASES.put(
                CanvasContract.BrowseHeaders.DEFAULT_ITEM_WIDTH,
                BROWSE_HEADER_CASE_DEFAULT_ITEM_WIDTH);
        BROWSE_HEADER_COLUMN_CASES.put(
                CanvasContract.BrowseHeaders.DEFAULT_ITEM_HEIGHT,
                BROWSE_HEADER_CASE_DEFAULT_ITEM_HEIGHT);

        BROWSE_HEADER_PROJECTION_ALL = BROWSE_HEADER_COLUMN_CASES.keySet()
                .toArray(new String[] {});
    }

    private static final HashMap<String, Integer> BROWSE_COLUMN_CASES = new HashMap<String, Integer>();
    private static final String[] BROWSE_PROJECTION_ALL;

    private static final int BROWSE_CASE_ID = 0;
    private static final int BROWSE_CASE_COUNT = 1;
    private static final int BROWSE_CASE_PARENT_ID = 2;
    private static final int BROWSE_CASE_DISPLAY_NAME = 3;
    private static final int BROWSE_CASE_DISPLAY_DESCRIPTION = 4;
    private static final int BROWSE_CASE_IMAGE_URI = 5;
    private static final int BROWSE_CASE_WIDTH = 6;
    private static final int BROWSE_CASE_HEIGHT = 7;
    private static final int BROWSE_CASE_INTENT_URI = 8;

    static {
        BROWSE_COLUMN_CASES.put(CanvasContract.BrowseItems._ID, BROWSE_CASE_ID);
        BROWSE_COLUMN_CASES.put(CanvasContract.BrowseItems._COUNT,
                BROWSE_CASE_COUNT);
        BROWSE_COLUMN_CASES.put(CanvasContract.BrowseItems.PARENT_ID,
                BROWSE_CASE_PARENT_ID);
        BROWSE_COLUMN_CASES.put(CanvasContract.BrowseItems.DISPLAY_NAME,
                BROWSE_CASE_DISPLAY_NAME);
        BROWSE_COLUMN_CASES.put(CanvasContract.BrowseItems.DISPLAY_DESCRIPTION,
                BROWSE_CASE_DISPLAY_DESCRIPTION);
        BROWSE_COLUMN_CASES.put(CanvasContract.BrowseItems.IMAGE_URI,
                BROWSE_CASE_IMAGE_URI);
        BROWSE_COLUMN_CASES.put(CanvasContract.BrowseItems.WIDTH,
                BROWSE_CASE_WIDTH);
        BROWSE_COLUMN_CASES.put(CanvasContract.BrowseItems.HEIGHT,
                BROWSE_CASE_HEIGHT);
        BROWSE_COLUMN_CASES.put(CanvasContract.BrowseItems.INTENT_URI,
                BROWSE_CASE_INTENT_URI);

        BROWSE_PROJECTION_ALL = BROWSE_COLUMN_CASES.keySet().toArray(
                new String[] {});
    }

    // The max clusters that we'll return for a single launcher
    private static final int MAX_CLUSTER_SIZE = 3;
    // The max amount of items we'll return for a cluster
    private static final int MAX_CLUSTER_ITEM_SIZE = 10;
    private static final Integer CACHE_TIME_MS = 1*1000;

    private DataManager mDataManager;
    private MediaSet mRootSet;
    private ArrayList<Cluster> mClusters = new ArrayList<Cluster>(MAX_CLUSTER_SIZE);

    @Override
    public boolean onCreate() {
        GalleryApp app = (GalleryApp) getContext().getApplicationContext();
        mDataManager = app.getDataManager();
        return true;
    }

    private final static SyncListener sNullSyncListener = new SyncListener() {
        @Override
        public void onSyncDone(MediaSet mediaSet, int resultCode) {
        }
    };

    private final ContentListener mChangedListener = new ContentListener() {
        @Override
        public void onContentDirty() {
            getContext().getContentResolver().notifyChange(
                    NOTIFY_CHANGED_URI, null, false);
        }
    };

    private MediaSet loadRootMediaSet() {
        if (mRootSet == null) {
            String path = mDataManager.getTopSetPath(DataManager.INCLUDE_ALL);
            mRootSet = mDataManager.getMediaSet(path);
        }
        loadMediaSet(mRootSet);
        return mRootSet;
    }

    private void loadMediaSet(MediaSet set) {
        try {
            Future<Integer> future = set.requestSync(sNullSyncListener);
            synchronized (future) {
                if (!future.isDone()) {
                    future.wait(100);
                }
            }
        } catch (InterruptedException e) {
            Log.d(TAG, "timed out waiting for sync");
        }
        set.addContentListener(mChangedListener);
        set.loadIfDirty();
    }

    private void loadClustersIfEmpty() {
        if (mClusters.size() > 0) {
            return;
        }

        MediaSet root = loadRootMediaSet();
        int count = root.getSubMediaSetCount();
        for (int i = 0; i < count && mClusters.size() < MAX_CLUSTER_SIZE; i++) {
            MediaSet set = root.getSubMediaSet(i);
            loadMediaSet(set);
            Log.d(TAG, "Building set: " + set.getName());
            Cluster.Builder bob = new Cluster.Builder();
            bob.id(i);
            bob.displayName(set.getName());
            Intent intent = CanvasContract.getBrowseIntent(BROWSER_ROOT_URI, i);
            bob.intent(intent);
            bob.imageCropAllowed(true);
            bob.cacheTimeMs(CACHE_TIME_MS);
            int itemCount = Math.min(set.getMediaItemCount(), MAX_CLUSTER_ITEM_SIZE);
            List<MediaItem> items = set.getMediaItem(0, itemCount);
            if (itemCount != items.size()) {
                Log.d(TAG, "Size mismatch, expected " + itemCount + ", got " + items.size());
            }
            // This is done because not all items may have been synced yet
            itemCount = items.size();
            if (itemCount <= 0) {
                Log.d(TAG, "Skipping, no items...");
            }
            bob.visibleCount(itemCount);
            for (MediaItem item : items) {
                bob.addItem(createImageUri(item));
            }
            mClusters.add(bob.build());
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        long identity = Binder.clearCallingIdentity();
        try {
            MatrixCursor c;
            int match = sUriMatcher.match(uri);
            Log.d(TAG, "query: " + uri.toString() + ", match = " + match);
            switch (match) {
            case LAUNCHER:
                if (projection == null) {
                    projection = LAUNCHER_PROJECTION_ALL;
                }
                c = new MatrixCursor(projection);
                buildClusters(projection, c);
                break;
            case LAUNCHER_ITEMS:
                if (projection == null) {
                    projection = CLUSTER_PROJECTION_ALL;
                }
                c = new MatrixCursor(projection);
                buildMultiCluster(projection, c, uri);
                break;
            case LAUNCHER_ITEM_ID:
                if (projection == null) {
                    projection = CLUSTER_PROJECTION_ALL;
                }
                c = new MatrixCursor(projection);
                buildSingleCluster(projection, c, uri);
                break;
            case BROWSE_HEADERS:
                if (projection == null) {
                    projection = BROWSE_HEADER_PROJECTION_ALL;
                }
                c = new MatrixCursor(projection);
                buildBrowseHeaders(projection, c);
                break;
            case BROWSE:
                if (projection == null) {
                    projection = BROWSE_PROJECTION_ALL;
                }
                c = new MatrixCursor(projection);
                buildBrowseRow(projection, c, uri);
                break;
            default:
                c = new MatrixCursor(new String[] {BaseColumns._ID});
                break;
            }
            return c;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private static final JobContext sJobStub = new JobContext() {
        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void setCancelListener(CancelListener listener) {
        }

        @Override
        public boolean setMode(int mode) {
            return true;
        }
    };

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) {
        long identity = Binder.clearCallingIdentity();
        try {
            String path = uri.getQueryParameter("path");
            MediaItem item = (MediaItem) mDataManager.getMediaObject(path);
            Job<Bitmap> job = item.requestImage(MediaItem.TYPE_MICROTHUMBNAIL);
            final Bitmap bitmap = job.run(sJobStub);
            final ParcelFileDescriptor[] fds = ParcelFileDescriptor.createPipe();
            AsyncTask<Object, Object, Object> task = new AsyncTask<Object, Object, Object>() {
                @Override
                protected Object doInBackground(Object... params) {
                    OutputStream stream = new ParcelFileDescriptor.AutoCloseOutputStream(fds[1]);
                    bitmap.compress(CompressFormat.PNG, 100, stream);
                    try {
                        fds[1].close();
                    } catch (IOException e) {
                        Log.w(TAG, "Failure closing pipe", e);
                    }
                    return null;
                }
            };
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Object[])null);

            return fds[0];
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private Uri createImageUri(MediaItem item) {
        // TODO: Make a database to track URIs we've actually returned
        // for which to proxy to avoid things with
        // android.permission.ACCESS_APP_BROWSE_DATA being able to make
        // any request it wants on our behalf.
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .path(PATH_IMAGE)
                .appendQueryParameter("path", item.getPath().toString())
                .build();
    }

    private void buildClusters(String[] projection, MatrixCursor c) {
        mClusters.clear();
        loadClustersIfEmpty();

        int clusterCount = mClusters.size();
        for (Cluster cluster : mClusters) {

            Object[] row = new Object[projection.length];
            long id = cluster.getId();
            for (int j = 0; j < projection.length; j++) {
                if (!LAUNCHER_COLUMN_CASES.containsKey(projection[j])) {
                    continue;
                }
                int column = LAUNCHER_COLUMN_CASES.get(projection[j]);
                Object obj = null;
                switch (column) {
                    case LAUNCHER_CASE_ID:
                        obj = id;
                        break;
                    case LAUNCHER_CASE_COUNT:
                        obj = clusterCount;
                        break;
                    case LAUNCHER_CASE_NAME:
                        obj = cluster.getName();
                        break;
                    case LAUNCHER_CASE_IMPORTANCE:
                        obj = cluster.getImportance();
                        break;
                    case LAUNCHER_CASE_DISPLAY_NAME:
                        obj = cluster.getDisplayName();
                        break;
                    case LAUNCHER_CASE_VISIBLE_COUNT:
                        obj = cluster.getVisibleCount();
                        break;
                    case LAUNCHER_CASE_CACHE_TIME:
                        obj = cluster.getCacheTimeMs();
                        break;
                    case LAUNCHER_CASE_INTENT_URI:
                        obj = cluster.getIntent().toUri(Intent.URI_INTENT_SCHEME);
                        break;
                    case LAUNCHER_CASE_CROP_ALLOWED:
                        obj = cluster.isImageCropAllowed();
                        break;
                }
                row[j] = obj;
            }
            c.addRow(row);
        }
    }

    private void buildMultiCluster(String[] projection, MatrixCursor c, Uri uri) {
        for (int index = 0; index < mClusters.size(); ++index) {
            buildSingleCluster(projection, c,
                    uri.buildUpon().appendPath(String.valueOf(index)).build());
        }
    }

    private void buildSingleCluster(String[] projection, MatrixCursor c, Uri uri) {
        loadClustersIfEmpty();

        int parentId = Integer.parseInt(uri.getLastPathSegment());

        Cluster cluster = mClusters.get(parentId);
        int numItems = Math.min(cluster.getItemCount(), MAX_CLUSTER_ITEM_SIZE);
        for (int i = 0; i < numItems; i++) {
            Cluster.ClusterItem item = cluster.getItem(i);
            Object[] row = new Object[projection.length];

            for (int j = 0; j < projection.length; j++) {
                if (!CLUSTER_COLUMN_CASES.containsKey(projection[j])) {
                    continue;
                }
                int column = CLUSTER_COLUMN_CASES.get(projection[j]);
                switch (column) {
                    case CLUSTER_CASE_ID:
                        row[j] = i;
                        break;
                    case CLUSTER_CASE_COUNT:
                        row[j] = numItems;
                        break;
                    case CLUSTER_CASE_PARENT_ID:
                        row[j] = parentId;
                        break;
                    case CLUSTER_CASE_IMAGE_URI:
                        row[j] = item.getImageUri();
                        break;
                }
            }
            c.addRow(row);
        }
    }

    private void buildBrowseHeaders(String[] projection, MatrixCursor c) {
        // TODO: All images
        MediaSet root = loadRootMediaSet();
        int itemCount = root.getSubMediaSetCount();
        for (int i = 0; i < itemCount; i++) {
            Object[] header = new Object[projection.length];
            MediaSet item = root.getSubMediaSet(i);
            for (int j = 0; j < projection.length; j++) {
                if (!BROWSE_HEADER_COLUMN_CASES.containsKey(projection[j])) {
                    continue;
                }
                int column = BROWSE_HEADER_COLUMN_CASES.get(projection[j]);
                Object obj = null;
                switch (column) {
                    case BROWSE_HEADER_CASE_ID:
                        obj = i;
                        break;
                    case BROWSE_HEADER_CASE_COUNT:
                        obj = itemCount;
                        break;
                    case BROWSE_HEADER_CASE_NAME:
                    case BROWSE_HEADER_CASE_DISPLAY_NAME:
                        obj = item.getName();
                        break;
                    case BROWSE_HEADER_CASE_ICON_URI:
                        break;
                    case BROWSE_HEADER_CASE_BADGE_URI:
                        break;
                    case BROWSE_HEADER_CASE_COLOR_HINT:
                        break;
                    case BROWSE_HEADER_CASE_TEXT_COLOR_HINT:
                        break;
                    case BROWSE_HEADER_CASE_BG_IMAGE_URI:
                        break;
                    case BROWSE_HEADER_CASE_EXPAND_GROUP:
                        obj = 0;
                        break;
                    case BROWSE_HEADER_CASE_WRAP:
                        obj = i % 2;
                        break;
                    case BROWSE_HEADER_CASE_DEFAULT_ITEM_WIDTH:
                    case BROWSE_HEADER_CASE_DEFAULT_ITEM_HEIGHT:
                        obj = MediaItem.getTargetSize(MediaItem.TYPE_MICROTHUMBNAIL);
                        break;
                }
                header[j] = obj;
            }
            c.addRow(header);
        }
    }

    private void buildBrowseRow(String[] projection, MatrixCursor c, Uri uri) {
        int row = Integer.parseInt(uri.getLastPathSegment());
        MediaSet album = loadRootMediaSet().getSubMediaSet(row);
        loadMediaSet(album);
        int itemCount = album.getMediaItemCount();
        ArrayList<MediaItem> items = album.getMediaItem(0, itemCount);
        itemCount = items.size();
        for (int i = 0; i < itemCount; i++) {
            Object[] header = new Object[projection.length];
            MediaItem item = items.get(i);
            for (int j = 0; j < projection.length; j++) {
                if (!BROWSE_COLUMN_CASES.containsKey(projection[j])) {
                    continue;
                }
                int column = BROWSE_COLUMN_CASES.get(projection[j]);
                Object obj = null;
                switch (column) {
                    case BROWSE_CASE_ID:
                        obj = i;
                        break;
                    case BROWSE_CASE_COUNT:
                        obj = itemCount;
                        break;
                    case BROWSE_CASE_DISPLAY_NAME:
                        obj = item.getName();
                        break;
                    case BROWSE_CASE_DISPLAY_DESCRIPTION:
                        obj = item.getFilePath();
                        break;
                    case BROWSE_CASE_IMAGE_URI:
                        obj = createImageUri(item);
                        break;
                    case BROWSE_CASE_WIDTH:
                    case BROWSE_CASE_HEIGHT:
                        obj = MediaItem.getTargetSize(MediaItem.TYPE_MICROTHUMBNAIL);
                        break;
                    case BROWSE_CASE_INTENT_URI:
                        Intent intent = new Intent(Intent.ACTION_VIEW, item.getContentUri());
                        obj = intent.toUri(Intent.URI_INTENT_SCHEME);
                        break;
                }
                header[j] = obj;
            }
            c.addRow(header);
        }
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Insert not supported");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Delete not supported");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        throw new UnsupportedOperationException("Update not supported");
    }

}
