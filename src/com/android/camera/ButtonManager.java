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

    /** For two state MultiStateToggleButtons, the off index. */
    public static final int OFF = 0;
    /** For two state MultiStateToggleButtons, the on index. */
    public static final int ON = 1;

    /** A reference to the activity for finding button views on demand. */
    private final CameraActivity mActivity;
    /** A reference to the application's settings manager. */
    private final SettingsManager mSettingsManager;

    /**
     * Get a new global ButtonManager.
     */
    public ButtonManager(CameraActivity activity) {
        mActivity = activity;
        mSettingsManager = activity.getSettingsManager();
        mSettingsManager.addListener(this);
    }

    // TODO:
    // Get references to the buttons in the constructor
    // to avoid looking up the buttons constantly.
    // The ButtonManager can know about the particular res id of a button.

    @Override
    public void onSettingChanged(SettingsManager settingsManager, int id) {
        MultiToggleImageButton button = null;
        int index = 0;

        switch (id) {
            case SettingsManager.SETTING_FLASH_MODE: {
                index = mSettingsManager.getStringValueIndex(id);
                button = getButtonOrError(BUTTON_FLASH, R.id.flash_toggle_button);
                break;
            }
            case SettingsManager.SETTING_VIDEOCAMERA_FLASH_MODE: {
                index = mSettingsManager.getStringValueIndex(id);
                button = getButtonOrError(BUTTON_TORCH, R.id.flash_toggle_button);
                break;
            }
            case SettingsManager.SETTING_CAMERA_ID: {
                index = mSettingsManager.getStringValueIndex(id);
                button = getButtonOrError(BUTTON_CAMERA, R.id.camera_toggle_button);
                break;
            }
            case SettingsManager.SETTING_CAMERA_HDR: {
                index = mSettingsManager.getStringValueIndex(id);
                button = getButtonOrError(BUTTON_HDRPLUS, R.id.hdr_plus_toggle_button);
                break;
            }
            case SettingsManager.SETTING_CAMERA_REFOCUS: {
                index = mSettingsManager.getStringValueIndex(id);
                button = getButtonOrError(BUTTON_REFOCUS, R.id.refocus_toggle_button);
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

    private MultiToggleImageButton getButtonOrError(int buttonId, int resId) {
        MultiToggleImageButton button
            = (MultiToggleImageButton) mActivity.findViewById(resId);
        if (button == null) {
            switch (buttonId) {
                case BUTTON_FLASH:
                    throw new IllegalStateException("Flash button could not be found.");
                case BUTTON_TORCH:
                    throw new IllegalStateException("Torch button could not be found.");
                case BUTTON_CAMERA:
                    throw new IllegalStateException("Camera button could not be found.");
                case BUTTON_HDRPLUS:
                    throw new IllegalStateException("Hdr button could not be found.");
                case BUTTON_REFOCUS:
                    throw new IllegalStateException("Refocus button could not be found.");
                default:
                    throw new IllegalArgumentException("button not known by id=" + buttonId);
            }
        }
        return button;
    }

    private ImageButton getImageButtonOrError(int buttonId, int resId) {
        ImageButton button = (ImageButton) mActivity.findViewById(resId);
        if (button == null) {
            switch (buttonId) {
                case BUTTON_CANCEL:
                    throw new IllegalStateException("Cancel button could not be found.");
                case BUTTON_DONE:
                    throw new IllegalStateException("Done button could not be found.");
                case BUTTON_RETAKE:
                    throw new IllegalStateException("Retake button could not be found.");
                case BUTTON_REVIEW:
                    throw new IllegalStateException("Review button could not be found.");

                default:
                    throw new IllegalArgumentException("button not known by id=" + buttonId);
            }
        }
        return button;
    }

    /**
     * Enable a known button by id, with a state change callback and
     * a resource id that points to an array of drawables.
     */
    public void enableButton(int buttonId, int resId, ButtonCallback cb, int resIdImages) {
        MultiToggleImageButton button = getButtonOrError(buttonId, resId);
        switch (buttonId) {
            case BUTTON_FLASH:
                enableFlashButton(button, cb, resIdImages);
                break;
            case BUTTON_CAMERA:
                enableCameraButton(button, cb, resIdImages);
                break;
            case BUTTON_HDRPLUS:
                enableHdrPlusButton(button, cb, resIdImages);
                break;
            case BUTTON_TORCH:
                enableTorchButton(button, cb, resIdImages);
                break;
            case BUTTON_REFOCUS:
                enableRefocusButton(button, cb, resIdImages);
                break;
            default:
                throw new IllegalArgumentException("button not known by id=" + buttonId);
        }
        button.setVisibility(View.VISIBLE);
    }

    /**
     * Enable a known button with a click listener and a resource id.
     * Sets the button visible.
     */
    public void enablePushButton(int buttonId, int resId, View.OnClickListener cb,
            int imageId) {
        ImageButton button = getImageButtonOrError(buttonId, resId);
        button.setOnClickListener(cb);
        button.setEnabled(true);
        button.setImageResource(imageId);
    }

    /**
     * Enable a known button with a click listener. Sets the button visible.
     */
    public void enablePushButton(int buttonId, int resId, View.OnClickListener cb) {
        ImageButton button = getImageButtonOrError(buttonId, resId);
        button.setOnClickListener(cb);
        button.setEnabled(true);
    }

    /**
     * Sets a button in its disabled (greyed out) state.
     */
    public void disableButton(int buttonId, int resId) {
        MultiToggleImageButton button = getButtonOrError(buttonId, resId);
        disableButton(button);
    }

    private void disableButton(MultiToggleImageButton button) {
        button.setEnabled(false);
        button.setVisibility(View.VISIBLE);
    }

    /**
     * Hide a button by id.
     */
    public void hideButton(int buttonId, int resId) {
        MultiToggleImageButton button = getButtonOrError(buttonId, resId);
        button.setVisibility(View.INVISIBLE);
    }

    /**
     * Enable a flash button.
     */
    private void enableFlashButton(MultiToggleImageButton button,
            final ButtonCallback cb, int resIdImages) {

        if (!mSettingsManager.isCameraBackFacing()) {
            disableButton(button);
            return;
        }

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

        button.setEnabled(true);
    }

    /**
     * Enable video torch button
     */
    private void enableTorchButton(MultiToggleImageButton button,
            final ButtonCallback cb, int resIdImages) {

        if (!mSettingsManager.isCameraBackFacing()) {
            disableButton(button);
            return;
        }

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

        button.setEnabled(true);
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

        button.setEnabled(true);
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

        button.setEnabled(true);
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

        button.setEnabled(true);
    }

}