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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.ShareActionProvider;
import android.widget.ShareActionProvider.OnShareTargetSelectedListener;

import com.android.gallery3d.R;
import com.android.gallery3d.data.MediaObject;

import java.util.ArrayList;
import java.util.List;

public class MultiChoiceManager implements MultiChoiceModeListener,
    OnShareTargetSelectedListener, SelectionManager.SelectedUriSource {

    public interface Delegate {
        public SparseBooleanArray getSelectedItemPositions();
        public int getSelectedItemCount();
        public int getItemMediaType(Object item);
        public int getItemSupportedOperations(Object item);
        public ArrayList<Uri> getSubItemUrisForItem(Object item);
        public Object getItemAtPosition(int position);
        public Object getPathForItemAtPosition(int position);
        public void deleteItemWithPath(Object itemPath);
    }

    private SelectionManager mSelectionManager;
    private ShareActionProvider mShareActionProvider;
    private ActionMode mActionMode;
    private int numSubItemsCollected = 0;
    private Context mContext;
    private Delegate mDelegate;

    private ArrayList<Uri> mSelectedUrisArray = new ArrayList<Uri>();

    public MultiChoiceManager(Context context, Delegate delegate) {
        mContext = context;
        mDelegate = delegate;
    }

    public void setSelectionManager(SelectionManager selectionManager) {
        mSelectionManager = selectionManager;
    }

    @Override
    public ArrayList<Uri> getSelectedShareableUris() {
        return mSelectedUrisArray;
    }

    private void updateSelectedTitle(ActionMode mode) {
        int count = mDelegate.getSelectedItemCount();
        mode.setTitle(mContext.getResources().getQuantityString(
                R.plurals.number_of_items_selected, count, count));
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
            boolean checked) {
        updateSelectedTitle(mode);
        Object item = mDelegate.getItemAtPosition(position);

        ArrayList<Uri> subItems = mDelegate.getSubItemUrisForItem(item);
        if (checked) {
            mSelectedUrisArray.addAll(subItems);
            numSubItemsCollected += subItems.size();
        } else {
            mSelectedUrisArray.removeAll(subItems);
            numSubItemsCollected -= subItems.size();
        }

        mSelectionManager.onItemSelectedStateChanged(mShareActionProvider,
                mDelegate.getItemMediaType(item),
                mDelegate.getItemSupportedOperations(item),
                checked);
        updateActionItemVisibilities(mode.getMenu(),
                mSelectionManager.getSupportedOperations());
    }

    private void updateActionItemVisibilities(Menu menu, int supportedOperations) {
        MenuItem shareItem = menu.findItem(R.id.menu_share);
        MenuItem deleteItem = menu.findItem(R.id.menu_delete);
        shareItem.setVisible((supportedOperations & MediaObject.SUPPORT_SHARE) > 0);
        deleteItem.setVisible((supportedOperations & MediaObject.SUPPORT_DELETE) > 0);
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mSelectionManager.setSelectedUriSource(this);
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
        // onDestroyActionMode gets called when the share target was selected,
        // but apparently before the ArrayList is serialized in the intent
        // so we can't clear the old one here.
        mSelectedUrisArray = new ArrayList<Uri>();
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
        mActionMode.finish();
        return false;
    }

    private static class BulkDeleteTask extends AsyncTask<Void, Void, Void> {
        private Delegate mDelegate;
        private List<Object> mPaths;

        public BulkDeleteTask(Delegate delegate, List<Object> paths) {
            mDelegate = delegate;
            mPaths = paths;
        }

        @Override
        protected Void doInBackground(Void... ignored) {
            for (Object path : mPaths) {
                mDelegate.deleteItemWithPath(path);
            }
            return null;
        }
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_delete:
                BulkDeleteTask deleteTask = new BulkDeleteTask(mDelegate,
                        getPathsForSelectedItems());
                deleteTask.execute();
                mode.finish();
                return true;
            default:
                return false;
        }
    }

    private List<Object> getPathsForSelectedItems() {
        List<Object> paths = new ArrayList<Object>();
        SparseBooleanArray selected = mDelegate.getSelectedItemPositions();
        for (int i = 0; i < selected.size(); i++) {
            if (selected.valueAt(i)) {
                paths.add(mDelegate.getPathForItemAtPosition(i));
            }
        }
        return paths;
    }
}
