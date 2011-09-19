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

package com.android.gallery3d.ui;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryActionBar;
import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.ui.CustomMenu.DropDownMenu;
import com.android.gallery3d.ui.MenuExecutor.ProgressListener;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.ShareActionProvider.OnShareTargetSelectedListener;
import android.widget.ShareActionProvider;

import java.util.ArrayList;

public class ActionModeHandler implements ActionMode.Callback {
    private static final String TAG = "ActionModeHandler";
    private static final int SUPPORT_MULTIPLE_MASK = MediaObject.SUPPORT_DELETE
            | MediaObject.SUPPORT_ROTATE | MediaObject.SUPPORT_SHARE
            | MediaObject.SUPPORT_CACHE | MediaObject.SUPPORT_IMPORT;

    public interface ActionModeListener {
        public boolean onActionItemClicked(MenuItem item);
    }

    private final GalleryActivity mActivity;
    private final MenuExecutor mMenuExecutor;
    private final SelectionManager mSelectionManager;
    private Menu mMenu;
    private DropDownMenu mSelectionMenu;
    private ActionModeListener mListener;
    private Future<?> mMenuTask;
    private Handler mMainHandler;
    private ShareActionProvider mShareActionProvider;

    public ActionModeHandler(
            GalleryActivity activity, SelectionManager selectionManager) {
        mActivity = Utils.checkNotNull(activity);
        mSelectionManager = Utils.checkNotNull(selectionManager);
        mMenuExecutor = new MenuExecutor(activity, selectionManager);
        mMainHandler = new Handler(activity.getMainLooper());
    }

    public ActionMode startActionMode() {
        Activity a = (Activity) mActivity;
        final ActionMode actionMode = a.startActionMode(this);
        CustomMenu customMenu = new CustomMenu(a);
        View customView = LayoutInflater.from(a).inflate(
                R.layout.action_mode, null);
        actionMode.setCustomView(customView);
        mSelectionMenu = customMenu.addDropDownMenu(
                (Button) customView.findViewById(R.id.selection_menu),
                R.menu.selection);
        updateSelectionMenu();
        customMenu.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                return onActionItemClicked(actionMode, item);
            }
        });
        return actionMode;
    }

    public void setTitle(String title) {
        mSelectionMenu.setTitle(title);
    }

    public void setActionModeListener(ActionModeListener listener) {
        mListener = listener;
    }

    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        boolean result;
        if (mListener != null) {
            result = mListener.onActionItemClicked(item);
            if (result) {
                mSelectionManager.leaveSelectionMode();
                return result;
            }
        }
        ProgressListener listener = null;
        if (item.getItemId() == R.id.action_import) {
            listener = new ImportCompleteListener(mActivity);
        }
        result = mMenuExecutor.onMenuClicked(item, listener);
        if (item.getItemId() == R.id.action_select_all) {
            updateSupportedOperation();
            updateSelectionMenu();
        }
        return result;
    }

    private void updateSelectionMenu() {
        // update title
        int count = mSelectionManager.getSelectedCount();
        String format = mActivity.getResources().getQuantityString(
                R.plurals.number_of_items_selected, count);
        setTitle(String.format(format, count));
        // For clients who call SelectionManager.selectAll() directly, we need to ensure the
        // menu status is consistent with selection manager.
        MenuItem item = mSelectionMenu.findItem(R.id.action_select_all);
        if (item != null) {
            if (mSelectionManager.inSelectAllMode()) {
                item.setChecked(true);
                item.setTitle(R.string.deselect_all);
            } else {
                item.setChecked(false);
                item.setTitle(R.string.select_all);
            }
        }
    }

    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.operation, menu);

        mShareActionProvider = GalleryActionBar.initializeShareActionProvider(menu);
        OnShareTargetSelectedListener listener = new OnShareTargetSelectedListener() {
            public boolean onShareTargetSelected(ShareActionProvider source, Intent intent) {
                mSelectionManager.leaveSelectionMode();
                return false;
            }
        };

        mShareActionProvider.setOnShareTargetSelectedListener(listener);
        mMenu = menu;
        return true;
    }

    public void onDestroyActionMode(ActionMode mode) {
        mSelectionManager.leaveSelectionMode();
    }

    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return true;
    }

    // Menu options are determined by selection set itself.
    // We cannot expand it because MenuExecuter executes it based on
    // the selection set instead of the expanded result.
    // e.g. LocalImage can be rotated but collections of them (LocalAlbum) can't.
    private void updateMenuOptions(JobContext jc) {
        ArrayList<Path> paths = mSelectionManager.getSelected(false);
        if (paths.size() == 0) return;

        int operation = MediaObject.SUPPORT_ALL;
        DataManager manager = mActivity.getDataManager();
        int type = 0;
        for (Path path : paths) {
            if (jc.isCancelled()) return;
            int support = manager.getSupportedOperations(path);
            type |= manager.getMediaType(path);
            operation &= support;
        }

        final String mimeType = MenuExecutor.getMimeType(type);
        if (paths.size() == 1) {
            if (!GalleryUtils.isEditorAvailable((Context) mActivity, mimeType)) {
                operation &= ~MediaObject.SUPPORT_EDIT;
            }
        } else {
            operation &= SUPPORT_MULTIPLE_MASK;
        }

        final int supportedOperation = operation;

        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mMenuTask = null;
                MenuExecutor.updateMenuOperation(mMenu, supportedOperation);
            }
        });
    }

    // Share intent needs to expand the selection set so we can get URI of
    // each media item
    private void updateSharingIntent(JobContext jc) {
        if (mShareActionProvider == null) return;
        ArrayList<Path> paths = mSelectionManager.getSelected(true);
        if (paths.size() == 0) return;

        final ArrayList<Uri> uris = new ArrayList<Uri>();

        DataManager manager = mActivity.getDataManager();
        int type = 0;

        final Intent intent = new Intent();
        for (Path path : paths) {
            int support = manager.getSupportedOperations(path);
            type |= manager.getMediaType(path);

            if ((support & MediaObject.SUPPORT_SHARE) != 0) {
                uris.add(manager.getContentUri(path));
            }
        }

        final int size = uris.size();
        if (size > 0) {
            final String mimeType = MenuExecutor.getMimeType(type);
            if (size > 1) {
                intent.setAction(Intent.ACTION_SEND_MULTIPLE).setType(mimeType);
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            } else {
                intent.setAction(Intent.ACTION_SEND).setType(mimeType);
                intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            }
            intent.setType(mimeType);

            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Log.v(TAG, "Sharing intent is ready: action = " + intent.getAction());
                    mShareActionProvider.setShareIntent(intent);
                }
            });
        }
    }

    public void updateSupportedOperation(Path path, boolean selected) {
        // TODO: We need to improve the performance
        updateSupportedOperation();
    }

    public void updateSupportedOperation() {
        if (mMenuTask != null) {
            mMenuTask.cancel();
        }

        // Disable share action until share intent is in good shape
        if (mShareActionProvider != null) {
            Log.v(TAG, "Disable sharing until intent is ready");
            mShareActionProvider.setShareIntent(null);
        }

        // Generate sharing intent and update supported operations in the background
        mMenuTask = mActivity.getThreadPool().submit(new Job<Void>() {
            public Void run(JobContext jc) {
                updateMenuOptions(jc);
                updateSharingIntent(jc);
                return null;
            }
        });
    }

    public void pause() {
        if (mMenuTask != null) {
            mMenuTask.cancel();
            mMenuTask = null;
        }
        mMenuExecutor.pause();
    }

    public void resume() {
        updateSupportedOperation();
    }
}
