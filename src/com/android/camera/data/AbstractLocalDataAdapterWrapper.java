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

package com.android.camera.data;

import android.content.Context;
import android.net.Uri;

/**
 * An abstract {@link LocalDataAdapter} implementation to wrap another
 * {@link LocalDataAdapter}. All implementations related to data id is not
 * addressed in this abstract class since wrapping another data adapter
 * surely makes things different for data id.
 *
 * @see FixedFirstDataAdapter
 * @see FixedLastDataAdapter
 */
public abstract class AbstractLocalDataAdapterWrapper implements LocalDataAdapter {

    protected final Context mContext;
    protected final LocalDataAdapter mAdapter;
    protected int mSuggestedWidth;
    protected int mSuggestedHeight;

    /**
     * Constructor.
     *
     * @param context A valid Android context.
     * @param wrappedAdapter The {@link LocalDataAdapter} to be wrapped.
     */
    AbstractLocalDataAdapterWrapper(Context context, LocalDataAdapter wrappedAdapter) {
        if (wrappedAdapter == null) {
            throw new AssertionError("data adapter is null");
        }
        mContext = context;
        mAdapter = wrappedAdapter;
    }

    @Override
    public void suggestViewSizeBound(int w, int h) {
        mSuggestedWidth = w;
        mSuggestedHeight = h;
        mAdapter.suggestViewSizeBound(w, h);
    }

    @Override
    public void setListener(Listener listener) {
        mAdapter.setListener(listener);
    }

    @Override
    public void setLocalDataListener(LocalDataListener listener) {
        mAdapter.setLocalDataListener(listener);
    }

    @Override
    public void requestLoad() {
        mAdapter.requestLoad();
    }

    @Override
    public void addNewVideo(Uri uri) {
        mAdapter.addNewVideo(uri);
    }

    @Override
    public void addNewPhoto(Uri uri) {
        mAdapter.addNewPhoto(uri);
    }

    @Override
    public void addNewSession(Uri uri) {
        mAdapter.addNewSession(uri);
    }

    @Override
    public void insertData(LocalData data) {
        mAdapter.insertData(data);
    }

    @Override
    public void flush() {
        mAdapter.flush();
    }

    @Override
    public boolean executeDeletion() {
        return mAdapter.executeDeletion();
    }

    @Override
    public boolean undoDataRemoval() {
        return mAdapter.undoDataRemoval();
    }

    @Override
    public void finishSession(Uri uri) {
        mAdapter.finishSession(uri);
    }
    @Override
    public void refresh(Uri uri) {
        mAdapter.refresh(uri);
    }

    @Override
    public void updateMetadata(int dataId) {
        mAdapter.updateMetadata(dataId);
    }

    @Override
    public boolean isMetadataUpdated(int dataId) {
        return mAdapter.isMetadataUpdated(dataId);
    }
}
