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

import android.app.ActionBar.OnMenuVisibilityListener;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ShareActionProvider;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.MtpDevice;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.data.SnailSource;
import com.android.gallery3d.picasasource.PicasaSource;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.ui.DetailsHelper.CloseListener;
import com.android.gallery3d.ui.DetailsHelper.DetailsSource;
import com.android.gallery3d.ui.GLCanvas;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.ImportCompleteListener;
import com.android.gallery3d.ui.MenuExecutor;
import com.android.gallery3d.ui.PhotoView;
import com.android.gallery3d.ui.ScreenNail;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.GalleryUtils;

public class PhotoPage extends ActivityState implements
        PhotoView.Listener, OrientationManager.Listener, AppBridge.Server {
    private static final String TAG = "PhotoPage";

    private static final int MSG_HIDE_BARS = 1;
    private static final int MSG_LOCK_ORIENTATION = 2;
    private static final int MSG_UNLOCK_ORIENTATION = 3;
    private static final int MSG_ON_FULL_SCREEN_CHANGED = 4;
    private static final int MSG_UPDATE_ACTION_BAR = 5;

    private static final int HIDE_BARS_TIMEOUT = 3500;

    private static final int REQUEST_SLIDESHOW = 1;
    private static final int REQUEST_CROP = 2;
    private static final int REQUEST_CROP_PICASA = 3;
    private static final int REQUEST_EDIT = 4;

    public static final String KEY_MEDIA_SET_PATH = "media-set-path";
    public static final String KEY_MEDIA_ITEM_PATH = "media-item-path";
    public static final String KEY_INDEX_HINT = "index-hint";
    public static final String KEY_OPEN_ANIMATION_RECT = "open-animation-rect";
    public static final String KEY_APP_BRIDGE = "app-bridge";

    public static final String KEY_RETURN_INDEX_HINT = "return-index-hint";

    private GalleryApp mApplication;
    private SelectionManager mSelectionManager;

    private PhotoView mPhotoView;
    private PhotoPage.Model mModel;
    private DetailsHelper mDetailsHelper;
    private boolean mShowDetails;
    private Path mPendingSharePath;

    // mMediaSet could be null if there is no KEY_MEDIA_SET_PATH supplied.
    // E.g., viewing a photo in gmail attachment
    private MediaSet mMediaSet;
    private Menu mMenu;

    private int mCurrentIndex = 0;
    private Handler mHandler;
    private boolean mShowBars = true;
    // The value of canShowBars() last time the bar updates state.
    private boolean mCanShowBars = false;
    private volatile boolean mActionBarAllowed = true;
    private GalleryActionBar mActionBar;
    private MyMenuVisibilityListener mMenuVisibilityListener;
    private boolean mIsMenuVisible;
    private MediaItem mCurrentPhoto = null;
    private MenuExecutor mMenuExecutor;
    private boolean mIsActive;
    private ShareActionProvider mShareActionProvider;
    private String mSetPathString;
    // This is the original mSetPathString before adding the camera preview item.
    private String mOriginalSetPathString;
    private AppBridge mAppBridge;
    private ScreenNail mScreenNail;
    private MediaItem mScreenNailItem;
    private OrientationManager mOrientationManager;

    private NfcAdapter mNfcAdapter;

    public static interface Model extends PhotoView.Model {
        public void resume();
        public void pause();
        public boolean isEmpty();
        public MediaItem getCurrentMediaItem();
        public void setCurrentPhoto(Path path, int indexHint);
    }

    private class MyMenuVisibilityListener implements OnMenuVisibilityListener {
        @Override
        public void onMenuVisibilityChanged(boolean isVisible) {
            mIsMenuVisible = isVisible;
            refreshHidingMessage();
        }
    }

    private final GLView mRootPane = new GLView() {

        @Override
        protected void renderBackground(GLCanvas view) {
            view.clearBuffer();
        }

        @Override
        protected void onLayout(
                boolean changed, int left, int top, int right, int bottom) {
            mPhotoView.layout(0, 0, right - left, bottom - top);
            if (mShowDetails) {
                mDetailsHelper.layout(left, mActionBar.getHeight(), right, bottom);
            }
        }

        @Override
        protected void orient(int displayRotation, int compensation) {
            displayRotation = mOrientationManager.getDisplayRotation();
            Log.d(TAG, "orient -- display rotation " + displayRotation
                    + ", compensation = " + compensation);
            super.orient(displayRotation, compensation);
        }
    };

    @Override
    public void onCreate(Bundle data, Bundle restoreState) {
        mActionBar = mActivity.getGalleryActionBar();
        mSelectionManager = new SelectionManager(mActivity, false);
        mMenuExecutor = new MenuExecutor(mActivity, mSelectionManager);

        mPhotoView = new PhotoView(mActivity);
        mPhotoView.setListener(this);
        mRootPane.addComponent(mPhotoView);
        mApplication = (GalleryApp)((Activity) mActivity).getApplication();
        mOrientationManager = mActivity.getOrientationManager();
        mOrientationManager.addListener(this);

        mSetPathString = data.getString(KEY_MEDIA_SET_PATH);
        mOriginalSetPathString = mSetPathString;
        mNfcAdapter = NfcAdapter.getDefaultAdapter(mActivity.getAndroidContext());
        Path itemPath = Path.fromString(data.getString(KEY_MEDIA_ITEM_PATH));

        if (mSetPathString != null) {
            mAppBridge = (AppBridge) data.getParcelable(KEY_APP_BRIDGE);
            if (mAppBridge != null) {
                mOrientationManager.lockOrientation();

                // Get the ScreenNail from AppBridge and register it.
                mScreenNail = mAppBridge.attachScreenNail();
                int id = SnailSource.registerScreenNail(mScreenNail);
                Path screenNailSetPath = SnailSource.getSetPath(id);
                Path screenNailItemPath = SnailSource.getItemPath(id);
                mScreenNailItem = (MediaItem) mActivity.getDataManager()
                        .getMediaObject(screenNailItemPath);

                // Combine the original MediaSet with the one for CameraScreenNail.
                mSetPathString = "/combo/item/{" + screenNailSetPath +
                        "," + mSetPathString + "}";

                // Start from the screen nail.
                itemPath = screenNailItemPath;

                // Action bar should not be displayed when camera starts.
                mFlags |= FLAG_HIDE_ACTION_BAR;
                mShowBars = false;
            }

            mMediaSet = mActivity.getDataManager().getMediaSet(mSetPathString);
            mCurrentIndex = data.getInt(KEY_INDEX_HINT, 0);
            if (mMediaSet == null) {
                Log.w(TAG, "failed to restore " + mSetPathString);
            }
            PhotoDataAdapter pda = new PhotoDataAdapter(
                    mActivity, mPhotoView, mMediaSet, itemPath, mCurrentIndex,
                    mAppBridge == null ? -1 : 0);
            mModel = pda;
            mPhotoView.setModel(mModel);

            pda.setDataListener(new PhotoDataAdapter.DataListener() {

                @Override
                public void onPhotoChanged(int index, Path item) {
                    mCurrentIndex = index;
                    if (item != null) {
                        MediaItem photo = mModel.getCurrentMediaItem();
                        if (photo != null) updateCurrentPhoto(photo);
                    }
                    updateBars();
                }

                @Override
                public void onLoadingFinished() {
                    GalleryUtils.setSpinnerVisibility((Activity) mActivity, false);
                    if (!mModel.isEmpty()) {
                        MediaItem photo = mModel.getCurrentMediaItem();
                        if (photo != null) updateCurrentPhoto(photo);
                    } else if (mIsActive) {
                        mActivity.getStateManager().finishState(PhotoPage.this);
                    }
                }

                @Override
                public void onLoadingStarted() {
                    GalleryUtils.setSpinnerVisibility((Activity) mActivity, true);
                }
            });
        } else {
            // Get default media set by the URI
            MediaItem mediaItem = (MediaItem)
                    mActivity.getDataManager().getMediaObject(itemPath);
            mModel = new SinglePhotoDataAdapter(mActivity, mPhotoView, mediaItem);
            mPhotoView.setModel(mModel);
            updateCurrentPhoto(mediaItem);
        }

        mHandler = new SynchronizedHandler(mActivity.getGLRoot()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_HIDE_BARS: {
                        hideBars();
                        break;
                    }
                    case MSG_LOCK_ORIENTATION: {
                        mOrientationManager.lockOrientation();
                        updateBars();
                        break;
                    }
                    case MSG_UNLOCK_ORIENTATION: {
                        mOrientationManager.unlockOrientation();
                        updateBars();
                        break;
                    }
                    case MSG_ON_FULL_SCREEN_CHANGED: {
                        mAppBridge.onFullScreenChanged(message.arg1 == 1);
                        break;
                    }
                    case MSG_UPDATE_ACTION_BAR: {
                        updateBars();
                        break;
                    }
                    default: throw new AssertionError(message.what);
                }
            }
        };

        // start the opening animation only if it's not restored.
        if (restoreState == null) {
            mPhotoView.setOpenAnimationRect((Rect) data.getParcelable(KEY_OPEN_ANIMATION_RECT));
        }
    }

    private void updateShareURI(Path path) {
        if (mShareActionProvider != null) {
            DataManager manager = mActivity.getDataManager();
            int type = manager.getMediaType(path);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(MenuExecutor.getMimeType(type));
            intent.putExtra(Intent.EXTRA_STREAM, manager.getContentUri(path));
            mShareActionProvider.setShareIntent(intent);
            if (mNfcAdapter != null) {
                mNfcAdapter.setBeamPushUris(new Uri[]{manager.getContentUri(path)},
                        (Activity)mActivity);
            }
            mPendingSharePath = null;
        } else {
            // This happens when ActionBar is not created yet.
            mPendingSharePath = path;
        }
    }

    private void updateCurrentPhoto(MediaItem photo) {
        if (mCurrentPhoto == photo) return;
        mCurrentPhoto = photo;
        if (mCurrentPhoto == null) return;
        updateMenuOperations();
        updateTitle();
        if (mShowDetails) {
            mDetailsHelper.reloadDetails(mModel.getCurrentIndex());
        }
        mPhotoView.showVideoPlayIcon(
                photo.getMediaType() == MediaObject.MEDIA_TYPE_VIDEO);

        if ((photo.getSupportedOperations() & MediaItem.SUPPORT_SHARE) != 0) {
            updateShareURI(photo.getPath());
        }
    }

    private void updateTitle() {
        if (mCurrentPhoto == null) return;
        boolean showTitle = mActivity.getAndroidContext().getResources().getBoolean(
                R.bool.show_action_bar_title);
        if (showTitle && mCurrentPhoto.getName() != null)
            mActionBar.setTitle(mCurrentPhoto.getName());
        else
            mActionBar.setTitle("");
    }

    private void updateMenuOperations() {
        if (mMenu == null) return;
        MenuItem item = mMenu.findItem(R.id.action_slideshow);
        if (item != null) {
            item.setVisible(canDoSlideShow());
        }
        if (mCurrentPhoto == null) return;
        int supportedOperations = mCurrentPhoto.getSupportedOperations();
        if (!GalleryUtils.isEditorAvailable((Context) mActivity, "image/*")) {
            supportedOperations &= ~MediaObject.SUPPORT_EDIT;
        }

        MenuExecutor.updateMenuOperation(mMenu, supportedOperations);
    }

    private boolean canDoSlideShow() {
        if (mMediaSet == null || mCurrentPhoto == null) {
            return false;
        }
        if (mCurrentPhoto.getMediaType() != MediaObject.MEDIA_TYPE_IMAGE) {
            return false;
        }
        if (mMediaSet instanceof MtpDevice) {
            return false;
        }
        return true;
    }

    //////////////////////////////////////////////////////////////////////////
    //  Action Bar show/hide management
    //////////////////////////////////////////////////////////////////////////

    private void showBars() {
        if (mShowBars) return;
        mShowBars = true;
        mActionBar.show();
        WindowManager.LayoutParams params =
                ((Activity) mActivity).getWindow().getAttributes();
        params.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE;
        ((Activity) mActivity).getWindow().setAttributes(params);
        refreshHidingMessage();
    }

    private void hideBars() {
        if (!mShowBars) return;
        mShowBars = false;
        mActionBar.hide();
        WindowManager.LayoutParams params =
                ((Activity) mActivity).getWindow().getAttributes();
        params.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE;
        ((Activity) mActivity).getWindow().setAttributes(params);
        mHandler.removeMessages(MSG_HIDE_BARS);
    }

    private void refreshHidingMessage() {
        mHandler.removeMessages(MSG_HIDE_BARS);
        if (!mIsMenuVisible) {
            mHandler.sendEmptyMessageDelayed(MSG_HIDE_BARS, HIDE_BARS_TIMEOUT);
        }
    }

    private boolean canShowBars() {
        // No bars if we are showing camera preview.
        if (mAppBridge != null && mCurrentIndex == 0) return false;
        // No bars if it's not allowed.
        if (!mActionBarAllowed) return false;
        // No bars if the orientation is locked.
        if (mOrientationManager.isOrientationLocked()) return false;

        return true;
    }

    private void toggleBars() {
        mCanShowBars = canShowBars();
        if (mShowBars) {
            hideBars();
        } else {
            if (mCanShowBars) showBars();
        }
    }

    private void updateBars() {
        boolean v = canShowBars();
        if (mCanShowBars == v) return;
        mCanShowBars = v;

        if (mCanShowBars) {
            showBars();
        } else {
            hideBars();
        }
    }

    @Override
    public void onOrientationCompensationChanged(int degrees) {
        mActivity.getGLRoot().setOrientationCompensation(degrees);
    }

    @Override
    protected void onBackPressed() {
        if (mShowDetails) {
            hideDetails();
        } else if (mScreenNail == null
                || !switchWithCaptureAnimation(-1)) {
            // We are leaving this page. Set the result now.
            setResult();
            super.onBackPressed();
        }
    }

    private void onUpPressed() {
        if (mActivity.getStateManager().getStateCount() > 1) {
            super.onBackPressed();
        } else if (mOriginalSetPathString != null) {
            // We're in view mode so set up the stacks on our own.
            Bundle data = new Bundle(getData());
            data.putString(AlbumPage.KEY_MEDIA_PATH, mOriginalSetPathString);
            data.putString(AlbumPage.KEY_PARENT_MEDIA_PATH,
                    mActivity.getDataManager().getTopSetPath(
                            DataManager.INCLUDE_ALL));
            mActivity.getStateManager().switchState(this, AlbumPage.class, data);
        }
    }

    private void setResult() {
        Intent result = null;
        if (!mPhotoView.getFilmMode()) {
            result = new Intent();
            result.putExtra(KEY_RETURN_INDEX_HINT, mCurrentIndex);
        }
        setStateResult(Activity.RESULT_OK, result);
    }

    //////////////////////////////////////////////////////////////////////////
    //  AppBridge.Server interface
    //////////////////////////////////////////////////////////////////////////

    @Override
    public void setCameraNaturalFrame(Rect frame) {
        mPhotoView.setCameraNaturalFrame(frame);
    }

    @Override
    public boolean switchWithCaptureAnimation(int offset) {
        return mPhotoView.switchWithCaptureAnimation(offset);
    }

    @Override
    protected boolean onCreateActionBar(Menu menu) {
        MenuInflater inflater = ((Activity) mActivity).getMenuInflater();
        inflater.inflate(R.menu.photo, menu);
        mShareActionProvider = GalleryActionBar.initializeShareActionProvider(menu);
        if (mPendingSharePath != null) updateShareURI(mPendingSharePath);
        mMenu = menu;
        updateMenuOperations();
        updateTitle();
        return true;
    }

    @Override
    protected boolean onItemSelected(MenuItem item) {
        MediaItem current = mModel.getCurrentMediaItem();

        if (current == null) {
            // item is not ready, ignore
            return true;
        }

        int currentIndex = mModel.getCurrentIndex();
        Path path = current.getPath();

        DataManager manager = mActivity.getDataManager();
        int action = item.getItemId();
        boolean needsConfirm = false;
        switch (action) {
            case android.R.id.home: {
                onUpPressed();
                return true;
            }
            case R.id.action_slideshow: {
                Bundle data = new Bundle();
                data.putString(SlideshowPage.KEY_SET_PATH, mMediaSet.getPath().toString());
                data.putString(SlideshowPage.KEY_ITEM_PATH, path.toString());
                data.putInt(SlideshowPage.KEY_PHOTO_INDEX, currentIndex);
                data.putBoolean(SlideshowPage.KEY_REPEAT, true);
                mActivity.getStateManager().startStateForResult(
                        SlideshowPage.class, REQUEST_SLIDESHOW, data);
                return true;
            }
            case R.id.action_crop: {
                Activity activity = (Activity) mActivity;
                Intent intent = new Intent(CropImage.CROP_ACTION);
                intent.setClass(activity, CropImage.class);
                intent.setData(manager.getContentUri(path));
                activity.startActivityForResult(intent, PicasaSource.isPicasaImage(current)
                        ? REQUEST_CROP_PICASA
                        : REQUEST_CROP);
                return true;
            }
            case R.id.action_edit: {
                Intent intent = new Intent(Intent.ACTION_EDIT)
                        .setData(manager.getContentUri(path))
                        .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                ((Activity) mActivity).startActivityForResult(Intent.createChooser(intent, null),
                        REQUEST_EDIT);
                return true;
            }
            case R.id.action_details: {
                if (mShowDetails) {
                    hideDetails();
                } else {
                    showDetails(currentIndex);
                }
                return true;
            }
            case R.id.action_delete:
                needsConfirm = true;
            case R.id.action_setas:
            case R.id.action_rotate_ccw:
            case R.id.action_rotate_cw:
            case R.id.action_show_on_map:
                mSelectionManager.deSelectAll();
                mSelectionManager.toggle(path);
                mMenuExecutor.onMenuClicked(item, needsConfirm, null);
                return true;
            case R.id.action_import:
                mSelectionManager.deSelectAll();
                mSelectionManager.toggle(path);
                mMenuExecutor.onMenuClicked(item, needsConfirm,
                        new ImportCompleteListener(mActivity));
                return true;
            default :
                return false;
        }
    }

    private void hideDetails() {
        mShowDetails = false;
        mDetailsHelper.hide();
    }

    private void showDetails(int index) {
        mShowDetails = true;
        if (mDetailsHelper == null) {
            mDetailsHelper = new DetailsHelper(mActivity, mRootPane, new MyDetailsSource());
            mDetailsHelper.setCloseListener(new CloseListener() {
                @Override
                public void onClose() {
                    hideDetails();
                }
            });
        }
        mDetailsHelper.reloadDetails(index);
        mDetailsHelper.show();
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Callbacks from PhotoView
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onSingleTapUp(int x, int y) {
        if (mAppBridge != null) {
            if (mAppBridge.onSingleTapUp(x, y)) return;
        }

        MediaItem item = mModel.getCurrentMediaItem();
        if (item == null || item == mScreenNailItem) {
            // item is not ready or it is camera preview, ignore
            return;
        }

        boolean playVideo =
                (item.getSupportedOperations() & MediaItem.SUPPORT_PLAY) != 0;

        if (playVideo) {
            // determine if the point is at center (1/6) of the photo view.
            // (The position of the "play" icon is at center (1/6) of the photo)
            int w = mPhotoView.getWidth();
            int h = mPhotoView.getHeight();
            playVideo = (Math.abs(x - w / 2) * 12 <= w)
                && (Math.abs(y - h / 2) * 12 <= h);
        }

        if (playVideo) {
            playVideo((Activity) mActivity, item.getPlayUri(), item.getName());
        } else {
            toggleBars();
        }
    }

    @Override
    public void lockOrientation() {
        mHandler.sendEmptyMessage(MSG_LOCK_ORIENTATION);
    }

    @Override
    public void unlockOrientation() {
        mHandler.sendEmptyMessage(MSG_UNLOCK_ORIENTATION);
    }

    @Override
    public void onActionBarAllowed(boolean allowed) {
        mActionBarAllowed = allowed;
        mHandler.sendEmptyMessage(MSG_UPDATE_ACTION_BAR);
    }

    @Override
    public void onFullScreenChanged(boolean full) {
        Message m = mHandler.obtainMessage(
                MSG_ON_FULL_SCREEN_CHANGED, full ? 1 : 0, 0);
        m.sendToTarget();
    }

    public static void playVideo(Activity activity, Uri uri, String title) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "video/*");
            intent.putExtra(Intent.EXTRA_TITLE, title);
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, activity.getString(R.string.video_err),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void setCurrentPhotoByIntent(Intent intent) {
        if (intent == null) return;
        Path path = mApplication.getDataManager()
                .findPathByUri(intent.getData(), intent.getType());
        if (path != null) {
            mModel.setCurrentPhoto(path, mCurrentIndex);
        }
    }

    @Override
    protected void onStateResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_EDIT:
                setCurrentPhotoByIntent(data);
                break;
            case REQUEST_CROP:
                if (resultCode == Activity.RESULT_OK) {
                    setCurrentPhotoByIntent(data);
                }
                break;
            case REQUEST_CROP_PICASA: {
                if (resultCode == Activity.RESULT_OK) {
                    Context context = mActivity.getAndroidContext();
                    // TODO: Use crop_saved instead of photo_saved after its new translation is done.
                    String message = context.getString(R.string.photo_saved,
                            context.getString(R.string.folder_download));
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                }
                break;
            }
            case REQUEST_SLIDESHOW: {
                if (data == null) break;
                String path = data.getStringExtra(SlideshowPage.KEY_ITEM_PATH);
                int index = data.getIntExtra(SlideshowPage.KEY_PHOTO_INDEX, 0);
                if (path != null) {
                    mModel.setCurrentPhoto(Path.fromString(path), index);
                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsActive = false;
        if (mAppBridge != null) mAppBridge.setServer(null);
        DetailsHelper.pause();
        mPhotoView.pause();
        mModel.pause();
        mHandler.removeMessages(MSG_HIDE_BARS);
        mActionBar.removeOnMenuVisibilityListener(mMenuVisibilityListener);

        mMenuExecutor.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsActive = true;
        setContentPane(mRootPane);

        mModel.resume();
        mPhotoView.resume();
        if (mMenuVisibilityListener == null) {
            mMenuVisibilityListener = new MyMenuVisibilityListener();
        }
        mActionBar.setDisplayOptions(mSetPathString != null, true);
        mActionBar.addOnMenuVisibilityListener(mMenuVisibilityListener);

        if (mAppBridge != null) {
            mAppBridge.setServer(this);
            mPhotoView.resetToFirstPicture();
        }
    }

    @Override
    protected void onDestroy() {
        if (mAppBridge != null) {
            // Unregister the ScreenNail and notify mAppBridge.
            SnailSource.unregisterScreenNail(mScreenNail);
            mAppBridge.detachScreenNail();
            mAppBridge = null;
            mScreenNail = null;
        }
        mOrientationManager.removeListener(this);

        // Remove all pending messages.
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private class MyDetailsSource implements DetailsSource {
        private int mIndex;

        @Override
        public MediaDetails getDetails() {
            return mModel.getCurrentMediaItem().getDetails();
        }

        @Override
        public int size() {
            return mMediaSet != null ? mMediaSet.getMediaItemCount() : 1;
        }

        @Override
        public int findIndex(int indexHint) {
            mIndex = indexHint;
            return indexHint;
        }

        @Override
        public int getIndex() {
            return mIndex;
        }
    }
}
