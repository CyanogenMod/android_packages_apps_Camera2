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

import android.annotation.TargetApi;
import android.app.ActionBar.OnMenuVisibilityListener;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateBeamUrisCallback;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.AccelerateInterpolator;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.anim.FloatAnimation;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.ComboAlbum;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.FilterDeleteSet;
import com.android.gallery3d.data.FilterSource;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.MtpSource;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.data.SecureAlbum;
import com.android.gallery3d.data.SecureSource;
import com.android.gallery3d.data.SnailAlbum;
import com.android.gallery3d.data.SnailItem;
import com.android.gallery3d.data.SnailSource;
import com.android.gallery3d.picasasource.PicasaSource;
import com.android.gallery3d.ui.AnimationTime;
import com.android.gallery3d.ui.BitmapScreenNail;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.ui.DetailsHelper.CloseListener;
import com.android.gallery3d.ui.DetailsHelper.DetailsSource;
import com.android.gallery3d.ui.GLCanvas;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLRoot.OnGLIdleListener;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.ImportCompleteListener;
import com.android.gallery3d.ui.MenuExecutor;
import com.android.gallery3d.ui.PhotoFallbackEffect;
import com.android.gallery3d.ui.PhotoView;
import com.android.gallery3d.ui.PreparePageFadeoutTexture;
import com.android.gallery3d.ui.RawTexture;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.LightCycleHelper;

