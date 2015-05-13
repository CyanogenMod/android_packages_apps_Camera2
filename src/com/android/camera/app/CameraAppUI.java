/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.camera.app;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.util.CameraPerformanceTracker;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import com.android.camera.AccessibilityUtil;
import com.android.camera.AnimationManager;
import com.android.camera.ButtonManager;
import com.android.camera.CaptureLayoutHelper;
import com.android.camera.ShutterButton;
import com.android.camera.TextureViewHelper;
import com.android.camera.debug.Log;
import com.android.camera.filmstrip.FilmstripContentPanel;
import com.android.camera.hardware.HardwareSpec;
import com.android.camera.module.ModuleController;
import com.android.camera.settings.Keys;
import com.android.camera.settings.SettingsManager;
import com.android.camera.ui.AbstractTutorialOverlay;
import com.android.camera.ui.BottomBar;
import com.android.camera.ui.CaptureAnimationOverlay;
import com.android.camera.ui.GridLines;
import com.android.camera.ui.MainActivityLayout;
import com.android.camera.ui.ModeListView;
import com.android.camera.ui.ModeTransitionView;
import com.android.camera.ui.PreviewOverlay;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera.ui.StickyBottomCaptureLayout;
import com.android.camera.ui.TouchCoordinate;
import com.android.camera.ui.focus.FocusRing;
import com.android.camera.util.AndroidServices;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.Gusterpolator;
import com.android.camera.util.PhotoSphereHelper;
import com.android.camera.widget.Cling;
import com.android.camera.widget.FilmstripLayout;
import com.android.camera.widget.IndicatorIconController;
import com.android.camera.widget.ModeOptionsOverlay;
import com.android.camera.widget.RoundedThumbnailView;
import com.android.camera2.R;

/**
 * CameraAppUI centralizes control of views shared across modules. Whereas module
 * specific views will be handled in each Module UI. For example, we can now
 * bring the flash animation and capture animation up from each module to app
 * level, as these animations are largely the same for all modules.
 *
 * This class also serves to disambiguate touch events. It recognizes all the
 * swipe gestures that happen on the preview by attaching a touch listener to
 * a full-screen view on top of preview TextureView. Since CameraAppUI has knowledge
 * of how swipe from each direction should be handled, it can then redirect these
 * events to appropriate recipient views.
 */
