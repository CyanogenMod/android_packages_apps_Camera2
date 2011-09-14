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

import com.android.gallery3d.R;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.ImageCacheService;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLRootView;
import com.android.gallery3d.ui.PositionRepository;
import com.android.gallery3d.util.ThreadPool;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;

public class AbstractGalleryActivity extends Activity implements GalleryActivity {
    @SuppressWarnings("unused")
    private static final String TAG = "AbstractGalleryActivity";
    private GLRootView mGLRootView;
    private StateManager mStateManager;
    private PositionRepository mPositionRepository = new PositionRepository();

    private AlertDialog mAlertDialog = null;
    private BroadcastReceiver mMountReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getExternalCacheDir() != null) onStorageReady();
        }
    };
    private IntentFilter mMountFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        mGLRootView.lockRenderThread();
        try {
            super.onSaveInstanceState(outState);
            getStateManager().saveState(outState);
        } finally {
            mGLRootView.unlockRenderThread();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        mStateManager.onConfigurationChange(config);
    }

    public Context getAndroidContext() {
        return this;
    }

    public ImageCacheService getImageCacheService() {
        return ((GalleryApp) getApplication()).getImageCacheService();
    }

    public DataManager getDataManager() {
        return ((GalleryApp) getApplication()).getDataManager();
    }

    public ThreadPool getThreadPool() {
        return ((GalleryApp) getApplication()).getThreadPool();
    }

    public GalleryApp getGalleryApplication() {
        return (GalleryApp) getApplication();
    }

    public synchronized StateManager getStateManager() {
        if (mStateManager == null) {
            mStateManager = new StateManager(this);
        }
        return mStateManager;
    }

    public GLRoot getGLRoot() {
        return mGLRootView;
    }

    public PositionRepository getPositionRepository() {
        return mPositionRepository;
    }

    @Override
    public void setContentView(int resId) {
        super.setContentView(resId);
        mGLRootView = (GLRootView) findViewById(R.id.gl_root_view);
    }

    public int getActionBarHeight() {
        ActionBar actionBar = getActionBar();
        return actionBar != null ? actionBar.getHeight() : 0;
    }

    protected void onStorageReady() {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
            unregisterReceiver(mMountReceiver);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (getExternalCacheDir() == null) {
            OnCancelListener onCancel = new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            };
            OnClickListener onClick = new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            };
            mAlertDialog = new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle("No Storage")
                    .setMessage("No external storage available.")
                    .setNegativeButton(android.R.string.cancel, onClick)
                    .setOnCancelListener(onCancel)
                    .show();
            registerReceiver(mMountReceiver, mMountFilter);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAlertDialog != null) {
            unregisterReceiver(mMountReceiver);
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGLRootView.lockRenderThread();
        try {
            getStateManager().resume();
            getDataManager().resume();
        } finally {
            mGLRootView.unlockRenderThread();
        }
        mGLRootView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGLRootView.onPause();
        mGLRootView.lockRenderThread();
        try {
            getStateManager().pause();
            getDataManager().pause();
        } finally {
            mGLRootView.unlockRenderThread();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mGLRootView.lockRenderThread();
        try {
            getStateManager().notifyActivityResult(
                    requestCode, resultCode, data);
        } finally {
            mGLRootView.unlockRenderThread();
        }
    }

    @Override
    public GalleryActionBar getGalleryActionBar() {
        return null;
    }
}
