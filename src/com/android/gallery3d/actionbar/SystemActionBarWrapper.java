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
import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ShareActionProvider;
import android.widget.SpinnerAdapter;

import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;

import java.util.HashMap;

@TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
public class SystemActionBarWrapper implements ActionBarInterface {
    private final ActionBar mActionBar;
    private final Activity mActivity;
    private final HashMap<Integer, Object> mListenerMap = new HashMap<Integer, Object>();

    private Menu mMenu;
    private MenuItem mShareMenuItem;
    private ShareActionProvider mShareActionProvider;

    public SystemActionBarWrapper(Activity activity) {
        mActivity = activity;
        mActionBar = activity.getActionBar();
    }

    @Override
    public int getHeight() {
        return mActionBar.getHeight();
    }

    @Override
    public void setListNavigationCallbacks(
            SpinnerAdapter adapter, final OnNavigationListener listener) {
        mActionBar.setListNavigationCallbacks(adapter, new ActionBar.OnNavigationListener() {
                @Override
            public boolean onNavigationItemSelected(int itemPosition, long itemId) {
                return listener.onNavigationItemSelected(itemPosition, itemId);
            }
        });
    }

    @Override
    public void setNavigationMode(int mode) {
        mActionBar.setNavigationMode(mode);
    }

    @Override
    public void setSelectedNavigationItem(int index) {
        mActionBar.setSelectedNavigationItem(index);
    }

    @Override
    public void addOnMenuVisibilityListener(final OnMenuVisibilityListener l) {
        ActionBar.OnMenuVisibilityListener wrapper =
                new ActionBar.OnMenuVisibilityListener() {
            @Override
            public void onMenuVisibilityChanged(boolean isVisible) {
                l.onMenuVisibilityChanged(isVisible);
            }
        };
        Utils.assertTrue(mListenerMap.put(System.identityHashCode(l), wrapper) == null);
        mActionBar.addOnMenuVisibilityListener(wrapper);
    }

    @Override
    public void removeOnMenuVisibilityListener(OnMenuVisibilityListener l) {
        ActionBar.OnMenuVisibilityListener wrapper = (ActionBar.OnMenuVisibilityListener)
                mListenerMap.remove(System.identityHashCode(l));
        if (wrapper != null) mActionBar.removeOnMenuVisibilityListener(wrapper);
    }

    @Override
    public void setDisplayOptions(int options, int mask) {
        mActionBar.setDisplayOptions(options, mask);
    }

    @Override
    public void setHomeButtonEnabled(boolean enabled) {
        mActionBar.setHomeButtonEnabled(enabled);
    }

    @Override
    public void setTitle(String title) {
        mActionBar.setTitle(title);
    }

    @Override
    public void setLogo(Drawable logo) {
        mActionBar.setLogo(logo);
    }

    @Override
    public void setSubtitle(String subtitle) {
        mActionBar.setSubtitle(subtitle);
    }

    @Override
    public void show() {
        mActionBar.show();
    }

    @Override
    public void hide() {
        mActionBar.hide();
    }

    @Override
    public void setShareIntent(Intent intent) {
        if (mShareMenuItem != null) {
            mShareMenuItem.setEnabled(intent != null);
        }
        if (mShareActionProvider != null) {
            mShareActionProvider.setShareIntent(intent);
        }
    }

    @Override
    public boolean createActionMenu(Menu menu, int menuRes) {
        mActivity.getMenuInflater().inflate(menuRes, menu);
        mMenu = menu;
        mShareMenuItem = menu.findItem(R.id.action_share);
        if (mShareMenuItem != null) {
            mShareActionProvider = new ShareActionProvider(mActivity);
            mShareMenuItem.setActionProvider(mShareActionProvider);
        }
        return true;
    }

    @Override
    public boolean hasShareMenuItem() {
        return mShareMenuItem != null;
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