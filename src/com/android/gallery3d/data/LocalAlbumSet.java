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

import android.net.Uri;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.data.BucketHelper.BucketEntry;
import com.android.gallery3d.util.MediaSetUtils;

import java.util.ArrayList;
import java.util.Comparator;

// LocalAlbumSet lists all image or video albums in the local storage.
// The path should be "/local/image", "local/video" or "/local/all"
public class LocalAlbumSet extends MediaSet {
    @SuppressWarnings("unused")
    private static final String TAG = "LocalAlbumSet";

    public static final Path PATH_ALL = Path.fromString("/local/all");
    public static final Path PATH_IMAGE = Path.fromString("/local/image");
    public static final Path PATH_VIDEO = Path.fromString("/local/video");

    private static final Uri[] mWatchUris =
        {Images.Media.EXTERNAL_CONTENT_URI, Video.Media.EXTERNAL_CONTENT_URI};

    private final GalleryApp mApplication;
    private final int mType;
    private ArrayList<MediaSet> mAlbums = new ArrayList<MediaSet>();
    private final ChangeNotifier mNotifier;
    private final String mName;

    public LocalAlbumSet(Path path, GalleryApp application) {
        super(path, nextVersionNumber());
        mApplication = application;
        mType = getTypeFromPath(path);
        mNotifier = new ChangeNotifier(this, mWatchUris, application);
        mName = application.getResources().getString(
                R.string.set_label_local_albums);
    }

    private static int getTypeFromPath(Path path) {
        String name[] = path.split();
        if (name.length < 2) {
            throw new IllegalArgumentException(path.toString());
        }
        return getTypeFromString(name[1]);
    }

    @Override
    public MediaSet getSubMediaSet(int index) {
        return mAlbums.get(index);
    }

    @Override
    public int getSubMediaSetCount() {
        return mAlbums.size();
    }

    @Override
    public String getName() {
        return mName;
    }

    private static int findBucket(BucketEntry entries[], int bucketId) {
        for (int i = 0, n = entries.length; i < n; ++i) {
            if (entries[i].bucketId == bucketId) return i;
        }
        return -1;
    }

    private MediaSet getLocalAlbum(
            DataManager manager, int type, Path parent, int id, String name) {
        synchronized (DataManager.LOCK) {
            Path path = parent.getChild(id);
            MediaObject object = manager.peekMediaObject(path);
            if (object != null) return (MediaSet) object;
            switch (type) {
                case MEDIA_TYPE_IMAGE:
                    return new LocalAlbum(path, mApplication, id, true, name);
                case MEDIA_TYPE_VIDEO:
                    return new LocalAlbum(path, mApplication, id, false, name);
                case MEDIA_TYPE_ALL:
                    Comparator<MediaItem> comp = DataManager.sDateTakenComparator;
                    return new LocalMergeAlbum(path, comp, new MediaSet[] {
                            getLocalAlbum(manager, MEDIA_TYPE_IMAGE, PATH_IMAGE, id, name),
                            getLocalAlbum(manager, MEDIA_TYPE_VIDEO, PATH_VIDEO, id, name)}, id);
            }
            throw new IllegalArgumentException(String.valueOf(type));
        }
    }

    @Override
    protected boolean isDirtyLocked() {
        return mNotifier.isDirty();
    }

    @Override
    protected void load() throws InterruptedException {
        // Note: it will be faster if we only select media_type and bucket_id.
        //       need to test the performance if that is worth
        BucketEntry[] entries = BucketHelper.loadBucketEntries(
                createJobContextCompat(), mApplication.getContentResolver(), mType);

        // BucketHelper.loadBucketEntries returns null if it was canceled
        if (entries == null || Thread.interrupted()) {
            throw new InterruptedException();
        }

        int offset = 0;
        // Move camera and download bucket to the front, while keeping the
        // order of others.
        int index = findBucket(entries, MediaSetUtils.CAMERA_BUCKET_ID);
        if (index != -1) {
            circularShiftRight(entries, offset++, index);
        }
        index = findBucket(entries, MediaSetUtils.DOWNLOAD_BUCKET_ID);
        if (index != -1) {
            circularShiftRight(entries, offset++, index);
        }

        mAlbums.clear();
        DataManager dataManager = mApplication.getDataManager();
        for (BucketEntry entry : entries) {
            MediaSet album = getLocalAlbum(dataManager,
                    mType, mPath, entry.bucketId, entry.bucketName);
            mAlbums.add(album);
        }

        for (MediaSet album : mAlbums) {
            album.loadIfDirty();
        }
    }

    // For debug only. Fake there is a ContentObserver.onChange() event.
    void fakeChange() {
        mNotifier.fakeChange();
    }

    // Circular shift the array range from a[i] to a[j] (inclusive). That is,
    // a[i] -> a[i+1] -> a[i+2] -> ... -> a[j], and a[j] -> a[i]
    private static <T> void circularShiftRight(T[] array, int i, int j) {
        T temp = array[j];
        for (int k = j; k > i; k--) {
            array[k] = array[k - 1];
        }
        array[i] = temp;
    }
}
