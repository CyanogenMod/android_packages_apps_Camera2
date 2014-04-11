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

package com.android.camera;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageButton;

import com.android.camera.app.AppController;
import com.android.camera.app.CameraAppUI;
import com.android.camera.settings.SettingsManager;
import com.android.camera.util.PhotoSphereHelper;

import com.android.camera2.R;

/**
 * A  class for generating pre-initialized
 * {@link #android.widget.ImageButton}s.
 */
public class ButtonManager implements SettingsManager.OnSettingChangedListener {

    public static final int BUTTON_FLASH = 0;
    public static final int BUTTON_TORCH = 1;
    public static final int BUTTON_CAMERA = 2;
    public static final int BUTTON_HDRPLUS = 3;
    public static final int BUTTON_HDR = 4;
    public static final int BUTTON_CANCEL = 6;
    public static final int BUTTON_DONE = 7;
    public static final int BUTTON_RETAKE = 8;
    public static final int BUTTON_REVIEW = 9;
    public static final int BUTTON_PANO_ORIENTATION = 10;
    public static final int BUTTON_GRID_LINES = 11;
    public static final int BUTTON_EXPOSURE_COMPENSATION = 12;

    /** For two state MultiToggleImageButtons, the off index. */
    public static final int OFF = 0;
    /** For two state MultiToggleImageButtons, the on index. */
    public static final int ON = 1;

    /** A reference to the application's settings manager. */
    private final SettingsManager mSettingsManager;

    /** Bottom bar options toggle buttons. */
    private MultiToggleImageButton mButtonCamera;
    private MultiToggleImageButton mButtonFlash;
    private MultiToggleImageButton mButtonHdr;
    private MultiToggleImageButton mButtonGridlines;
    private MultiToggleImageButton mButtonPanoOrientation;

    /** Intent UI buttons. */
    private ImageButton mButtonCancel;
    private ImageButton mButtonDone;
    private ImageButton mButtonRetake; // same as review.

    private ImageButton mButtonExposureCompensation;
    private ImageButton mExposureN2;
    private ImageButton mExposureN1;
    private ImageButton mExposure0;
    private ImageButton mExposureP1;
    private ImageButton mExposureP2;

    /** A listener for button enabled and visibility
        state changes. */
    private ButtonStatusListener mListener;

    /** An reference to the gcam mode index. */
    private static int sGcamIndex;

    private AppController mAppController;

    /**
     * Get a new global ButtonManager.
     */
    public ButtonManager(AppController app) {
        mAppController = app;

        Context context = app.getAndroidContext();
        sGcamIndex = context.getResources().getInteger(R.integer.camera_mode_gcam);

        mSettingsManager = app.getSettingsManager();
        mSettingsManager.addListener(this);
    }

    /**
     * Load references to buttons under a root View.
     * Call this after the root clears/reloads all of its children
     * to prevent stale references button views.
     */
    public void load(View root) {
        getButtonsReferences(root);
    }

    /**
     * ButtonStatusListener provides callbacks for when button's
     * visibility changes and enabled status changes.
     */
    public interface ButtonStatusListener {
        /**
         * A button's visibility has changed.
         */
        public void onButtonVisibilityChanged(ButtonManager buttonManager, int buttonId);

        /**
         * A button's enabled state has changed.
         */
        public void onButtonEnabledChanged(ButtonManager buttonManager, int buttonId);
    }

    /**
     * Sets the ButtonStatusListener.
     */
    public void setListener(ButtonStatusListener listener) {
        mListener = listener;
    }

