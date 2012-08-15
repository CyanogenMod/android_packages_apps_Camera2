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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.SpinnerAdapter;

import com.android.gallery3d.R;

public class SimpleActionBar implements ActionBarInterface {
    private final FrameLayout mHeaderView;
    private final SimpleActionBarView mActionBar;
    private final SimpleMenu mOptionsMenu = new SimpleMenu();
    private final Context mContext;

    public SimpleActionBar(Activity activity) {
        mContext = activity;
        mHeaderView = (FrameLayout) activity.findViewById(R.id.header);
        mActionBar = new SimpleActionBarView(activity, null);

        if (mHeaderView != null) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

            // Unhide the next line to show the menu button
            // mHeaderView.setVisibility(View.VISIBLE);
            mHeaderView.addView(mActionBar, params);
        }
    }

    @Override
    public void setMenuItemVisible(int menuItemId, boolean visible) {
        mOptionsMenu.setMenuItemVisible(menuItemId, visible);
    }

    @Override
    public void setMenuItemTitle(int menuItemId, String title) {
        mOptionsMenu.setMenuItemTitle(menuItemId, title);
    }

    @Override
    public void setMenuItemIntent(int menuItemId, Intent intent) {
        mOptionsMenu.setMenuItemIntent(menuItemId, intent);
    }

    @Override
    public int getHeight() {
        return mActionBar.getHeight();
    }

    @Override
    public void setListNavigationCallbacks(SpinnerAdapter adapter, OnNavigationListener listener) {
    }

    @Override
    public void setNavigationMode(int mode) {
    }

    @Override
    public void setSelectedNavigationItem(int index) {
    }

    @Override
    public void addOnMenuVisibilityListener(OnMenuVisibilityListener l) {
    }

    @Override
    public void removeOnMenuVisibilityListener(OnMenuVisibilityListener l) {
    }

    @Override
    public void setDisplayOptions(int options, int mask) {
    }

    @Override
    public void setHomeButtonEnabled(boolean enabled) {
    }

    @Override
    public void setTitle(String title) {
    }

    @Override
    public void setSubtitle(String subtitle) {
    }

    @Override
    public void show() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void setShareIntent(Intent intent) {
    }

    @Override
    public void setLogo(Drawable logo) {
    }

    @Override
    public boolean createActionMenu(Menu menu, int menuResId) {
        SimpleMenuInflater inflater = new SimpleMenuInflater(mContext);
        mOptionsMenu.clear();
        inflater.inflate(mOptionsMenu, menuResId);
        mActionBar.setOptionsMenu(mOptionsMenu);
        return false;
    }

    @Override
    public boolean hasShareMenuItem() {
        return false;
    }
}
