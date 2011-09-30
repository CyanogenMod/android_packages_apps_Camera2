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
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

abstract public class ActivityState {
    public static final int FLAG_HIDE_ACTION_BAR = 1;
    public static final int FLAG_HIDE_STATUS_BAR = 2;

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

    protected void onPause() {
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

        if ((mFlags & FLAG_HIDE_STATUS_BAR) != 0) {
            WindowManager.LayoutParams params = ((Activity) mActivity).getWindow().getAttributes();
            params.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE;
            ((Activity) mActivity).getWindow().setAttributes(params);
        } else {
            WindowManager.LayoutParams params = ((Activity) mActivity).getWindow().getAttributes();
            params.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE;
            ((Activity) mActivity).getWindow().setAttributes(params);
        }

        ResultEntry entry = mReceivedResults;
        if (entry != null) {
            mReceivedResults = null;
            onStateResult(entry.requestCode, entry.resultCode, entry.resultData);
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
