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
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ShareActionProvider;
import android.widget.TextView;

import java.util.ArrayList;

public class GalleryActionBar implements ActionBar.OnNavigationListener {
    private static final String TAG = "GalleryActionBar";

    public interface ClusterRunner {
        public void doCluster(int id);
    }

    private static class ActionItem {
        public int action;
        public boolean enabled;
        public boolean visible;
        public int spinnerTitle;
        public int dialogTitle;
        public int clusterBy;

        public ActionItem(int action, boolean applied, boolean enabled, int title,
                int clusterBy) {
            this(action, applied, enabled, title, title, clusterBy);
        }

        public ActionItem(int action, boolean applied, boolean enabled, int spinnerTitle,
                int dialogTitle, int clusterBy) {
            this.action = action;
            this.enabled = enabled;
            this.spinnerTitle = spinnerTitle;
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

    private class ClusterAdapter extends BaseAdapter {

        public int getCount() {
            return sClusterItems.length;
        }

        public Object getItem(int position) {
            return sClusterItems[position];
        }

        public long getItemId(int position) {
            return sClusterItems[position].action;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.action_bar_text,
                        parent, false);
            }
            TextView view = (TextView) convertView;
            view.setText(sClusterItems[position].spinnerTitle);
            return convertView;
        }
    }

    private ClusterRunner mClusterRunner;
    private CharSequence[] mTitles;
    private ArrayList<Integer> mActions;
    private Context mContext;
    private LayoutInflater mInflater;
    private GalleryActivity mActivity;
    private ActionBar mActionBar;
    private int mCurrentIndex;
    private ClusterAdapter mAdapter = new ClusterAdapter();

    public GalleryActionBar(GalleryActivity activity) {
        mActionBar = ((Activity) activity).getActionBar();
        mContext = activity.getAndroidContext();
        mActivity = activity;
        mInflater = ((Activity) mActivity).getLayoutInflater();
        mCurrentIndex = 0;
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
        return sClusterItems[mCurrentIndex].action;
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

    public void showClusterMenu(int action, ClusterRunner runner) {
        Log.v(TAG, "showClusterMenu: runner=" + runner);
        // Don't set cluster runner until action bar is ready.
        mClusterRunner = null;
        mActionBar.setListNavigationCallbacks(mAdapter, this);
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        setSelectedAction(action);
        mClusterRunner = runner;
    }

    public void hideClusterMenu() {
        mClusterRunner = null;
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
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

    public boolean setSelectedAction(int type) {
        for (int i = 0, n = sClusterItems.length; i < n; i++) {
            ActionItem item = sClusterItems[i];
            if (item.visible && item.action == type) {
                mActionBar.setSelectedNavigationItem(i);
                mCurrentIndex = i;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        if (itemPosition != mCurrentIndex && mClusterRunner != null) {
            mActivity.getGLRoot().lockRenderThread();
            try {
                mClusterRunner.doCluster(sClusterItems[itemPosition].action);
            } finally {
                mActivity.getGLRoot().unlockRenderThread();
            }
        }
        return false;
    }
}