    /**
     * Gets references to all known buttons.
     */
    private void getButtonsReferences(View root) {
        mButtonCamera
            = (MultiToggleImageButton) root.findViewById(R.id.camera_toggle_button);
        mButtonFlash
            = (MultiToggleImageButton) root.findViewById(R.id.flash_toggle_button);
        mButtonHdr
            = (MultiToggleImageButton) root.findViewById(R.id.hdr_plus_toggle_button);
        mButtonGridlines
            = (MultiToggleImageButton) root.findViewById(R.id.grid_lines_toggle_button);
        mButtonPanoOrientation
            = (MultiToggleImageButton) root.findViewById(R.id.pano_orientation_toggle_button);
        mButtonCancel
            = (ImageButton) root.findViewById(R.id.cancel_button);
        mButtonDone
            = (ImageButton) root.findViewById(R.id.done_button);
        mButtonRetake
            = (ImageButton) root.findViewById(R.id.retake_button);

        mButtonExposureCompensation =
            (ImageButton) root.findViewById(R.id.exposure_button);
        mExposureN2 = (ImageButton) root.findViewById(R.id.exposure_n2);
        mExposureN1 = (ImageButton) root.findViewById(R.id.exposure_n1);
        mExposure0 = (ImageButton) root.findViewById(R.id.exposure_0);
        mExposureP1 = (ImageButton) root.findViewById(R.id.exposure_p1);
        mExposureP2 = (ImageButton) root.findViewById(R.id.exposure_p2);
    }

    @Override
    public void onSettingChanged(SettingsManager settingsManager, int id) {
        MultiToggleImageButton button = null;
        int index = 0;

        switch (id) {
            case SettingsManager.SETTING_FLASH_MODE: {
                index = mSettingsManager.getStringValueIndex(id);
                button = getButtonOrError(BUTTON_FLASH);
                break;
            }
            case SettingsManager.SETTING_VIDEOCAMERA_FLASH_MODE: {
                index = mSettingsManager.getStringValueIndex(id);
                button = getButtonOrError(BUTTON_TORCH);
                break;
            }
            case SettingsManager.SETTING_CAMERA_ID: {
                index = mSettingsManager.getStringValueIndex(id);
                button = getButtonOrError(BUTTON_CAMERA);
                break;
            }
            case SettingsManager.SETTING_CAMERA_HDR_PLUS: {
                index = mSettingsManager.getStringValueIndex(id);
                button = getButtonOrError(BUTTON_HDRPLUS);
                break;
            }
            case SettingsManager.SETTING_CAMERA_HDR: {
                index = mSettingsManager.getStringValueIndex(id);
                button = getButtonOrError(BUTTON_HDR);
                break;
            }
            case SettingsManager.SETTING_CAMERA_GRID_LINES: {
                index = mSettingsManager.getStringValueIndex(id);
                button = getButtonOrError(BUTTON_GRID_LINES);
                break;
            }
            case SettingsManager.SETTING_CAMERA_PANO_ORIENTATION: {
                index = mSettingsManager.getStringValueIndex(id);
                button = getButtonOrError(BUTTON_PANO_ORIENTATION);
                break;
            }
            case SettingsManager.SETTING_EXPOSURE_COMPENSATION_VALUE: {
                updateExposureButtons();
                break;
            }
            default: {
                // Do nothing.
            }
        }

        // In case SharedPreferences has changed but the button hasn't been toggled,
        // make sure the toggle state is in sync.
        if (button != null && button.getState() != index) {
            button.setState(Math.max(index, 0), false);
        }
    }

    /**
     * A callback executed in the state listener of a button.

     * Used by a module to set specific behavior when a button's
     * state changes.
     */
    public interface ButtonCallback {
        public void onStateChanged(int state);
    }

