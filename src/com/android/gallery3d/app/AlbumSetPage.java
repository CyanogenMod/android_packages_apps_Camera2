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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.picasasource.PicasaSource;
import com.android.gallery3d.settings.GallerySettings;
import com.android.gallery3d.ui.ActionModeHandler;
import com.android.gallery3d.ui.ActionModeHandler.ActionModeListener;
import com.android.gallery3d.ui.AlbumSetSlotRenderer;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.ui.DetailsHelper.CloseListener;
import com.android.gallery3d.ui.FadeTexture;
import com.android.gallery3d.ui.GLCanvas;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.ui.SlotView;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.HelpUtils;

public class AlbumSetPage extends ActivityState implements
        SelectionManager.SelectionListener, GalleryActionBar.ClusterRunner,
        EyePosition.EyePositionListener, MediaSet.SyncListener {
    @SuppressWarnings("unused")
    private static final String TAG = "AlbumSetPage";

    private static final int MSG_PICK_ALBUM = 1;

    public static final String KEY_MEDIA_PATH = "media-path";
    public static final String KEY_SET_TITLE = "set-title";
    public static final String KEY_SET_SUBTITLE = "set-subtitle";
    public static final String KEY_SELECTED_CLUSTER_TYPE = "selected-cluster";

    private static final int DATA_CACHE_SIZE = 256;
    private static final int REQUEST_DO_ANIMATION = 1;

    private static final int BIT_LOADING_RELOAD = 1;
    private static final int BIT_LOADING_SYNC = 2;

    private boolean mIsActive = false;
    private SlotView mSlotView;
    private AlbumSetSlotRenderer mAlbumSetView;

    private MediaSet mMediaSet;
    private String mTitle;
    private String mSubtitle;
    private boolean mShowClusterMenu;
    private GalleryActionBar mActionBar;
    private int mSelectedAction;
    private Vibrator mVibrator;

    protected SelectionManager mSelectionManager;
    private AlbumSetDataLoader mAlbumSetDataAdapter;

    private boolean mGetContent;
    private boolean mGetAlbum;
    private ActionMode mActionMode;
    private ActionModeHandler mActionModeHandler;
    private DetailsHelper mDetailsHelper;
    private MyDetailsSource mDetailsSource;
    private boolean mShowDetails;
    private EyePosition mEyePosition;
    private Handler mHandler;

    // The eyes' position of the user, the origin is at the center of the
    // device and the unit is in pixels.
    private float mX;
    private float mY;
    private float mZ;

    private Future<Integer> mSyncTask = null;

    private int mLoadingBits = 0;
    private boolean mInitialSynced = false;

    private final GLView mRootPane = new GLView() {
        private final float mMatrix[] = new float[16];

        @Override
        protected void renderBackground(GLCanvas view) {
            view.clearBuffer();
        }

        @Override
        protected void onLayout(
                boolean changed, int left, int top, int right, int bottom) {
            mEyePosition.resetPosition();

            int slotViewTop = mActionBar.getHeight();
            int slotViewBottom = bottom - top;
            int slotViewRight = right - left;

            if (mShowDetails) {
                mDetailsHelper.layout(left, slotViewTop, right, bottom);
            } else {
                mAlbumSetView.setHighlightItemPath(null);
            }

            mSlotView.layout(0, slotViewTop, slotViewRight, slotViewBottom);
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

    @Override
    public void onEyePositionChanged(float x, float y, float z) {
        mRootPane.lockRendering();
        mX = x;
        mY = y;
        mZ = z;
        mRootPane.unlockRendering();
        mRootPane.invalidate();
    }

    @Override
    public void onBackPressed() {
        if (mShowDetails) {
            hideDetails();
        } else if (mSelectionManager.inSelectionMode()) {
            mSelectionManager.leaveSelectionMode();
        } else {
            super.onBackPressed();
        }
    }

    private void getSlotCenter(int slotIndex, int center[]) {
        Rect offset = new Rect();
        mRootPane.getBoundsOf(mSlotView, offset);
        Rect r = mSlotView.getSlotRect(slotIndex);
        int scrollX = mSlotView.getScrollX();
        int scrollY = mSlotView.getScrollY();
        center[0] = offset.left + (r.left + r.right) / 2 - scrollX;
        center[1] = offset.top + (r.top + r.bottom) / 2 - scrollY;
    }

    public void onSingleTapUp(int slotIndex) {
        if (!mIsActive) return;

        if (mSelectionManager.inSelectionMode()) {
            MediaSet targetSet = mAlbumSetDataAdapter.getMediaSet(slotIndex);
            if (targetSet == null) return; // Content is dirty, we shall reload soon
            mSelectionManager.toggle(targetSet.getPath());
            mSlotView.invalidate();
        } else {
            // Show pressed-up animation for the single-tap.
            mAlbumSetView.setPressedIndex(slotIndex);
            mAlbumSetView.setPressedUp();
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_PICK_ALBUM, slotIndex, 0),
                    FadeTexture.DURATION);
        }
    }

    private void pickAlbum(int slotIndex) {
        if (!mIsActive) return;

        MediaSet targetSet = mAlbumSetDataAdapter.getMediaSet(slotIndex);
        if (targetSet == null) return; // Content is dirty, we shall reload soon
        String mediaPath = targetSet.getPath().toString();

        Bundle data = new Bundle(getData());
        int[] center = new int[2];
        getSlotCenter(slotIndex, center);
        data.putIntArray(AlbumPage.KEY_SET_CENTER, center);
        if (mGetAlbum && targetSet.isLeafAlbum()) {
            Activity activity = (Activity) mActivity;
            Intent result = new Intent()
                    .putExtra(AlbumPicker.KEY_ALBUM_PATH, targetSet.getPath().toString());
            activity.setResult(Activity.RESULT_OK, result);
            activity.finish();
        } else if (targetSet.getSubMediaSetCount() > 0) {
            data.putString(AlbumSetPage.KEY_MEDIA_PATH, mediaPath);
            mActivity.getStateManager().startStateForResult(
                    AlbumSetPage.class, REQUEST_DO_ANIMATION, data);
        } else {
            if (!mGetContent && (targetSet.getSupportedOperations()
                    & MediaObject.SUPPORT_IMPORT) != 0) {
                data.putBoolean(AlbumPage.KEY_AUTO_SELECT_ALL, true);
            }
            data.putString(AlbumPage.KEY_MEDIA_PATH, mediaPath);
            boolean inAlbum = mActivity.getStateManager().hasStateClass(AlbumPage.class);
            // We only show cluster menu in the first AlbumPage in stack
            data.putBoolean(AlbumPage.KEY_SHOW_CLUSTER_MENU, !inAlbum);
            mActivity.getStateManager().startStateForResult(
                    AlbumPage.class, REQUEST_DO_ANIMATION, data);
        }
    }

    private void onDown(int index) {
        mAlbumSetView.setPressedIndex(index);
    }

    private void onUp(boolean followedByLongPress) {
        if (followedByLongPress) {
            // Avoid showing press-up animations for long-press.
            mAlbumSetView.setPressedIndex(-1);
        } else {
            mAlbumSetView.setPressedUp();
        }
    }

    public void onLongTap(int slotIndex) {
        if (mGetContent || mGetAlbum) return;
        MediaSet set = mAlbumSetDataAdapter.getMediaSet(slotIndex);
        if (set == null) return;
        mSelectionManager.setAutoLeaveSelectionMode(true);
        mSelectionManager.toggle(set.getPath());
        mDetailsSource.findIndex(slotIndex);
        mSlotView.invalidate();
    }

    @Override
    public void doCluster(int clusterType) {
        String basePath = mMediaSet.getPath().toString();
        String newPath = FilterUtils.switchClusterPath(basePath, clusterType);
        Bundle data = new Bundle(getData());
        data.putString(AlbumSetPage.KEY_MEDIA_PATH, newPath);
        data.putInt(KEY_SELECTED_CLUSTER_TYPE, clusterType);
        mActivity.getStateManager().switchState(this, AlbumSetPage.class, data);
    }

    @Override
    public void onCreate(Bundle data, Bundle restoreState) {
        initializeViews();
        initializeData(data);
        Context context = mActivity.getAndroidContext();
        mGetContent = data.getBoolean(Gallery.KEY_GET_CONTENT, false);
        mGetAlbum = data.getBoolean(Gallery.KEY_GET_ALBUM, false);
        mTitle = data.getString(AlbumSetPage.KEY_SET_TITLE);
        mSubtitle = data.getString(AlbumSetPage.KEY_SET_SUBTITLE);
        mEyePosition = new EyePosition(context, this);
        mDetailsSource = new MyDetailsSource();
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mActionBar = mActivity.getGalleryActionBar();
        mSelectedAction = data.getInt(AlbumSetPage.KEY_SELECTED_CLUSTER_TYPE,
                FilterUtils.CLUSTER_BY_ALBUM);

        mHandler = new SynchronizedHandler(mActivity.getGLRoot()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_PICK_ALBUM: {
                        pickAlbum(message.arg1);
                        break;
                    }
                    default: throw new AssertionError(message.what);
                }
            }
        };
    }

    private void clearLoadingBit(int loadingBit) {
        mLoadingBits &= ~loadingBit;
        if (mLoadingBits == 0 && mIsActive) {
            // Only show toast when there's no album and we are going to finish
            // the page. Toast is redundant if we are going to stay on this page.
            if ((mAlbumSetDataAdapter.size() == 0)) {
                if (mActivity.getStateManager().getStateCount() > 1) {
                    Toast.makeText((Context) mActivity,
                            R.string.empty_album, Toast.LENGTH_LONG).show();
                    mActivity.getStateManager().finishState(this);
                }
            }
        }
    }

    private void setLoadingBit(int loadingBit) {
        mLoadingBits |= loadingBit;
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsActive = false;
        mActionModeHandler.pause();
        mAlbumSetDataAdapter.pause();
        mAlbumSetView.pause();
        mEyePosition.pause();
        DetailsHelper.pause();
        // Call disableClusterMenu to avoid receiving callback after paused.
        // Don't hide menu here otherwise the list menu will disappear earlier than
        // the action bar, which is janky and unwanted behavior.
        mActionBar.disableClusterMenu(false);
        if (mSyncTask != null) {
            mSyncTask.cancel();
            mSyncTask = null;
            clearLoadingBit(BIT_LOADING_SYNC);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsActive = true;
        setContentPane(mRootPane);

        // Set the reload bit here to prevent it exit this page in clearLoadingBit().
        setLoadingBit(BIT_LOADING_RELOAD);
        mAlbumSetDataAdapter.resume();

        mAlbumSetView.resume();
        mEyePosition.resume();
        mActionModeHandler.resume();
        if (mShowClusterMenu) {
            mActionBar.enableClusterMenu(mSelectedAction, this);
        }
        if (!mInitialSynced) {
            setLoadingBit(BIT_LOADING_SYNC);
            mSyncTask = mMediaSet.requestSync(AlbumSetPage.this);
        }
    }

    private void initializeData(Bundle data) {
        String mediaPath = data.getString(AlbumSetPage.KEY_MEDIA_PATH);
        mMediaSet = mActivity.getDataManager().getMediaSet(mediaPath);
        mSelectionManager.setSourceMediaSet(mMediaSet);
        mAlbumSetDataAdapter = new AlbumSetDataLoader(
                mActivity, mMediaSet, DATA_CACHE_SIZE);
        mAlbumSetDataAdapter.setLoadingListener(new MyLoadingListener());
        mAlbumSetView.setModel(mAlbumSetDataAdapter);
    }

    private void initializeViews() {
        mSelectionManager = new SelectionManager(mActivity, true);
        mSelectionManager.setSelectionListener(this);

        Config.AlbumSetPage config = Config.AlbumSetPage.get((Context) mActivity);
        mSlotView = new SlotView(mActivity, config.slotViewSpec);
        mAlbumSetView = new AlbumSetSlotRenderer(
                mActivity, mSelectionManager, mSlotView, config.labelSpec);
        mSlotView.setSlotRenderer(mAlbumSetView);
        mSlotView.setListener(new SlotView.SimpleListener() {
            @Override
            public void onDown(int index) {
                AlbumSetPage.this.onDown(index);
            }

            @Override
            public void onUp(boolean followedByLongPress) {
                AlbumSetPage.this.onUp(followedByLongPress);
            }

            @Override
            public void onSingleTapUp(int slotIndex) {
                AlbumSetPage.this.onSingleTapUp(slotIndex);
            }

            @Override
            public void onLongTap(int slotIndex) {
                AlbumSetPage.this.onLongTap(slotIndex);
            }
        });

        mActionModeHandler = new ActionModeHandler(mActivity, mSelectionManager);
        mActionModeHandler.setActionModeListener(new ActionModeListener() {
            @Override
            public boolean onActionItemClicked(MenuItem item) {
                return onItemSelected(item);
            }
        });
        mRootPane.addComponent(mSlotView);
    }

    @Override
    protected boolean onCreateActionBar(Menu menu) {
        Activity activity = (Activity) mActivity;
        MenuInflater inflater = activity.getMenuInflater();

        final boolean inAlbum = mActivity.getStateManager().hasStateClass(
                AlbumPage.class);

        if (mGetContent) {
            inflater.inflate(R.menu.pickup, menu);
            int typeBits = mData.getInt(
                    Gallery.KEY_TYPE_BITS, DataManager.INCLUDE_IMAGE);
            int id = R.string.select_image;
            if ((typeBits & DataManager.INCLUDE_VIDEO) != 0) {
                id = (typeBits & DataManager.INCLUDE_IMAGE) == 0
                        ? R.string.select_video
                        : R.string.select_item;
            }
            mActionBar.setTitle(id);
        } else  if (mGetAlbum) {
            inflater.inflate(R.menu.pickup, menu);
            mActionBar.setTitle(R.string.select_album);
        } else {
            mShowClusterMenu = !inAlbum;
            inflater.inflate(R.menu.albumset, menu);
            MenuItem selectItem = menu.findItem(R.id.action_select);

            if (selectItem != null) {
                boolean selectAlbums = !inAlbum &&
                        mActionBar.getClusterTypeAction() == FilterUtils.CLUSTER_BY_ALBUM;
                if (selectAlbums) {
                    selectItem.setTitle(R.string.select_album);
                } else {
                    selectItem.setTitle(R.string.select_group);
                }
            }

            FilterUtils.setupMenuItems(mActionBar, mMediaSet.getPath(), false);
            MenuItem switchCamera = menu.findItem(R.id.action_camera);
            if (switchCamera != null) {
                switchCamera.setVisible(GalleryUtils.isCameraAvailable(activity));
            }
            final MenuItem helpMenu = menu.findItem(R.id.action_general_help);
            HelpUtils.prepareHelpMenuItem(mActivity.getAndroidContext(),
                    helpMenu, R.string.help_url_gallery_main);

            mActionBar.setTitle(mTitle);
            mActionBar.setSubtitle(mSubtitle);
        }
        return true;
    }

    @Override
    protected boolean onItemSelected(MenuItem item) {
        Activity activity = (Activity) mActivity;
        switch (item.getItemId()) {
            case R.id.action_cancel:
                activity.setResult(Activity.RESULT_CANCELED);
                activity.finish();
                return true;
            case R.id.action_select:
                mSelectionManager.setAutoLeaveSelectionMode(false);
                mSelectionManager.enterSelectionMode();
                return true;
            case R.id.action_details:
                if (mAlbumSetDataAdapter.size() != 0) {
                    if (mShowDetails) {
                        hideDetails();
                    } else {
                        showDetails();
                    }
                } else {
                    Toast.makeText(activity,
                            activity.getText(R.string.no_albums_alert),
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.action_camera: {
                GalleryUtils.startCameraActivity(activity);
                return true;
            }
            case R.id.action_manage_offline: {
                Bundle data = new Bundle();
                String mediaPath = mActivity.getDataManager().getTopSetPath(
                    DataManager.INCLUDE_ALL);
                data.putString(AlbumSetPage.KEY_MEDIA_PATH, mediaPath);
                mActivity.getStateManager().startState(ManageCachePage.class, data);
                return true;
            }
            case R.id.action_sync_picasa_albums: {
                PicasaSource.requestSync(activity);
                return true;
            }
            case R.id.action_settings: {
                activity.startActivity(new Intent(activity, GallerySettings.class));
                return true;
            }
            default:
                return false;
        }
    }

    @Override
    protected void onStateResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_DO_ANIMATION: {
                mSlotView.startRisingAnimation();
            }
        }
    }

    private String getSelectedString() {
        int count = mSelectionManager.getSelectedCount();
        int action = mActionBar.getClusterTypeAction();
        int string = action == FilterUtils.CLUSTER_BY_ALBUM
                ? R.plurals.number_of_albums_selected
                : R.plurals.number_of_groups_selected;
        String format = mActivity.getResources().getQuantityString(string, count);
        return String.format(format, count);
    }

    @Override
    public void onSelectionModeChange(int mode) {
        switch (mode) {
            case SelectionManager.ENTER_SELECTION_MODE: {
                mActionBar.disableClusterMenu(true);
                mActionMode = mActionModeHandler.startActionMode();
                mVibrator.vibrate(100);
                break;
            }
            case SelectionManager.LEAVE_SELECTION_MODE: {
                mActionMode.finish();
                if (mShowClusterMenu) {
                    mActionBar.enableClusterMenu(mSelectedAction, this);
                }
                mRootPane.invalidate();
                break;
            }
            case SelectionManager.SELECT_ALL_MODE: {
                mActionModeHandler.updateSupportedOperation();
                mRootPane.invalidate();
                break;
            }
        }
    }

    @Override
    public void onSelectionChange(Path path, boolean selected) {
        Utils.assertTrue(mActionMode != null);
        mActionModeHandler.setTitle(getSelectedString());
        mActionModeHandler.updateSupportedOperation(path, selected);
    }

    private void hideDetails() {
        mShowDetails = false;
        mDetailsHelper.hide();
        mAlbumSetView.setHighlightItemPath(null);
        mSlotView.invalidate();
    }

    private void showDetails() {
        mShowDetails = true;
        if (mDetailsHelper == null) {
            mDetailsHelper = new DetailsHelper(mActivity, mRootPane, mDetailsSource);
            mDetailsHelper.setCloseListener(new CloseListener() {
                @Override
                public void onClose() {
                    hideDetails();
                }
            });
        }
        mDetailsHelper.show();
    }

    @Override
    public void onSyncDone(final MediaSet mediaSet, final int resultCode) {
        if (resultCode == MediaSet.SYNC_RESULT_ERROR) {
            Log.d(TAG, "onSyncDone: " + Utils.maskDebugInfo(mediaSet.getName()) + " result="
                    + resultCode);
        }
        ((Activity) mActivity).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                GLRoot root = mActivity.getGLRoot();
                root.lockRenderThread();
                try {
                    if (resultCode == MediaSet.SYNC_RESULT_SUCCESS) {
                        mInitialSynced = true;
                    }
                    clearLoadingBit(BIT_LOADING_SYNC);
                    if (resultCode == MediaSet.SYNC_RESULT_ERROR && mIsActive) {
                        Log.w(TAG, "failed to load album set");
                    }
                } finally {
                    root.unlockRenderThread();
                }
            }
        });
    }

    private class MyLoadingListener implements LoadingListener {
        @Override
        public void onLoadingStarted() {
            setLoadingBit(BIT_LOADING_RELOAD);
        }

        @Override
        public void onLoadingFinished() {
            clearLoadingBit(BIT_LOADING_RELOAD);
        }
    }

    private class MyDetailsSource implements DetailsHelper.DetailsSource {
        private int mIndex;

        @Override
        public int size() {
            return mAlbumSetDataAdapter.size();
        }

        @Override
        public int getIndex() {
            return mIndex;
        }

        // If requested index is out of active window, suggest a valid index.
        // If there is no valid index available, return -1.
        @Override
        public int findIndex(int indexHint) {
            if (mAlbumSetDataAdapter.isActive(indexHint)) {
                mIndex = indexHint;
            } else {
                mIndex = mAlbumSetDataAdapter.getActiveStart();
                if (!mAlbumSetDataAdapter.isActive(mIndex)) {
                    return -1;
                }
            }
            return mIndex;
        }

        @Override
        public MediaDetails getDetails() {
            MediaObject item = mAlbumSetDataAdapter.getMediaSet(mIndex);
            if (item != null) {
                mAlbumSetView.setHighlightItemPath(item.getPath());
                return item.getDetails();
            } else {
                return null;
            }
        }
    }
}
