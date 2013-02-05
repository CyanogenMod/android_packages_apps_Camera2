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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import com.android.gallery3d.R;
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
import com.google.android.canvas.provider.EnableSyncActivity;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CanvasProvider extends CanvasProviderBase {

    private static final String TAG = "GalleryCanvasProvider";

    // TODO: Temporary for testing, remove once b/8099821 fixed
    public static final String PHOTO_AUTHORITY = "com.google.android.gallery3d.GooglePhotoProvider";
    public static final String ACCOUNT_TYPE = "com.google";

    private DataManager mDataManager;
    private MediaSet mRootSet;

    private static final long SYNC_IN_PROGRESS = -1;
    private HashMap<MediaSet, Long> mSyncedSets = new HashMap<MediaSet, Long>();

    private final SyncListener mSyncListener = new SyncListener() {

        @Override
        public void onSyncDone(MediaSet mediaSet, int resultCode) {
            if (resultCode != MediaSet.SYNC_RESULT_SUCCESS) {
                mSyncedSets.put(mediaSet, (long) 0);
            } else {
                mSyncedSets.put(mediaSet, SystemClock.uptimeMillis());
            }
        }
    };

    private final ContentListener mChangedListener = new ContentListener() {

        @Override
        public void onContentDirty() {
            getContext().getContentResolver().notifyChange(NOTIFY_CHANGED_URI,
                    null, false);
        }
    };

    @Override
    public boolean onCreate() {
        GalleryApp app = (GalleryApp) getContext().getApplicationContext();
        mDataManager = app.getDataManager();
        return true;
    }

    private MediaSet loadRootMediaSet() {
        if (mRootSet == null) {
            String path = mDataManager.getTopSetPath(DataManager.INCLUDE_ALL);
            mRootSet = mDataManager.getMediaSet(path);
        }
        loadMediaSet(mRootSet);
        return mRootSet;
    }

    private boolean shouldRequestSync(MediaSet set) {
        if (!mSyncedSets.containsKey(set)) {
            return true;
        }
        long lastSynced = mSyncedSets.get(set);
        if (lastSynced == SYNC_IN_PROGRESS) {
            return true;
        }
        return (SystemClock.uptimeMillis() - lastSynced) > CACHE_TIME_MS;
    }

    private void loadMediaSet(MediaSet set) {
        try {
            if (shouldRequestSync(set)) {
                mSyncedSets.put(set, SYNC_IN_PROGRESS);
                Future<Integer> future = set.requestSync(mSyncListener);
                synchronized (future) {
                    if (!future.isDone()) {
                        future.wait(500);
                    }
                }
            }
        } catch (InterruptedException e) {
            Log.d(TAG, "timed out waiting for sync");
        }
        set.addContentListener(mChangedListener);
        set.loadIfDirty();
    }

    @Override
    protected void loadClusters(List<Cluster> clusters) {
        MediaSet root = loadRootMediaSet();
        int count = root.getSubMediaSetCount();
        for (int i = 0; i < count && clusters.size() < MAX_CLUSTER_SIZE; i++) {
            MediaSet set = root.getSubMediaSet(i);
            loadMediaSet(set);
            Cluster.Builder bob = new Cluster.Builder();
            bob.id(i);
            bob.displayName(set.getName());
            Intent intent = CanvasContract.getBrowseIntent(BROWSER_ROOT_URI, i);
            bob.intent(intent);
            bob.imageCropAllowed(true);
            bob.cacheTimeMs(CACHE_TIME_MS);
            int itemCount = Math.min(set.getMediaItemCount(),
                    MAX_CLUSTER_ITEM_SIZE);
            List<MediaItem> items = set.getMediaItem(0, itemCount);
            // This is done because not all items may have been synced yet
            itemCount = items.size();
            if (itemCount <= 0) {
                continue;
            }
            bob.visibleCount(itemCount);
            for (MediaItem item : items) {
                bob.addItem(createImageUri(item));
            }
            clusters.add(bob.build());
        }

        if (clusters.size() == 0) {
            handleEmptyClusters(clusters);
        }
    }

    private void handleEmptyClusters(List<Cluster> clusters) {
        // Images are temporary, remove once b/8108002 fixed
        Account[] accounts = AccountManager.get(getContext()).getAccountsByType(ACCOUNT_TYPE);
        boolean syncEnabled = false;
        for (Account account : accounts) {
            syncEnabled |= ContentResolver.getSyncAutomatically(account, PHOTO_AUTHORITY);
        }
        Cluster.Builder bob = new Cluster.Builder();
        bob.displayName(getContext().getString(R.string.no_albums_alert));
        bob.addItem(createImageUri(R.mipmap.ic_launcher_gallery));
        bob.visibleCount(1);
        clusters.add(bob.build());
        if (!syncEnabled) {
            bob = new Cluster.Builder();
            bob.displayName("Enable Picasa sync");
            bob.id(1);
            bob.intent(new Intent(getContext(), EnableSyncActivity.class));
            bob.addItem(createImageUri(R.drawable.frame_overlay_gallery_picasa));
            bob.visibleCount(1);
            clusters.add(bob.build());
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
            if (path == null) {
                return null;
            }
            MediaItem item = (MediaItem) mDataManager.getMediaObject(path);
            Job<Bitmap> job = item.requestImage(MediaItem.TYPE_MICROTHUMBNAIL);
            final Bitmap bitmap = job.run(sJobStub);
            final ParcelFileDescriptor[] fds = ParcelFileDescriptor
                    .createPipe();
            AsyncTask<Object, Object, Object> task = new AsyncTask<Object, Object, Object>() {

                @Override
                protected Object doInBackground(Object... params) {
                    OutputStream stream = new ParcelFileDescriptor.AutoCloseOutputStream(
                            fds[1]);
                    bitmap.compress(CompressFormat.PNG, 100, stream);
                    try {
                        fds[1].close();
                    } catch (IOException e) {
                        Log.w(TAG, "Failure closing pipe", e);
                    }
                    return null;
                }
            };
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                    (Object[]) null);

            return fds[0];
        } catch (Exception e) {
            Log.w(TAG, "openFile failed", e);
            throw new RuntimeException(e);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode)
            throws FileNotFoundException {
        ParcelFileDescriptor fd = openFile(uri, mode);
        if (fd != null) {
            return new AssetFileDescriptor(fd, 0, -1);
        }
        int id = Integer.parseInt(uri.getPathSegments().get(0));
        return getContext().getResources().openRawResourceFd(id);
    }

    private Uri createImageUri(int imageResId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .path(Integer.toString(imageResId))
                .build();
    }

    private Uri createImageUri(MediaItem item) {
        // TODO: Make a database to track URIs we've actually returned
        // for which to proxy to avoid things with
        // android.permission.ACCESS_APP_BROWSE_DATA being able to make
        // any request it wants on our behalf.
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY).path(PATH_IMAGE)
                .appendQueryParameter("path", item.getPath().toString())
                .build();
    }

    @Override
    protected void buildBrowseHeaders(String[] projection, MatrixCursor c) {
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
                    obj = MediaItem
                            .getTargetSize(MediaItem.TYPE_MICROTHUMBNAIL);
                    break;
                }
                header[j] = obj;
            }
            c.addRow(header);
        }
    }

    @Override
    protected void buildBrowseRow(String[] projection, MatrixCursor c, Uri uri) {
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
                    obj = MediaItem
                            .getTargetSize(MediaItem.TYPE_MICROTHUMBNAIL);
                    break;
                case BROWSE_CASE_INTENT_URI:
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            item.getContentUri());
                    obj = intent.toUri(Intent.URI_INTENT_SCHEME);
                    break;
                }
                header[j] = obj;
            }
            c.addRow(header);
        }
    }

    // TODO: Remove once b/8079561 is resolved
    public static boolean startBrowseActivity(Activity activity) {
        Configuration config = activity.getResources().getConfiguration();
        if (config.touchscreen == Configuration.TOUCHSCREEN_NOTOUCH) {
            try {
                Intent intent = CanvasContract.getBrowseIntent(
                        BROWSER_ROOT_URI, 0);
                activity.startActivity(intent);
                return true;
            } catch (ActivityNotFoundException ex) {
            }
        }
        return false;
    }

}