    /**
     * Returns the appropriate {@link com.android.camera.MultiToggleImageButton}
     * based on button id.  An IllegalStateException will be throw if the
     * button could not be found in the view hierarchy.
     */
    private MultiToggleImageButton getButtonOrError(int buttonId) {
        switch (buttonId) {
            case BUTTON_FLASH:
                if (mButtonFlash == null) {
                    throw new IllegalStateException("Flash button could not be found.");
                }
                return mButtonFlash;
            case BUTTON_TORCH:
                if (mButtonFlash== null) {
                    throw new IllegalStateException("Torch button could not be found.");
                }
                return mButtonFlash;
            case BUTTON_CAMERA:
                if (mButtonCamera == null) {
                    throw new IllegalStateException("Camera button could not be found.");
                }
                return mButtonCamera;
            case BUTTON_HDRPLUS:
                if (mButtonHdr == null) {
                    throw new IllegalStateException("Hdr plus button could not be found.");
                }
                return mButtonHdr;
            case BUTTON_HDR:
                if (mButtonHdr == null) {
                    throw new IllegalStateException("Hdr button could not be found.");
                }
                return mButtonHdr;
            case BUTTON_GRID_LINES:
                if (mButtonGridlines == null) {
                    throw new IllegalStateException("Grid lines button could not be found.");
                }
                return mButtonGridlines;
            case BUTTON_PANO_ORIENTATION:
                if (mButtonPanoOrientation == null) {
                    throw new IllegalStateException("Pano orientation button could not be found.");
                }
                return mButtonPanoOrientation;
            default:
                throw new IllegalArgumentException("button not known by id=" + buttonId);
        }
    }

    /**
     * Returns the appropriate {@link android.widget.ImageButton}
     * based on button id.  An IllegalStateException will be throw if the
     * button could not be found in the view hierarchy.
     */
    private ImageButton getImageButtonOrError(int buttonId) {
        switch (buttonId) {
            case BUTTON_CANCEL:
                if (mButtonCancel == null) {
                    throw new IllegalStateException("Cancel button could not be found.");
                }
                return mButtonCancel;
            case BUTTON_DONE:
                if (mButtonDone == null) {
                    throw new IllegalStateException("Done button could not be found.");
                }
                return mButtonDone;
            case BUTTON_RETAKE:
                if (mButtonRetake == null) {
                    throw new IllegalStateException("Retake button could not be found.");
                }
                return mButtonRetake;
            case BUTTON_REVIEW:
                if (mButtonRetake == null) {
                    throw new IllegalStateException("Review button could not be found.");
                }
                return mButtonRetake;
            case BUTTON_EXPOSURE_COMPENSATION:
                if (mButtonExposureCompensation == null) {
                    throw new IllegalStateException("Exposure Compensation button could not be found.");
                }
                return mButtonExposureCompensation;
            default:
                throw new IllegalArgumentException("button not known by id=" + buttonId);
        }
    }

    /**
     * Initialize a known button by id, with a state change callback and
     * a resource id that points to an array of drawables, and then enable
     * the button.
     */
    public void initializeButton(int buttonId, ButtonCallback cb) {
        MultiToggleImageButton button = getButtonOrError(buttonId);
        switch (buttonId) {
            case BUTTON_FLASH:
                initializeFlashButton(button, cb, R.array.camera_flashmode_icons);
                break;
            case BUTTON_TORCH:
                initializeTorchButton(button, cb, R.array.video_flashmode_icons);
                break;
            case BUTTON_CAMERA:
                initializeCameraButton(button, cb, R.array.camera_id_icons);
                break;
            case BUTTON_HDRPLUS:
                initializeHdrPlusButton(button, cb, R.array.pref_camera_hdr_plus_icons);
                break;
            case BUTTON_HDR:
                initializeHdrButton(button, cb, R.array.pref_camera_hdr_icons);
                break;
            case BUTTON_GRID_LINES:
                initializeGridLinesButton(button, cb, R.array.grid_lines_icons);
                break;
            case BUTTON_PANO_ORIENTATION:
                initializePanoOrientationButton(button, cb,
                        PhotoSphereHelper.getPanoramaOrientationOptionArrayId());
                break;
            default:
                throw new IllegalArgumentException("button not known by id=" + buttonId);
        }

        enableButton(buttonId);
    }

    /**
     * Initialize a known button with a click listener and a resource id.
     * Sets the button visible.
     */
    public void initializePushButton(int buttonId, View.OnClickListener cb,
            int imageId) {
        ImageButton button = getImageButtonOrError(buttonId);
        button.setOnClickListener(cb);
        button.setImageResource(imageId);

        if (!button.isEnabled()) {
            button.setEnabled(true);
            if (mListener != null) {
                mListener.onButtonEnabledChanged(this, buttonId);
            }
        }
        button.setTag(R.string.tag_enabled_id, (Integer) buttonId);

        if (button.getVisibility() != View.VISIBLE) {
            button.setVisibility(View.VISIBLE);
            if (mListener != null) {
                mListener.onButtonVisibilityChanged(this, buttonId);
            }
        }
    }

