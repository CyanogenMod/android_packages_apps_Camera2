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

package com.android.gallery3d.onetimeinitializer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.LocalAlbum;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.gadget.WidgetDatabaseHelper;
import com.android.gallery3d.gadget.WidgetDatabaseHelper.Entry;
import com.android.gallery3d.util.GalleryUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;

/**
 * This one-timer migrates local-album gallery app widgets from pre-JB releases to JB (or later)
 * due to bucket ID (i.e., directory hash) change in JB (as the external storage path is changed
 * from /mnt/sdcard to /storage/sdcard0).
 */
public class GalleryWidgetMigrator {
    private static final String TAG = "GalleryWidgetMigrator";
    private static final String OLD_EXT_PATH = "/mnt/sdcard";
    private static final String NEW_EXT_PATH =
            Environment.getExternalStorageDirectory().getAbsolutePath();
    private static final int RELATIVE_PATH_START = NEW_EXT_PATH.length();
    private static final String KEY_MIGRATION_DONE = "gallery_widget_migration_done";

    /**
     * Migrates local-album gallery widgets from pre-JB releases to JB (or later) due to bucket ID
     * (i.e., directory hash) change in JB.
     */
    public static void migrateGalleryWidgets(Context context) {
        // no migration needed if path of external storage is not changed
        if (OLD_EXT_PATH.equals(NEW_EXT_PATH)) return;

        // only need to migrate once; the "done" bit is saved to SharedPreferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean isDone = prefs.getBoolean(KEY_MIGRATION_DONE, false);
        if (isDone) return;

        try {
            migrateGalleryWidgetsInternal(context);
            prefs.edit().putBoolean(KEY_MIGRATION_DONE, true).commit();
        } catch (Throwable t) {
            // exception may be thrown if external storage is not available(?)
            Log.w(TAG, "migrateGalleryWidgets", t);
        }
    }

    private static void migrateGalleryWidgetsInternal(Context context) {
        GalleryApp galleryApp = (GalleryApp) context.getApplicationContext();
        DataManager manager = galleryApp.getDataManager();
        WidgetDatabaseHelper dbHelper = new WidgetDatabaseHelper(context);

        // only need to migrate local-album entries of type TYPE_ALBUM
        List<Entry> entries = dbHelper.getEntries(WidgetDatabaseHelper.TYPE_ALBUM);
        if (entries != null) {
            HashMap<Integer, Entry> localEntries = new HashMap<Integer, Entry>(entries.size());
            for (Entry entry : entries) {
                Path path = Path.fromString(entry.albumPath);
                MediaSet mediaSet = (MediaSet) manager.getMediaObject(path);
                if (mediaSet instanceof LocalAlbum) {
                    int bucketId = Integer.parseInt(path.getSuffix());
                    localEntries.put(bucketId, entry);
                }
            }
            if (!localEntries.isEmpty()) migrateLocalEntries(localEntries, dbHelper);
        }
    }

    private static void migrateLocalEntries(
            HashMap<Integer, Entry> entries, WidgetDatabaseHelper dbHelper) {
        File root = Environment.getExternalStorageDirectory();

        // check the DCIM directory first; this should take care of 99% use cases
        updatePath(new File(root, "DCIM"), entries, dbHelper);

        // check other directories if DCIM doesn't cut it
        if (!entries.isEmpty()) updatePath(root, entries, dbHelper);
    }

    private static void updatePath(
            File root, HashMap<Integer, Entry> entries, WidgetDatabaseHelper dbHelper) {
        File[] files = root.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && !entries.isEmpty()) {
                    String path = file.getAbsolutePath();
                    String oldPath = OLD_EXT_PATH + path.substring(RELATIVE_PATH_START);
                    int oldBucketId = GalleryUtils.getBucketId(oldPath);
                    Entry entry = entries.remove(oldBucketId);
                    if (entry != null) {
                        int newBucketId = GalleryUtils.getBucketId(path);
                        String newAlbumPath = Path.fromString(entry.albumPath)
                                .getParent()
                                .getChild(newBucketId)
                                .toString();
                        Log.d(TAG, "migrate from " + entry.albumPath + " to " + newAlbumPath);
                        entry.albumPath = newAlbumPath;
                        dbHelper.updateEntry(entry);
                    }
                    updatePath(file, entries, dbHelper); // recursion
                }
            }
        }
    }
}
