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
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.MtpDevice;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.ui.ActionModeHandler;
import com.android.gallery3d.ui.ActionModeHandler.ActionModeListener;
import com.android.gallery3d.ui.AlbumView;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.ui.DetailsHelper.CloseListener;
import com.android.gallery3d.ui.GLCanvas;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.GridDrawer;
import com.android.gallery3d.ui.HighlightDrawer;
import com.android.gallery3d.ui.PositionProvider;
import com.android.gallery3d.ui.PositionRepository;
import com.android.gallery3d.ui.PositionRepository.Position;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.ui.SlotView;
import com.android.gallery3d.ui.StaticBackground;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.GalleryUtils;

import java.util.Random;

public class AlbumPage extends ActivityState implements GalleryActionBar.ClusterRunner,
        SelectionManager.SelectionListener, MediaSet.SyncListener {
    @SuppressWarnings("unused")
    private static final String TAG = "AlbumPage";

    public static final String KEY_MEDIA_PATH = "media-path";
    public static final String KEY_SET_CENTER = "set-center";
    public static final String KEY_AUTO_SELECT_ALL = "auto-select-all";
    public static final String KEY_SHOW_CLUSTER_MENU = "cluster-menu";

    private static final int REQUEST_SLIDESHOW = 1;
    private static final int REQUEST_PHOTO = 2;
    private static final int REQUEST_DO_ANIMATION = 3;

    private static final float USER_DISTANCE_METER = 0.3f;

    private boolean mIsActive = false;
    private StaticBackground mStaticBackground;
    private AlbumView mAlbumView;
    private Path mMediaSetPath;

    private AlbumDataAdapter mAlbumDataAdapter;

    protected SelectionManager mSelectionManager;
    private Vibrator mVibrator;
    private GridDrawer mGridDrawer;
    private HighlightDrawer mHighlightDrawer;

    private boolean mGetContent;
    private boolean mShowClusterMenu;

    private ActionMode mActionMode;
    private ActionModeHandler mActionModeHandler;
    private int mFocusIndex = 0;
    private DetailsHelper mDetailsHelper;
    private MyDetailsSource mDetailsSource;
    private MediaSet mMediaSet;
    private boolean mShowDetails;
    private float mUserDistance; // in pixel

    private ProgressDialog mProgressDialog;
    private Future<?> mPendingTask;

    private Future<Integer> mSyncTask = null;

    private GLView mRootPane = new GLView() {
        private float mMatrix[] = new float[16];

        @Override
        protected void onLayout(
                boolean changed, int left, int top, int right, int bottom) {
            mStaticBackground.layout(0, 0, right - left, bottom - top);

            int slotViewTop = GalleryActionBar.getHeight((Activity) mActivity);
            int slotViewBottom = bottom - top;
            int slotViewRight = right - left;

            if (mShowDetails) {
                mDetailsHelper.layout(left, slotViewTop, right, bottom);
            } else {
                mAlbumView.setSelectionDrawer(mGridDrawer);
            }

            mAlbumView.layout(0, slotViewTop, slotViewRight, slotViewBottom);
            GalleryUtils.setViewPointMatrix(mMatrix,
                    (right - left) / 2, (bottom - top) / 2, -mUserDistance);
            PositionRepository.getInstance(mActivity).setOffset(
                    0, slotViewTop);
        }

        @Override
        protected void render(GLCanvas canvas) {
            canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
            canvas.multiplyMatrix(mMatrix, 0);
            super.render(canvas);
            canvas.restore();
        }
    };

    @Override
    protected void onBackPressed() {
        if (mShowDetails) {
            hideDetails();
        } else if (mSelectionManager.inSelectionMode()) {
            mSelectionManager.leaveSelectionMode();
        } else {
            mAlbumView.savePositions(PositionRepository.getInstance(mActivity));
            super.onBackPressed();
        }
    }

    private void onDown(int index) {
        MediaItem item = mAlbumDataAdapter.get(index);
        Path path = (item == null) ? null : item.getPath();
        mSelectionManager.setPressedPath(path);
        mAlbumView.invalidate();
    }

    private void onUp() {
        mSelectionManager.setPressedPath(null);
        mAlbumView.invalidate();
    }

    public void onSingleTapUp(int slotIndex) {
        MediaItem item = mAlbumDataAdapter.get(slotIndex);
        if (item == null) {
            Log.w(TAG, "item not ready yet, ignore the click");
            return;
        }
        if (mShowDetails) {
            mHighlightDrawer.setHighlightItem(item.getPath());
            mDetailsHelper.reloadDetails(slotIndex);
        } else if (!mSelectionManager.inSelectionMode()) {
            if (mGetContent) {
                onGetContent(item);
            } else {
                boolean playVideo =
                    (item.getSupportedOperations() & MediaItem.SUPPORT_PLAY) != 0;
                if (playVideo) {
                    // Play the video.
                    PhotoPage.playVideo((Activity) mActivity, item.getPlayUri(), item.getName());
                } else {
                    // Get into the PhotoPage.
                    Bundle data = new Bundle();
                    mAlbumView.savePositions(PositionRepository.getInstance(mActivity));
                    data.putInt(PhotoPage.KEY_INDEX_HINT, slotIndex);
                    data.putString(PhotoPage.KEY_MEDIA_SET_PATH,
                            mMediaSetPath.toString());
                    data.putString(PhotoPage.KEY_MEDIA_ITEM_PATH,
                            item.getPath().toString());
                    mActivity.getStateManager().startStateForResult(
                            PhotoPage.class, REQUEST_PHOTO, data);
                }
            }
        } else {
            mSelectionManager.toggle(item.getPath());
            mAlbumView.invalidate();
        }
    }

    private void onGetContent(final MediaItem item) {
        DataManager dm = mActivity.getDataManager();
        Activity activity = (Activity) mActivity;
        if (mData.getString(Gallery.EXTRA_CROP) != null) {
            // TODO: Handle MtpImagew
            Uri uri = dm.getContentUri(item.getPath());
            Intent intent = new Intent(CropImage.ACTION_CROP, uri)
                    .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                    .putExtras(getData());
            if (mData.getParcelable(MediaStore.EXTRA_OUTPUT) == null) {
                intent.putExtra(CropImage.KEY_RETURN_DATA, true);
            }
            activity.startActivity(intent);
            activity.finish();
        } else {
            activity.setResult(Activity.RESULT_OK,
                    new Intent(null, item.getContentUri()));
            activity.finish();
        }
    }

    public void onLongTap(int slotIndex) {
        if (mGetContent) return;
        if (mShowDetails) {
            onSingleTapUp(slotIndex);
        } else {
            MediaItem item = mAlbumDataAdapter.get(slotIndex);
            if (item == null) return;
            mSelectionManager.setAutoLeaveSelectionMode(true);
            mSelectionManager.toggle(item.getPath());
            mDetailsSource.findIndex(slotIndex);
            mAlbumView.invalidate();
        }
    }

    public void doCluster(int clusterType) {
        String basePath = mMediaSet.getPath().toString();
        String newPath = FilterUtils.newClusterPath(basePath, clusterType);
        Bundle data = new Bundle(getData());
        data.putString(AlbumSetPage.KEY_MEDIA_PATH, newPath);
        if (mShowClusterMenu) {
            Context context = mActivity.getAndroidContext();
            data.putString(AlbumSetPage.KEY_SET_TITLE, mMediaSet.getName());
            data.putString(AlbumSetPage.KEY_SET_SUBTITLE,
                    GalleryActionBar.getClusterByTypeString(context, clusterType));
        }

        mAlbumView.savePositions(PositionRepository.getInstance(mActivity));
        mActivity.getStateManager().startStateForResult(
                AlbumSetPage.class, REQUEST_DO_ANIMATION, data);
    }

    public void doFilter(int filterType) {
        String basePath = mMediaSet.getPath().toString();
        String newPath = FilterUtils.switchFilterPath(basePath, filterType);
        Bundle data = new Bundle(getData());
        data.putString(AlbumPage.KEY_MEDIA_PATH, newPath);
        mAlbumView.savePositions(PositionRepository.getInstance(mActivity));
        mActivity.getStateManager().switchState(this, AlbumPage.class, data);
    }

    public void onOperationComplete() {
        mAlbumView.invalidate();
        // TODO: enable animation
    }

    @Override
    protected void onCreate(Bundle data, Bundle restoreState) {
        mUserDistance = GalleryUtils.meterToPixel(USER_DISTANCE_METER);
        initializeViews();
        initializeData(data);
        mGetContent = data.getBoolean(Gallery.KEY_GET_CONTENT, false);
        mShowClusterMenu = data.getBoolean(KEY_SHOW_CLUSTER_MENU, false);
        mDetailsSource = new MyDetailsSource();
        Context context = mActivity.getAndroidContext();
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        startTransition(data);

        // Enable auto-select-all for mtp album
        if (data.getBoolean(KEY_AUTO_SELECT_ALL)) {
            mSelectionManager.selectAll();
        }
    }

    private void startTransition() {
        final PositionRepository repository =
                PositionRepository.getInstance(mActivity);
        mAlbumView.startTransition(new PositionProvider() {
            private Position mTempPosition = new Position();
            public Position getPosition(long identity, Position target) {
                Position p = repository.get(identity);
                if (p != null) return p;
                mTempPosition.set(target);
                mTempPosition.z = 128;
                return mTempPosition;
            }
        });
    }

    private void startTransition(Bundle data) {
        final PositionRepository repository =
                PositionRepository.getInstance(mActivity);
        final int[] center = data == null
                ? null
                : data.getIntArray(KEY_SET_CENTER);
        final Random random = new Random();
        mAlbumView.startTransition(new PositionProvider() {
            private Position mTempPosition = new Position();
            public Position getPosition(long identity, Position target) {
                Position p = repository.get(identity);
                if (p != null) return p;
                if (center != null) {
                    random.setSeed(identity);
                    mTempPosition.set(center[0], center[1],
                            0, random.nextInt(60) - 30, 0);
                } else {
                    mTempPosition.set(target);
                    mTempPosition.z = 128;
                }
                return mTempPosition;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsActive = true;
        setContentPane(mRootPane);
        mAlbumDataAdapter.resume();
        mAlbumView.resume();
        mActionModeHandler.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsActive = false;
        mAlbumDataAdapter.pause();
        mAlbumView.pause();
        DetailsHelper.pause();
        Future<?> task = mPendingTask;
        if (task != null) {
            // cancel on going task
            task.cancel();
            task.waitDone();
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
        }
        if (mSyncTask != null) {
            mSyncTask.cancel();
            mSyncTask = null;
        }
        mActionModeHandler.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAlbumDataAdapter != null) {
            mAlbumDataAdapter.setLoadingListener(null);
        }
    }

    private void initializeViews() {
        mStaticBackground = new StaticBackground((Context) mActivity);
        mRootPane.addComponent(mStaticBackground);

        mSelectionManager = new SelectionManager(mActivity, false);
        mSelectionManager.setSelectionListener(this);
        mGridDrawer = new GridDrawer((Context) mActivity, mSelectionManager);
        Config.AlbumPage config = Config.AlbumPage.get((Context) mActivity);
        mAlbumView = new AlbumView(mActivity, config.slotViewSpec,
                0 /* don't cache thumbnail */);
        mAlbumView.setSelectionDrawer(mGridDrawer);
        mRootPane.addComponent(mAlbumView);
        mAlbumView.setListener(new SlotView.SimpleListener() {
            @Override
            public void onDown(int index) {
                AlbumPage.this.onDown(index);
            }

            @Override
            public void onUp() {
                AlbumPage.this.onUp();
            }

            @Override
            public void onSingleTapUp(int slotIndex) {
                AlbumPage.this.onSingleTapUp(slotIndex);
            }

            @Override
            public void onLongTap(int slotIndex) {
                AlbumPage.this.onLongTap(slotIndex);
            }
        });
        mActionModeHandler = new ActionModeHandler(mActivity, mSelectionManager);
        mActionModeHandler.setActionModeListener(new ActionModeListener() {
            public boolean onActionItemClicked(MenuItem item) {
                return onItemSelected(item);
            }
        });
        mStaticBackground.setImage(R.drawable.background,
                R.drawable.background_portrait);
    }

    private void initializeData(Bundle data) {
        mMediaSetPath = Path.fromString(data.getString(KEY_MEDIA_PATH));
        mMediaSet = mActivity.getDataManager().getMediaSet(mMediaSetPath);
        Utils.assertTrue(mMediaSet != null,
                "MediaSet is null. Path = %s", mMediaSetPath);
        mSelectionManager.setSourceMediaSet(mMediaSet);
        mAlbumDataAdapter = new AlbumDataAdapter(mActivity, mMediaSet);
        mAlbumDataAdapter.setLoadingListener(new MyLoadingListener());
        mAlbumView.setModel(mAlbumDataAdapter);
    }

    private void showDetails() {
        mShowDetails = true;
        if (mDetailsHelper == null) {
            mHighlightDrawer = new HighlightDrawer(mActivity.getAndroidContext(),
                    mSelectionManager);
            mDetailsHelper = new DetailsHelper(mActivity, mRootPane, mDetailsSource);
            mDetailsHelper.setCloseListener(new CloseListener() {
                public void onClose() {
                    hideDetails();
                }
            });
        }
        mAlbumView.setSelectionDrawer(mHighlightDrawer);
        mDetailsHelper.show();
    }

    private void hideDetails() {
        mShowDetails = false;
        mDetailsHelper.hide();
        mAlbumView.setSelectionDrawer(mGridDrawer);
        mAlbumView.invalidate();
    }

    @Override
    protected boolean onCreateActionBar(Menu menu) {
        Activity activity = (Activity) mActivity;
        GalleryActionBar actionBar = mActivity.getGalleryActionBar();
        MenuInflater inflater = activity.getMenuInflater();

        if (mGetContent) {
            inflater.inflate(R.menu.pickup, menu);
            int typeBits = mData.getInt(Gallery.KEY_TYPE_BITS,
                    DataManager.INCLUDE_IMAGE);

            actionBar.setTitle(GalleryUtils.getSelectionModePrompt(typeBits));
        } else {
            inflater.inflate(R.menu.album, menu);
            actionBar.setTitle(mMediaSet.getName());
            if (mMediaSet instanceof MtpDevice) {
                menu.findItem(R.id.action_slideshow).setVisible(false);
            } else {
                menu.findItem(R.id.action_slideshow).setVisible(true);
            }

            MenuItem groupBy = menu.findItem(R.id.action_group_by);
            FilterUtils.setupMenuItems(actionBar, mMediaSetPath, true);

            if (groupBy != null) {
                groupBy.setVisible(mShowClusterMenu);
            }

            actionBar.setTitle(mMediaSet.getName());
        }
        actionBar.setSubtitle(null);

        return true;
    }

    @Override
    protected boolean onItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_cancel:
                mActivity.getStateManager().finishState(this);
                return true;
            case R.id.action_select:
                mSelectionManager.setAutoLeaveSelectionMode(false);
                mSelectionManager.enterSelectionMode();
                return true;
            case R.id.action_group_by: {
                mActivity.getGalleryActionBar().showClusterDialog(this);
                return true;
            }
            case R.id.action_slideshow: {
                Bundle data = new Bundle();
                data.putString(SlideshowPage.KEY_SET_PATH,
                        mMediaSetPath.toString());
                data.putBoolean(SlideshowPage.KEY_REPEAT, true);
                mActivity.getStateManager().startStateForResult(
                        SlideshowPage.class, REQUEST_SLIDESHOW, data);
                return true;
            }
            case R.id.action_details: {
                if (mShowDetails) {
                    hideDetails();
                } else {
                    showDetails();
                }
                return true;
            }
            default:
                return false;
        }
    }

    @Override
    protected void onStateResult(int request, int result, Intent data) {
        switch (request) {
            case REQUEST_SLIDESHOW: {
                // data could be null, if there is no images in the album
                if (data == null) return;
                mFocusIndex = data.getIntExtra(SlideshowPage.KEY_PHOTO_INDEX, 0);
                mAlbumView.setCenterIndex(mFocusIndex);
                break;
            }
            case REQUEST_PHOTO: {
                if (data == null) return;
                mFocusIndex = data.getIntExtra(PhotoPage.KEY_INDEX_HINT, 0);
                mAlbumView.setCenterIndex(mFocusIndex);
                startTransition();
                break;
            }
            case REQUEST_DO_ANIMATION: {
                startTransition(null);
                break;
            }
        }
    }

    public void onSelectionModeChange(int mode) {
        switch (mode) {
            case SelectionManager.ENTER_SELECTION_MODE: {
                mActionMode = mActionModeHandler.startActionMode();
                mVibrator.vibrate(100);
                break;
            }
            case SelectionManager.LEAVE_SELECTION_MODE: {
                mActionMode.finish();
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

    public void onSelectionChange(Path path, boolean selected) {
        Utils.assertTrue(mActionMode != null);
        int count = mSelectionManager.getSelectedCount();
        String format = mActivity.getResources().getQuantityString(
                R.plurals.number_of_items_selected, count);
        mActionModeHandler.setTitle(String.format(format, count));
        mActionModeHandler.updateSupportedOperation(path, selected);
    }

    @Override
    public void onSyncDone(final MediaSet mediaSet, final int resultCode) {
        Log.d(TAG, "onSyncDone: " + Utils.maskDebugInfo(mediaSet.getName()) + " result="
                + resultCode);
        ((Activity) mActivity).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!mIsActive) return;
                mediaSet.notifyContentChanged(); // force reload to handle spinner

                if (resultCode == MediaSet.SYNC_RESULT_ERROR) {
                    Toast.makeText((Context) mActivity, R.string.sync_album_error,
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private class MyLoadingListener implements LoadingListener {
        @Override
        public void onLoadingStarted() {
            GalleryUtils.setSpinnerVisibility((Activity) mActivity, true);
        }

        @Override
        public void onLoadingFinished() {
            if (!mIsActive) return;
            if (mAlbumDataAdapter.size() == 0) {
                if (mSyncTask == null) {
                    mSyncTask = mMediaSet.requestSync(AlbumPage.this);
                }
                if (mSyncTask.isDone()){
                    Toast.makeText((Context) mActivity,
                            R.string.empty_album, Toast.LENGTH_LONG).show();
                    mActivity.getStateManager().finishState(AlbumPage.this);
                }
            }
            if (mSyncTask == null || mSyncTask.isDone()) {
                GalleryUtils.setSpinnerVisibility((Activity) mActivity, false);
            }
        }
    }

    private class MyDetailsSource implements DetailsHelper.DetailsSource {
        private int mIndex;
        public int size() {
            return mAlbumDataAdapter.size();
        }

        public int getIndex() {
            return mIndex;
        }

        // If requested index is out of active window, suggest a valid index.
        // If there is no valid index available, return -1.
        public int findIndex(int indexHint) {
            if (mAlbumDataAdapter.isActive(indexHint)) {
                mIndex = indexHint;
            } else {
                mIndex = mAlbumDataAdapter.getActiveStart();
                if (!mAlbumDataAdapter.isActive(mIndex)) {
                    return -1;
                }
            }
            return mIndex;
        }

        public MediaDetails getDetails() {
            MediaObject item = mAlbumDataAdapter.get(mIndex);
            if (item != null) {
                mHighlightDrawer.setHighlightItem(item.getPath());
                return item.getDetails();
            } else {
                return null;
            }
        }
    }
}