    /**
     * Initialize a known button with a click listener. Sets the button visible.
     */
    public void initializePushButton(int buttonId, View.OnClickListener cb) {
        ImageButton button = getImageButtonOrError(buttonId);
        if (cb != null) {
            button.setOnClickListener(cb);
        }

        if (!button.isEnabled()) {
            button.setEnabled(true);
            if (mListener != null) {
                mListener.onButtonEnabledChanged(this, buttonId);
            }
        }
        button.setTag(R.string.tag_enabled_id, (Integer) buttonId);

        if (button.getVisibility() != View.VISIBLE) {
            button.setVisibility(View.VISIBLE);
            if (mListener != null) {
                mListener.onButtonVisibilityChanged(this, buttonId);
            }
        }
    }

    /**
     * Sets a button in its disabled (greyed out) state.
     */
    public void disableButton(int buttonId) {
        MultiToggleImageButton button = getButtonOrError(buttonId);
        if (button.isEnabled()) {
            button.setEnabled(false);
            if (mListener != null) {
                mListener.onButtonEnabledChanged(this, buttonId);
            }
        }
        button.setTag(R.string.tag_enabled_id, null);

        if (button.getVisibility() != View.VISIBLE) {
            button.setVisibility(View.VISIBLE);
            if (mListener != null) {
                mListener.onButtonVisibilityChanged(this, buttonId);
            }
        }
    }

    /**
     * Enables a button that has already been initialized.
     */
    public void enableButton(int buttonId) {
        ImageButton button = getButtonOrError(buttonId);
        if (!button.isEnabled()) {
            button.setEnabled(true);
            if (mListener != null) {
                mListener.onButtonEnabledChanged(this, buttonId);
            }
        }
        button.setTag(R.string.tag_enabled_id, buttonId);

        if (button.getVisibility() != View.VISIBLE) {
            button.setVisibility(View.VISIBLE);
            if (mListener != null) {
                mListener.onButtonVisibilityChanged(this, buttonId);
            }
        }
    }

    /**
     * Hide a button by id.
     */
    public void hideButton(int buttonId) {
        View button;
        try {
            button = (View) getButtonOrError(buttonId);
        } catch (IllegalArgumentException e) {
            button = (View) getImageButtonOrError(buttonId);
        }
        if (button.getVisibility() == View.VISIBLE) {
            button.setVisibility(View.GONE);
            if (mListener != null) {
                mListener.onButtonVisibilityChanged(this, buttonId);
            }
        }
    }

    public void setExposureCompensationCallback(final CameraAppUI.BottomBarUISpec
                                        .ExposureCompensationSetCallback cb) {
        if (cb == null) {
            mExposureN2.setOnClickListener(null);
            mExposureN1.setOnClickListener(null);
            mExposure0.setOnClickListener(null);
            mExposureP1.setOnClickListener(null);
            mExposureP2.setOnClickListener(null);
        } else {
            View.OnClickListener onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int comp = 0;
                    switch(v.getId()) {
                        case R.id.exposure_n2:
                            comp = -2;
                            break;
                        case R.id.exposure_n1:
                            comp = -1;
                            break;
                        case R.id.exposure_0:
                            comp = 0;
                            break;
                        case R.id.exposure_p1:
                            comp = 1;
                            break;
                        case R.id.exposure_p2:
                            comp = 2;
                    }
                    // Each integer compensation represent 1/6 of a stop.
                    cb.setExposure(comp * 6);
                }
            };

