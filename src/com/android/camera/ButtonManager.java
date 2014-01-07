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

import android.app.Activity;
import android.view.View;
import android.widget.ImageButton;
import android.util.Log;

import com.android.camera.settings.SettingsManager;

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
    public static final int BUTTON_REFOCUS = 4;
    public static final int BUTTON_CANCEL = 5;
    public static final int BUTTON_DONE = 6;
    public static final int BUTTON_RETAKE = 7;
    public static final int BUTTON_REVIEW = 8;

    /** For two state MultiToggleImageButtons, the off index. */
    public static final int OFF = 0;
    /** For two state MultiToggleImageButtons, the on index. */
    public static final int ON = 1;

    /** A reference to the application's settings manager. */
    private final SettingsManager mSettingsManager;

    /** Bottom bar options buttons. */
    private MultiToggleImageButton mButtonFlash; // same as torch.
    private MultiToggleImageButton mButtonCamera;
    private MultiToggleImageButton mButtonHdrPlus;
    private MultiToggleImageButton mButtonRefocus;

    /** Intent UI buttons. */
    private ImageButton mButtonCancel;
    private ImageButton mButtonDone;
    private ImageButton mButtonRetake; // same as review.

    /** A listener for button enabled and visibility
        state changes. */
    private ButtonStatusListener mListener;

    /**
     * Get a new global ButtonManager.
     */
    public ButtonManager(CameraActivity activity) {
        getButtonsReferences(activity);
        mSettingsManager = activity.getSettingsManager();
        mSettingsManager.addListener(this);
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
    private void getButtonsReferences(Activity activity) {
        mButtonFlash
            = (MultiToggleImageButton) activity.findViewById(R.id.flash_toggle_button);
        mButtonCamera
            = (MultiToggleImageButton) activity.findViewById(R.id.camera_toggle_button);
        mButtonHdrPlus
            = (MultiToggleImageButton) activity.findViewById(R.id.hdr_plus_toggle_button);
        mButtonRefocus
            = (MultiToggleImageButton) activity.findViewById(R.id.refocus_toggle_button);
        mButtonCancel
            = (ImageButton) activity.findViewById(R.id.cancel_button);
        mButtonDone
            = (ImageButton) activity.findViewById(R.id.done_button);
        mButtonRetake
            = (ImageButton) activity.findViewById(R.id.retake_button);
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
            case SettingsManager.SETTING_CAMERA_HDR: {
                index = mSettingsManager.getStringValueIndex(id);
                button = getButtonOrError(BUTTON_HDRPLUS);
                break;
            }
            case SettingsManager.SETTING_CAMERA_REFOCUS: {
                index = mSettingsManager.getStringValueIndex(id);
                button = getButtonOrError(BUTTON_REFOCUS);
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
                if (mButtonFlash == null) {
                    throw new IllegalStateException("Torch button could not be found.");
                }
                return mButtonFlash;
            case BUTTON_CAMERA:
                if (mButtonCamera == null) {
                    throw new IllegalStateException("Camera button could not be found.");
                }
                return mButtonCamera;
            case BUTTON_HDRPLUS:
                if (mButtonHdrPlus == null) {
                    throw new IllegalStateException("Hdr button could not be found.");
                }
                return mButtonHdrPlus;
            case BUTTON_REFOCUS:
                if (mButtonRefocus == null) {
                    throw new IllegalStateException("Refocus button could not be found.");
                }
                return mButtonRefocus;
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
            default:
                throw new IllegalArgumentException("button not known by id=" + buttonId);
        }
    }

    /**
     * Enable a known button by id, with a state change callback and
     * a resource id that points to an array of drawables.
     */
    public void enableButton(int buttonId, ButtonCallback cb, int resIdImages) {
        MultiToggleImageButton button = getButtonOrError(buttonId);
        switch (buttonId) {
            case BUTTON_FLASH:
                if (!mSettingsManager.isCameraBackFacing()) {
                    disableButton(BUTTON_FLASH);
                    return;
                }
                enableFlashButton(button, cb, resIdImages);
                break;
            case BUTTON_TORCH:
                if (!mSettingsManager.isCameraBackFacing()) {
                    disableButton(BUTTON_TORCH);
                    return;
                }
                enableTorchButton(button, cb, resIdImages);
                break;
            case BUTTON_CAMERA:
                enableCameraButton(button, cb, resIdImages);
                break;
            case BUTTON_HDRPLUS:
                enableHdrPlusButton(button, cb, resIdImages);
                break;
            case BUTTON_REFOCUS:
                enableRefocusButton(button, cb, resIdImages);
                break;
            default:
                throw new IllegalArgumentException("button not known by id=" + buttonId);
        }

        if (!button.isEnabled()) {
            button.setEnabled(true);
            if (mListener != null) {
                mListener.onButtonEnabledChanged(this, buttonId);
            }
        }

        if (button.getVisibility() != View.VISIBLE) {
            button.setVisibility(View.VISIBLE);
            if (mListener != null) {
                mListener.onButtonVisibilityChanged(this, buttonId);
            }
        }
    }

    /**
     * Enable a known button with a click listener and a resource id.
     * Sets the button visible.
     */
    public void enablePushButton(int buttonId, View.OnClickListener cb,
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
    }

    /**
     * Enable a known button with a click listener. Sets the button visible.
     */
    public void enablePushButton(int buttonId, View.OnClickListener cb) {
        ImageButton button = getImageButtonOrError(buttonId);
        button.setOnClickListener(cb);

        if (!button.isEnabled()) {
            button.setEnabled(true);
            if (mListener != null) {
                mListener.onButtonEnabledChanged(this, buttonId);
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
        MultiToggleImageButton button = getButtonOrError(buttonId);
        if (button.getVisibility() == View.VISIBLE) {
            button.setVisibility(View.INVISIBLE);
            if (mListener != null) {
                mListener.onButtonVisibilityChanged(this, buttonId);
            }
        }
    }

    /**
     * Check if a button is enabled.
     */
    public boolean isEnabled(int buttonId) {
        MultiToggleImageButton button = getButtonOrError(buttonId);
        return button.isEnabled();
    }

    /**
     * Check if a button is visible.
     */
    public boolean isVisible(int buttonId) {
        MultiToggleImageButton button = getButtonOrError(buttonId);
        return (button.getVisibility() == View.VISIBLE);
    }

    /**
     * Enable a flash button.
     */
    private void enableFlashButton(MultiToggleImageButton button,
            final ButtonCallback cb, int resIdImages) {

        if (resIdImages > 0) {
            button.overrideImageIds(resIdImages);
        }
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
     * Enable video torch button
     */
    private void enableTorchButton(MultiToggleImageButton button,
            final ButtonCallback cb, int resIdImages) {

        if (resIdImages > 0) {
            button.overrideImageIds(resIdImages);
        }
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
     * Enable a camera button.
     */
    private void enableCameraButton(MultiToggleImageButton button,
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
                    if (cb != null) {
                        cb.onStateChanged(cameraId);
                    }
                }
            });
    }

    /**
     * Enable an hdr plus button.
     */
    private void enableHdrPlusButton(MultiToggleImageButton button,
            final ButtonCallback cb, int resIdImages) {

        if (resIdImages > 0) {
            button.overrideImageIds(resIdImages);
        }

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
     * Enable a refocus button.
     */
    private void enableRefocusButton(MultiToggleImageButton button,
            final ButtonCallback cb, int resIdImages) {

        if (resIdImages > 0) {
            button.overrideImageIds(resIdImages);
        }

        int index = mSettingsManager.getStringValueIndex(SettingsManager.SETTING_CAMERA_REFOCUS);
        button.setState(index >= 0 ? index : 0, false);

        button.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener() {
                @Override
                public void stateChanged(View view, int state) {
                    mSettingsManager.setStringValueIndex(SettingsManager.SETTING_CAMERA_REFOCUS, state);
                    if (cb != null) {
                        cb.onStateChanged(state);
                    }
                }
            });
    }
}