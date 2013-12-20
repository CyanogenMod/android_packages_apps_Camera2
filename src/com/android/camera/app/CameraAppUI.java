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

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.camera.AnimationManager;
import com.android.camera.TextureViewHelper;
import com.android.camera.filmstrip.FilmstripContentPanel;
import com.android.camera.ui.BottomBar;
import com.android.camera.ui.CaptureAnimationOverlay;
import com.android.camera.ui.MainActivityLayout;
import com.android.camera.ui.ModeListView;
import com.android.camera.ui.ModeTransitionView;
import com.android.camera.ui.PreviewOverlay;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera.widget.FilmstripLayout;
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
        TextureView.SurfaceTextureListener {

    /**
     * The bottom controls on the filmstrip.
     */
    public static interface BottomControls {
        /** Values for the view state of the button. */
        public final int VIEW_NONE = 0;
        public final int VIEW_PHOTO_SPHERE = 1;
        public final int VIEW_RGBZ = 2;

        /**
         * Sets a new or replaces an existing listener for bottom control events.
         */
        void setListener(Listener listener);

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
         * @param state one of {@link #VIEW_NONE}, {@link #VIEW_PHOTO_SPHERE},
         *            {@link #VIEW_RGBZ}.
         */
        void setViewButtonVisibility(int state);

        /**
         * @param enabled Whether the button is enabled.
         */
        void setViewEnabled(boolean enabled);

        /**
         * @param visible Whether the button is visible.
         */
        void setTinyPlanetButtonVisibility(boolean visible);

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
         * @param visible Whether the button is visible.
         */
        void setGalleryButtonVisibility(boolean visible);

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
             * Called when the "gallery" button is pressed.
             */
            public void onGallery();
        }
    }

    private final static String TAG = "CameraAppUI";

    private final AppController mController;
    private final boolean mIsCaptureIntent;
    private final boolean mIsSecureCamera;
    private final AnimationManager mAnimationManager;

    // Swipe states:
    private final static int IDLE = 0;
    private final static int SWIPE_UP = 1;
    private final static int SWIPE_DOWN = 2;
    private final static int SWIPE_LEFT = 3;
    private final static int SWIPE_RIGHT = 4;

    // Touch related measures:
    private final int mSlop;
    private final static int SWIPE_TIME_OUT_MS = 500;

    private final static int SHIMMY_DELAY_MS = 1000;

    // Mode cover states:
    private final static int COVER_HIDDEN = 0;
    private final static int COVER_SHOWN = 1;
    private final static int COVER_WILL_HIDE_AT_NEXT_FRAME = 2;
    private static final int COVER_WILL_HIDE_AT_NEXT_TEXTURE_UPDATE = 3;

    // App level views:
    private final FrameLayout mCameraRootView;
    private final ModeTransitionView mModeTransitionView;
    private final MainActivityLayout mAppRootView;
    private final ModeListView mModeListView;
    private final FilmstripLayout mFilmstripLayout;
    private TextureView mTextureView;
    private FrameLayout mModuleUI;

    private BottomBar mBottomBar;
    private TextureViewHelper mTextureViewHelper;
    private final GestureDetector mGestureDetector;
    private int mSwipeState = IDLE;
    private PreviewOverlay mPreviewOverlay;
    private CaptureAnimationOverlay mCaptureOverlay;
    private PreviewStatusListener mPreviewStatusListener;
    private int mModeCoverState = COVER_HIDDEN;
    private final FilmstripBottomControls mFilmstripBottomControls;
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

    public void updatePreviewAspectRatio(float aspectRatio) {
        mTextureViewHelper.updateAspectRatio(aspectRatio);
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
                    || mSwipeState != IDLE) {
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
                    } else if (deltaY >= Math.abs(deltaX)) {
                        // Swipe down.
                        setSwipeState(SWIPE_DOWN);
                    } else if (deltaY <= -Math.abs(deltaX)) {
                        // Swipe up.
                        setSwipeState(SWIPE_UP);
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
                       boolean isSecureCamera, boolean isCaptureIntent) {
        mSlop = ViewConfiguration.get(controller.getAndroidContext()).getScaledTouchSlop();
        mController = controller;
        mIsSecureCamera = isSecureCamera;
        mIsCaptureIntent = isCaptureIntent;

        mAppRootView = appRootView;
        mFilmstripLayout = (FilmstripLayout) appRootView.findViewById(R.id.filmstrip_layout);
        mCameraRootView = (FrameLayout) appRootView.findViewById(R.id.camera_app_root);
        mModeTransitionView = (ModeTransitionView)
                mAppRootView.findViewById(R.id.mode_transition_view);
        mFilmstripBottomControls = new FilmstripBottomControls(
                (ViewGroup) mAppRootView.findViewById(R.id.filmstrip_bottom_controls));
        mFilmstripPanel = (FilmstripContentPanel) mAppRootView.findViewById(R.id.filmstrip_layout);
        mGestureDetector = new GestureDetector(controller.getAndroidContext(),
                new MyGestureListener());
        mModeListView = (ModeListView) appRootView.findViewById(R.id.mode_list_layout);
        if (mModeListView != null) {
            mModeListView.setModeSwitchListener(this);
        } else {
            Log.e(TAG, "Cannot find mode list in the view hierarchy");
        }
        mAnimationManager = new AnimationManager();
    }

    /**
     * Redirects touch events to appropriate recipient views based on swipe direction.
     * More specifically, swipe up and swipe down will be handled by the view that handles
     * mode transition; swipe left will be send to filmstrip; swipe right will be redirected
     * to mode list in order to bring up mode list.
     */
    private void onSwipeDetected(int swipeState) {
        if (swipeState == SWIPE_UP || swipeState == SWIPE_DOWN) {
            // Quick switch between photo/video.
            if (mController.getCurrentModuleIndex() == ModeListView.MODE_PHOTO ||
                    mController.getCurrentModuleIndex() == ModeListView.MODE_VIDEO) {
                mAppRootView.redirectTouchEventsTo(mModeTransitionView);

                final int moduleToTransitionTo =
                        mController.getCurrentModuleIndex() == ModeListView.MODE_PHOTO ?
                        ModeListView.MODE_VIDEO : ModeListView.MODE_PHOTO;
                int shadeColorId = ModeListView.getModeThemeColor(moduleToTransitionTo);
                int iconRes = ModeListView.getModeIconResourceId(moduleToTransitionTo);

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
                if (mSwipeState == SWIPE_UP) {
                    mModeTransitionView.prepareToPullUpShade(shadeColorId, iconRes, listener);
                } else {
                    mModeTransitionView.prepareToPullDownShade(shadeColorId, iconRes, listener);
                }
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
        if (mTextureView == null || mTextureView.getSurfaceTexture() != null) {
            mModeListView.startAccordionAnimationWithDelay(SHIMMY_DELAY_MS);
        } else {
            // Show mode theme cover until preview is ready
            showModeCoverUntilPreviewReady();
        }
        // Hide action bar first since we are in full screen mode first, and
        // switch the system UI to lights-out mode.
        mFilmstripPanel.hide();
    }

    /**
     * A cover view showing the mode theme color and mode icon will be visible on
     * top of preview until preview is ready (i.e. camera preview is started and
     * the first frame has been received).
     */
    private void showModeCoverUntilPreviewReady() {
        int modeId = mController.getCurrentModuleIndex();
        int colorId = ModeListView.getModeThemeColor(modeId);
        int iconId = ModeListView.getModeIconResourceId(modeId);
        mModeTransitionView.setupModeCover(colorId, iconId);
        mHideCoverRunnable = new Runnable() {
            @Override
            public void run() {
                mModeTransitionView.hideModeCover(new AnimationFinishedListener() {
                    @Override
                    public void onAnimationFinished(boolean success) {
                        if (success) {
                            // Show shimmy in SHIMMY_DELAY_MS
                            mModeListView.startAccordionAnimationWithDelay(SHIMMY_DELAY_MS);
                        }
                    }
                });
            }
        };
        mModeCoverState = COVER_SHOWN;
    }

    private void hideModeCover() {
        if (mHideCoverRunnable != null) {
            mAppRootView.post(mHideCoverRunnable);
            mHideCoverRunnable = null;
        }
        mModeCoverState = COVER_HIDDEN;
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
     * listens to SurfaceTexture changes. In addition, the listener will also provide
     * a {@link android.view.GestureDetector.OnGestureListener}, which will listen to
     * gestures that happen on camera preview.
     *
     * @param previewStatusListener the listener that gets notified when SurfaceTexture
     *                              changes
     */
    public void setPreviewStatusListener(PreviewStatusListener previewStatusListener) {
        mPreviewStatusListener = previewStatusListener;
        if (mPreviewStatusListener != null) {
            GestureDetector.OnGestureListener gestureListener
                    = mPreviewStatusListener.getGestureListener();
            if (gestureListener != null) {
                mPreviewOverlay.setGestureListener(gestureListener);
            }
            mTextureViewHelper.setAutoAdjustTransform(
                    mPreviewStatusListener.shouldAutoAdjustTransformMatrixOnLayout());
            if (mPreviewStatusListener.shouldAutoAdjustBottomBar()) {
                mBottomBar = (BottomBar) mCameraRootView.findViewById(R.id.bottom_bar);
                mTextureViewHelper.setPreviewSizeChangedListener(mBottomBar);
            }
        }
    }

    /**
     * This inflates generic_module layout, which contains all the shared views across
     * modules. Then each module inflates their own views in the given view group. For
     * now, this is called every time switching from a not-yet-refactored module to a
     * refactored module. In the future, this should only need to be done once per app
     * start.
     */
    public void prepareModuleUI() {
        mCameraRootView.removeAllViews();
        LayoutInflater inflater = (LayoutInflater) mController.getAndroidContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.generic_module, mCameraRootView, true);

        mModuleUI = (FrameLayout) mCameraRootView.findViewById(R.id.module_layout);
        mTextureView = (TextureView) mCameraRootView.findViewById(R.id.preview_content);
        mTextureViewHelper = new TextureViewHelper(mTextureView);
        mTextureViewHelper.setSurfaceTextureListener(this);
        mTextureViewHelper.setOnLayoutChangeListener(mPreviewLayoutChangeListener);

        mPreviewOverlay = (PreviewOverlay) mCameraRootView.findViewById(R.id.preview_overlay);
        mPreviewOverlay.setOnTouchListener(new MyTouchListener());
        mCaptureOverlay = (CaptureAnimationOverlay)
                mCameraRootView.findViewById(R.id.capture_overlay);
    }

    // TODO: Remove this when refactor is done.
    // This is here to ensure refactored modules can work with not-yet-refactored ones.
    public void clearCameraUI() {
        mCameraRootView.removeAllViews();
        mModuleUI = null;
        mTextureView = null;
        mPreviewOverlay = null;
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
        // TODO: Remove this when bottom bar is at the app level
        mBottomBar = null;
        mTextureViewHelper.setPreviewSizeChangedListener(null);

        mPreviewStatusListener = null;
        mPreviewOverlay.reset();
    }

    /**
     * Gets called when preview is started.
     */
    public void onPreviewStarted() {
        if (mModeCoverState == COVER_SHOWN) {
            if (mController.getCurrentModuleIndex() == ModeListView.MODE_GCAM) {
                // For modules that use camera2 api, check surfaceTexture update
                mModeCoverState = COVER_WILL_HIDE_AT_NEXT_TEXTURE_UPDATE;
            } else {
                mModeCoverState = COVER_WILL_HIDE_AT_NEXT_FRAME;
                mController.setupOneShotPreviewListener();
            }
        }
    }

    /**
     * Gets notified when next preview frame comes in.
     */
    public void onNewPreviewFrame() {
        hideModeCover();
        mModeCoverState = COVER_HIDDEN;
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
        mModeCoverState = COVER_SHOWN;

        int lastIndex = mController.getCurrentModuleIndex();
        mController.onModeSelected(modeIndex);
        int currentIndex = mController.getCurrentModuleIndex();

        if (mTextureView == null) {
            // TODO: Remove this when all the modules use TextureView
            int temporaryDelay = 600; // ms
            mModeListView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    hideModeCover();
                }
            }, temporaryDelay);
        } else if (lastIndex == currentIndex) {
            hideModeCover();
        }
    }

    /********************** Capture animation **********************/
    /* TODO: This session is subject to UX changes. In addition to the generic
       flash animation and post capture animation, consider designating a parameter
       for specifying the type of animation, as well as an animation finished listener
       so that modules can have more knowledge of the status of the animation. */

    /**
     * Starts the pre-capture animation.
     */
    public void startPreCaptureAnimation() {
        mCaptureOverlay.startFlashAnimation();
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
     * @return The {@link com.android.camera.app.CameraAppUI.BottomControls} on the
     * bottom of the filmstrip.
     */
    public BottomControls getFilmstripBottomControls() {
        return mFilmstripBottomControls;
    }

    /**
     * @param listener The listener for bottom controls.
     */
    public void setFilmstripBottomControlsListener(BottomControls.Listener listener) {
        mFilmstripBottomControls.setListener(listener);
    }

    /***************************SurfaceTexture Listener*********************************/

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.v(TAG, "SurfaceTexture is available");
        if (mPreviewStatusListener != null) {
            mPreviewStatusListener.onSurfaceTextureAvailable(surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        if (mPreviewStatusListener != null) {
            mPreviewStatusListener.onSurfaceTextureSizeChanged(surface, width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.v(TAG, "SurfaceTexture is destroyed");
        if (mPreviewStatusListener != null) {
            return mPreviewStatusListener.onSurfaceTextureDestroyed(surface);
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        if (mModeCoverState == COVER_WILL_HIDE_AT_NEXT_TEXTURE_UPDATE) {
            hideModeCover();
            mModeCoverState = COVER_HIDDEN;
        }
        if (mPreviewStatusListener != null) {
            mPreviewStatusListener.onSurfaceTextureUpdated(surface);
        }
    }
}
