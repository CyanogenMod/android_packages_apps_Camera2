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

import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.Files.FileColumns;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.photos.adapters.AlbumSetCursorAdapter;
import com.android.photos.data.AlbumSetLoader;
import com.android.photos.shims.LoaderCompatShim;
import com.android.photos.shims.MediaSetLoader;

import java.util.ArrayList;


public class AlbumSetFragment extends Fragment implements OnItemClickListener,
    LoaderCallbacks<Cursor>, MultiChoiceManager.Delegate, SelectionManager.Client {

    private GridView mAlbumSetView;
    private View mEmptyView;
    private AlbumSetCursorAdapter mAdapter;
    private LoaderCompatShim<Cursor> mLoaderCompatShim;
    private MultiChoiceManager mMultiChoiceManager;
    private SelectionManager mSelectionManager;

    private static final int LOADER_ALBUMSET = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = getActivity();
        mAdapter = new AlbumSetCursorAdapter(context);
        mMultiChoiceManager = new MultiChoiceManager(context, this);
        mMultiChoiceManager.setSelectionManager(mSelectionManager);
    }

    @Override
    public void setSelectionManager(SelectionManager manager) {
        mSelectionManager = manager;
        if (mMultiChoiceManager != null) {
            mMultiChoiceManager.setSelectionManager(manager);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.album_set, container, false);
        mAlbumSetView = (GridView) root.findViewById(android.R.id.list);
        mEmptyView = root.findViewById(android.R.id.empty);
        mEmptyView.setVisibility(View.GONE);
        mAlbumSetView.setAdapter(mAdapter);
        mAlbumSetView.setChoiceMode(GridView.CHOICE_MODE_MULTIPLE_MODAL);
        mAlbumSetView.setMultiChoiceModeListener(mMultiChoiceManager);
        mAlbumSetView.setOnItemClickListener(this);
        getLoaderManager().initLoader(LOADER_ALBUMSET, null, this);
        updateEmptyStatus();
        return root;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // TODO: Switch to AlbumSetLoader
        MediaSetLoader loader = new MediaSetLoader(getActivity());
        mAdapter.setDrawableFactory(loader);
        mLoaderCompatShim = loader;
        return loader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader,
            Cursor data) {
        mAdapter.swapCursor(data);
        updateEmptyStatus();
    }

    private void updateEmptyStatus() {
        boolean empty = (mAdapter == null || mAdapter.getCount() == 0);
        mAlbumSetView.setVisibility(empty ? View.GONE : View.VISIBLE);
        mEmptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    @Override
    public void onItemClick(AdapterView<?> av, View v, int pos, long id) {
        if (mLoaderCompatShim == null) {
            // Not fully initialized yet, discard
            return;
        }
        Cursor item = (Cursor) mAdapter.getItem(pos);
        Toast.makeText(v.getContext(),
                "Tapped " + item.getInt(AlbumSetLoader.INDEX_ID),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public int getItemMediaType(Object item) {
        return FileColumns.MEDIA_TYPE_NONE;
    }

    @Override
    public int getItemSupportedOperations(Object item) {
        return ((Cursor) item).getInt(AlbumSetLoader.INDEX_SUPPORTED_OPERATIONS);
    }

    @Override
    public Object getItemAtPosition(int position) {
        return mAdapter.getItem(position);
    }

    @Override
    public ArrayList<Uri> getSubItemUrisForItem(Object item) {
        return mLoaderCompatShim.urisForSubItems((Cursor) item);
    }

    @Override
    public Object getPathForItemAtPosition(int position) {
        return mLoaderCompatShim.getPathForItem((Cursor) mAdapter.getItem(position));
    }

    @Override
    public void deleteItemWithPath(Object itemPath) {
        mLoaderCompatShim.deleteItemWithPath(itemPath);
    }

    @Override
    public SparseBooleanArray getSelectedItemPositions() {
        return mAlbumSetView.getCheckedItemPositions();
    }

    @Override
    public int getSelectedItemCount() {
        return mAlbumSetView.getCheckedItemCount();
    }

    @Override
    public Uri getItemUri(Object item) {
        return mLoaderCompatShim.uriForItem((Cursor) item);
    }
}