public class CameraAppUI implements ModeListView.ModeSwitchListener,
                                    TextureView.SurfaceTextureListener,
                                    ModeListView.ModeListOpenListener,
                                    SettingsManager.OnSettingChangedListener,
                                    ShutterButton.OnShutterButtonListener {

    /**
     * The bottom controls on the filmstrip.
     */
    public static interface BottomPanel {
        /** Values for the view state of the button. */
        public final int VIEWER_NONE = 0;
        public final int VIEWER_PHOTO_SPHERE = 1;
        public final int VIEWER_REFOCUS = 2;
        public final int VIEWER_OTHER = 3;

        /**
         * Sets a new or replaces an existing listener for bottom control events.
         */
        void setListener(Listener listener);

        /**
         * Sets cling for external viewer button.
         */
        void setClingForViewer(int viewerType, Cling cling);

        /**
         * Clears cling for external viewer button.
         */
        void clearClingForViewer(int viewerType);

        /**
         * Returns a cling for the specified viewer type.
         */
        Cling getClingForViewer(int viewerType);

        /**
         * Set if the bottom controls are visible.
         * @param visible {@code true} if visible.
         */
        void setVisible(boolean visible);

        /**
         * @param visible Whether the button is visible.
         */
        void setEditButtonVisibility(boolean visible);

        /**
         * @param enabled Whether the button is enabled.
         */
        void setEditEnabled(boolean enabled);

        /**
         * Sets the visibility of the view-photosphere button.
         *
         * @param state one of {@link #VIEWER_NONE}, {@link #VIEWER_PHOTO_SPHERE},
         *            {@link #VIEWER_REFOCUS}.
         */
        void setViewerButtonVisibility(int state);

        /**
         * @param enabled Whether the button is enabled.
         */
        void setViewEnabled(boolean enabled);

        /**
         * @param enabled Whether the button is enabled.
         */
        void setTinyPlanetEnabled(boolean enabled);

        /**
         * @param visible Whether the button is visible.
         */
        void setDeleteButtonVisibility(boolean visible);

        /**
         * @param enabled Whether the button is enabled.
         */
        void setDeleteEnabled(boolean enabled);

        /**
         * @param visible Whether the button is visible.
         */
        void setShareButtonVisibility(boolean visible);

        /**
         * @param enabled Whether the button is enabled.
         */
        void setShareEnabled(boolean enabled);

        /**
         * Sets the texts for progress UI.
         *
         * @param text The text to show.
         */
        void setProgressText(CharSequence text);

        /**
         * Sets the progress.
         *
         * @param progress The progress value. Should be between 0 and 100.
         */
        void setProgress(int progress);

        /**
         * Replaces the progress UI with an error message.
         */
        void showProgressError(CharSequence message);

        /**
         * Hide the progress error message.
         */
        void hideProgressError();

        /**
         * Shows the progress.
         */
        void showProgress();

        /**
         * Hides the progress.
         */
        void hideProgress();

        /**
         * Shows the controls.
         */
        void showControls();

        /**
         * Hides the controls.
         */
        void hideControls();

        /**
         * Classes implementing this interface can listen for events on the bottom
         * controls.
         */
        public static interface Listener {
            /**
             * Called when the user pressed the "view" button to e.g. view a photo
             * sphere or RGBZ image.
             */
            public void onExternalViewer();

            /**
             * Called when the "edit" button is pressed.
             */
            public void onEdit();

            /**
             * Called when the "tiny planet" button is pressed.
             */
            public void onTinyPlanet();

            /**
             * Called when the "delete" button is pressed.
             */
            public void onDelete();

            /**
             * Called when the "share" button is pressed.
             */
            public void onShare();

            /**
             * Called when the progress error message is clicked.
             */
            public void onProgressErrorClicked();
        }
    }

    /**
     * BottomBarUISpec provides a structure for modules
     * to specify their ideal bottom bar mode options layout.
     *
     * Once constructed by a module, this class should be
     * treated as read only.
     *
     * The application then edits this spec according to
     * hardware limitations and displays the final bottom
     * bar ui.
     */
    public static class BottomBarUISpec {
        /** Mode options UI */

        /**
         * Set true if the camera option should be enabled.
         * If not set or false, and multiple cameras are supported,
         * the camera option will be disabled.
         *
         * If multiple cameras are not supported, this preference
         * is ignored and the camera option will not be visible.
         */
        public boolean enableCamera;

        /**
         * Set true if the camera option should not be visible, regardless
         * of hardware limitations.
         */
        public boolean hideCamera;

        /**
         * Set true if the photo flash option should be enabled.
         * If not set or false, the photo flash option will be
         * disabled.
         *
         * If the hardware does not support multiple flash values,
         * this preference is ignored and the flash option will
         * be disabled.  It will not be made invisible in order to
         * preserve a consistent experience across devices and between
         * front and back cameras.
         */
        public boolean enableFlash;

        /**
         * Set true if the video flash option should be enabled.
         * Same disable rules apply as the photo flash option.
         */
        public boolean enableTorchFlash;

        /**
         * Set true if the HDR+ flash option should be enabled.
         * Same disable rules apply as the photo flash option.
         */
        public boolean enableHdrPlusFlash;

        /**
         * Set true if flash should not be visible, regardless of
         * hardware limitations.
         */
        public boolean hideFlash;

        /**
         * Set true if the hdr/hdr+ option should be enabled.
         * If not set or false, the hdr/hdr+ option will be disabled.
         *
         * Hdr or hdr+ will be chosen based on hardware limitations,
         * with hdr+ prefered.
         *
         * If hardware supports neither hdr nor hdr+, then the hdr/hdr+
         * will not be visible.
         */
        public boolean enableHdr;

        /**
         * Set true if hdr/hdr+ should not be visible, regardless of
         * hardware limitations.
         */
        public boolean hideHdr;

        /**
         * Set true if grid lines should be visible.  Not setting this
         * causes grid lines to be disabled.  This option is agnostic to
         * the hardware.
         */
        public boolean enableGridLines;

        /**
         * Set true if grid lines should not be visible.
         */
        public boolean hideGridLines;

        /**
         * Set true if the panorama orientation option should be visible.
         *
         * This option is not constrained by hardware limitations.
         */
        public boolean enablePanoOrientation;

        /**
         * Set true if manual exposure compensation should be visible.
         *
         * This option is not constrained by hardware limitations.
         * For example, this is false in HDR+ mode.
         */
        public boolean enableExposureCompensation;

        /**
         * Set true if the device and module support exposure compensation.
         * Used only to show exposure button in disabled (greyed out) state.
         */
        public boolean isExposureCompensationSupported;

        /** Intent UI */

        /**
         * Set true if the intent ui cancel option should be visible.
         */
        public boolean showCancel;
        /**
         * Set true if the intent ui done option should be visible.
         */
        public boolean showDone;
        /**
         * Set true if the intent ui retake option should be visible.
         */
        public boolean showRetake;
        /**
         * Set true if the intent ui review option should be visible.
         */
        public boolean showReview;

        /** Mode options callbacks */

        /**
         * A {@link com.android.camera.ButtonManager.ButtonCallback}
         * that will be executed when the camera option is pressed. This
         * callback can be null.
         */
        public ButtonManager.ButtonCallback cameraCallback;

        /**
         * A {@link com.android.camera.ButtonManager.ButtonCallback}
         * that will be executed when the flash option is pressed. This
         * callback can be null.
         */
        public ButtonManager.ButtonCallback flashCallback;

        /**
         * A {@link com.android.camera.ButtonManager.ButtonCallback}
         * that will be executed when the hdr/hdr+ option is pressed. This
         * callback can be null.
         */
        public ButtonManager.ButtonCallback hdrCallback;

        /**
         * A {@link com.android.camera.ButtonManager.ButtonCallback}
         * that will be executed when the grid lines option is pressed. This
         * callback can be null.
         */
        public ButtonManager.ButtonCallback gridLinesCallback;

        /**
         * A {@link com.android.camera.ButtonManager.ButtonCallback}
         * that will execute when the panorama orientation option is pressed.
         * This callback can be null.
         */
        public ButtonManager.ButtonCallback panoOrientationCallback;

        /** Intent UI callbacks */

        /**
         * A {@link android.view.View.OnClickListener} that will execute
         * when the cancel option is pressed. This callback can be null.
         */
        public View.OnClickListener cancelCallback;

        /**
         * A {@link android.view.View.OnClickListener} that will execute
         * when the done option is pressed. This callback can be null.
         */
        public View.OnClickListener doneCallback;

        /**
         * A {@link android.view.View.OnClickListener} that will execute
         * when the retake option is pressed. This callback can be null.
         */
        public View.OnClickListener retakeCallback;

        /**
         * A {@link android.view.View.OnClickListener} that will execute
         * when the review option is pressed. This callback can be null.
         */
        public View.OnClickListener reviewCallback;

        /**
         * A ExposureCompensationSetCallback that will execute
         * when an expsosure button is pressed. This callback can be null.
         */
        public interface ExposureCompensationSetCallback {
            public void setExposure(int value);
        }
        public ExposureCompensationSetCallback exposureCompensationSetCallback;

        /**
         * Exposure compensation parameters.
         */
        public int minExposureCompensation;
        public int maxExposureCompensation;
        public float exposureCompensationStep;

        /**
         * Whether self-timer is enabled.
         */
        public boolean enableSelfTimer = false;

        /**
         * Whether the option for self-timer should show. If true and
         * {@link #enableSelfTimer} is false, then the option should be shown
         * disabled.
         */
        public boolean showSelfTimer = false;
    }


    private final static Log.Tag TAG = new Log.Tag("CameraAppUI");

    private final AppController mController;
    private final boolean mIsCaptureIntent;
    private final AnimationManager mAnimationManager;

    // Swipe states:
    private final static int IDLE = 0;
    private final static int SWIPE_UP = 1;
    private final static int SWIPE_DOWN = 2;
    private final static int SWIPE_LEFT = 3;
    private final static int SWIPE_RIGHT = 4;
    private boolean mSwipeEnabled = true;

    // Shared Surface Texture properities.
    private SurfaceTexture mSurface;
    private int mSurfaceWidth;
    private int mSurfaceHeight;

    // Touch related measures:
    private final int mSlop;
    private final static int SWIPE_TIME_OUT_MS = 500;

    // Mode cover states:
    private final static int COVER_HIDDEN = 0;
    private final static int COVER_SHOWN = 1;
    private final static int COVER_WILL_HIDE_AT_NEXT_FRAME = 2;
    private final static int COVER_WILL_HIDE_AFTER_NEXT_TEXTURE_UPDATE = 3;
    private final static int COVER_WILL_HIDE_AT_NEXT_TEXTURE_UPDATE = 4;

    /**
     * Preview down-sample rate when taking a screenshot.
     */
    private final static int DOWN_SAMPLE_RATE_FOR_SCREENSHOT = 2;

    // App level views:
    private final FrameLayout mCameraRootView;
    private final ModeTransitionView mModeTransitionView;
    private final MainActivityLayout mAppRootView;
    private final ModeListView mModeListView;
    private final FilmstripLayout mFilmstripLayout;
    private TextureView mTextureView;
    private FrameLayout mModuleUI;
    private ShutterButton mShutterButton;
    private ImageButton mCountdownCancelButton;
    private BottomBar mBottomBar;
    private ModeOptionsOverlay mModeOptionsOverlay;
    private IndicatorIconController mIndicatorIconController;
    private FocusRing mFocusRing;
    private FrameLayout mTutorialsPlaceHolderWrapper;
    private StickyBottomCaptureLayout mStickyBottomCaptureLayout;
    private TextureViewHelper mTextureViewHelper;
    private final GestureDetector mGestureDetector;
    private DisplayManager.DisplayListener mDisplayListener;
    private int mLastRotation;
    private int mSwipeState = IDLE;
    private PreviewOverlay mPreviewOverlay;
    private GridLines mGridLines;
    private CaptureAnimationOverlay mCaptureOverlay;
    private PreviewStatusListener mPreviewStatusListener;
    private int mModeCoverState = COVER_HIDDEN;
    private final FilmstripBottomPanel mFilmstripBottomControls;
    private final FilmstripContentPanel mFilmstripPanel;
    private Runnable mHideCoverRunnable;
    private final View.OnLayoutChangeListener mPreviewLayoutChangeListener
            = new View.OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                int oldTop, int oldRight, int oldBottom) {
            if (mPreviewStatusListener != null) {
                mPreviewStatusListener.onPreviewLayoutChanged(v, left, top, right, bottom, oldLeft,
                        oldTop, oldRight, oldBottom);
            }
        }
    };
    private View mModeOptionsToggle;
    private final RoundedThumbnailView mRoundedThumbnailView;
    private final CaptureLayoutHelper mCaptureLayoutHelper;
    private final View mAccessibilityAffordances;
    private AccessibilityUtil mAccessibilityUtil;

    private boolean mDisableAllUserInteractions;
    /** Whether to prevent capture indicator from being triggered. */
    private boolean mSuppressCaptureIndicator;

    /** Supported HDR mode (none, hdr, hdr+). */
    private String mHdrSupportMode;

    /** Used to track the last scope used to update the bottom bar UI. */
    private String mCurrentCameraScope;
    private String mCurrentModuleScope;

    /**
     * Provides current preview frame and the controls/overlay from the module that
     * are shown on top of the preview.
     */
    public interface CameraModuleScreenShotProvider {
        /**
         * Returns the current preview frame down-sampled using the given down-sample
         * factor.
         *
         * @param downSampleFactor the down sample factor for down sampling the
         *                         preview frame. (e.g. a down sample factor of
         *                         2 means to scale down the preview frame to 1/2
         *                         the width and height.)
         * @return down-sampled preview frame
         */
        public Bitmap getPreviewFrame(int downSampleFactor);

        /**
         * @return the controls and overlays that are currently showing on top of
         *         the preview drawn into a bitmap with no scaling applied.
         */
        public Bitmap getPreviewOverlayAndControls();

        /**
         * Returns a bitmap containing the current screenshot.
         *
         * @param previewDownSampleFactor the downsample factor applied on the
         *                                preview frame when taking the screenshot
         */
        public Bitmap getScreenShot(int previewDownSampleFactor);
    }

    /**
     * This listener gets called when the size of the window (excluding the system
     * decor such as status bar and nav bar) has changed.
     */
    public interface NonDecorWindowSizeChangedListener {
        public void onNonDecorWindowSizeChanged(int width, int height, int rotation);
    }

    private final CameraModuleScreenShotProvider mCameraModuleScreenShotProvider =
            new CameraModuleScreenShotProvider() {
                @Override
                public Bitmap getPreviewFrame(int downSampleFactor) {
                    if (mCameraRootView == null || mTextureView == null) {
                        return null;
                    }
                    // Gets the bitmap from the preview TextureView.
                    Bitmap preview = mTextureViewHelper.getPreviewBitmap(downSampleFactor);
                    return preview;
                }

                @Override
                public Bitmap getPreviewOverlayAndControls() {
                    Bitmap overlays = Bitmap.createBitmap(mCameraRootView.getWidth(),
                            mCameraRootView.getHeight(), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(overlays);
                    mCameraRootView.draw(canvas);
                    return overlays;
                }

                @Override
                public Bitmap getScreenShot(int previewDownSampleFactor) {
                    Bitmap screenshot = Bitmap.createBitmap(mCameraRootView.getWidth(),
                            mCameraRootView.getHeight(), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(screenshot);
                    canvas.drawARGB(255, 0, 0, 0);
                    Bitmap preview = mTextureViewHelper.getPreviewBitmap(previewDownSampleFactor);
                    if (preview != null) {
                        canvas.drawBitmap(preview, null, mTextureViewHelper.getPreviewArea(), null);
                    }
                    Bitmap overlay = getPreviewOverlayAndControls();
                    if (overlay != null) {
                        canvas.drawBitmap(overlay, 0f, 0f, null);
                    }
                    return screenshot;
                }
            };

    private long mCoverHiddenTime = -1; // System time when preview cover was hidden.

    public long getCoverHiddenTime() {
        return mCoverHiddenTime;
    }

    /**
     * This resets the preview to have no applied transform matrix.
     */
    public void clearPreviewTransform() {
        mTextureViewHelper.clearTransform();
    }

    public void updatePreviewAspectRatio(float aspectRatio) {
        mTextureViewHelper.updateAspectRatio(aspectRatio);
    }

    /**
     * WAR: Reset the SurfaceTexture's default buffer size to the current view dimensions of
     * its TextureView.  This is necessary to get the expected behavior for the TextureView's
     * HardwareLayer transform matrix (set by TextureView#setTransform) after configuring the
     * SurfaceTexture as an output for the Camera2 API (which involves changing the default buffer
     * size).
     *
     * b/17286155 - Tracking a fix for this in HardwareLayer.
     */
    public void setDefaultBufferSizeToViewDimens() {
        if (mSurface == null || mTextureView == null) {
            Log.w(TAG, "Could not set SurfaceTexture default buffer dimensions, not yet setup");
            return;
        }
        mSurface.setDefaultBufferSize(mTextureView.getWidth(), mTextureView.getHeight());
    }

    /**
     * Updates the preview matrix without altering it.
     *
     * @param matrix
     * @param aspectRatio the desired aspect ratio for the preview.
     */
    public void updatePreviewTransformFullscreen(Matrix matrix, float aspectRatio) {
        mTextureViewHelper.updateTransformFullScreen(matrix, aspectRatio);
    }

    /**
     * @return the rect that will display the preview.
     */
    public RectF getFullscreenRect() {
        return mTextureViewHelper.getFullscreenRect();
    }

    /**
     * This is to support modules that calculate their own transform matrix because
     * they need to use a transform matrix to rotate the preview.
     *
     * @param matrix transform matrix to be set on the TextureView
     */
    public void updatePreviewTransform(Matrix matrix) {
        mTextureViewHelper.updateTransform(matrix);
    }

    public interface AnimationFinishedListener {
        public void onAnimationFinished(boolean success);
    }

    private class MyTouchListener implements View.OnTouchListener {
        private boolean mScaleStarted = false;
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                mScaleStarted = false;
            } else if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                mScaleStarted = true;
            }
            return (!mScaleStarted) && mGestureDetector.onTouchEvent(event);
        }
    }

    /**
     * This gesture listener finds out the direction of the scroll gestures and
     * sends them to CameraAppUI to do further handling.
     */
    private class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        private MotionEvent mDown;

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent ev, float distanceX, float distanceY) {
            if (ev.getEventTime() - ev.getDownTime() > SWIPE_TIME_OUT_MS
                    || mSwipeState != IDLE
                    || mIsCaptureIntent
                    || !mSwipeEnabled) {
                return false;
            }

            int deltaX = (int) (ev.getX() - mDown.getX());
            int deltaY = (int) (ev.getY() - mDown.getY());
            if (ev.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (Math.abs(deltaX) > mSlop || Math.abs(deltaY) > mSlop) {
                    // Calculate the direction of the swipe.
                    if (deltaX >= Math.abs(deltaY)) {
                        // Swipe right.
                        setSwipeState(SWIPE_RIGHT);
                    } else if (deltaX <= -Math.abs(deltaY)) {
                        // Swipe left.
                        setSwipeState(SWIPE_LEFT);
                    }
                }
            }
            return true;
        }

        private void setSwipeState(int swipeState) {
            mSwipeState = swipeState;
            // Notify new swipe detected.
            onSwipeDetected(swipeState);
        }

        @Override
        public boolean onDown(MotionEvent ev) {
            mDown = MotionEvent.obtain(ev);
            mSwipeState = IDLE;
            return false;
        }
    }

    public CameraAppUI(AppController controller, MainActivityLayout appRootView,
            boolean isCaptureIntent) {
        mSlop = ViewConfiguration.get(controller.getAndroidContext()).getScaledTouchSlop();
        mController = controller;
        mIsCaptureIntent = isCaptureIntent;

        mAppRootView = appRootView;
        mFilmstripLayout = (FilmstripLayout) appRootView.findViewById(R.id.filmstrip_layout);
        mCameraRootView = (FrameLayout) appRootView.findViewById(R.id.camera_app_root);
        mModeTransitionView = (ModeTransitionView)
                mAppRootView.findViewById(R.id.mode_transition_view);
        mFilmstripBottomControls = new FilmstripBottomPanel(controller,
                (ViewGroup) mAppRootView.findViewById(R.id.filmstrip_bottom_panel));
        mFilmstripPanel = (FilmstripContentPanel) mAppRootView.findViewById(R.id.filmstrip_layout);
        mGestureDetector = new GestureDetector(controller.getAndroidContext(),
                new MyGestureListener());
        Resources res = controller.getAndroidContext().getResources();
        mCaptureLayoutHelper = new CaptureLayoutHelper(
                res.getDimensionPixelSize(R.dimen.bottom_bar_height_min),
                res.getDimensionPixelSize(R.dimen.bottom_bar_height_max),
                res.getDimensionPixelSize(R.dimen.bottom_bar_height_optimal));
        mModeListView = (ModeListView) appRootView.findViewById(R.id.mode_list_layout);
        if (mModeListView != null) {
            mModeListView.setModeSwitchListener(this);
            mModeListView.setModeListOpenListener(this);
            mModeListView.setCameraModuleScreenShotProvider(mCameraModuleScreenShotProvider);
            mModeListView.setCaptureLayoutHelper(mCaptureLayoutHelper);
            boolean shouldShowSettingsCling = mController.getSettingsManager().getBoolean(
                    SettingsManager.SCOPE_GLOBAL,
                    Keys.KEY_SHOULD_SHOW_SETTINGS_BUTTON_CLING);
            mModeListView.setShouldShowSettingsCling(shouldShowSettingsCling);
        } else {
            Log.e(TAG, "Cannot find mode list in the view hierarchy");
        }
        mAnimationManager = new AnimationManager();
        mRoundedThumbnailView = (RoundedThumbnailView) appRootView.findViewById(R.id.rounded_thumbnail_view);
        mRoundedThumbnailView.setCallback(new RoundedThumbnailView.Callback() {
            @Override
            public void onHitStateFinished() {
                mFilmstripLayout.showFilmstrip();
            }
        });

        mAppRootView.setNonDecorWindowSizeChangedListener(mCaptureLayoutHelper);
        initDisplayListener();
        mAccessibilityAffordances = mAppRootView.findViewById(R.id.accessibility_affordances);
        View modeListToggle = mAppRootView.findViewById(R.id.accessibility_mode_toggle_button);
        modeListToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openModeList();
            }
        });
        View filmstripToggle = mAppRootView.findViewById(
                R.id.accessibility_filmstrip_toggle_button);
        filmstripToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showFilmstrip();
            }
        });

        mSuppressCaptureIndicator = false;
    }


    /**
     * Freeze what is currently shown on screen until the next preview frame comes
     * in.
     */
    public void freezeScreenUntilPreviewReady() {
        Log.v(TAG, "freezeScreenUntilPreviewReady");
        mModeTransitionView.setupModeCover(mCameraModuleScreenShotProvider
                .getScreenShot(DOWN_SAMPLE_RATE_FOR_SCREENSHOT));
        mHideCoverRunnable = new Runnable() {
            @Override
            public void run() {
                mModeTransitionView.hideImageCover();
            }
        };
        mModeCoverState = COVER_SHOWN;
    }

    /**
     * Creates a cling for the specific viewer and links the cling to the corresponding
     * button for layout position.
     *
     * @param viewerType defines which viewer the cling is for.
     */
    public void setupClingForViewer(int viewerType) {
        if (viewerType == BottomPanel.VIEWER_REFOCUS) {
            FrameLayout filmstripContent = (FrameLayout) mAppRootView
                    .findViewById(R.id.camera_filmstrip_content_layout);
            if (filmstripContent != null) {
                // Creates refocus cling.
                LayoutInflater inflater = AndroidServices.instance().provideLayoutInflater();
                Cling refocusCling = (Cling) inflater.inflate(R.layout.cling_widget, null, false);
                // Sets instruction text in the cling.
                refocusCling.setText(mController.getAndroidContext().getResources()
                        .getString(R.string.cling_text_for_refocus_editor_button));

                // Adds cling into view hierarchy.
                int clingWidth = mController.getAndroidContext()
                        .getResources().getDimensionPixelSize(R.dimen.default_cling_width);
                filmstripContent.addView(refocusCling, clingWidth,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                mFilmstripBottomControls.setClingForViewer(viewerType, refocusCling);
            }
        }
    }

    /**
     * Clears the listeners for the cling and remove it from the view hierarchy.
     *
     * @param viewerType defines which viewer the cling is for.
     */
    public void clearClingForViewer(int viewerType) {
        Cling clingToBeRemoved = mFilmstripBottomControls.getClingForViewer(viewerType);
        if (clingToBeRemoved == null) {
            // No cling is created for the specific viewer type.
            return;
        }
        mFilmstripBottomControls.clearClingForViewer(viewerType);
        clingToBeRemoved.setVisibility(View.GONE);
        mAppRootView.removeView(clingToBeRemoved);
    }

    /**
     * Enable or disable swipe gestures. We want to disable them e.g. while we
     * record a video.
     */
    public void setSwipeEnabled(boolean enabled) {
        mSwipeEnabled = enabled;
        // TODO: This can be removed once we come up with a new design for handling swipe
        // on shutter button and mode options. (More details: b/13751653)
        mAppRootView.setSwipeEnabled(enabled);
    }

    public void onDestroy() {
        AndroidServices.instance().provideDisplayManager()
                .unregisterDisplayListener(mDisplayListener);
    }

    /**
     * Initializes the display listener to listen to display changes such as
     * 180-degree rotation change, which will not have an onConfigurationChanged
     * callback.
     */
    private void initDisplayListener() {
        if (ApiHelper.HAS_DISPLAY_LISTENER) {
            mLastRotation = CameraUtil.getDisplayRotation();

            mDisplayListener = new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int arg0) {
                    // Do nothing.
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    int rotation = CameraUtil.getDisplayRotation(
                    );
                    if ((rotation - mLastRotation + 360) % 360 == 180
                            && mPreviewStatusListener != null) {
                        mPreviewStatusListener.onPreviewFlipped();
                        mStickyBottomCaptureLayout.requestLayout();
                        mModeListView.requestLayout();
                        mTextureView.requestLayout();
                    }
                    mLastRotation = rotation;
                }

                @Override
                public void onDisplayRemoved(int arg0) {
                    // Do nothing.
                }
            };

            AndroidServices.instance().provideDisplayManager()
                  .registerDisplayListener(mDisplayListener, null);
        }
    }

    /**
     * Redirects touch events to appropriate recipient views based on swipe direction.
     * More specifically, swipe up and swipe down will be handled by the view that handles
     * mode transition; swipe left will be send to filmstrip; swipe right will be redirected
     * to mode list in order to bring up mode list.
     */
    private void onSwipeDetected(int swipeState) {
        if (swipeState == SWIPE_UP || swipeState == SWIPE_DOWN) {
            // TODO: Polish quick switch after this release.
            // Quick switch between modes.
            int currentModuleIndex = mController.getCurrentModuleIndex();
            final int moduleToTransitionTo =
                    mController.getQuickSwitchToModuleId(currentModuleIndex);
            if (currentModuleIndex != moduleToTransitionTo) {
                mAppRootView.redirectTouchEventsTo(mModeTransitionView);
                int shadeColorId = R.color.camera_gray_background;
                int iconRes = CameraUtil.getCameraModeCoverIconResId(moduleToTransitionTo,
                        mController.getAndroidContext());

                AnimationFinishedListener listener = new AnimationFinishedListener() {
                    @Override
                    public void onAnimationFinished(boolean success) {
                        if (success) {
                            mHideCoverRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    mModeTransitionView.startPeepHoleAnimation();
                                }
                            };
                            mModeCoverState = COVER_SHOWN;
                            // Go to new module when the previous operation is successful.
                            mController.onModeSelected(moduleToTransitionTo);
                        }
                    }
                };
            }
        } else if (swipeState == SWIPE_LEFT) {
            // Pass the touch sequence to filmstrip layout.
            mAppRootView.redirectTouchEventsTo(mFilmstripLayout);
        } else if (swipeState == SWIPE_RIGHT) {
            // Pass the touch to mode switcher
            mAppRootView.redirectTouchEventsTo(mModeListView);
        }
    }

    /**
     * Gets called when activity resumes in preview.
     */
    public void resume() {
        // Show mode theme cover until preview is ready
        showModeCoverUntilPreviewReady();

        // Hide action bar first since we are in full screen mode first, and
        // switch the system UI to lights-out mode.
        mFilmstripPanel.hide();

        // Show UI that is meant to only be used when spoken feedback is
        // enabled.
        mAccessibilityAffordances.setVisibility(
                (!mIsCaptureIntent && mAccessibilityUtil.isAccessibilityEnabled()) ? View.VISIBLE
                        : View.GONE);
    }

    /**
     * Opens the mode list (e.g. because of the menu button being pressed) and
     * adapts the rest of the UI.
     */
    public void openModeList() {
        mModeOptionsOverlay.closeModeOptions();
        mModeListView.onMenuPressed();
    }

    public void showAccessibilityZoomUI(float maxZoom) {
        mAccessibilityUtil.showZoomUI(maxZoom);
    }

    public void hideAccessibilityZoomUI() {
        mAccessibilityUtil.hideZoomUI();
    }

    /**
     * A cover view showing the mode theme color and mode icon will be visible on
     * top of preview until preview is ready (i.e. camera preview is started and
     * the first frame has been received).
     */
    private void showModeCoverUntilPreviewReady() {
        int modeId = mController.getCurrentModuleIndex();
        int colorId = R.color.camera_gray_background;;
        int iconId = CameraUtil.getCameraModeCoverIconResId(modeId, mController.getAndroidContext());
        mModeTransitionView.setupModeCover(colorId, iconId);
        mHideCoverRunnable = new Runnable() {
            @Override
            public void run() {
                mModeTransitionView.hideModeCover(null);
                if (!mDisableAllUserInteractions) {
                    showShimmyDelayed();
                }
            }
        };
        mModeCoverState = COVER_SHOWN;
    }

    private void showShimmyDelayed() {
        if (!mIsCaptureIntent) {
            // Show shimmy in SHIMMY_DELAY_MS
            mModeListView.showModeSwitcherHint();
        }
    }

    private void hideModeCover() {
        if (mHideCoverRunnable != null) {
            mAppRootView.post(mHideCoverRunnable);
            mHideCoverRunnable = null;
        }
        mModeCoverState = COVER_HIDDEN;
        if (mCoverHiddenTime < 0) {
            mCoverHiddenTime = System.currentTimeMillis();
        }
    }


    public void onPreviewVisiblityChanged(int visibility) {
        if (visibility == ModuleController.VISIBILITY_HIDDEN) {
            setIndicatorBottomBarWrapperVisible(false);
            mAccessibilityAffordances.setVisibility(View.GONE);
        } else {
            setIndicatorBottomBarWrapperVisible(true);
            if (!mIsCaptureIntent && mAccessibilityUtil.isAccessibilityEnabled()) {
                mAccessibilityAffordances.setVisibility(View.VISIBLE);
            } else {
                mAccessibilityAffordances.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Call to stop the preview from being rendered. Sets the entire capture
     * root view to invisible which includes the preview plus focus indicator
     * and any other auxiliary views for capture modes.
     */
    public void pausePreviewRendering() {
        mCameraRootView.setVisibility(View.INVISIBLE);
    }

    /**
     * Call to begin rendering the preview and auxiliary views again.
     */
    public void resumePreviewRendering() {
        mCameraRootView.setVisibility(View.VISIBLE);
    }

    /**
     * Returns the transform associated with the preview view.
     *
     * @param m the Matrix in which to copy the current transform.
     * @return The specified matrix if not null or a new Matrix instance
     *         otherwise.
     */
    public Matrix getPreviewTransform(Matrix m) {
        return mTextureView.getTransform(m);
    }

    @Override
    public void onOpenFullScreen() {
        // Do nothing.
    }

    @Override
    public void onModeListOpenProgress(float progress) {
        // When the mode list is in transition, ensure the large layers are
        // hardware accelerated.
        if (progress >= 1.0f || progress <= 0.0f) {
            // Convert hardware layers back to default layer types when animation stops
            // to prevent accidental artifacting.
            if(mModeOptionsToggle.getLayerType() == View.LAYER_TYPE_HARDWARE ||
                  mShutterButton.getLayerType() == View.LAYER_TYPE_HARDWARE) {
                Log.v(TAG, "Disabling hardware layer for the Mode Options Toggle Button.");
                mModeOptionsToggle.setLayerType(View.LAYER_TYPE_NONE, null);
                Log.v(TAG, "Disabling hardware layer for the Shutter Button.");
                mShutterButton.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        } else {
            if(mModeOptionsToggle.getLayerType() != View.LAYER_TYPE_HARDWARE ||
                  mShutterButton.getLayerType() != View.LAYER_TYPE_HARDWARE) {
                Log.v(TAG, "Enabling hardware layer for the Mode Options Toggle Button.");
                mModeOptionsToggle.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                Log.v(TAG, "Enabling hardware layer for the Shutter Button.");
                mShutterButton.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
        }

        progress = 1 - progress;
        float interpolatedProgress = Gusterpolator.INSTANCE.getInterpolation(progress);
        mModeOptionsToggle.setAlpha(interpolatedProgress);
        // Change shutter button alpha linearly based on the mode list open progress:
        // set the alpha to disabled alpha when list is fully open, to enabled alpha
        // when the list is fully closed.
        mShutterButton.setAlpha(progress * ShutterButton.ALPHA_WHEN_ENABLED
                + (1 - progress) * ShutterButton.ALPHA_WHEN_DISABLED);
    }

    @Override
    public void onModeListClosed() {
        // Convert hardware layers back to default layer types when animation stops
        // to prevent accidental artifacting.
        if(mModeOptionsToggle.getLayerType() == View.LAYER_TYPE_HARDWARE ||
              mShutterButton.getLayerType() == View.LAYER_TYPE_HARDWARE) {
            Log.v(TAG, "Disabling hardware layer for the Mode Options Toggle Button.");
            mModeOptionsToggle.setLayerType(View.LAYER_TYPE_NONE, null);
            Log.v(TAG, "Disabling hardware layer for the Shutter Button.");
            mShutterButton.setLayerType(View.LAYER_TYPE_NONE, null);
        }

        // Make sure the alpha on mode options ellipse is reset when mode drawer
        // is closed.
        mModeOptionsToggle.setAlpha(1f);
        mShutterButton.setAlpha(ShutterButton.ALPHA_WHEN_ENABLED);
    }

    /**
     * Called when the back key is pressed.
     *
     * @return Whether the UI responded to the key event.
     */
    public boolean onBackPressed() {
        if (mFilmstripLayout.getVisibility() == View.VISIBLE) {
            return mFilmstripLayout.onBackPressed();
        } else {
            return mModeListView.onBackPressed();
        }
    }

    /**
     * Sets a {@link com.android.camera.ui.PreviewStatusListener} that
     * listens to SurfaceTexture changes. In addition, listeners are set on
     * dependent app ui elements.
     *
     * @param previewStatusListener the listener that gets notified when SurfaceTexture
     *                              changes
     */
    public void setPreviewStatusListener(PreviewStatusListener previewStatusListener) {
        mPreviewStatusListener = previewStatusListener;
        if (mPreviewStatusListener != null) {
            onPreviewListenerChanged();
        }
    }

    /**
     * When the PreviewStatusListener changes, listeners need to be
     * set on the following app ui elements:
     * {@link com.android.camera.ui.PreviewOverlay},
     * {@link com.android.camera.ui.BottomBar},
     * {@link com.android.camera.ui.IndicatorIconController}.
     */
    private void onPreviewListenerChanged() {
        // Set a listener for recognizing preview gestures.
        GestureDetector.OnGestureListener gestureListener
            = mPreviewStatusListener.getGestureListener();
        if (gestureListener != null) {
            mPreviewOverlay.setGestureListener(gestureListener);
        }
        View.OnTouchListener touchListener = mPreviewStatusListener.getTouchListener();
        if (touchListener != null) {
            mPreviewOverlay.setTouchListener(touchListener);
        }

        mTextureViewHelper.setAutoAdjustTransform(
                mPreviewStatusListener.shouldAutoAdjustTransformMatrixOnLayout());
    }

    /**
     * This method should be called in onCameraOpened.  It defines CameraAppUI
     * specific changes that depend on the camera or camera settings.
     */
    public void onChangeCamera() {
        ModuleController moduleController = mController.getCurrentModuleController();
        HardwareSpec hardwareSpec = moduleController.getHardwareSpec();

        /**
         * The current UI requires that the flash option visibility in front-
         * facing camera be
         *   * disabled if back facing camera supports flash
         *   * hidden if back facing camera does not support flash
         * We save whether back facing camera supports flash because we cannot
         * get this in front facing camera without a camera switch.
         *
         * If this preference is cleared, we also need to clear the camera
         * facing setting so we default to opening the camera in back facing
         * camera, and can save this flash support value again.
         */
        if (hardwareSpec != null) {
            if (!mController.getSettingsManager().isSet(SettingsManager.SCOPE_GLOBAL,
                    Keys.KEY_FLASH_SUPPORTED_BACK_CAMERA)) {
                mController.getSettingsManager().set(SettingsManager.SCOPE_GLOBAL,
                        Keys.KEY_FLASH_SUPPORTED_BACK_CAMERA,
                        hardwareSpec.isFlashSupported());
            }
            /** Similar logic applies to the HDR option. */
            if (!mController.getSettingsManager().isSet(SettingsManager.SCOPE_GLOBAL,
                    Keys.KEY_HDR_SUPPORT_MODE_BACK_CAMERA)) {
                String hdrSupportMode;
                if (hardwareSpec.isHdrPlusSupported()) {
                    hdrSupportMode = getResourceString(
                            R.string.pref_camera_hdr_supportmode_hdr_plus);
                } else if (hardwareSpec.isHdrSupported()) {
                    hdrSupportMode = getResourceString(R.string.pref_camera_hdr_supportmode_hdr);
                } else {
                    hdrSupportMode = getResourceString(R.string.pref_camera_hdr_supportmode_none);
                }
                mController.getSettingsManager().set(SettingsManager.SCOPE_GLOBAL,
                        Keys.KEY_HDR_SUPPORT_MODE_BACK_CAMERA, hdrSupportMode);
            }
        }

        applyModuleSpecs(hardwareSpec, moduleController.getBottomBarSpec(),
                true /*skipScopeCheck*/);
        syncModeOptionIndicators();
    }

    /**
     * Updates the mode option indicators according to the current settings.
     */
    public void syncModeOptionIndicators() {
        if (mIndicatorIconController != null) {
            // Sync the settings state with the indicator state.
            mIndicatorIconController.syncIndicators();
        }
    }

    /**
     * Adds a listener to receive callbacks when preview area changes.
     */
    public void addPreviewAreaChangedListener(
            PreviewStatusListener.PreviewAreaChangedListener listener) {
        mTextureViewHelper.addPreviewAreaSizeChangedListener(listener);
    }

    /**
     * Removes a listener that receives callbacks when preview area changes.
     */
    public void removePreviewAreaChangedListener(
            PreviewStatusListener.PreviewAreaChangedListener listener) {
        mTextureViewHelper.removePreviewAreaSizeChangedListener(listener);
    }

    /**
     * This inflates generic_module layout, which contains all the shared views across
     * modules. Then each module inflates their own views in the given view group. For
     * now, this is called every time switching from a not-yet-refactored module to a
     * refactored module. In the future, this should only need to be done once per app
     * start.
     */
    public void prepareModuleUI() {
        mController.getSettingsManager().addListener(this);
        mModuleUI = (FrameLayout) mCameraRootView.findViewById(R.id.module_layout);
        mTextureView = (TextureView) mCameraRootView.findViewById(R.id.preview_content);
        mTextureViewHelper = new TextureViewHelper(mTextureView, mCaptureLayoutHelper,
                mController.getCameraProvider(), mController);
        mTextureViewHelper.setSurfaceTextureListener(this);
        mTextureViewHelper.setOnLayoutChangeListener(mPreviewLayoutChangeListener);

        mBottomBar = (BottomBar) mCameraRootView.findViewById(R.id.bottom_bar);
        int unpressedColor = mController.getAndroidContext().getResources()
            .getColor(R.color.camera_gray_background);
        setBottomBarColor(unpressedColor);
        updateModeSpecificUIColors();

        mBottomBar.setCaptureLayoutHelper(mCaptureLayoutHelper);

        mModeOptionsOverlay
            = (ModeOptionsOverlay) mCameraRootView.findViewById(R.id.mode_options_overlay);

        // Sets the visibility of the bottom bar and the mode options.
        resetBottomControls(mController.getCurrentModuleController(),
            mController.getCurrentModuleIndex());
        mModeOptionsOverlay.setCaptureLayoutHelper(mCaptureLayoutHelper);

        mShutterButton = (ShutterButton) mCameraRootView.findViewById(R.id.shutter_button);
        addShutterListener(mController.getCurrentModuleController());
        addShutterListener(mModeOptionsOverlay);
        addShutterListener(this);

        mGridLines = (GridLines) mCameraRootView.findViewById(R.id.grid_lines);
        mTextureViewHelper.addPreviewAreaSizeChangedListener(mGridLines);

        mPreviewOverlay = (PreviewOverlay) mCameraRootView.findViewById(R.id.preview_overlay);
        mPreviewOverlay.setOnTouchListener(new MyTouchListener());
        mPreviewOverlay.setOnPreviewTouchedListener(mModeOptionsOverlay);
        mAccessibilityUtil = new AccessibilityUtil(mPreviewOverlay, mAccessibilityAffordances);

        mCaptureOverlay = (CaptureAnimationOverlay)
                mCameraRootView.findViewById(R.id.capture_overlay);
        mTextureViewHelper.addPreviewAreaSizeChangedListener(mPreviewOverlay);
        mTextureViewHelper.addPreviewAreaSizeChangedListener(mCaptureOverlay);

        if (mIndicatorIconController == null) {
            mIndicatorIconController =
                new IndicatorIconController(mController, mAppRootView);
        }

        mController.getButtonManager().load(mCameraRootView);
        mController.getButtonManager().setListener(mIndicatorIconController);
        mController.getSettingsManager().addListener(mIndicatorIconController);

        mModeOptionsToggle = mCameraRootView.findViewById(R.id.mode_options_toggle);
        mFocusRing = (FocusRing) mCameraRootView.findViewById(R.id.focus_ring);
        mTutorialsPlaceHolderWrapper = (FrameLayout) mCameraRootView
                .findViewById(R.id.tutorials_placeholder_wrapper);
        mStickyBottomCaptureLayout = (StickyBottomCaptureLayout) mAppRootView
                .findViewById(R.id.sticky_bottom_capture_layout);
        mStickyBottomCaptureLayout.setCaptureLayoutHelper(mCaptureLayoutHelper);
        mCountdownCancelButton = (ImageButton) mStickyBottomCaptureLayout
                .findViewById(R.id.shutter_cancel_button);

        mTextureViewHelper.addPreviewAreaSizeChangedListener(mModeListView);
        mTextureViewHelper.addAspectRatioChangedListener(
                new PreviewStatusListener.PreviewAspectRatioChangedListener() {
                    @Override
                    public void onPreviewAspectRatioChanged(float aspectRatio) {
                        mModeOptionsOverlay.requestLayout();
                        mBottomBar.requestLayout();
                    }
                }
        );
    }

    /**
     * Called indirectly from each module in their initialization to get a view group
     * to inflate the module specific views in.
     *
     * @return a view group for modules to attach views to
     */
    public FrameLayout getModuleRootView() {
        // TODO: Change it to mModuleUI when refactor is done
        return mCameraRootView;
    }

    /**
     * Remove all the module specific views.
     */
    public void clearModuleUI() {
        if (mModuleUI != null) {
            mModuleUI.removeAllViews();
        }
        removeShutterListener(mController.getCurrentModuleController());
        mTutorialsPlaceHolderWrapper.removeAllViews();
        mTutorialsPlaceHolderWrapper.setVisibility(View.GONE);

        setShutterButtonEnabled(true);
        mPreviewStatusListener = null;
        mPreviewOverlay.reset();

        Log.v(TAG, "mFocusRing.stopFocusAnimations()");
        mFocusRing.stopFocusAnimations();
    }

    /**
     * Gets called when preview is ready to start. It sets up one shot preview callback
     * in order to receive a callback when the preview frame is available, so that
     * the preview cover can be hidden to reveal preview.
     *
     * An alternative for getting the timing to hide preview cover is through
     * {@link CameraAppUI#onSurfaceTextureUpdated(android.graphics.SurfaceTexture)},
     * which is less accurate but therefore is the fallback for modules that manage
     * their own preview callbacks (as setting one preview callback will override
     * any other installed preview callbacks), or use camera2 API.
     */
    public void onPreviewReadyToStart() {
        if (mModeCoverState == COVER_SHOWN) {
            mModeCoverState = COVER_WILL_HIDE_AT_NEXT_FRAME;
            mController.setupOneShotPreviewListener();
        }
    }

    /**
     * Gets called when preview is started.
     */
    public void onPreviewStarted() {
        Log.v(TAG, "onPreviewStarted");
        if (mModeCoverState == COVER_SHOWN) {
            // This is a work around of the face detection failure in b/20724126.
            // In particular, we need to drop the first preview frame in order to
            // make face detection work and also need to hide this preview frame to
            // avoid potential janks. We do this only for L, Nexus 6 and Haleakala.
            if (ApiHelper.isLorLMr1() && ApiHelper.IS_NEXUS_6) {
                mModeCoverState = COVER_WILL_HIDE_AFTER_NEXT_TEXTURE_UPDATE;
            } else {
                mModeCoverState = COVER_WILL_HIDE_AT_NEXT_TEXTURE_UPDATE;
            }
        }
        enableModeOptions();
    }

    /**
     * Gets notified when next preview frame comes in.
     */
    public void onNewPreviewFrame() {
        Log.v(TAG, "onNewPreviewFrame");
        CameraPerformanceTracker.onEvent(CameraPerformanceTracker.FIRST_PREVIEW_FRAME);
        hideModeCover();
    }

    @Override
    public void onShutterButtonClick() {
        /*
         * Set the mode options toggle unclickable, generally
         * throughout the app, whenever the shutter button is clicked.
         *
         * This could be done in the OnShutterButtonListener of the
         * ModeOptionsOverlay, but since it is very important that we
         * can clearly see when the toggle becomes clickable again,
         * keep all of that logic at this level.
         */
        // disableModeOptions();
    }

    @Override
    public void onShutterCoordinate(TouchCoordinate coord) {
        // Do nothing.
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
        // noop
    }

    @Override
    public void onShutterButtonLongPressed() {
        // noop
    }

    /**
     * Set the mode options toggle clickable.
     */
    public void enableModeOptions() {
        /*
         * For modules using camera 1 api, this gets called in
         * onSurfaceTextureUpdated whenever the preview gets stopped and
         * started after each capture.  This also takes care of the
         * case where the mode options might be unclickable when we
         * switch modes
         *
         * For modules using camera 2 api, they're required to call this
         * method when a capture is "completed".  Unfortunately this differs
         * per module implementation.
         */
        if (!mDisableAllUserInteractions) {
            mModeOptionsOverlay.setToggleClickable(true);
        }
    }

    /**
     * Set the mode options toggle not clickable.
     */
    public void disableModeOptions() {
        mModeOptionsOverlay.setToggleClickable(false);
    }

    public void setDisableAllUserInteractions(boolean disable) {
        if (disable) {
            disableModeOptions();
            setShutterButtonEnabled(false);
            setSwipeEnabled(false);
            mModeListView.hideAnimated();
        } else {
            enableModeOptions();
            setShutterButtonEnabled(true);
            setSwipeEnabled(true);
        }
        mDisableAllUserInteractions = disable;
    }

    @Override
    public void onModeButtonPressed(int modeIndex) {
        // TODO: Make CameraActivity listen to ModeListView's events.
        int pressedModuleId = mController.getModuleId(modeIndex);
        int currentModuleId = mController.getCurrentModuleIndex();
        if (pressedModuleId != currentModuleId) {
            hideCaptureIndicator();
        }
    }

    /**
     * Gets called when a mode is selected from {@link com.android.camera.ui.ModeListView}
     *
     * @param modeIndex mode index of the selected mode
     */
    @Override
    public void onModeSelected(int modeIndex) {
        mHideCoverRunnable = new Runnable() {
            @Override
            public void run() {
                mModeListView.startModeSelectionAnimation();
            }
        };
        mShutterButton.setAlpha(ShutterButton.ALPHA_WHEN_ENABLED);
        mModeCoverState = COVER_SHOWN;

        int lastIndex = mController.getCurrentModuleIndex();
        // Actual mode teardown / new mode initialization happens here
        mController.onModeSelected(modeIndex);
        int currentIndex = mController.getCurrentModuleIndex();

        if (lastIndex == currentIndex) {
            hideModeCover();
        }

        updateModeSpecificUIColors();
    }

    private void updateModeSpecificUIColors() {
        setBottomBarColorsForModeIndex(mController.getCurrentModuleIndex());
    }

    @Override
    public void onSettingsSelected() {
        mController.getSettingsManager().set(SettingsManager.SCOPE_GLOBAL,
                                             Keys.KEY_SHOULD_SHOW_SETTINGS_BUTTON_CLING, false);
        mModeListView.setShouldShowSettingsCling(false);
        mController.onSettingsSelected();
    }

    @Override
    public int getCurrentModeIndex() {
        return mController.getCurrentModuleIndex();
    }

    /********************** Capture animation **********************/
    /* TODO: This session is subject to UX changes. In addition to the generic
       flash animation and post capture animation, consider designating a parameter
       for specifying the type of animation, as well as an animation finished listener
       so that modules can have more knowledge of the status of the animation. */

    /**
     * Turns on or off the capture indicator suppression.
     */
    public void setShouldSuppressCaptureIndicator(boolean suppress) {
        mSuppressCaptureIndicator = suppress;
    }

    /**
     * Starts the capture indicator pop-out animation.
     *
     * @param accessibilityString An accessibility String to be announced during the peek animation.
     */
    public void startCaptureIndicatorRevealAnimation(String accessibilityString) {
        if (mSuppressCaptureIndicator || mFilmstripLayout.getVisibility() == View.VISIBLE) {
            return;
        }
        mRoundedThumbnailView.startRevealThumbnailAnimation(accessibilityString);
    }

    /**
     * Updates the thumbnail image in the capture indicator.
     *
     * @param thumbnailBitmap The thumbnail image to be shown.
     */
    public void updateCaptureIndicatorThumbnail(Bitmap thumbnailBitmap, int rotation) {
        if (mSuppressCaptureIndicator || mFilmstripLayout.getVisibility() == View.VISIBLE) {
            return;
        }
        mRoundedThumbnailView.setThumbnail(thumbnailBitmap, rotation);
    }

    /**
     * Hides the capture indicator.
     */
    public void hideCaptureIndicator() {
        mRoundedThumbnailView.hideThumbnail();
    }

    /**
     * Starts the flash animation.
     */
    public void startFlashAnimation(boolean shortFlash) {
        mCaptureOverlay.startFlashAnimation(shortFlash);
    }

    /**
     * Cancels the pre-capture animation.
     */
    public void cancelPreCaptureAnimation() {
        mAnimationManager.cancelAnimations();
    }

    /**
     * Cancels the post-capture animation.
     */
    public void cancelPostCaptureAnimation() {
        mAnimationManager.cancelAnimations();
    }

    public FilmstripContentPanel getFilmstripContentPanel() {
        return mFilmstripPanel;
    }

    /**
     * @return The {@link com.android.camera.app.CameraAppUI.BottomPanel} on the
     * bottom of the filmstrip.
     */
    public BottomPanel getFilmstripBottomControls() {
        return mFilmstripBottomControls;
    }

    public void showBottomControls() {
        mFilmstripBottomControls.show();
    }

    public void hideBottomControls() {
        mFilmstripBottomControls.hide();
    }

    /**
     * @param listener The listener for bottom controls.
     */
    public void setFilmstripBottomControlsListener(BottomPanel.Listener listener) {
        mFilmstripBottomControls.setListener(listener);
    }

    /***************************SurfaceTexture Api and Listener*********************************/

    /**
     * Return the shared surface texture.
     */
    public SurfaceTexture getSurfaceTexture() {
        return mSurface;
    }

    /**
     * Return the shared {@link android.graphics.SurfaceTexture}'s width.
     */
    public int getSurfaceWidth() {
        return mSurfaceWidth;
    }

    /**
     * Return the shared {@link android.graphics.SurfaceTexture}'s height.
     */
    public int getSurfaceHeight() {
        return mSurfaceHeight;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mSurface = surface;
        mSurfaceWidth = width;
        mSurfaceHeight = height;
        Log.v(TAG, "SurfaceTexture is available");
        if (mPreviewStatusListener != null) {
            mPreviewStatusListener.onSurfaceTextureAvailable(surface, width, height);
        }
        enableModeOptions();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        mSurface = surface;
        mSurfaceWidth = width;
        mSurfaceHeight = height;
        if (mPreviewStatusListener != null) {
            mPreviewStatusListener.onSurfaceTextureSizeChanged(surface, width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mSurface = null;
        Log.v(TAG, "SurfaceTexture is destroyed");
        if (mPreviewStatusListener != null) {
            return mPreviewStatusListener.onSurfaceTextureDestroyed(surface);
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        mSurface = surface;
        if (mPreviewStatusListener != null) {
            mPreviewStatusListener.onSurfaceTextureUpdated(surface);
        }
        // Do not show the first preview frame. Due to the bug b/20724126, we need to have
        // a WAR to request a preview frame followed by 5-frame ZSL burst before the repeating
        // preview and ZSL streams. Need to hide the first preview frame since it is janky.
        // We do this only for L, Nexus 6 and Haleakala.
        if (mModeCoverState == COVER_WILL_HIDE_AFTER_NEXT_TEXTURE_UPDATE) {
            mModeCoverState = COVER_WILL_HIDE_AT_NEXT_TEXTURE_UPDATE;
        } else if (mModeCoverState == COVER_WILL_HIDE_AT_NEXT_TEXTURE_UPDATE){
            Log.v(TAG, "hiding cover via onSurfaceTextureUpdated");
            CameraPerformanceTracker.onEvent(CameraPerformanceTracker.FIRST_PREVIEW_FRAME);
            hideModeCover();
        }
    }

    /****************************Grid lines api ******************************/

    /**
     * Show a set of evenly spaced lines over the preview.  The number
     * of lines horizontally and vertically is determined by
     * {@link com.android.camera.ui.GridLines}.
     */
    public void showGridLines() {
        if (mGridLines != null) {
            mGridLines.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Hide the set of evenly spaced grid lines overlaying the preview.
     */
    public void hideGridLines() {
        if (mGridLines != null) {
            mGridLines.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Return a callback which shows or hide the preview grid lines
     * depending on whether the grid lines setting is set on.
     */
    public ButtonManager.ButtonCallback getGridLinesCallback() {
        return new ButtonManager.ButtonCallback() {
            @Override
            public void onStateChanged(int state) {
                if (!mController.isPaused()) {
                    if (Keys.areGridLinesOn(mController.getSettingsManager())) {
                        showGridLines();
                    } else {
                        hideGridLines();
                    }
                }
            }
        };
    }

    /***************************Mode options api *****************************/

    /**
     * Set the mode options visible.
     */
    public void showModeOptions() {
        /* Make mode options clickable. */
        enableModeOptions();
        mModeOptionsOverlay.setVisibility(View.VISIBLE);
    }

    /**
     * Set the mode options invisible.  This is necessary for modes
     * that don't show a bottom bar for the capture UI.
     */
    public void hideModeOptions() {
        mModeOptionsOverlay.setVisibility(View.INVISIBLE);
    }

    /****************************Bottom bar api ******************************/

    /**
     * Sets up the bottom bar and mode options with the correct
     * shutter button and visibility based on the current module.
     */
    public void resetBottomControls(ModuleController module, int moduleIndex) {
        if (areBottomControlsUsed(module)) {
            setBottomBarShutterIcon(moduleIndex);
            mCaptureLayoutHelper.setShowBottomBar(true);
        } else {
            mCaptureLayoutHelper.setShowBottomBar(false);
        }
    }

    /**
     * Show or hide the mode options and bottom bar, based on
     * whether the current module is using the bottom bar.  Returns
     * whether the mode options and bottom bar are used.
     */
    private boolean areBottomControlsUsed(ModuleController module) {
        if (module.isUsingBottomBar()) {
            showBottomBar();
            showModeOptions();
            return true;
        } else {
            hideBottomBar();
            hideModeOptions();
            return false;
        }
    }

    /**
     * Set the bottom bar visible.
     */
    public void showBottomBar() {
        mBottomBar.setVisibility(View.VISIBLE);
    }

    /**
     * Set the bottom bar invisible.
     */
    public void hideBottomBar() {
        mBottomBar.setVisibility(View.INVISIBLE);
    }

    /**
     * Sets the color of the bottom bar.
     */
    public void setBottomBarColor(int colorId) {
        mBottomBar.setBackgroundColor(colorId);
    }

    /**
     * Sets the pressed color of the bottom bar for a camera mode index.
     */
    public void setBottomBarColorsForModeIndex(int index) {
        mBottomBar.setColorsForModeIndex(index);
    }

    /**
     * Sets the shutter button icon on the bottom bar, based on
     * the mode index.
     */
    public void setBottomBarShutterIcon(int modeIndex) {
        int shutterIconId = CameraUtil.getCameraShutterIconId(modeIndex,
            mController.getAndroidContext());
        mBottomBar.setShutterButtonIcon(shutterIconId);
    }

    public void animateBottomBarToVideoStop(int shutterIconId) {
        mBottomBar.animateToVideoStop(shutterIconId);
    }

    public void animateBottomBarToFullSize(int shutterIconId) {
        mBottomBar.animateToFullSize(shutterIconId);
    }

    public void setShutterButtonEnabled(final boolean enabled) {
        if (!mDisableAllUserInteractions) {
            mBottomBar.post(new Runnable() {
                @Override
                public void run() {
                    mBottomBar.setShutterButtonEnabled(enabled);
                }
            });
        }
    }

    public void setShutterButtonImportantToA11y(boolean important) {
        mBottomBar.setShutterButtonImportantToA11y(important);
    }

    public boolean isShutterButtonEnabled() {
        return mBottomBar.isShutterButtonEnabled();
    }

    public void setIndicatorBottomBarWrapperVisible(boolean visible) {
        mStickyBottomCaptureLayout.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * Set the visibility of the bottom bar.
     */
    // TODO: needed for when panorama is managed by the generic module ui.
    public void setBottomBarVisible(boolean visible) {
        mBottomBar.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * Add a {@link #ShutterButton.OnShutterButtonListener} to the shutter button.
     */
    public void addShutterListener(ShutterButton.OnShutterButtonListener listener) {
        mShutterButton.addOnShutterButtonListener(listener);
    }

    /**
     * Remove a {@link #ShutterButton.OnShutterButtonListener} from the shutter button.
     */
    public void removeShutterListener(ShutterButton.OnShutterButtonListener listener) {
        mShutterButton.removeOnShutterButtonListener(listener);
    }

    /**
     * Sets or replaces the "cancel shutter" button listener.
     * <p>
     * TODO: Make this part of the interface the same way shutter button
     * listeners are.
     */
    public void setCancelShutterButtonListener(View.OnClickListener listener) {
        mCountdownCancelButton.setOnClickListener(listener);
    }

    /**
     * Performs a transition to the capture layout of the bottom bar.
     */
    public void transitionToCapture() {
        ModuleController moduleController = mController.getCurrentModuleController();
        applyModuleSpecs(moduleController.getHardwareSpec(),
            moduleController.getBottomBarSpec());
        mBottomBar.transitionToCapture();
    }

    /**
     * Displays the Cancel button instead of the capture button.
     */
    public void transitionToCancel() {
        ModuleController moduleController = mController.getCurrentModuleController();
        applyModuleSpecs(moduleController.getHardwareSpec(),
                moduleController.getBottomBarSpec());
        mBottomBar.transitionToCancel();
    }

    /**
     * Performs a transition to the global intent layout.
     */
    public void transitionToIntentCaptureLayout() {
        ModuleController moduleController = mController.getCurrentModuleController();
        applyModuleSpecs(moduleController.getHardwareSpec(),
            moduleController.getBottomBarSpec());
        mBottomBar.transitionToIntentCaptureLayout();
        showModeOptions();
    }

    /**
     * Performs a transition to the global intent review layout.
     */
    public void transitionToIntentReviewLayout() {
        ModuleController moduleController = mController.getCurrentModuleController();
        applyModuleSpecs(moduleController.getHardwareSpec(),
            moduleController.getBottomBarSpec());
        mBottomBar.transitionToIntentReviewLayout();
        hideModeOptions();

        // Hide the preview snapshot since the screen is frozen when users tap
        // shutter button in capture intent.
        hideModeCover();
    }

    /**
     * @return whether UI is in intent review mode
     */
    public boolean isInIntentReview() {
        return mBottomBar.isInIntentReview();
    }

    @Override
    public void onSettingChanged(SettingsManager settingsManager, String key) {
        // Update the mode options based on the hardware spec,
        // when hdr changes to prevent flash from getting out of sync.
        if (key.equals(Keys.KEY_CAMERA_HDR)) {
            ModuleController moduleController = mController.getCurrentModuleController();
            applyModuleSpecs(moduleController.getHardwareSpec(),
                             moduleController.getBottomBarSpec(),
                             true /*skipScopeCheck*/);
        }
    }

    /**
     * Applies a {@link com.android.camera.CameraAppUI.BottomBarUISpec}
     * to the bottom bar mode options based on limitations from a
     * {@link com.android.camera.hardware.HardwareSpec}.
     *
     * Options not supported by the hardware are either hidden
     * or disabled, depending on the option.
     *
     * Otherwise, the option is fully enabled and clickable.
     */
    public void applyModuleSpecs(HardwareSpec hardwareSpec,
            BottomBarUISpec bottomBarSpec) {
        applyModuleSpecs(hardwareSpec, bottomBarSpec, false /*skipScopeCheck*/);
    }

    private void applyModuleSpecs(final HardwareSpec hardwareSpec,
           final BottomBarUISpec bottomBarSpec, boolean skipScopeCheck) {
        if (hardwareSpec == null || bottomBarSpec == null) {
            return;
        }

        ButtonManager buttonManager = mController.getButtonManager();
        SettingsManager settingsManager = mController.getSettingsManager();

        buttonManager.setToInitialState();

        if (skipScopeCheck
                || !mController.getModuleScope().equals(mCurrentModuleScope)
                || !mController.getCameraScope().equals(mCurrentCameraScope)) {

            // Scope dependent options, update only if the module or the
            // camera scope changed or scope-check skip was requested.
            mCurrentModuleScope = mController.getModuleScope();
            mCurrentCameraScope = mController.getCameraScope();

            mHdrSupportMode = settingsManager.getString(SettingsManager.SCOPE_GLOBAL,
                    Keys.KEY_HDR_SUPPORT_MODE_BACK_CAMERA);

            /** Standard mode options */
            if (mController.getCameraProvider().getNumberOfCameras() > 1 &&
                    hardwareSpec.isFrontCameraSupported()) {
                if (bottomBarSpec.enableCamera) {
                    int hdrButtonId = ButtonManager.BUTTON_HDR;
                    if (mHdrSupportMode.equals(getResourceString(
                            R.string.pref_camera_hdr_supportmode_hdr_plus))) {
                        hdrButtonId = ButtonManager.BUTTON_HDR_PLUS;
                    }
                    buttonManager.initializeButton(ButtonManager.BUTTON_CAMERA,
                            bottomBarSpec.cameraCallback,
                            getDisableButtonCallback(hdrButtonId));
                } else {
                    buttonManager.disableButton(ButtonManager.BUTTON_CAMERA);
                }
            } else {
                // Hide camera icon if front camera not available.
                buttonManager.hideButton(ButtonManager.BUTTON_CAMERA);
            }

            boolean flashBackCamera = settingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                    Keys.KEY_FLASH_SUPPORTED_BACK_CAMERA);
            if (bottomBarSpec.hideFlash
                    || !flashBackCamera) {
                // Hide both flash and torch button in flash disable logic
                buttonManager.hideButton(ButtonManager.BUTTON_FLASH);
                buttonManager.hideButton(ButtonManager.BUTTON_TORCH);
            } else {
                if (hardwareSpec.isFlashSupported()) {
                    if (bottomBarSpec.enableFlash) {
                        buttonManager.initializeButton(ButtonManager.BUTTON_FLASH,
                                bottomBarSpec.flashCallback);
                    } else if (bottomBarSpec.enableTorchFlash) {
                        buttonManager.initializeButton(ButtonManager.BUTTON_TORCH,
                                bottomBarSpec.flashCallback);
                    } else if (bottomBarSpec.enableHdrPlusFlash) {
                        buttonManager.initializeButton(ButtonManager.BUTTON_HDR_PLUS_FLASH,
                                bottomBarSpec.flashCallback);
                    } else {
                        // Disable both flash and torch button in flash disable
                        // logic. Need to ensure it's visible, it may be hidden
                        // from previous non-flash mode.
                        buttonManager.showButton(ButtonManager.BUTTON_FLASH);
                        buttonManager.disableButton(ButtonManager.BUTTON_FLASH);
                        buttonManager.disableButton(ButtonManager.BUTTON_TORCH);
                    }
                } else {
                    // Flash not supported but another module does.
                    // Disable flash button. Need to ensure it's visible,
                    // it may be hidden from previous non-flash mode.
                    buttonManager.showButton(ButtonManager.BUTTON_FLASH);
                    buttonManager.disableButton(ButtonManager.BUTTON_FLASH);
                    buttonManager.disableButton(ButtonManager.BUTTON_TORCH);
                }
            }

            if (bottomBarSpec.hideHdr || mIsCaptureIntent) {
                // Force hide hdr or hdr plus icon.
                buttonManager.hideButton(ButtonManager.BUTTON_HDR_PLUS);
            } else {
                if (hardwareSpec.isHdrPlusSupported()) {
                    mHdrSupportMode = getResourceString(
                            R.string.pref_camera_hdr_supportmode_hdr_plus);
                    if (bottomBarSpec.enableHdr) {
                        buttonManager.initializeButton(ButtonManager.BUTTON_HDR_PLUS,
                                bottomBarSpec.hdrCallback,
                                getDisableButtonCallback(ButtonManager.BUTTON_CAMERA));
                    } else {
                        buttonManager.disableButton(ButtonManager.BUTTON_HDR_PLUS);
                    }
                } else if (hardwareSpec.isHdrSupported()) {
                    mHdrSupportMode = getResourceString(R.string.pref_camera_hdr_supportmode_hdr);
                    if (bottomBarSpec.enableHdr) {
                        buttonManager.initializeButton(ButtonManager.BUTTON_HDR,
                                bottomBarSpec.hdrCallback,
                                getDisableButtonCallback(ButtonManager.BUTTON_CAMERA));
                    } else {
                        buttonManager.disableButton(ButtonManager.BUTTON_HDR);
                    }
                } else {
                    // Hide hdr plus or hdr icon if neither are supported overall.
                    if (mHdrSupportMode.isEmpty() || mHdrSupportMode
                            .equals(getResourceString(R.string.pref_camera_hdr_supportmode_none))) {
                        buttonManager.hideButton(ButtonManager.BUTTON_HDR_PLUS);
                    } else {
                        // Disable HDR button. Need to ensure it's visible,
                        // it may be hidden from previous non HDR mode (eg. Video).
                        int buttonId = ButtonManager.BUTTON_HDR;
                        if (mHdrSupportMode.equals(
                                getResourceString(R.string.pref_camera_hdr_supportmode_hdr_plus))) {
                            buttonId = ButtonManager.BUTTON_HDR_PLUS;
                        }
                        buttonManager.showButton(buttonId);
                        buttonManager.disableButton(buttonId);
                    }
                }
            }

        }
        if (bottomBarSpec.hideGridLines) {
            // Force hide grid lines icon.
            buttonManager.hideButton(ButtonManager.BUTTON_GRID_LINES);
            hideGridLines();
        } else {
            if (bottomBarSpec.enableGridLines) {
                buttonManager.initializeButton(ButtonManager.BUTTON_GRID_LINES,
                        bottomBarSpec.gridLinesCallback != null ?
                                bottomBarSpec.gridLinesCallback : getGridLinesCallback()
                );
            } else {
                buttonManager.disableButton(ButtonManager.BUTTON_GRID_LINES);
                hideGridLines();
            }
        }

        if (bottomBarSpec.enableSelfTimer) {
            buttonManager.initializeButton(ButtonManager.BUTTON_COUNTDOWN, null);
        } else {
            if (bottomBarSpec.showSelfTimer) {
                buttonManager.disableButton(ButtonManager.BUTTON_COUNTDOWN);
            } else {
                buttonManager.hideButton(ButtonManager.BUTTON_COUNTDOWN);
            }
        }

        if (bottomBarSpec.enablePanoOrientation
                && PhotoSphereHelper.getPanoramaOrientationOptionArrayId() > 0) {
            buttonManager.initializePanoOrientationButtons(bottomBarSpec.panoOrientationCallback);
        }



        // If manual exposure is enabled both in SettingsManager and
        // BottomBarSpec,then show the exposure button.
        // If manual exposure is disabled in the BottomBarSpec (eg. HDR+
        // enabled), but the device/module has the feature, then disable the exposure
        // button.
        // Otherwise, hide the button.
        if (bottomBarSpec.enableExposureCompensation
                && !(bottomBarSpec.minExposureCompensation == 0 && bottomBarSpec.maxExposureCompensation == 0)
                && mController.getSettingsManager().getBoolean(SettingsManager.SCOPE_GLOBAL,
                        Keys.KEY_EXPOSURE_COMPENSATION_ENABLED)) {
            buttonManager.initializePushButton(ButtonManager.BUTTON_EXPOSURE_COMPENSATION,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mModeOptionsOverlay.showExposureOptions();
                        }
                    });
            buttonManager.setExposureCompensationParameters(
                    bottomBarSpec.minExposureCompensation,
                    bottomBarSpec.maxExposureCompensation,
                    bottomBarSpec.exposureCompensationStep);

            buttonManager.setExposureCompensationCallback(
                    bottomBarSpec.exposureCompensationSetCallback);
            buttonManager.updateExposureButtons();
        } else if (mController.getSettingsManager().getBoolean(SettingsManager.SCOPE_GLOBAL,
                Keys.KEY_EXPOSURE_COMPENSATION_ENABLED)
                && bottomBarSpec.isExposureCompensationSupported) {
            buttonManager.disableButton(ButtonManager.BUTTON_EXPOSURE_COMPENSATION);
        } else {
            buttonManager.hideButton(ButtonManager.BUTTON_EXPOSURE_COMPENSATION);
            buttonManager.setExposureCompensationCallback(null);
        }

        /** Intent UI */
        if (bottomBarSpec.showCancel) {
            buttonManager.initializePushButton(ButtonManager.BUTTON_CANCEL,
                    bottomBarSpec.cancelCallback);
        }
        if (bottomBarSpec.showDone) {
            buttonManager.initializePushButton(ButtonManager.BUTTON_DONE,
                    bottomBarSpec.doneCallback);
        }
        if (bottomBarSpec.showRetake) {
            buttonManager.initializePushButton(ButtonManager.BUTTON_RETAKE,
                    bottomBarSpec.retakeCallback,
                    R.drawable.ic_back,
                    R.string.retake_button_description);
        }
        if (bottomBarSpec.showReview) {
            buttonManager.initializePushButton(ButtonManager.BUTTON_REVIEW,
                    bottomBarSpec.reviewCallback,
                    R.drawable.ic_play,
                    R.string.review_button_description);
        }
    }

    /**
     * Returns a {@link com.android.camera.ButtonManager.ButtonCallback} that
     * will disable the button identified by the parameter.
     *
     * @param conflictingButton The button id to be disabled.
     */
    private ButtonManager.ButtonCallback getDisableButtonCallback(final int conflictingButton) {
        return new ButtonManager.ButtonCallback() {
            @Override
            public void onStateChanged(int state) {
                mController.getButtonManager().disableButton(conflictingButton);
            }
        };
    }

    private String getResourceString(int stringId) {
        try {
            return mController.getAndroidContext().getResources().getString(stringId);
        } catch (Resources.NotFoundException e) {
            // String not found, returning empty string.
            return "";
        }
    }

    /**
     * Shows the given tutorial on the screen.
     */
    public void showTutorial(AbstractTutorialOverlay tutorial, LayoutInflater inflater) {
        tutorial.show(mTutorialsPlaceHolderWrapper, inflater);
    }

    /**
     * Whether the capture ratio selector dialog must be shown on this device.
     * */
    public boolean shouldShowAspectRatioDialog() {
        final boolean isAspectRatioPreferenceSet = mController.getSettingsManager().getBoolean(
                SettingsManager.SCOPE_GLOBAL, Keys.KEY_USER_SELECTED_ASPECT_RATIO);
        final boolean isAspectRatioDevice =
                ApiHelper.IS_NEXUS_4 || ApiHelper.IS_NEXUS_5 || ApiHelper.IS_NEXUS_6;
        return isAspectRatioDevice && !isAspectRatioPreferenceSet;
    }


    /***************************Filmstrip api *****************************/

    public void showFilmstrip() {
        mModeListView.onBackPressed();
        mFilmstripLayout.showFilmstrip();
    }

    public void hideFilmstrip() {
        mFilmstripLayout.hideFilmstrip();
    }

    public int getFilmstripVisibility() {
        return mFilmstripLayout.getVisibility();
    }
}
