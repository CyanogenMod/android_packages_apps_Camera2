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
import android.os.AsyncTask;

import com.android.camera.util.Callback;

import java.util.List;

/**
 * An abstract {@link LocalFilmstripDataAdapter} implementation to wrap another
 * {@link LocalFilmstripDataAdapter}. All implementations related to data id is not
 * addressed in this abstract class since wrapping another data adapter
 * surely makes things different for data id.
 *
 * @see FixedFirstProxyAdapter
 * @see FixedLastProxyAdapter
 */
public abstract class FilmstripDataAdapterProxy implements
      LocalFilmstripDataAdapter {

    protected final Context mContext;
    protected final LocalFilmstripDataAdapter mAdapter;
    protected int mSuggestedWidth;
    protected int mSuggestedHeight;

    /**
     * Constructor.
     *
     * @param context A valid Android context.
     * @param wrappedAdapter The {@link LocalFilmstripDataAdapter} to be wrapped.
     */
    FilmstripDataAdapterProxy(Context context,
          LocalFilmstripDataAdapter wrappedAdapter) {
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
    public void setLocalDataListener(FilmstripItemListener listener) {
        mAdapter.setLocalDataListener(listener);
    }

    @Override
    public void requestLoad(Callback<Void> onDone) {
        mAdapter.requestLoad(onDone);
    }

    @Override
    public void requestLoadNewPhotos() {
        mAdapter.requestLoadNewPhotos();
    }

    @Override
    public boolean addOrUpdate(FilmstripItem item) {
        return mAdapter.addOrUpdate(item);
    }

    @Override
    public void clear() {
        mAdapter.clear();
    }

    @Override
    public boolean executeDeletion() {
        return mAdapter.executeDeletion();
    }

    @Override
    public boolean undoDeletion() {
        return mAdapter.undoDeletion();
    }

    @Override
    public void refresh(Uri uri) {
        mAdapter.refresh(uri);
    }

    @Override
    public AsyncTask updateMetadataAt(int index) {
        return mAdapter.updateMetadataAt(index);
    }

    @Override
    public boolean isMetadataUpdatedAt(int index) {
        return mAdapter.isMetadataUpdatedAt(index);
    }

    @Override
    public List<AsyncTask> preloadItems(List<Integer> items) {
        return mAdapter.preloadItems(items);
    }

    @Override
    public void cancelItems(List<AsyncTask> loadTokens) {
        mAdapter.cancelItems(loadTokens);
    }

    @Override
    public List<Integer> getItemsInRange(int startPosition, int endPosition) {
        return mAdapter.getItemsInRange(startPosition, endPosition);
    }

    @Override
    public int getCount() {
        return mAdapter.getCount();
    }
}
