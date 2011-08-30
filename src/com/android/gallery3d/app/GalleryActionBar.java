/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.gallery3d.app;

import com.android.gallery3d.R;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ShareActionProvider;

import java.util.ArrayList;

public class GalleryActionBar implements ActionBar.TabListener {
    private static final String TAG = "GalleryActionBar";

    public interface ClusterRunner {
        public void doCluster(int id);
    }

    private static class ActionItem {
        public int action;
        public boolean enabled;
        public boolean visible;
        public int tabTitle;
        public int dialogTitle;
        public int clusterBy;

        public ActionItem(int action, boolean applied, boolean enabled, int title,
                int clusterBy) {
            this(action, applied, enabled, title, title, clusterBy);
        }

        public ActionItem(int action, boolean applied, boolean enabled, int tabTitle,
                int dialogTitle, int clusterBy) {
            this.action = action;
            this.enabled = enabled;
            this.tabTitle = tabTitle;
            this.dialogTitle = dialogTitle;
            this.clusterBy = clusterBy;
            this.visible = true;
        }
    }

    private static final ActionItem[] sClusterItems = new ActionItem[] {
        new ActionItem(FilterUtils.CLUSTER_BY_ALBUM, true, false, R.string.albums,
                R.string.group_by_album),
        new ActionItem(FilterUtils.CLUSTER_BY_LOCATION, true, false,
                R.string.locations, R.string.location, R.string.group_by_location),
        new ActionItem(FilterUtils.CLUSTER_BY_TIME, true, false, R.string.times,
                R.string.time, R.string.group_by_time),
        new ActionItem(FilterUtils.CLUSTER_BY_FACE, true, false, R.string.people,
                R.string.group_by_faces),
        new ActionItem(FilterUtils.CLUSTER_BY_TAG, true, false, R.string.tags,
                R.string.group_by_tags)
    };

    private ClusterRunner mClusterRunner;
    private CharSequence[] mTitles;
    private ArrayList<Integer> mActions;
    private Context mContext;
    private ActionBar mActionBar;
    // We need this because ActionBar.getSelectedTab() doesn't work when
    // ActionBar is hidden.
    private Tab mCurrentTab;

    public GalleryActionBar(Activity activity) {
        mActionBar = activity.getActionBar();
        mContext = activity;

        for (ActionItem item : sClusterItems) {
            mActionBar.addTab(mActionBar.newTab().setText(item.tabTitle).
                    setTag(item).setTabListener(this));
        }
    }

    public static int getHeight(Activity activity) {
        ActionBar actionBar = activity.getActionBar();
        return actionBar != null ? actionBar.getHeight() : 0;
    }

    private void createDialogData() {
        ArrayList<CharSequence> titles = new ArrayList<CharSequence>();
        mActions = new ArrayList<Integer>();
        for (ActionItem item : sClusterItems) {
            if (item.enabled && item.visible) {
                titles.add(mContext.getString(item.dialogTitle));
                mActions.add(item.action);
            }
        }
        mTitles = new CharSequence[titles.size()];
        titles.toArray(mTitles);
    }

    public void setClusterItemEnabled(int id, boolean enabled) {
        for (ActionItem item : sClusterItems) {
            if (item.action == id) {
                item.enabled = enabled;
                return;
            }
        }
    }

    public void setClusterItemVisibility(int id, boolean visible) {
        for (ActionItem item : sClusterItems) {
            if (item.action == id) {
                item.visible = visible;
                return;
            }
        }
    }

    public int getClusterTypeAction() {
        if (mCurrentTab != null) {
            ActionItem item = (ActionItem) mCurrentTab.getTag();
            return item.action;
        }
        // By default, it's group-by-album
        return FilterUtils.CLUSTER_BY_ALBUM;
    }

    public static String getClusterByTypeString(Context context, int type) {
        for (ActionItem item : sClusterItems) {
            if (item.action == type) {
                return context.getString(item.clusterBy);
            }
        }
        return null;
    }

    public static ShareActionProvider initializeShareActionProvider(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_share);
        ShareActionProvider shareActionProvider = null;
        if (item != null) {
            shareActionProvider = (ShareActionProvider) item.getActionProvider();
        }
        return shareActionProvider;
    }

    public void showClusterTabs(ClusterRunner runner) {
        Log.v(TAG, "showClusterTabs: runner=" + runner);
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        mClusterRunner = runner;
    }

    public void hideClusterTabs() {
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        mClusterRunner = null;
        Log.v(TAG, "hideClusterTabs: runner=" + mClusterRunner);
    }

    public void showClusterDialog(final ClusterRunner clusterRunner) {
        createDialogData();
        final ArrayList<Integer> actions = mActions;
        new AlertDialog.Builder(mContext).setTitle(R.string.group_by).setItems(
                mTitles, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                clusterRunner.doCluster(actions.get(which).intValue());
            }
        }).create().show();
    }

    public void setTitle(String title) {
        if (mActionBar != null) mActionBar.setTitle(title);
    }

    public void setTitle(int titleId) {
        if (mActionBar != null) mActionBar.setTitle(titleId);
    }

    public void setSubtitle(String title) {
        if (mActionBar != null) mActionBar.setSubtitle(title);
    }

    public void setNavigationMode(int mode) {
        if (mActionBar != null) mActionBar.setNavigationMode(mode);
    }

    public int getHeight() {
        return mActionBar == null ? 0 : mActionBar.getHeight();
    }

    @Override
    public void onTabSelected(Tab tab, FragmentTransaction ft) {
        if (mCurrentTab == tab) return;
        mCurrentTab = tab;
        ActionItem item = (ActionItem) tab.getTag();
        Log.v(TAG, "onTabSelected: clusterrRunner=" + mClusterRunner);
        if (mClusterRunner != null) mClusterRunner.doCluster(item.action);
    }

    @Override
    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
    }

    @Override
    public void onTabReselected(Tab tab, FragmentTransaction ft) {
    }

    public boolean setSelectedTab(int type) {
        for (int i = 0, n = sClusterItems.length; i < n; ++i) {
            ActionItem item = sClusterItems[i];
            if (item.visible && item.action == type) {
                mActionBar.selectTab(mActionBar.getTabAt(i));
                return true;
            }
        }
        return false;
    }
}