public class PhotoPage extends ActivityState implements
        PhotoView.Listener, OrientationManager.Listener, AppBridge.Server,
        PhotoPageBottomControls.Delegate, GalleryActionBar.OnAlbumModeSelectedListener {
    private static final String TAG = "PhotoPage";

    private static final int MSG_HIDE_BARS = 1;
    private static final int MSG_ON_FULL_SCREEN_CHANGED = 4;
    private static final int MSG_UPDATE_ACTION_BAR = 5;
    private static final int MSG_UNFREEZE_GLROOT = 6;
    private static final int MSG_WANT_BARS = 7;
    private static final int MSG_REFRESH_BOTTOM_CONTROLS = 8;
    private static final int MSG_ON_CAMERA_CENTER = 9;
    private static final int MSG_ON_PICTURE_CENTER = 10;
    private static final int MSG_REFRESH_IMAGE = 11;

    private static final int HIDE_BARS_TIMEOUT = 3500;
    private static final int UNFREEZE_GLROOT_TIMEOUT = 250;

    private static final int REQUEST_SLIDESHOW = 1;
    private static final int REQUEST_CROP = 2;
    private static final int REQUEST_CROP_PICASA = 3;
    private static final int REQUEST_EDIT = 4;
    private static final int REQUEST_PLAY_VIDEO = 5;
    private static final int REQUEST_TRIM = 6;

    public static final String KEY_MEDIA_SET_PATH = "media-set-path";
    public static final String KEY_MEDIA_ITEM_PATH = "media-item-path";
    public static final String KEY_INDEX_HINT = "index-hint";
    public static final String KEY_OPEN_ANIMATION_RECT = "open-animation-rect";
    public static final String KEY_APP_BRIDGE = "app-bridge";
    public static final String KEY_TREAT_BACK_AS_UP = "treat-back-as-up";
    public static final String KEY_START_IN_FILMSTRIP = "start-in-filmstrip";
    public static final String KEY_RETURN_INDEX_HINT = "return-index-hint";
    public static final String KEY_SHOW_WHEN_LOCKED = "show_when_locked";
    public static final String KEY_IN_CAMERA_ROLL = "in_camera_roll";

    public static final String KEY_ALBUMPAGE_TRANSITION = "albumpage-transition";
    public static final int MSG_ALBUMPAGE_NONE = 0;
    public static final int MSG_ALBUMPAGE_STARTED = 1;
    public static final int MSG_ALBUMPAGE_RESUMED = 2;
    public static final int MSG_ALBUMPAGE_PICKED = 4;

    public static final String ACTION_NEXTGEN_EDIT = "action_nextgen_edit";

    private GalleryApp mApplication;
    private SelectionManager mSelectionManager;

    private PhotoView mPhotoView;
    private PhotoPage.Model mModel;
    private DetailsHelper mDetailsHelper;
    private boolean mShowDetails;

    // mMediaSet could be null if there is no KEY_MEDIA_SET_PATH supplied.
    // E.g., viewing a photo in gmail attachment
    private FilterDeleteSet mMediaSet;

    // The mediaset used by camera launched from secure lock screen.
    private SecureAlbum mSecureAlbum;

    private int mCurrentIndex = 0;
    private Handler mHandler;
    private boolean mShowBars = true;
    private volatile boolean mActionBarAllowed = true;
    private GalleryActionBar mActionBar;
    private boolean mIsMenuVisible;
    private boolean mHaveImageEditor;
    private PhotoPageBottomControls mBottomControls;
    private PhotoPageProgressBar mProgressBar;
    private MediaItem mCurrentPhoto = null;
    private MenuExecutor mMenuExecutor;
    private boolean mIsActive;
    private boolean mShowSpinner;
    private String mSetPathString;
    // This is the original mSetPathString before adding the camera preview item.
    private String mOriginalSetPathString;
    private AppBridge mAppBridge;
    private SnailItem mScreenNailItem;
    private SnailAlbum mScreenNailSet;
    private OrientationManager mOrientationManager;
    private boolean mHasActivityResult;
    private boolean mTreatBackAsUp;
    private boolean mStartInFilmstrip;
    private boolean mInCameraRoll;
    private boolean mStartedFromAlbumPage;
    private boolean mRecenterCameraOnResume = true;

    private long mCameraSwitchCutoff = 0;
    private boolean mSkipUpdateCurrentPhoto = false;
    private static final long CAMERA_SWITCH_CUTOFF_THRESHOLD_MS = 300;

    private RawTexture mFadeOutTexture;
    private Rect mOpenAnimationRect;
    public static final int ANIM_TIME_OPENING = 300;

    // The item that is deleted (but it can still be undeleted before commiting)
    private Path mDeletePath;
    private boolean mDeleteIsFocus;  // whether the deleted item was in focus

    private Uri[] mNfcPushUris = new Uri[1];

    private final MyMenuVisibilityListener mMenuVisibilityListener =
            new MyMenuVisibilityListener();
    private UpdateProgressListener mProgressListener;

    public static interface Model extends PhotoView.Model {
        public void resume();
        public void pause();
        public boolean isEmpty();
        public void setCurrentPhoto(Path path, int indexHint);
    }

    private class MyMenuVisibilityListener implements OnMenuVisibilityListener {
        @Override
        public void onMenuVisibilityChanged(boolean isVisible) {
            mIsMenuVisible = isVisible;
            refreshHidingMessage();
        }
    }

    private static class BackgroundFadeOut extends FloatAnimation {
        public BackgroundFadeOut() {
            super(1f, 0f, ANIM_TIME_OPENING);
            setInterpolator(new AccelerateInterpolator(2f));
        }
    }

    private class UpdateProgressListener implements StitchingChangeListener {

        @Override
        public void onStitchingResult(Path path, Uri uri) {
            sendUpdate(path);
        }

        @Override
        public void onStitchingQueued(Path path) {
            sendUpdate(path);
        }

        @Override
        public void onStitchingProgress(Path path, final int progress) {
            sendUpdate(path);
        }

        private void sendUpdate(Path path) {
            boolean isCurrentPhoto = mCurrentPhoto != null
                    && mCurrentPhoto.getPath().toString().equals(path.toString());
            if (isCurrentPhoto) {
                mHandler.sendEmptyMessage(MSG_REFRESH_IMAGE);

            }
        }
    };

    private final FloatAnimation mBackgroundFade = new BackgroundFadeOut();

    @Override
    protected int getBackgroundColorId() {
        return R.color.photo_background;
    }

    private final GLView mRootPane = new GLView() {
        @Override
        protected void renderBackground(GLCanvas view) {
            if (mFadeOutTexture != null) {
                if (mBackgroundFade.calculate(AnimationTime.get())) invalidate();
                if (!mBackgroundFade.isActive()) {
                    mFadeOutTexture = null;
                    mOpenAnimationRect = null;
                    BitmapScreenNail.enableDrawPlaceholder();
                } else {
                    float fadeAlpha = mBackgroundFade.get();
                    if (fadeAlpha < 1f) {
                        view.clearBuffer(getBackgroundColor());
                        view.setAlpha(fadeAlpha);
                    }
                    mFadeOutTexture.draw(view, 0, 0);
                    view.setAlpha(1f - fadeAlpha);
                    return;
                }
            }
            view.clearBuffer(getBackgroundColor());
        }

        @Override
        protected void onLayout(
                boolean changed, int left, int top, int right, int bottom) {
            mPhotoView.layout(0, 0, right - left, bottom - top);
            if (mShowDetails) {
                mDetailsHelper.layout(left, mActionBar.getHeight(), right, bottom);
            }
        }
    };

    @Override
    public void onCreate(Bundle data, Bundle restoreState) {
        super.onCreate(data, restoreState);
        mActionBar = mActivity.getGalleryActionBar();
        mSelectionManager = new SelectionManager(mActivity, false);
        mMenuExecutor = new MenuExecutor(mActivity, mSelectionManager);

        mPhotoView = new PhotoView(mActivity);
        mPhotoView.setListener(this);
        mRootPane.addComponent(mPhotoView);
        mApplication = (GalleryApp) ((Activity) mActivity).getApplication();
        mOrientationManager = mActivity.getOrientationManager();
        mOrientationManager.addListener(this);
        mActivity.getGLRoot().setOrientationSource(mOrientationManager);

        mHandler = new SynchronizedHandler(mActivity.getGLRoot()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_HIDE_BARS: {
                        hideBars();
                        break;
                    }
                    case MSG_REFRESH_BOTTOM_CONTROLS: {
                        if (mBottomControls != null) mBottomControls.refresh();
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
                    case MSG_WANT_BARS: {
                        wantBars();
                        break;
                    }
                    case MSG_UNFREEZE_GLROOT: {
                        mActivity.getGLRoot().unfreeze();
                        break;
                    }
                    case MSG_ON_CAMERA_CENTER: {
                        mSkipUpdateCurrentPhoto = false;
                        boolean stayedOnCamera = false;
                        if (!mPhotoView.getFilmMode()) {
                            stayedOnCamera = true;
                        } else if (SystemClock.uptimeMillis() < mCameraSwitchCutoff &&
                                mMediaSet.getMediaItemCount() > 1) {
                            mPhotoView.switchToImage(1);
                        } else {
                            mPhotoView.setFilmMode(mPhotoView.canUndo());
                            stayedOnCamera = true;
                        }

                        if (stayedOnCamera) {
                            updateBars();
                            updateCurrentPhoto(mModel.getMediaItem(0));
                        }
                        break;
                    }
                    case MSG_ON_PICTURE_CENTER: {
                        if (mCurrentPhoto != null
                                && (mCurrentPhoto.getSupportedOperations() & MediaObject.SUPPORT_ACTION) != 0) {
                            mPhotoView.setFilmMode(true);
                        }
                        break;
                    }
                    case MSG_REFRESH_IMAGE: {
                        MediaItem currentPhoto = mCurrentPhoto;
                        mCurrentPhoto = null;
                        updateCurrentPhoto(currentPhoto);
                        break;
                    }
                    default: throw new AssertionError(message.what);
                }
            }
        };

        mSetPathString = data.getString(KEY_MEDIA_SET_PATH);
        mOriginalSetPathString = mSetPathString;
        setupNfcBeamPush();
        String itemPathString = data.getString(KEY_MEDIA_ITEM_PATH);
        Path itemPath = itemPathString != null ?
                Path.fromString(data.getString(KEY_MEDIA_ITEM_PATH)) :
                    null;
        mTreatBackAsUp = data.getBoolean(KEY_TREAT_BACK_AS_UP, false);
        mStartInFilmstrip = data.getBoolean(KEY_START_IN_FILMSTRIP, false);
        mInCameraRoll = data.getBoolean(KEY_IN_CAMERA_ROLL, false);
        mStartedFromAlbumPage =
                data.getInt(KEY_ALBUMPAGE_TRANSITION,
                        MSG_ALBUMPAGE_NONE) == MSG_ALBUMPAGE_STARTED;
        mCurrentIndex = data.getInt(KEY_INDEX_HINT, 0);
        if (mSetPathString != null) {
            mShowSpinner = true;
            mAppBridge = (AppBridge) data.getParcelable(KEY_APP_BRIDGE);
            if (mAppBridge != null) {
                mShowBars = false;
                mInCameraRoll = true;
                mAppBridge.setServer(this);

                // Get the ScreenNail from AppBridge and register it.
                int id = SnailSource.newId();
                Path screenNailSetPath = SnailSource.getSetPath(id);
                Path screenNailItemPath = SnailSource.getItemPath(id);
                mScreenNailSet = (SnailAlbum) mActivity.getDataManager()
                        .getMediaObject(screenNailSetPath);
                mScreenNailItem = (SnailItem) mActivity.getDataManager()
                        .getMediaObject(screenNailItemPath);
                mScreenNailItem.setScreenNail(mAppBridge.attachScreenNail());

                if (data.getBoolean(KEY_SHOW_WHEN_LOCKED, false)) {
                    // Set the flag to be on top of the lock screen.
                    mFlags |= FLAG_SHOW_WHEN_LOCKED;
                }

                // Don't display "empty album" action item or panorama
                // progress for capture intents.
                if (!mSetPathString.equals("/local/all/0")) {
                    // Check if the path is a secure album.
                    if (SecureSource.isSecurePath(mSetPathString)) {
                        mSecureAlbum = (SecureAlbum) mActivity.getDataManager()
                                .getMediaSet(mSetPathString);
                        mShowSpinner = false;
                    } else {
                        // Use lightcycle album to handle panorama progress if
                        // the path is not a secure album.
                        if (LightCycleHelper.hasLightCycleCapture(mActivity.getAndroidContext())) {
                            mSetPathString = LightCycleHelper.wrapGalleryPath(mSetPathString);
                        }
                    }
                    mSetPathString = "/filter/empty/{"+mSetPathString+"}";
                }

                // Combine the original MediaSet with the one for ScreenNail
                // from AppBridge.
                mSetPathString = "/combo/item/{" + screenNailSetPath +
                        "," + mSetPathString + "}";

                // Start from the screen nail.
                itemPath = screenNailItemPath;
            } else if (mInCameraRoll && GalleryUtils.isCameraAvailable(mActivity)) {
                mSetPathString = "/combo/item/{" + FilterSource.FILTER_CAMERA_SHORTCUT +
                        "," + mSetPathString + "}";
                mCurrentIndex++;
            }

            MediaSet originalSet = mActivity.getDataManager()
                    .getMediaSet(mSetPathString);
            if (mInCameraRoll && originalSet instanceof ComboAlbum) {
                // Use the name of the camera album rather than the default
                // ComboAlbum behavior
                ((ComboAlbum) originalSet).useNameOfChild(1);
            }
            mSelectionManager.setSourceMediaSet(originalSet);
            mSetPathString = "/filter/delete/{" + mSetPathString + "}";
            mMediaSet = (FilterDeleteSet) mActivity.getDataManager()
                    .getMediaSet(mSetPathString);
            if (mMediaSet == null) {
                Log.w(TAG, "failed to restore " + mSetPathString);
            }
            if (itemPath == null) {
                int mediaItemCount = mMediaSet.getMediaItemCount();
                if (mediaItemCount > 0) {
                    if (mCurrentIndex >= mediaItemCount) mCurrentIndex = 0;
                    itemPath = mMediaSet.getMediaItem(mCurrentIndex, 1)
                        .get(0).getPath();
                } else {
                    // Bail out, PhotoPage can't load on an empty album
                    return;
                }
            }
            PhotoDataAdapter pda = new PhotoDataAdapter(
                    mActivity, mPhotoView, mMediaSet, itemPath, mCurrentIndex,
                    mAppBridge == null ? -1 : 0,
                    mAppBridge == null ? false : mAppBridge.isPanorama(),
                    mAppBridge == null ? false : mAppBridge.isStaticCamera());
            mModel = pda;
            mPhotoView.setModel(mModel);

            pda.setDataListener(new PhotoDataAdapter.DataListener() {

                @Override
                public void onPhotoChanged(int index, Path item) {
                    int oldIndex = mCurrentIndex;
                    mCurrentIndex = index;

                    if (mAppBridge != null) {
                        if (mCurrentIndex > 0) {
                            mSkipUpdateCurrentPhoto = false;
                        }

                        if (oldIndex == 0 && mCurrentIndex > 0
                                && !mPhotoView.getFilmMode()) {
                            mPhotoView.setFilmMode(true);
                        } else if (oldIndex == 2 && mCurrentIndex == 1) {
                            mCameraSwitchCutoff = SystemClock.uptimeMillis() +
                                    CAMERA_SWITCH_CUTOFF_THRESHOLD_MS;
                            mPhotoView.stopScrolling();
                        } else if (oldIndex >= 1 && mCurrentIndex == 0) {
                            mPhotoView.setWantPictureCenterCallbacks(true);
                            mSkipUpdateCurrentPhoto = true;
                        }
                    }
                    if (!mSkipUpdateCurrentPhoto) {
                        if (item != null) {
                            MediaItem photo = mModel.getMediaItem(0);
                            if (photo != null) updateCurrentPhoto(photo);
                        }
                        updateBars();
                    }
                    // Reset the timeout for the bars after a swipe
                    refreshHidingMessage();
                }

                @Override
                public void onLoadingFinished() {
                    if (!mModel.isEmpty()) {
                        MediaItem photo = mModel.getMediaItem(0);
                        if (photo != null) updateCurrentPhoto(photo);
                    } else if (mIsActive) {
                        // We only want to finish the PhotoPage if there is no
                        // deletion that the user can undo.
                        if (mMediaSet.getNumberOfDeletions() == 0) {
                            mActivity.getStateManager().finishState(
                                    PhotoPage.this);
                        }
                    }
                }

                @Override
                public void onLoadingStarted() {
                }
            });
        } else {
            // Get default media set by the URI
            MediaItem mediaItem = (MediaItem)
                    mActivity.getDataManager().getMediaObject(itemPath);
            mModel = new SinglePhotoDataAdapter(mActivity, mPhotoView, mediaItem);
            mPhotoView.setModel(mModel);
            updateCurrentPhoto(mediaItem);
            mShowSpinner = false;
        }

        mPhotoView.setFilmMode(mStartInFilmstrip && mMediaSet.getMediaItemCount() > 1);
        RelativeLayout galleryRoot = (RelativeLayout) ((Activity) mActivity)
                .findViewById(mAppBridge != null ? R.id.content : R.id.gallery_root);
        if (galleryRoot != null) {
            if (mSecureAlbum == null) {
                mBottomControls = new PhotoPageBottomControls(this, mActivity, galleryRoot);
            }
            StitchingProgressManager progressManager = mApplication.getStitchingProgressManager();
            if (progressManager != null) {
                mProgressBar = new PhotoPageProgressBar(mActivity, galleryRoot);
                mProgressListener = new UpdateProgressListener();
                progressManager.addChangeListener(mProgressListener);
            }
        }
    }

    public void onPictureCenter(boolean isCamera) {
        mPhotoView.setWantPictureCenterCallbacks(false);
        mHandler.removeMessages(MSG_ON_CAMERA_CENTER);
        mHandler.removeMessages(MSG_ON_PICTURE_CENTER);
        mHandler.sendEmptyMessage(isCamera ? MSG_ON_CAMERA_CENTER : MSG_ON_PICTURE_CENTER);
    }

    public boolean canDisplayBottomControls() {
        return mIsActive && !mPhotoView.getFilmMode();
    }

    public boolean canDisplayBottomControl(int control) {
        if (mCurrentPhoto == null) return false;
        switch(control) {
            case R.id.photopage_bottom_control_edit:
                return mHaveImageEditor && mShowBars
                        && (mCurrentPhoto.getSupportedOperations()
                        & MediaItem.SUPPORT_EDIT) != 0
                        && mCurrentPhoto.getMediaType()
                        == MediaObject.MEDIA_TYPE_IMAGE;
            case R.id.photopage_bottom_control_panorama:
                return (mCurrentPhoto.getSupportedOperations()
                        & MediaItem.SUPPORT_PANORAMA) != 0;
            default:
                return false;
        }
    }

    public void onBottomControlClicked(int control) {
        switch(control) {
            case R.id.photopage_bottom_control_edit:
                launchPhotoEditor();
                return;
            case R.id.photopage_bottom_control_panorama:
                mRecenterCameraOnResume = false;
                LightCycleHelper.viewPanorama(mActivity, mCurrentPhoto.getContentUri());
                return;
            default:
                return;
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.JELLY_BEAN)
    private void setupNfcBeamPush() {
        if (!ApiHelper.HAS_SET_BEAM_PUSH_URIS) return;

        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mActivity);
        if (adapter != null) {
            adapter.setBeamPushUris(null, mActivity);
            adapter.setBeamPushUrisCallback(new CreateBeamUrisCallback() {
                @Override
                public Uri[] createBeamUris(NfcEvent event) {
                    return mNfcPushUris;
                }
            }, mActivity);
        }
    }

    private void setNfcBeamPushUri(Uri uri) {
        mNfcPushUris[0] = uri;
    }

    private Intent createShareIntent(Path path) {
        DataManager manager = mActivity.getDataManager();
        int type = manager.getMediaType(path);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(MenuExecutor.getMimeType(type));
        Uri uri = manager.getContentUri(path);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        return intent;
    }

    private Intent createSharePanoramaIntent(Path path) {
        DataManager manager = mActivity.getDataManager();
        int supported = manager.getSupportedOperations(path);
        if ((supported & MediaObject.SUPPORT_PANORAMA360) == 0) {
            return null;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(GalleryUtils.MIME_TYPE_PANORAMA360);
        Uri uri = manager.getContentUri(path);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        return intent;
    }

    private void launchPhotoEditor() {
        MediaItem current = mModel.getMediaItem(0);
        if (current == null || (current.getSupportedOperations()
                & MediaObject.SUPPORT_EDIT) == 0) {
            return;
        }

        Intent intent = new Intent(ACTION_NEXTGEN_EDIT);

        intent.setDataAndType(current.getContentUri(), current.getMimeType())
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (mActivity.getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).size() == 0) {
            intent.setAction(Intent.ACTION_EDIT);
        }
        mRecenterCameraOnResume = false;
        ((Activity) mActivity).startActivityForResult(Intent.createChooser(intent, null),
                REQUEST_EDIT);
    }

    private void updateShareURI(Path path) {
        DataManager manager = mActivity.getDataManager();
        mActionBar.setShareIntents(
                createSharePanoramaIntent(path),
                createShareIntent(path));
        Uri uri = manager.getContentUri(path);
        setNfcBeamPushUri(uri);
    }

    private void updateCurrentPhoto(MediaItem photo) {
        if (mCurrentPhoto == photo) return;
        mCurrentPhoto = photo;
        if (mCurrentPhoto == null) return;

        // If by swiping or deletion the user ends up on an action item
        // and zoomed in, zoom out so that the context of the action is
        // more clear
        if ((photo.getSupportedOperations() & MediaObject.SUPPORT_ACTION) != 0
                && !mPhotoView.getFilmMode()) {
            mPhotoView.setWantPictureCenterCallbacks(true);
        }

        updateMenuOperations();
        if (mBottomControls != null) mBottomControls.refresh();
        if (mShowDetails) {
            mDetailsHelper.reloadDetails();
        }
        if ((mSecureAlbum == null)
                && (photo.getSupportedOperations() & MediaItem.SUPPORT_SHARE) != 0) {
            updateShareURI(photo.getPath());
        }
        StitchingProgressManager progressManager = mApplication.getStitchingProgressManager();
        if (progressManager != null) {
            int itemCount = progressManager.getItemCount();
            mProgressBar.hideProgress();
            for (int i = 0; i < itemCount; i++) {
                MediaItem item = progressManager.getItem(i);
                if (item.getPath().equals(photo.getPath())) {
                    mProgressBar.setProgress(progressManager.getProgress(item.getFilePath()));
                    break;
                }
            }
        }
    }

    private void updateMenuOperations() {
        Menu menu = mActionBar.getMenu();

        // it could be null if onCreateActionBar has not been called yet
        if (menu == null) return;

        MenuItem item = menu.findItem(R.id.action_slideshow);
        if (item != null) {
            item.setVisible((mSecureAlbum == null) && canDoSlideShow());
        }
        if (mCurrentPhoto == null) return;

        int supportedOperations = mCurrentPhoto.getSupportedOperations();
        if (mSecureAlbum != null) {
            supportedOperations &= MediaObject.SUPPORT_DELETE;
        } else if (!mHaveImageEditor) {
            supportedOperations &= ~MediaObject.SUPPORT_EDIT;
        }
        MenuExecutor.updateMenuOperation(menu, supportedOperations);
        if ((supportedOperations & MediaObject.SUPPORT_PANORAMA360) != 0) {
            mActivity.invalidateOptionsMenu();
            item = menu.findItem(R.id.action_share);
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            item.setTitle(
                    mActivity.getResources().getString(R.string.share_as_photo));
        } else if ((supportedOperations & MediaObject.SUPPORT_SHARE) != 0) {
            item = menu.findItem(R.id.action_share);
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            item.setTitle(
                    mActivity.getResources().getString(R.string.share));
        }
    }

    private boolean canDoSlideShow() {
        if (mMediaSet == null || mCurrentPhoto == null) {
            return false;
        }
        if (mCurrentPhoto.getMediaType() != MediaObject.MEDIA_TYPE_IMAGE) {
            return false;
        }
        if (MtpSource.isMtpPath(mOriginalSetPathString)) {
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
        mOrientationManager.unlockOrientation();
        mActionBar.show();
        mActivity.getGLRoot().setLightsOutMode(false);
        refreshHidingMessage();
        if (mBottomControls != null) mBottomControls.refresh();
    }

    private void hideBars() {
        if (!mShowBars) return;
        mShowBars = false;
        mActionBar.hide();
        mActivity.getGLRoot().setLightsOutMode(true);
        mHandler.removeMessages(MSG_HIDE_BARS);
        if (mBottomControls != null) mBottomControls.refresh();
    }

    private void refreshHidingMessage() {
        mHandler.removeMessages(MSG_HIDE_BARS);
        if (!mIsMenuVisible && !mPhotoView.getFilmMode()) {
            mHandler.sendEmptyMessageDelayed(MSG_HIDE_BARS, HIDE_BARS_TIMEOUT);
        }
    }

    private boolean canShowBars() {
        // No bars if we are showing camera preview.
        if (mAppBridge != null && mCurrentIndex == 0
                && !mPhotoView.getFilmMode()) return false;

        // No bars if it's not allowed.
        if (!mActionBarAllowed) return false;

        return true;
    }

    private void wantBars() {
        if (canShowBars()) showBars();
    }

    private void toggleBars() {
        if (mShowBars) {
            hideBars();
        } else {
            if (canShowBars()) showBars();
        }
    }

    private void updateBars() {
        if (!canShowBars()) {
            hideBars();
        }
    }

    @Override
    public void onOrientationCompensationChanged() {
        mActivity.getGLRoot().requestLayoutContentPane();
    }

    @Override
    protected void onBackPressed() {
        if (mShowDetails) {
            hideDetails();
        } else if (mAppBridge == null || !switchWithCaptureAnimation(-1)) {
            // We are leaving this page. Set the result now.
            setResult();
            if (mStartInFilmstrip && !mPhotoView.getFilmMode()) {
                mPhotoView.setFilmMode(true);
            } else if (mTreatBackAsUp) {
                onUpPressed();
            } else {
                super.onBackPressed();
            }
        }
    }

    private void onUpPressed() {
        if ((mStartInFilmstrip || mAppBridge != null)
                && !mPhotoView.getFilmMode()) {
            mPhotoView.setFilmMode(true);
            return;
        }

        if (mActivity.getStateManager().getStateCount() > 1) {
            setResult();
            super.onBackPressed();
            return;
        }

        if (mOriginalSetPathString == null) return;

        if (mAppBridge == null) {
            // We're in view mode so set up the stacks on our own.
            Bundle data = new Bundle(getData());
            data.putString(AlbumPage.KEY_MEDIA_PATH, mOriginalSetPathString);
            data.putString(AlbumPage.KEY_PARENT_MEDIA_PATH,
                    mActivity.getDataManager().getTopSetPath(
                            DataManager.INCLUDE_ALL));
            mActivity.getStateManager().switchState(this, AlbumPage.class, data);
        } else {
            GalleryUtils.startGalleryActivity(mActivity);
        }
    }

    private void setResult() {
        Intent result = null;
        result = new Intent();
        result.putExtra(KEY_RETURN_INDEX_HINT, mCurrentIndex);
        setStateResult(Activity.RESULT_OK, result);
    }

    //////////////////////////////////////////////////////////////////////////
    //  AppBridge.Server interface
    //////////////////////////////////////////////////////////////////////////

    @Override
    public void setCameraRelativeFrame(Rect frame) {
        mPhotoView.setCameraRelativeFrame(frame);
    }

    @Override
    public boolean switchWithCaptureAnimation(int offset) {
        return mPhotoView.switchWithCaptureAnimation(offset);
    }

    @Override
    public void setSwipingEnabled(boolean enabled) {
        mPhotoView.setSwipingEnabled(enabled);
    }

    @Override
    public void notifyScreenNailChanged() {
        mScreenNailItem.setScreenNail(mAppBridge.attachScreenNail());
        mScreenNailSet.notifyChange();
    }

    @Override
    public void addSecureAlbumItem(boolean isVideo, int id) {
        mSecureAlbum.addMediaItem(isVideo, id);
    }

    @Override
    protected boolean onCreateActionBar(Menu menu) {
        mActionBar.createActionBarMenu(R.menu.photo, menu);
        mHaveImageEditor = GalleryUtils.isEditorAvailable(mActivity, "image/*");
        updateMenuOperations();
        mActionBar.setTitle(mMediaSet != null ? mMediaSet.getName() : "");
        return true;
    }

    private MenuExecutor.ProgressListener mConfirmDialogListener =
            new MenuExecutor.ProgressListener() {
        @Override
        public void onProgressUpdate(int index) {}

        @Override
        public void onProgressComplete(int result) {}

        @Override
        public void onConfirmDialogShown() {
            mHandler.removeMessages(MSG_HIDE_BARS);
        }

        @Override
        public void onConfirmDialogDismissed(boolean confirmed) {
            refreshHidingMessage();
        }

        @Override
        public void onProgressStart() {}
    };

    private void switchToGrid() {
        if (mStartedFromAlbumPage) {
            onUpPressed();
        } else {
            if (mOriginalSetPathString == null) return;
            preparePhotoFallbackView();
            Bundle data = new Bundle(getData());
            data.putString(AlbumPage.KEY_MEDIA_PATH, mOriginalSetPathString);
            data.putString(AlbumPage.KEY_PARENT_MEDIA_PATH,
                    mActivity.getDataManager().getTopSetPath(
                            DataManager.INCLUDE_ALL));

            // We only show cluster menu in the first AlbumPage in stack
            // TODO: Enable this when running from the camera app
            boolean inAlbum = mActivity.getStateManager().hasStateClass(AlbumPage.class);
            data.putBoolean(AlbumPage.KEY_SHOW_CLUSTER_MENU, !inAlbum
                    && mAppBridge == null);

            data.putBoolean(PhotoPage.KEY_APP_BRIDGE, mAppBridge != null);

            // Account for live preview being first item
            mActivity.getTransitionStore().put(KEY_RETURN_INDEX_HINT,
                    mAppBridge != null ? mCurrentIndex - 1 : mCurrentIndex);

            mActivity.getStateManager().startState(AlbumPage.class, data);
        }
    }

    @Override
    protected boolean onItemSelected(MenuItem item) {
        if (mModel == null) return true;
        refreshHidingMessage();
        MediaItem current = mModel.getMediaItem(0);

        if (current == null) {
            // item is not ready, ignore
            return true;
        }

        int currentIndex = mModel.getCurrentIndex();
        Path path = current.getPath();

        DataManager manager = mActivity.getDataManager();
        int action = item.getItemId();
        String confirmMsg = null;
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
                Activity activity = mActivity;
                Intent intent = new Intent(CropImage.CROP_ACTION);
                intent.setClass(activity, CropImage.class);
                intent.setData(manager.getContentUri(path));
                activity.startActivityForResult(intent, PicasaSource.isPicasaImage(current)
                        ? REQUEST_CROP_PICASA
                        : REQUEST_CROP);
                return true;
            }
            case R.id.action_trim: {
                Intent intent = new Intent(mActivity, TrimVideo.class);
                intent.setData(manager.getContentUri(path));
                // We need the file path to wrap this into a RandomAccessFile.
                intent.putExtra(KEY_MEDIA_ITEM_PATH, current.getFilePath());
                mActivity.startActivityForResult(intent, REQUEST_TRIM);
                return true;
            }
            case R.id.action_edit: {
                launchPhotoEditor();
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
            case R.id.action_delete:
                confirmMsg = mActivity.getResources().getQuantityString(
                        R.plurals.delete_selection, 1);
            case R.id.action_setas:
            case R.id.action_rotate_ccw:
            case R.id.action_rotate_cw:
            case R.id.action_show_on_map:
                mSelectionManager.deSelectAll();
                mSelectionManager.toggle(path);
                mMenuExecutor.onMenuClicked(item, confirmMsg, mConfirmDialogListener);
                return true;
            case R.id.action_import:
                mSelectionManager.deSelectAll();
                mSelectionManager.toggle(path);
                mMenuExecutor.onMenuClicked(item, confirmMsg,
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

    private void showDetails() {
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

        MediaItem item = mModel.getMediaItem(0);
        if (item == null || item == mScreenNailItem) {
            // item is not ready or it is camera preview, ignore
            return;
        }

        int supported = item.getSupportedOperations();
        boolean playVideo = (mSecureAlbum == null) &&
                ((supported & MediaItem.SUPPORT_PLAY) != 0);
        boolean unlock = ((supported & MediaItem.SUPPORT_UNLOCK) != 0);
        boolean goBack = ((supported & MediaItem.SUPPORT_BACK) != 0);
        boolean launchCamera = ((supported & MediaItem.SUPPORT_CAMERA_SHORTCUT) != 0);

        if (playVideo) {
            // determine if the point is at center (1/6) of the photo view.
            // (The position of the "play" icon is at center (1/6) of the photo)
            int w = mPhotoView.getWidth();
            int h = mPhotoView.getHeight();
            playVideo = (Math.abs(x - w / 2) * 12 <= w)
                && (Math.abs(y - h / 2) * 12 <= h);
        }

        if (playVideo) {
            playVideo(mActivity, item.getPlayUri(), item.getName());
        } else if (goBack) {
            onBackPressed();
        } else if (unlock) {
            mActivity.getStateManager().finishState(this);
        } else if (launchCamera) {
            GalleryUtils.startCameraActivity(mActivity);
        } else {
            toggleBars();
        }
    }

    @Override
    public void onActionBarAllowed(boolean allowed) {
        mActionBarAllowed = allowed;
        mHandler.sendEmptyMessage(MSG_UPDATE_ACTION_BAR);
    }

    @Override
    public void onActionBarWanted() {
        mHandler.sendEmptyMessage(MSG_WANT_BARS);
    }

    @Override
    public void onFullScreenChanged(boolean full) {
        Message m = mHandler.obtainMessage(
                MSG_ON_FULL_SCREEN_CHANGED, full ? 1 : 0, 0);
        m.sendToTarget();
    }

    // How we do delete/undo:
    //
    // When the user choose to delete a media item, we just tell the
    // FilterDeleteSet to hide that item. If the user choose to undo it, we
    // again tell FilterDeleteSet not to hide it. If the user choose to commit
    // the deletion, we then actually delete the media item.
    @Override
    public void onDeleteImage(Path path, int offset) {
        onCommitDeleteImage();  // commit the previous deletion
        mDeletePath = path;
        mDeleteIsFocus = (offset == 0);
        mMediaSet.addDeletion(path, mCurrentIndex + offset);
    }

    @Override
    public void onUndoDeleteImage() {
        if (mDeletePath == null) return;
        // If the deletion was done on the focused item, we want the model to
        // focus on it when it is undeleted.
        if (mDeleteIsFocus) mModel.setFocusHintPath(mDeletePath);
        mMediaSet.removeDeletion(mDeletePath);
        mDeletePath = null;
    }

    @Override
    public void onCommitDeleteImage() {
        if (mDeletePath == null) return;
        mSelectionManager.deSelectAll();
        mSelectionManager.toggle(mDeletePath);
        mMenuExecutor.onMenuClicked(R.id.action_delete, null, true, false);
        mDeletePath = null;
    }

    public void playVideo(Activity activity, Uri uri, String title) {
        mRecenterCameraOnResume = false;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "video/*")
                    .putExtra(Intent.EXTRA_TITLE, title)
                    .putExtra(MovieActivity.KEY_TREAT_UP_AS_BACK, true);
            activity.startActivityForResult(intent, REQUEST_PLAY_VIDEO);
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
        mHasActivityResult = true;
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
                    String message = context.getString(R.string.crop_saved,
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
    protected void clearStateResult() {
        mHasActivityResult = false;
    }

    private class PreparePhotoFallback implements OnGLIdleListener {
        private PhotoFallbackEffect mPhotoFallback = new PhotoFallbackEffect();
        private boolean mResultReady = false;

        public synchronized PhotoFallbackEffect get() {
            while (!mResultReady) {
                Utils.waitWithoutInterrupt(this);
            }
            return mPhotoFallback;
        }

        @Override
        public boolean onGLIdle(GLCanvas canvas, boolean renderRequested) {
            mPhotoFallback = mPhotoView.buildFallbackEffect(mRootPane, canvas);
            synchronized (this) {
                mResultReady = true;
                notifyAll();
            }
            return false;
        }
    }

    private void preparePhotoFallbackView() {
        GLRoot root = mActivity.getGLRoot();
        PreparePhotoFallback task = new PreparePhotoFallback();
        root.unlockRenderThread();
        PhotoFallbackEffect anim;
        try {
            root.addOnGLIdleListener(task);
            anim = task.get();
        } finally {
            root.lockRenderThread();
        }
        mActivity.getTransitionStore().put(
                AlbumPage.KEY_RESUME_ANIMATION, anim);
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsActive = false;

        mActivity.getGLRoot().unfreeze();
        mHandler.removeMessages(MSG_UNFREEZE_GLROOT);

        DetailsHelper.pause();
        // Hide the detail dialog on exit
        if (mShowDetails) hideDetails();
        if (mModel != null) {
            if (isFinishing()) preparePhotoFallbackView();
            mModel.pause();
        }
        mPhotoView.pause();
        mHandler.removeMessages(MSG_HIDE_BARS);
        mHandler.removeMessages(MSG_REFRESH_BOTTOM_CONTROLS);
        if (mBottomControls != null) {
            mBottomControls.refresh();
        }
        mActionBar.removeOnMenuVisibilityListener(mMenuVisibilityListener);
        if (mShowSpinner) {
            mActionBar.disableAlbumModeMenu(true);
        }
        onCommitDeleteImage();
        mMenuExecutor.pause();
        if (mMediaSet != null) mMediaSet.clearDeletion();
    }

    @Override
    public void onCurrentImageUpdated() {
        mActivity.getGLRoot().unfreeze();
    }

    @Override
    public void onFilmModeChanged(boolean enabled) {
        mHandler.sendEmptyMessage(MSG_REFRESH_BOTTOM_CONTROLS);
        if (enabled) {
            mHandler.removeMessages(MSG_HIDE_BARS);
        } else {
            refreshHidingMessage();
        }
    }

    private void transitionFromAlbumPageIfNeeded() {
        TransitionStore transitions = mActivity.getTransitionStore();

        int albumPageTransition = transitions.get(
                KEY_ALBUMPAGE_TRANSITION, MSG_ALBUMPAGE_NONE);

        if (albumPageTransition == MSG_ALBUMPAGE_NONE && mAppBridge != null
                && mRecenterCameraOnResume) {
            // Generally, resuming the PhotoPage when in Camera should
            // reset to the capture mode to allow quick photo taking
            mCurrentIndex = 0;
            mPhotoView.resetToFirstPicture();
        } else {
            int resumeIndex = transitions.get(KEY_INDEX_HINT, -1);
            if (resumeIndex >= 0) {
                if (mInCameraRoll) {
                    // Account for preview/placeholder being the first item
                    resumeIndex++;
                }
                if (resumeIndex < mMediaSet.getMediaItemCount()) {
                    mCurrentIndex = resumeIndex;
                    mModel.moveTo(mCurrentIndex);
                }
            }
        }

        if (albumPageTransition == MSG_ALBUMPAGE_RESUMED) {
            mPhotoView.setFilmMode(mStartInFilmstrip || mAppBridge != null);
        } else if (albumPageTransition == MSG_ALBUMPAGE_PICKED) {
            mPhotoView.setFilmMode(false);
        }

        mFadeOutTexture = transitions.get(PreparePageFadeoutTexture.KEY_FADE_TEXTURE);
        if (mFadeOutTexture != null) {
            mBackgroundFade.start();
            BitmapScreenNail.disableDrawPlaceholder();
            mOpenAnimationRect =
                    albumPageTransition == MSG_ALBUMPAGE_NONE ?
                    (Rect) mData.getParcelable(KEY_OPEN_ANIMATION_RECT) :
                    (Rect) transitions.get(KEY_OPEN_ANIMATION_RECT);
            mPhotoView.setOpenAnimationRect(mOpenAnimationRect);
            mBackgroundFade.start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mModel == null) {
            mActivity.getStateManager().finishState(this);
            return;
        }
        transitionFromAlbumPageIfNeeded();

        mActivity.getGLRoot().freeze();
        mIsActive = true;
        setContentPane(mRootPane);

        mModel.resume();
        mPhotoView.resume();
        mActionBar.setDisplayOptions(
                ((mSecureAlbum == null) && (mSetPathString != null)), false);
        mActionBar.addOnMenuVisibilityListener(mMenuVisibilityListener);
        if (mBottomControls != null) {
            mBottomControls.refresh();
        }
        if (mShowSpinner) {
            mActionBar.enableAlbumModeMenu(
                    GalleryActionBar.ALBUM_FILMSTRIP_MODE_SELECTED, this);
        }
        if (!mShowBars) {
            mActionBar.hide();
            mActivity.getGLRoot().setLightsOutMode(true);
        }
        boolean haveImageEditor = GalleryUtils.isEditorAvailable(mActivity, "image/*");
        if (haveImageEditor != mHaveImageEditor) {
            mHaveImageEditor = haveImageEditor;
            updateMenuOperations();
        }

        mHasActivityResult = false;
        mRecenterCameraOnResume = true;
        mHandler.sendEmptyMessageDelayed(MSG_UNFREEZE_GLROOT, UNFREEZE_GLROOT_TIMEOUT);
    }

    @Override
    protected void onDestroy() {
        if (mAppBridge != null) {
            mAppBridge.setServer(null);
            mScreenNailItem.setScreenNail(null);
            mAppBridge.detachScreenNail();
            mAppBridge = null;
            mScreenNailSet = null;
            mScreenNailItem = null;
        }
        mOrientationManager.removeListener(this);
        mActivity.getGLRoot().setOrientationSource(null);
        if (mBottomControls != null) mBottomControls.cleanup();

        // Remove all pending messages.
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private class MyDetailsSource implements DetailsSource {

        @Override
        public MediaDetails getDetails() {
            return mModel.getMediaItem(0).getDetails();
        }

        @Override
        public int size() {
            return mMediaSet != null ? mMediaSet.getMediaItemCount() : 1;
        }

        @Override
        public int setIndex() {
            return mModel.getCurrentIndex();
        }
    }

    @Override
    public void onAlbumModeSelected(int mode) {
        if (mode == GalleryActionBar.ALBUM_GRID_MODE_SELECTED) {
            switchToGrid();
        }
    }
}
