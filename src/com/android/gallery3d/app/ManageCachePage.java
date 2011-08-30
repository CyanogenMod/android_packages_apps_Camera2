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
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.ui.AlbumSetView;
import com.android.gallery3d.ui.CacheBarView;
import com.android.gallery3d.ui.GLCanvas;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.ManageCacheDrawer;
import com.android.gallery3d.ui.MenuExecutor;
import com.android.gallery3d.ui.SelectionDrawer;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.ui.SlotView;
import com.android.gallery3d.ui.StaticBackground;
import com.android.gallery3d.util.GalleryUtils;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import java.util.ArrayList;

public class ManageCachePage extends ActivityState implements
        SelectionManager.SelectionListener, CacheBarView.Listener,
        MenuExecutor.ProgressListener, EyePosition.EyePositionListener {
    public static final String KEY_MEDIA_PATH = "media-path";
    private static final String TAG = "ManageCachePage";

    private static final float USER_DISTANCE_METER = 0.3f;
    private static final int DATA_CACHE_SIZE = 256;

    private StaticBackground mStaticBackground;
    private AlbumSetView mAlbumSetView;

    private MediaSet mMediaSet;

    protected SelectionManager mSelectionManager;
    protected SelectionDrawer mSelectionDrawer;
    private AlbumSetDataAdapter mAlbumSetDataAdapter;
    private float mUserDistance; // in pixel

    private CacheBarView mCacheBar;

    private EyePosition mEyePosition;

    // The eyes' position of the user, the origin is at the center of the
    // device and the unit is in pixels.
    private float mX;
    private float mY;
    private float mZ;

    private int mAlbumCountToMakeAvailableOffline;

    private GLView mRootPane = new GLView() {
        private float mMatrix[] = new float[16];

        @Override
        protected void onLayout(
                boolean changed, int left, int top, int right, int bottom) {
            mStaticBackground.layout(0, 0, right - left, bottom - top);
            mEyePosition.resetPosition();

            Config.ManageCachePage config = Config.ManageCachePage.get((Context) mActivity);

            ActionBar actionBar = ((Activity) mActivity).getActionBar();
            int slotViewTop = GalleryActionBar.getHeight((Activity) mActivity);
            int slotViewBottom = bottom - top - config.cacheBarHeight;

            mAlbumSetView.layout(0, slotViewTop, right - left, slotViewBottom);
            mCacheBar.layout(0, bottom - top - config.cacheBarHeight,
                    right - left, bottom - top);
        }

        @Override
        protected void render(GLCanvas canvas) {
            canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
            GalleryUtils.setViewPointMatrix(mMatrix,
                        getWidth() / 2 + mX, getHeight() / 2 + mY, mZ);
            canvas.multiplyMatrix(mMatrix, 0);
            super.render(canvas);
            canvas.restore();
        }
    };

    public void onEyePositionChanged(float x, float y, float z) {
        mRootPane.lockRendering();
        mX = x;
        mY = y;
        mZ = z;
        mRootPane.unlockRendering();
        mRootPane.invalidate();
    }

    public void onSingleTapUp(int slotIndex) {
        MediaSet targetSet = mAlbumSetDataAdapter.getMediaSet(slotIndex);
        if (targetSet == null) return; // Content is dirty, we shall reload soon

        // ignore selection action if the target set does not support cache
        // operation (like a local album).
        if ((targetSet.getSupportedOperations()
                & MediaSet.SUPPORT_CACHE) == 0) {
            showToastForLocalAlbum();
            return;
        }

        Path path = targetSet.getPath();
        boolean isFullyCached =
                (targetSet.getCacheFlag() == MediaObject.CACHE_FLAG_FULL);
        boolean isSelected = mSelectionManager.isItemSelected(path);

        if (!isFullyCached) {
            // We only count the media sets that will be made available offline
            // in this session.
            if (isSelected) {
                --mAlbumCountToMakeAvailableOffline;
            } else {
                ++mAlbumCountToMakeAvailableOffline;
            }
        }

        long sizeOfTarget = targetSet.getCacheSize();
        if (isFullyCached ^ isSelected) {
            mCacheBar.increaseTargetCacheSize(-sizeOfTarget);
        } else {
            mCacheBar.increaseTargetCacheSize(sizeOfTarget);
        }

        mSelectionManager.toggle(path);
        mAlbumSetView.invalidate();
    }

    @Override
    public void onCreate(Bundle data, Bundle restoreState) {
        Log.v(TAG, "onCreate");
        initializeViews();
        initializeData(data);
        mEyePosition = new EyePosition(mActivity.getAndroidContext(), this);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.v(TAG, "onPause");
        mAlbumSetDataAdapter.pause();
        mAlbumSetView.pause();
        mCacheBar.pause();
        mEyePosition.pause();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.v(TAG, "onResume");
        setContentPane(mRootPane);
        mAlbumSetDataAdapter.resume();
        mAlbumSetView.resume();
        mCacheBar.resume();
        mEyePosition.resume();
    }

    private void initializeData(Bundle data) {
        mUserDistance = GalleryUtils.meterToPixel(USER_DISTANCE_METER);
        String mediaPath = data.getString(ManageCachePage.KEY_MEDIA_PATH);
        mMediaSet = mActivity.getDataManager().getMediaSet(mediaPath);
        mSelectionManager.setSourceMediaSet(mMediaSet);

        // We will always be in selection mode in this page.
        mSelectionManager.setAutoLeaveSelectionMode(false);
        mSelectionManager.enterSelectionMode();

        mAlbumSetDataAdapter = new AlbumSetDataAdapter(
                mActivity, mMediaSet, DATA_CACHE_SIZE);
        mAlbumSetView.setModel(mAlbumSetDataAdapter);
    }

    private void initializeViews() {
        mSelectionManager = new SelectionManager(mActivity, true);
        mSelectionManager.setSelectionListener(this);
        mStaticBackground = new StaticBackground(mActivity.getAndroidContext());
        mRootPane.addComponent(mStaticBackground);

        mSelectionDrawer = new ManageCacheDrawer(
                (Context) mActivity, mSelectionManager);
        Config.ManageCachePage config = Config.ManageCachePage.get((Context) mActivity);
        mAlbumSetView = new AlbumSetView(mActivity, mSelectionDrawer,
                config.slotWidth, config.slotHeight,
                config.displayItemSize, config.labelFontSize,
                config.labelOffsetY, config.labelMargin);
        mAlbumSetView.setListener(new SlotView.SimpleListener() {
            @Override
            public void onSingleTapUp(int slotIndex) {
                ManageCachePage.this.onSingleTapUp(slotIndex);
            }
        });
        mRootPane.addComponent(mAlbumSetView);

        mCacheBar = new CacheBarView(mActivity, R.drawable.manage_bar,
                config.cacheBarHeight,
                config.cacheBarPinLeftMargin,
                config.cacheBarPinRightMargin,
                config.cacheBarButtonRightMargin,
                config.cacheBarFontSize);

        mCacheBar.setListener(this);
        mRootPane.addComponent(mCacheBar);

        mStaticBackground.setImage(R.drawable.background,
                R.drawable.background_portrait);
    }

    public void onDoneClicked() {
        ArrayList<Path> ids = mSelectionManager.getSelected(false);
        if (ids.size() == 0) {
            onBackPressed();
            return;
        }
        showToast();

        MenuExecutor menuExecutor = new MenuExecutor(mActivity,
                mSelectionManager);
        menuExecutor.startAction(R.id.action_toggle_full_caching,
                R.string.process_caching_requests, this);
    }

    private void showToast() {
        if (mAlbumCountToMakeAvailableOffline > 0) {
            Activity activity = (Activity) mActivity;
            Toast.makeText(activity, activity.getResources().getQuantityString(
                    R.plurals.make_albums_available_offline,
                    mAlbumCountToMakeAvailableOffline),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showToastForLocalAlbum() {
        Activity activity = (Activity) mActivity;
        Toast.makeText(activity, activity.getResources().getString(
            R.string.try_to_set_local_album_available_offline),
            Toast.LENGTH_SHORT).show();
    }

    public void onProgressComplete(int result) {
        onBackPressed();
    }

    public void onProgressUpdate(int index) {
    }

    public void onSelectionModeChange(int mode) {
    }

    public void onSelectionChange(Path path, boolean selected) {
    }
}
