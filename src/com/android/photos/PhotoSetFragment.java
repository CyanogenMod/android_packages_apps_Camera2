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

package com.android.photos;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;

import com.android.gallery3d.R;
import com.android.gallery3d.app.Gallery;
import com.android.photos.adapters.PhotoThumbnailAdapter;
import com.android.photos.data.PhotoSetLoader;
import com.android.photos.shims.LoaderCompatShim;
import com.android.photos.shims.MediaItemsLoader;

import java.util.ArrayList;

public class PhotoSetFragment extends Fragment implements OnItemClickListener,
    LoaderCallbacks<Cursor>, MultiChoiceManager.Delegate {

    private static final int LOADER_PHOTOSET = 1;

    private GridView mPhotoSetView;
    private View mEmptyView;

    private boolean mInitialLoadComplete = false;
    private LoaderCompatShim<Cursor> mLoaderCompatShim;
    private PhotoThumbnailAdapter mAdapter;
    private GalleryFragmentHost mHost;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = getActivity();
        mAdapter = new PhotoThumbnailAdapter(context);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mHost = (GalleryFragmentHost) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mHost = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.photo_set, container, false);
        mPhotoSetView = (GridView) root.findViewById(android.R.id.list);
        // TODO: Remove once UI stabilizes
        mPhotoSetView.setColumnWidth(MediaItemsLoader.getThumbnailSize());
        mPhotoSetView.setOnItemClickListener(this);
        mEmptyView = root.findViewById(android.R.id.empty);
        mEmptyView.setVisibility(View.GONE);
        mPhotoSetView.setAdapter(mAdapter);
        mPhotoSetView.setChoiceMode(GridView.CHOICE_MODE_MULTIPLE_MODAL);
        mPhotoSetView.setMultiChoiceModeListener(mHost.getMultiChoiceManager());
        getLoaderManager().initLoader(LOADER_PHOTOSET, null, this);
        updateEmptyStatus();
        return root;
    }

    private void updateEmptyStatus() {
        boolean empty = (mAdapter == null || mAdapter.getCount() == 0);
        mPhotoSetView.setVisibility(empty ? View.GONE : View.VISIBLE);
        mEmptyView.setVisibility(empty && mInitialLoadComplete
                ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
        if (mLoaderCompatShim == null) {
            // Not fully initialized yet, discard
            return;
        }
        Cursor item = mAdapter.getItem(position);
        Uri uri = mLoaderCompatShim.uriForItem(item);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setClass(getActivity(), Gallery.class);
        startActivity(intent);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // TODO: Switch to PhotoSetLoader
        MediaItemsLoader loader = new MediaItemsLoader(getActivity());
        mInitialLoadComplete = false;
        mLoaderCompatShim = loader;
        mAdapter.setDrawableFactory(mLoaderCompatShim);
        return loader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader,
            Cursor data) {
        mAdapter.swapCursor(data);
        mInitialLoadComplete = true;
        updateEmptyStatus();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    @Override
    public int getItemMediaType(Object item) {
        return ((Cursor) item).getInt(PhotoSetLoader.INDEX_MEDIA_TYPE);
    }

    @Override
    public int getItemSupportedOperations(Object item) {
        return ((Cursor) item).getInt(PhotoSetLoader.INDEX_SUPPORTED_OPERATIONS);
    }

    @Override
    public Object getItemAtPosition(int position) {
        return mAdapter.getItem(position);
    }

    private ArrayList<Uri> mSubItemUriTemp = new ArrayList<Uri>(1);
    @Override
    public ArrayList<Uri> getSubItemUrisForItem(Object item) {
        mSubItemUriTemp.clear();
        mSubItemUriTemp.add(mLoaderCompatShim.uriForItem((Cursor) item));
        return mSubItemUriTemp;
    }


    @Override
    public Object getPathForItemAtPosition(int position) {
        return mLoaderCompatShim.getPathForItem(mAdapter.getItem(position));
    }

    @Override
    public void deleteItemWithPath(Object itemPath) {
        mLoaderCompatShim.deleteItemWithPath(itemPath);
    }

    @Override
    public SparseBooleanArray getSelectedItemPositions() {
        return mPhotoSetView.getCheckedItemPositions();
    }

    @Override
    public int getSelectedItemCount() {
        return mPhotoSetView.getCheckedItemCount();
    }

    @Override
    public Uri getItemUri(Object item) {
        return mLoaderCompatShim.uriForItem((Cursor) item);
    }
}
