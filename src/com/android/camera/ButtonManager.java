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

import com.android.camera.settings.SettingsManager;

/**
 * A  class for generating pre-initialized
 * {@link #android.widget.ImageButton}s.
 */
public class ButtonManager {

    public static final int BUTTON_FLASH = 0;
    public static final int BUTTON_TORCH = 1;
    public static final int BUTTON_CAMERA = 2;
    public static final int BUTTON_HDRPLUS = 3;
    public static final int BUTTON_CANCEL = 4;
    public static final int BUTTON_DONE = 5;
    public static final int BUTTON_RETAKE = 6;
    public static final int BUTTON_REVIEW = 7;

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
            default:
                throw new IllegalArgumentException("button not known by id=" + buttonId);
        }
        button.setVisibility(View.VISIBLE);
    }

    public void enablePushButton(int buttonId, int resId, View.OnClickListener cb) {
        ImageButton button = getImageButtonOrError(buttonId, resId);
        button.setOnClickListener(cb);
        button.setEnabled(true);
    }

    /**
     * Disable a known button by id.
     */
    public void disableButton(int buttonId, int resId) {
        MultiToggleImageButton button = getButtonOrError(buttonId, resId);
        button.setEnabled(false);
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
        if (index >= 0) {
            button.setState(index, false);
        }
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
        if (index >= 0) {
            button.setState(index, false);
        }
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
        if (index >= 0) {
            button.setState(index, false);
        }

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
        if (index >= 0) {
            button.setState(index, false);
        }

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
}