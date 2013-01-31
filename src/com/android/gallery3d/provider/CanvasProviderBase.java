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
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.provider.BaseColumns;

import com.google.android.canvas.data.Cluster;
import com.google.android.canvas.provider.CanvasContract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class CanvasProviderBase extends ContentProvider {

    protected static final String AUTHORITY = "com.android.gallery3d.provider.CanvasProvider";
    public static Uri NOTIFY_CHANGED_URI = Uri.parse("content://" + AUTHORITY);

    // ***************************************************
    // Provider path and URI matching
    // ***************************************************

    protected static final String PATH_IMAGE = "image";
    protected static final String PATH_LAUNCHER = "launcher";
    protected static final String PATH_LAUNCHER_ITEM = PATH_LAUNCHER + "/"
            + CanvasContract.PATH_LAUNCHER_ITEM;
    protected static final String PATH_BROWSE = "browse";
    protected static final String PATH_BROWSE_HEADERS = PATH_BROWSE + "/"
            + CanvasContract.PATH_BROWSE_HEADERS;

    protected static final int LAUNCHER = 1;
    protected static final int LAUNCHER_ITEMS = 2;
    protected static final int LAUNCHER_ITEM_ID = 3;
    protected static final int BROWSE_HEADERS = 4;
    protected static final int BROWSE = 5;
    protected static final int IMAGE = 6;
    protected static final Uri BROWSER_ROOT_URI = Uri.parse("content://"
            + AUTHORITY + "/" + PATH_BROWSE);
    protected static final UriMatcher sUriMatcher = new UriMatcher(
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

    // ***************************************************
    // Columns and projections
    // ***************************************************

    protected static final HashMap<String, Integer> LAUNCHER_COLUMN_CASES = new HashMap<String, Integer>();
    protected static final String[] LAUNCHER_PROJECTION_ALL;
    protected static final int LAUNCHER_CASE_ID = 0;
    protected static final int LAUNCHER_CASE_COUNT = 1;
    protected static final int LAUNCHER_CASE_NAME = 2;
    protected static final int LAUNCHER_CASE_IMPORTANCE = 3;
    protected static final int LAUNCHER_CASE_DISPLAY_NAME = 4;
    protected static final int LAUNCHER_CASE_VISIBLE_COUNT = 5;
    protected static final int LAUNCHER_CASE_CROP_ALLOWED = 6;
    protected static final int LAUNCHER_CASE_CACHE_TIME = 7;
    protected static final int LAUNCHER_CASE_INTENT_URI = 8;

    static {
        LAUNCHER_COLUMN_CASES.put(BaseColumns._ID, LAUNCHER_CASE_ID);
        LAUNCHER_COLUMN_CASES.put(BaseColumns._COUNT, LAUNCHER_CASE_COUNT);
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

    protected static final HashMap<String, Integer> CLUSTER_COLUMN_CASES = new HashMap<String, Integer>();
    protected static final String[] CLUSTER_PROJECTION_ALL;
    protected static final int CLUSTER_CASE_ID = 0;
    protected static final int CLUSTER_CASE_COUNT = 1;
    protected static final int CLUSTER_CASE_PARENT_ID = 2;
    protected static final int CLUSTER_CASE_IMAGE_URI = 3;

    static {
        CLUSTER_COLUMN_CASES.put(BaseColumns._ID, CLUSTER_CASE_ID);
        CLUSTER_COLUMN_CASES.put(BaseColumns._COUNT, CLUSTER_CASE_COUNT);
        CLUSTER_COLUMN_CASES.put(CanvasContract.LauncherItem.PARENT_ID,
                CLUSTER_CASE_PARENT_ID);
        CLUSTER_COLUMN_CASES.put(CanvasContract.LauncherItem.IMAGE_URI,
                CLUSTER_CASE_IMAGE_URI);

        CLUSTER_PROJECTION_ALL = CLUSTER_COLUMN_CASES.keySet().toArray(
                new String[] {});
    }

    protected static final HashMap<String, Integer> BROWSE_HEADER_COLUMN_CASES = new HashMap<String, Integer>();
    protected static final String[] BROWSE_HEADER_PROJECTION_ALL;
    protected static final int BROWSE_HEADER_CASE_ID = 0;
    protected static final int BROWSE_HEADER_CASE_COUNT = 1;
    protected static final int BROWSE_HEADER_CASE_NAME = 2;
    protected static final int BROWSE_HEADER_CASE_DISPLAY_NAME = 3;
    protected static final int BROWSE_HEADER_CASE_ICON_URI = 4;
    protected static final int BROWSE_HEADER_CASE_BADGE_URI = 5;
    protected static final int BROWSE_HEADER_CASE_COLOR_HINT = 6;
    protected static final int BROWSE_HEADER_CASE_TEXT_COLOR_HINT = 7;
    protected static final int BROWSE_HEADER_CASE_BG_IMAGE_URI = 8;
    protected static final int BROWSE_HEADER_CASE_EXPAND_GROUP = 9;
    protected static final int BROWSE_HEADER_CASE_WRAP = 10;
    protected static final int BROWSE_HEADER_CASE_DEFAULT_ITEM_WIDTH = 11;
    protected static final int BROWSE_HEADER_CASE_DEFAULT_ITEM_HEIGHT = 12;

    static {
        BROWSE_HEADER_COLUMN_CASES.put(BaseColumns._ID, BROWSE_HEADER_CASE_ID);
        BROWSE_HEADER_COLUMN_CASES.put(BaseColumns._COUNT,
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

    protected static final HashMap<String, Integer> BROWSE_COLUMN_CASES = new HashMap<String, Integer>();
    protected static final String[] BROWSE_PROJECTION_ALL;
    protected static final int BROWSE_CASE_ID = 0;
    protected static final int BROWSE_CASE_COUNT = 1;
    protected static final int BROWSE_CASE_PARENT_ID = 2;
    protected static final int BROWSE_CASE_DISPLAY_NAME = 3;
    protected static final int BROWSE_CASE_DISPLAY_DESCRIPTION = 4;
    protected static final int BROWSE_CASE_IMAGE_URI = 5;
    protected static final int BROWSE_CASE_WIDTH = 6;
    protected static final int BROWSE_CASE_HEIGHT = 7;
    protected static final int BROWSE_CASE_INTENT_URI = 8;

    static {
        BROWSE_COLUMN_CASES.put(BaseColumns._ID, BROWSE_CASE_ID);
        BROWSE_COLUMN_CASES.put(BaseColumns._COUNT, BROWSE_CASE_COUNT);
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

    // ***************************************************
    // Configuration stuff
    // ***************************************************

    // The max clusters that we'll return for a single launcher
    protected static final int MAX_CLUSTER_SIZE = 3;
    // The max amount of items we'll return for a cluster
    protected static final int MAX_CLUSTER_ITEM_SIZE = 10;
    protected static final Integer CACHE_TIME_MS = 1 * 1000;

    private ArrayList<Cluster> mClusters = new ArrayList<Cluster>(
            MAX_CLUSTER_SIZE);

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        long identity = Binder.clearCallingIdentity();
        try {
            MatrixCursor c;
            int match = sUriMatcher.match(uri);
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
                c = new MatrixCursor(new String[] { BaseColumns._ID });
                break;
            }
            return c;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private void loadClustersIfEmpty() {
        if (mClusters.size() > 0) {
            return;
        }
        loadClusters(mClusters);
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
                    if (cluster.getIntent() != null) {
                        obj = cluster.getIntent().toUri(Intent.URI_INTENT_SCHEME);
                    }
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

    protected abstract void loadClusters(List<Cluster> clusters);

    protected abstract void buildBrowseRow(String[] projection, MatrixCursor c,
            Uri uri);

    protected abstract void buildBrowseHeaders(String[] projection,
            MatrixCursor c);
}
