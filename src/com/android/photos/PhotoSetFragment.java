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
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.ShareActionProvider;
import android.widget.ShareActionProvider.OnShareTargetSelectedListener;

import com.android.gallery3d.R;
import com.android.gallery3d.app.Gallery;
import com.android.gallery3d.data.MediaItem;
import com.android.photos.adapters.PhotoThumbnailAdapter;
import com.android.photos.data.PhotoSetLoader;
import com.android.photos.shims.LoaderCompatShim;
import com.android.photos.shims.MediaItemsLoader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class PhotoSetFragment extends Fragment implements LoaderCallbacks<Cursor>,
        OnItemClickListener, SelectionManager.SelectedUriSource, MultiChoiceModeListener,
        OnShareTargetSelectedListener {

    private static final int LOADER_PHOTOSET = 1;

    private GridView mPhotoSetView;
    private View mEmptyView;

    private boolean mInitialLoadComplete = false;
    private LoaderCompatShim<Cursor> mLoaderCompatShim;
    private PhotoThumbnailAdapter mAdapter;
    private SelectionManager mSelectionManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GalleryActivity activity = (GalleryActivity) getActivity();
        mSelectionManager = activity.getSelectionManager();
        mAdapter = new PhotoThumbnailAdapter(activity);
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
        mPhotoSetView.setMultiChoiceModeListener(this);
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

    private Set<Uri> mSelectedUris = new HashSet<Uri>();
    private ArrayList<Uri> mSelectedUrisArray = new ArrayList<Uri>();

    @Override
    public ArrayList<Uri> getSelectedShareableUris() {
        mSelectedUrisArray.clear();
        mSelectedUrisArray.addAll(mSelectedUris);
        return mSelectedUrisArray;
    }

    public ArrayList<Uri> getSelectedShareableUrisUncached() {
        mSelectedUrisArray.clear();
        SparseBooleanArray selected = mPhotoSetView.getCheckedItemPositions();

        for (int i = 0; i < selected.size(); i++) {
            if (selected.valueAt(i)) {
                Cursor item = mAdapter.getItem(selected.keyAt(i));
                int supported = item.getInt(PhotoSetLoader.INDEX_SUPPORTED_OPERATIONS);
                if ((supported & MediaItem.SUPPORT_SHARE) > 0) {
                    mSelectedUrisArray.add(mLoaderCompatShim.uriForItem(item));
                }
            }
        }

        return mSelectedUrisArray;
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }


    private ShareActionProvider mShareActionProvider;
    private ActionMode mActionMode;
    private boolean mSharePending = false;

    private void updateSelectedTitle(ActionMode mode) {
        int count = mPhotoSetView.getCheckedItemCount();
        mode.setTitle(getResources().getQuantityString(
                R.plurals.number_of_items_selected, count, count));
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
            boolean checked) {
        updateSelectedTitle(mode);
        Cursor item = mAdapter.getItem(position);

        if (checked) {
            mSelectedUris.add(mLoaderCompatShim.uriForItem(item));
        } else {
            mSelectedUris.remove(mLoaderCompatShim.uriForItem(item));
        }

        mSelectionManager.onItemSelectedStateChanged(mShareActionProvider,
                item.getInt(PhotoSetLoader.INDEX_MEDIA_TYPE),
                item.getInt(PhotoSetLoader.INDEX_SUPPORTED_OPERATIONS),
                checked);
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mSelectionManager.setSelectedUriSource(PhotoSetFragment.this);
        mActionMode = mode;
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.gallery_multiselect, menu);
        MenuItem menuItem = menu.findItem(R.id.menu_share);
        mShareActionProvider = (ShareActionProvider) menuItem.getActionProvider();
        mShareActionProvider.setOnShareTargetSelectedListener(this);
        updateSelectedTitle(mode);
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mSelectedUris.clear();
        if (mSharePending) {
            // onDestroyActionMode gets called when the share target was selected,
            // but apparently before the ArrayList is serialized in the intent
            // so we can't clear the old one here.
            mSelectedUrisArray = new ArrayList<Uri>();
            mSharePending = false;
        } else {
            mSelectedUrisArray.clear();
        }
        mSelectionManager.onClearSelection();
        mSelectionManager.setSelectedUriSource(null);
        mShareActionProvider = null;
        mActionMode = null;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        updateSelectedTitle(mode);
        return false;
    }

    @Override
    public boolean onShareTargetSelected(ShareActionProvider provider, Intent intent) {
        mSharePending = true;
        mActionMode.finish();
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        return false;
    }
}
