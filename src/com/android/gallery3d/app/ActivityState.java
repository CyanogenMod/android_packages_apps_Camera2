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

package com.android.gallery3d.app;

import com.android.gallery3d.ui.GLView;

import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

abstract public class ActivityState {
    public static final int FLAG_HIDE_ACTION_BAR = 1;
    public static final int FLAG_HIDE_STATUS_BAR = 2;
    public static final int FLAG_SCREEN_ON = 3;

    private static final int SCREEN_ON_FLAGS = (
              WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        );

    protected GalleryActivity mActivity;
    protected Bundle mData;
    protected int mFlags;

    protected ResultEntry mReceivedResults;
    protected ResultEntry mResult;

    protected static class ResultEntry {
        public int requestCode;
        public int resultCode = Activity.RESULT_CANCELED;
        public Intent resultData;
        ResultEntry next;
    }

    private boolean mDestroyed = false;
    private boolean mPlugged = false;

    protected ActivityState() {
    }

    protected void setContentPane(GLView content) {
        mActivity.getGLRoot().setContentPane(content);
    }

    void initialize(GalleryActivity activity, Bundle data) {
        mActivity = activity;
        mData = data;
    }

    public Bundle getData() {
        return mData;
    }

    protected void onBackPressed() {
        mActivity.getStateManager().finishState(this);
    }

    protected void setStateResult(int resultCode, Intent data) {
        if (mResult == null) return;
        mResult.resultCode = resultCode;
        mResult.resultData = data;
    }

    protected void onConfigurationChanged(Configuration config) {
    }

    protected void onSaveState(Bundle outState) {
    }

    protected void onStateResult(int requestCode, int resultCode, Intent data) {
    }

    protected void onCreate(Bundle data, Bundle storedState) {
    }

    BroadcastReceiver mPowerIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                boolean plugged = (0 != intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0));

                if (plugged != mPlugged) {
                    mPlugged = plugged;
                    final Window win = ((Activity) mActivity).getWindow();
                    final WindowManager.LayoutParams params = win.getAttributes();
                    setScreenOnFlags(params);
                    win.setAttributes(params);
                }
            }
        }
    };

    void setScreenOnFlags(WindowManager.LayoutParams params) {
        if (mPlugged && 0 != (mFlags & FLAG_SCREEN_ON)) {
            params.flags |= SCREEN_ON_FLAGS;
        } else {
            params.flags &= ~SCREEN_ON_FLAGS;
        }
    }

    protected void onPause() {
        if (0 != (mFlags & FLAG_SCREEN_ON)) {
            ((Activity) mActivity).unregisterReceiver(mPowerIntentReceiver);
        }
    }

    // should only be called by StateManager
    void resume() {
        Activity activity = (Activity) mActivity;
        ActionBar actionBar = activity.getActionBar();
        if (actionBar != null) {
            if ((mFlags & FLAG_HIDE_ACTION_BAR) != 0) {
                actionBar.hide();
            } else {
                actionBar.show();
            }
            int stateCount = mActivity.getStateManager().getStateCount();
            actionBar.setDisplayOptions(
                    stateCount == 1 ? 0 : ActionBar.DISPLAY_HOME_AS_UP,
                    ActionBar.DISPLAY_HOME_AS_UP);
            actionBar.setHomeButtonEnabled(true);
        }

        activity.invalidateOptionsMenu();

        final Window win = activity.getWindow();
        final WindowManager.LayoutParams params = win.getAttributes();

        if ((mFlags & FLAG_HIDE_STATUS_BAR) != 0) {
            params.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE;
        } else {
            params.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE;
        }

        setScreenOnFlags(params);
        win.setAttributes(params);

        ResultEntry entry = mReceivedResults;
        if (entry != null) {
            mReceivedResults = null;
            onStateResult(entry.requestCode, entry.resultCode, entry.resultData);
        }

        if (0 != (mFlags & FLAG_SCREEN_ON)) {
            // we need to know whether the device is plugged in to do this correctly
            final IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            activity.registerReceiver(mPowerIntentReceiver, filter);
        }
        onResume();
    }

    // a subclass of ActivityState should override the method to resume itself
    protected void onResume() {
    }

    protected boolean onCreateActionBar(Menu menu) {
        // TODO: we should return false if there is no menu to show
        //       this is a workaround for a bug in system
        return true;
    }

    protected boolean onItemSelected(MenuItem item) {
        return false;
    }

    protected void onDestroy() {
        mDestroyed = true;
    }

    boolean isDestroyed() {
        return mDestroyed;
    }
}
