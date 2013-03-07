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

import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.MediaSet.SyncListener;
import com.android.gallery3d.util.Future;

/**
 * Proof of concept, don't use
 */
public class MediaSetLoader extends AsyncTaskLoader<MediaSet> {

    private static final SyncListener sNullListener = new SyncListener() {
        @Override
        public void onSyncDone(MediaSet mediaSet, int resultCode) {
        }
    };

    private MediaSet mMediaSet;
    private Future<Integer> mSyncTask = null;
    private ContentListener mObserver = new ContentListener() {
        @Override
        public void onContentDirty() {
            onContentChanged();
        }
    };

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
    public MediaSet loadInBackground() {
        mMediaSet.loadIfDirty();
        return mMediaSet;
    }

}