            mExposureN2.setOnClickListener(onClickListener);
            mExposureN1.setOnClickListener(onClickListener);
            mExposure0.setOnClickListener(onClickListener);
            mExposureP1.setOnClickListener(onClickListener);
            mExposureP2.setOnClickListener(onClickListener);
        }
    }

    /**
     * Check if a button is enabled with the given button id..
     */
    public boolean isEnabled(int buttonId) {
        View button;
        try {
            button = (View) getButtonOrError(buttonId);
        } catch (IllegalArgumentException e) {
            button = (View) getImageButtonOrError(buttonId);
        }

        Integer enabledId = (Integer) button.getTag(R.string.tag_enabled_id);
        if (enabledId != null) {
            return (enabledId.intValue() == buttonId) && button.isEnabled();
        } else {
            return false;
        }
    }

    /**
     * Check if a button is visible.
     */
    public boolean isVisible(int buttonId) {
        View button;
        try {
            button = (View) getButtonOrError(buttonId);
        } catch (IllegalArgumentException e) {
            button = (View) getImageButtonOrError(buttonId);
        }
        return (button.getVisibility() == View.VISIBLE);
    }

    /**
     * Initialize a flash button.
     */
    private void initializeFlashButton(MultiToggleImageButton button,
            final ButtonCallback cb, int resIdImages) {

        if (resIdImages > 0) {
            button.overrideImageIds(resIdImages);
        }
        button.overrideContentDescriptions(R.array.camera_flash_descriptions);

        int index = mSettingsManager.getStringValueIndex(SettingsManager.SETTING_FLASH_MODE);
        button.setState(index >= 0 ? index : 0, false);

        button.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener() {
            @Override
            public void stateChanged(View view, int state) {
                mSettingsManager.setStringValueIndex(SettingsManager.SETTING_FLASH_MODE, state);
                if (cb != null) {
                    cb.onStateChanged(state);
                }
            }
        });
    }

    /**
     * Initialize video torch button
     */
    private void initializeTorchButton(MultiToggleImageButton button,
            final ButtonCallback cb, int resIdImages) {

        if (resIdImages > 0) {
            button.overrideImageIds(resIdImages);
        }
        button.overrideContentDescriptions(R.array.video_flash_descriptions);

        int index = mSettingsManager.getStringValueIndex(
                SettingsManager.SETTING_VIDEOCAMERA_FLASH_MODE);
        button.setState(index >= 0 ? index : 0, false);

        button.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener() {
            @Override
            public void stateChanged(View view, int state) {
                mSettingsManager.setStringValueIndex(
                        SettingsManager.SETTING_VIDEOCAMERA_FLASH_MODE, state);
                if(cb != null) {
                    cb.onStateChanged(state);
                }
            }
        });
    }

    /**
     * Initialize a camera button.
     */
    private void initializeCameraButton(final MultiToggleImageButton button,
            final ButtonCallback cb, int resIdImages) {

        if (resIdImages > 0) {
            button.overrideImageIds(resIdImages);
        }

        int index = mSettingsManager.getStringValueIndex(SettingsManager.SETTING_CAMERA_ID);
        button.setState(index >= 0 ? index : 0, false);

        button.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener() {
            @Override
            public void stateChanged(View view, int state) {
                mSettingsManager.setStringValueIndex(SettingsManager.SETTING_CAMERA_ID, state);
                int cameraId = Integer.parseInt(mSettingsManager.get(
                        SettingsManager.SETTING_CAMERA_ID));
                // This is a quick fix for ISE in Gcam module which can be
                // found by rapid pressing camera switch button. The assumption
                // here is that each time this button is clicked, the listener
                // will do something and then enable this button again.
                button.setEnabled(false);
                if (cb != null) {
                    cb.onStateChanged(cameraId);
                }
                mAppController.getCameraAppUI().onChangeCamera();
            }
        });
    }

    /**
     * Initialize an hdr plus button.
     */
    private void initializeHdrPlusButton(MultiToggleImageButton button,
            final ButtonCallback cb, int resIdImages) {

        if (resIdImages > 0) {
            button.overrideImageIds(resIdImages);
        }
        button.overrideContentDescriptions(R.array.hdr_plus_descriptions);

        int index = mSettingsManager.getStringValueIndex(SettingsManager.SETTING_CAMERA_HDR_PLUS);
        button.setState(index >= 0 ? index : 0, false);

        button.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener() {
            @Override
            public void stateChanged(View view, int state) {
                mSettingsManager.setStringValueIndex(SettingsManager.SETTING_CAMERA_HDR_PLUS, state);
                if (cb != null) {
                    cb.onStateChanged(state);
                }
            }
        });
    }

    /**
     * Initialize an hdr button.
     */
    private void initializeHdrButton(MultiToggleImageButton button,
            final ButtonCallback cb, int resIdImages) {

        if (resIdImages > 0) {
            button.overrideImageIds(resIdImages);
        }
        button.overrideContentDescriptions(R.array.hdr_descriptions);

        int index = mSettingsManager.getStringValueIndex(SettingsManager.SETTING_CAMERA_HDR);
        button.setState(index >= 0 ? index : 0, false);

        button.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener() {
            @Override
            public void stateChanged(View view, int state) {
                mSettingsManager.setStringValueIndex(SettingsManager.SETTING_CAMERA_HDR, state);
                if (cb != null) {
                    cb.onStateChanged(state);
                }
            }
        });
    }

    /**
     * Update the visual state of the manual exposure buttons
     */
    public void updateExposureButtons() {
        String compString = mSettingsManager.get(SettingsManager.SETTING_EXPOSURE_COMPENSATION_VALUE);
        int comp = Integer.parseInt(compString);

        // Reset all button states.
        mExposureN2.setBackground(null);
        mExposureN1.setBackground(null);
        mExposure0.setBackground(null);
        mExposureP1.setBackground(null);
        mExposureP2.setBackground(null);

        // Highlight the appropriate button.
        Context context = mAppController.getAndroidContext();
        Drawable background = context.getResources()
            .getDrawable(R.drawable.button_background_selected_photo);

        // Each integer compensation represent 1/6 of a stop.
        switch (comp / 6) {
            case -2:
                mExposureN2.setBackground(background);
                break;
            case -1:
                mExposureN1.setBackground(background);
                break;
            case 0:
                mExposure0.setBackground(background);
                break;
            case 1:
                mExposureP1.setBackground(background);
                break;
            case 2:
                mExposureP2.setBackground(background);
        }
    }

    /**
     * Initialize a grid lines button.
     */
    private void initializeGridLinesButton(MultiToggleImageButton button,
            final ButtonCallback cb, int resIdImages) {

        if (resIdImages > 0) {
            button.overrideImageIds(resIdImages);
        }
        button.overrideContentDescriptions(R.array.grid_lines_descriptions);
        button.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener() {
            @Override
            public void stateChanged(View view, int state) {
                mSettingsManager.setStringValueIndex(
                    SettingsManager.SETTING_CAMERA_GRID_LINES, state);
                if (cb != null) {
                    cb.onStateChanged(state);
                }
            }
        });

        int index = mSettingsManager.getStringValueIndex(
            SettingsManager.SETTING_CAMERA_GRID_LINES);
        button.setState(index >= 0 ? index : 0, true);
    }

   /**
     * Initialize a panorama orientation button.
     */
    private void initializePanoOrientationButton(MultiToggleImageButton button,
            final ButtonCallback cb, int resIdImages) {

        if (resIdImages > 0) {
            button.overrideImageIds(resIdImages);
        }
        button.overrideContentDescriptions(
            PhotoSphereHelper.getPanoramaOrientationDescriptions());
        button.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener() {
            @Override
            public void stateChanged(View view, int state) {
                mSettingsManager.setStringValueIndex(
                    SettingsManager.SETTING_CAMERA_PANO_ORIENTATION, state);
                if (cb != null) {
                    cb.onStateChanged(state);
                }
            }
        });

        int index = mSettingsManager.getStringValueIndex(
            SettingsManager.SETTING_CAMERA_PANO_ORIENTATION);
        button.setState(index >= 0 ? index : 0, true);
    }
}
