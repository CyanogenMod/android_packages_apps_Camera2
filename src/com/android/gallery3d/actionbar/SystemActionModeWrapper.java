/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.actionbar;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ShareActionProvider;

import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;

@TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
public class SystemActionModeWrapper implements ActionModeInterface {
    private ActionMode mActionMode;
    private Menu mMenu;
    private MenuItem mShareMenuItem;
    private ShareActionProvider mShareActionProvider;

    public SystemActionModeWrapper(Activity activity, ActionModeInterface.Callback callback) {
        // mActionMode will be set in callback.onCreateActionMode
        activity.startActionMode(new CallbackWrapper(callback));
    }

    private class CallbackWrapper implements ActionMode.Callback {
        ActionModeInterface.Callback mActual;

        public CallbackWrapper(Callback callback) {
            mActual = callback;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mMenu = menu;
            mActionMode = mode;
            return mActual.onCreateActionMode(SystemActionModeWrapper.this, menu);
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            // return true to turn on the system action mode
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return mActual.onActionItemClicked(SystemActionModeWrapper.this, item);
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActual.onDestroyActionMode(SystemActionModeWrapper.this);
        }
    }

    @Override
    public void setCustomView(View view) {
        mActionMode.setCustomView(view);
    }

    @Override
    public void finish() {
        mActionMode.finish();
    }

    @Override
    public void inflateMenu(int menuRes) {
        Utils.assertTrue(mMenu != null);
        mActionMode.getMenuInflater().inflate(menuRes, mMenu);
        mShareActionProvider = null;
        mShareMenuItem = mMenu.findItem(R.id.action_share);
        if (mShareMenuItem != null) {
            mShareActionProvider = (ShareActionProvider) mShareMenuItem.getActionProvider();
        }
    }

    @Override
    public void setShareIntent(Intent shareIntent) {
        if (mShareMenuItem != null) {
            mShareMenuItem.setEnabled(shareIntent != null);
            mShareActionProvider.setShareIntent(shareIntent);
        }
    }

    @Override
    public boolean hasShareButton() {
        return mShareMenuItem != null;
    }

    @Override
    public void setOnShareTargetSelectedListener(final OnShareTargetSelectedListener listener) {
        mShareActionProvider.setOnShareTargetSelectedListener(
                new ShareActionProvider.OnShareTargetSelectedListener() {
            @Override
            public boolean onShareTargetSelected(ShareActionProvider source, Intent intent) {
                return listener.onShareTargetSelected(intent);
            }
        });
    }

    @Override
    public void setMenuItemVisible(int menuItemId, boolean visible) {
        MenuItem item = mMenu.findItem(menuItemId);
        if (item != null) item.setVisible(visible);
    }

    @Override
    public void setMenuItemTitle(int menuItemId, String title) {
        MenuItem item = mMenu.findItem(menuItemId);
        if (item != null) item.setTitle(title);
    }

    @Override
    public void setMenuItemIntent(int menuItemId, Intent intent) {
        MenuItem item = mMenu.findItem(menuItemId);
        if (item != null) item.setIntent(intent);
    }
}
