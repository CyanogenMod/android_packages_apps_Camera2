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

package com.android.gallery3d.ingest;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.mtp.MtpObjectInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;

import com.android.gallery3d.R;
import com.android.gallery3d.ingest.adapter.MtpAdapter;
import com.android.gallery3d.ingest.ui.DateTileView;

import java.lang.ref.WeakReference;
import java.util.Collection;

public class IngestActivity extends Activity implements
        MtpDeviceIndex.ProgressListener, ImportTask.Listener {

    private IngestService mHelperService;
    private boolean mActive = false;
    private GridView mGridView;
    private MtpAdapter mAdapter;
    private Handler mHandler;
    private ProgressDialog mProgressDialog;
    private ActionMode mActiveActionMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        doBindHelperService();

        setContentView(R.layout.ingest_activity_item_list);
        mGridView = (GridView) findViewById(R.id.ingest_gridview);
        mAdapter = new MtpAdapter(this);
        mGridView.setAdapter(mAdapter);
        mGridView.setMultiChoiceModeListener(mMultiChoiceModeListener);
        mGridView.setOnItemClickListener(mOnItemClickListener);

        mHandler = new ItemListHandler(this);
    }

    private OnItemClickListener mOnItemClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View itemView, int position, long arg3) {
            mGridView.setItemChecked(position, !mGridView.getCheckedItemPositions().get(position));
        }
    };

    private MultiChoiceModeListener mMultiChoiceModeListener = new MultiChoiceModeListener() {
        private boolean mIgnoreItemCheckedStateChanges = false;

        private void updateSelectedTitle(ActionMode mode) {
            int count = mGridView.getCheckedItemCount();
            mode.setTitle(getResources().getQuantityString(
                    R.plurals.number_of_items_selected, count, count));
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
                boolean checked) {
            if (mIgnoreItemCheckedStateChanges) return;
            if (mAdapter.itemAtPositionIsBucket(position)) {
                SparseBooleanArray checkedItems = mGridView.getCheckedItemPositions();
                mIgnoreItemCheckedStateChanges = true;
                mGridView.setItemChecked(position, false);

                // Takes advantage of the fact that SectionIndexer imposes the
                // need to clamp to the valid range
                int nextSectionStart = mAdapter.getPositionForSection(
                        mAdapter.getSectionForPosition(position) + 1);
                if (nextSectionStart == position)
                    nextSectionStart = mAdapter.getCount();

                boolean rangeValue = false; // Value we want to set all of the bucket items to

                // Determine if all the items in the bucket are currently checked, so that we
                // can uncheck them, otherwise we will check all items in the bucket.
                for (int i = position + 1; i < nextSectionStart; i++) {
                    if (checkedItems.get(i) == false) {
                        rangeValue = true;
                        break;
                    }
                }

                // Set all items in the bucket to the desired state
                for (int i = position + 1; i < nextSectionStart; i++) {
                    if (checkedItems.get(i) != rangeValue)
                        mGridView.setItemChecked(i, rangeValue);
                }

                mIgnoreItemCheckedStateChanges = false;
            }
            updateSelectedTitle(mode);
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.import_items:
                    mHelperService.importSelectedItems(
                            mGridView.getCheckedItemPositions(),
                            mAdapter);
                    mode.finish();
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.ingest_menu_item_list_selection, menu);
            updateSelectedTitle(mode);
            mActiveActionMode = mode;
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActiveActionMode = null;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            updateSelectedTitle(mode);
            return false;
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindHelperService();
    }

    @Override
    protected void onResume() {
        DateTileView.refreshLocale();
        mActive = true;
        if (mHelperService != null) mHelperService.setClientActivity(this);
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (mHelperService != null) mHelperService.setClientActivity(null);
        mActive = false;
        cleanupProgressDialog();
        super.onPause();
    }

    protected void notifyIndexChanged() {
        mAdapter.notifyDataSetChanged();
        if (mActiveActionMode != null) {
            mActiveActionMode.finish();
            mActiveActionMode = null;
        }
    }

    private static class ProgressState {
        String message;
        String title;
        int current;
        int max;

        public void reset() {
            title = null;
            message = null;
            current = 0;
            max = 0;
        }
    }

    private ProgressState mProgressState = new ProgressState();

    @Override
    public void onObjectIndexed(MtpObjectInfo object, int numVisited) {
        // Not guaranteed to be called on the UI thread
        mProgressState.reset();
        mProgressState.max = 0;
        mProgressState.message = getResources().getQuantityString(
                R.plurals.ingest_number_of_items_scanned, numVisited, numVisited);
        mHandler.sendEmptyMessage(ItemListHandler.MSG_PROGRESS_UPDATE);
    }

    @Override
    public void onSorting() {
        // Not guaranteed to be called on the UI thread
        mProgressState.reset();
        mProgressState.max = 0;
        mProgressState.message = getResources().getString(R.string.ingest_sorting);
        mHandler.sendEmptyMessage(ItemListHandler.MSG_PROGRESS_UPDATE);
    }

    @Override
    public void onIndexFinish() {
        // Not guaranteed to be called on the UI thread
        mHandler.sendEmptyMessage(ItemListHandler.MSG_PROGRESS_HIDE);
        mHandler.sendEmptyMessage(ItemListHandler.MSG_ADAPTER_NOTIFY_CHANGED);
    }

    @Override
    public void onImportProgress(final int visitedCount, final int totalCount,
            String pathIfSuccessful) {
        // Not guaranteed to be called on the UI thread
        mProgressState.reset();
        mProgressState.max = totalCount;
        mProgressState.current = visitedCount;
        mProgressState.title = getResources().getString(R.string.ingest_importing);
        mHandler.sendEmptyMessage(ItemListHandler.MSG_PROGRESS_UPDATE);
    }

    @Override
    public void onImportFinish(Collection<MtpObjectInfo> objectsNotImported) {
        // Not guaranteed to be called on the UI thread
        mHandler.sendEmptyMessage(ItemListHandler.MSG_PROGRESS_HIDE);
        // TODO: maybe show an extra dialog listing the ones that failed
        // importing, if any?
    }

    private ProgressDialog getProgressDialog() {
        if (mProgressDialog == null || !mProgressDialog.isShowing()) {
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setCancelable(false);
        }
        return mProgressDialog;
    }

    private void updateProgressDialog() {
        ProgressDialog dialog = getProgressDialog();
        boolean indeterminate = (mProgressState.max == 0);
        dialog.setIndeterminate(indeterminate);
        dialog.setProgressStyle(indeterminate ? ProgressDialog.STYLE_SPINNER
                : ProgressDialog.STYLE_HORIZONTAL);
        if (mProgressState.title != null) {
            dialog.setTitle(mProgressState.title);
        }
        if (mProgressState.message != null) {
            dialog.setMessage(mProgressState.message);
        }
        if (!indeterminate) {
            dialog.setProgress(mProgressState.current);
            dialog.setMax(mProgressState.max);
        }
        if (!dialog.isShowing()) {
            dialog.show();
        }
    }

    private void cleanupProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.hide();
            mProgressDialog = null;
        }
    }

    // This is static and uses a WeakReference in order to avoid leaking the Activity
    private static class ItemListHandler extends Handler {
        public static final int MSG_PROGRESS_UPDATE = 0;
        public static final int MSG_PROGRESS_HIDE = 1;
        public static final int MSG_ADAPTER_NOTIFY_CHANGED = 2;

        WeakReference<IngestActivity> mParentReference;

        public ItemListHandler(IngestActivity parent) {
            super();
            mParentReference = new WeakReference<IngestActivity>(parent);
        }

        public void handleMessage(Message message) {
            IngestActivity parent = mParentReference.get();
            if (parent == null || !parent.mActive)
                return;
            switch (message.what) {
                case MSG_PROGRESS_HIDE:
                    parent.cleanupProgressDialog();
                    break;
                case MSG_PROGRESS_UPDATE:
                    parent.updateProgressDialog();
                    break;
                case MSG_ADAPTER_NOTIFY_CHANGED:
                    parent.notifyIndexChanged();
                    break;
                default:
                    break;
            }
        }
    }

    private ServiceConnection mHelperServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mHelperService = ((IngestService.LocalBinder) service).getService();
            mHelperService.setClientActivity(IngestActivity.this);
            mAdapter.setMtpDeviceIndex(mHelperService.getIndex());
        }

        public void onServiceDisconnected(ComponentName className) {
            mHelperService = null;
        }
    };

    private void doBindHelperService() {
        bindService(new Intent(getApplicationContext(), IngestService.class),
                mHelperServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void doUnbindHelperService() {
        if (mHelperService != null) {
            mHelperService.setClientActivity(null);
            unbindService(mHelperServiceConnection);
        }
    }
}
