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

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.picasasource.PicasaSource;

import android.os.Looper;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;

// This test reads real data directly and dump information out in the log.
public class RealDataTest extends AndroidTestCase {
    private static final String TAG = "RealDataTest";

    private HashSet<Path> mUsedId = new HashSet<Path>();
    private GalleryApp mApplication;
    private DataManager mDataManager;

    @LargeTest
    public void testRealData() {
        mUsedId.clear();
        mApplication = new GalleryAppMock(
                mContext,
                mContext.getContentResolver(),
                Looper.myLooper());
        mDataManager = mApplication.getDataManager();
        mDataManager.addSource(new LocalSource(mApplication));
        mDataManager.addSource(new PicasaSource(mApplication));
        new TestLocalImage().run();
        new TestLocalVideo().run();
        new TestPicasa().run();
    }

    class TestLocalImage {
        public void run() {
            MediaSet set = mDataManager.getMediaSet("/local/image");
            set.reload();
            Log.v(TAG, "LocalAlbumSet (Image)");
            dumpMediaSet(set, "");
        }
    }

    class TestLocalVideo {
        public void run() {
            MediaSet set = mDataManager.getMediaSet("/local/video");
            set.reload();
            Log.v(TAG, "LocalAlbumSet (Video)");
            dumpMediaSet(set, "");
        }
    }

    class TestPicasa implements Runnable {
        public void run() {
            MediaSet set = mDataManager.getMediaSet("/picasa");
            set.reload();
            Log.v(TAG, "PicasaAlbumSet");
            dumpMediaSet(set, "");
        }
    }

    void dumpMediaSet(MediaSet set, String prefix) {
        Log.v(TAG, "getName() = " + set.getName());
        Log.v(TAG, "getPath() = " + set.getPath());
        Log.v(TAG, "getMediaItemCount() = " + set.getMediaItemCount());
        Log.v(TAG, "getSubMediaSetCount() = " + set.getSubMediaSetCount());
        Log.v(TAG, "getTotalMediaItemCount() = " + set.getTotalMediaItemCount());
        assertNewId(set.getPath());
        for (int i = 0, n = set.getSubMediaSetCount(); i < n; i++) {
            MediaSet sub = set.getSubMediaSet(i);
            Log.v(TAG, prefix + "got set " + i);
            dumpMediaSet(sub, prefix + "  ");
        }
        for (int i = 0, n = set.getMediaItemCount(); i < n; i += 10) {
            ArrayList<MediaItem> list = set.getMediaItem(i, 10);
            Log.v(TAG, prefix + "got item " + i + " (+" + list.size() + ")");
            for (MediaItem item : list) {
                dumpMediaItem(item, prefix + "..");
            }
        }
    }

    void dumpMediaItem(MediaItem item, String prefix) {
        assertNewId(item.getPath());
        Log.v(TAG, prefix + "getPath() = " + item.getPath());
    }

    void assertNewId(Path key) {
        assertFalse(key + " has already appeared.", mUsedId.contains(key));
        mUsedId.add(key);
    }
}
