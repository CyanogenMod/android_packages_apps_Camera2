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
import android.content.Context;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.ImageButton;

import com.android.camera.settings.SettingsManager;
import com.android.camera2.R;

/**
 * A  class for generating pre-initialized
 * {@link #android.widget.ImageButton}s.
 */
public class ButtonManager {

    /**
     * Get a new manager instance. Modules should not share
     * a button manager instance because modules reference
     * different button instances.
     */
    public static ButtonManager getInstance(CameraActivity activity) {
        return new ButtonManager(activity);
    }

    public static final int BUTTON_FLASH = 0;
    public static final int BUTTON_CAMERA = 1;
    public static final int BUTTON_HDRPLUS = 2;

    /** A private store of uninitialized buttons. */
    private static SparseArray<MultiToggleImageButton> mButtonCache;
    /** A reference to the application's settings manager. */
    private SettingsManager mSettingsManager;

    private ButtonManager(CameraActivity activity) {
        mSettingsManager = activity.getSettingsManager();
        mButtonCache = getButtonReferences(activity);
    }

    /** Store uninitialized references to buttons with known keys. */
    private static SparseArray<MultiToggleImageButton> getButtonReferences(Activity activity) {
        SparseArray<MultiToggleImageButton> cache = new SparseArray<MultiToggleImageButton>();

        MultiToggleImageButton flashToggle
            = (MultiToggleImageButton) activity.findViewById(R.id.flash_toggle_button);
        cache.put(BUTTON_FLASH, flashToggle);

        MultiToggleImageButton cameraToggle
            = (MultiToggleImageButton) activity.findViewById(R.id.camera_toggle_button);
        cache.put(BUTTON_CAMERA, cameraToggle);

        MultiToggleImageButton hdrPlusToggle
            = (MultiToggleImageButton) activity.findViewById(R.id.hdr_plus_toggle_button);
        cache.put(BUTTON_HDRPLUS, hdrPlusToggle);
        return cache;
    }

    /**
     * A callback executed in the state listener of a button.
     * Used by a module to set specific behavior when button's
     * state changes.
     */
    public interface ButtonCallback {
        public void onStateChanged(int state);
    }


    /**
     * Initialize a known button by id, with a state change callback and
     * a resource id that points to an array of drawables.
     */
    public ImageButton getButton(int id, ButtonCallback cb, int resIdImages) {
        switch (id) {
            case BUTTON_FLASH:
                return getFlashButton(cb, resIdImages);
            case BUTTON_CAMERA:
                return getCameraButton(cb, resIdImages);
            case BUTTON_HDRPLUS:
                return getHdrPlusButton(cb, resIdImages);
            default:
                throw new IllegalArgumentException("Button not known by id=" + id);
        }
    }

    /**
     * Initialize a flash button.
     */
    private ImageButton getFlashButton(final ButtonCallback cb, int resIdImages) {
        MultiToggleImageButton flashToggle
            = (MultiToggleImageButton) mButtonCache.get(BUTTON_FLASH);

        if (flashToggle == null) {
            throw new IllegalStateException("Flash button could not be initialized.");
        }

        if (resIdImages > 0) {
            flashToggle.overrideImageIds(resIdImages);
        }
        int index = mSettingsManager.getStringValueIndex(SettingsManager.SETTING_FLASH_MODE);
        flashToggle.setState(index, false);
        flashToggle.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener() {
                @Override
                public void stateChanged(View view, int state) {
                    mSettingsManager.setStringValueIndex(SettingsManager.SETTING_FLASH_MODE, state);
                    if (cb != null) {
                        cb.onStateChanged(state);
                    }
                }
            });

        return flashToggle;
    }

    /**
     * Initialize a camera button.
     */
    private ImageButton getCameraButton(final ButtonCallback cb, int resIdImages) {
        MultiToggleImageButton cameraToggle
            = (MultiToggleImageButton) mButtonCache.get(BUTTON_CAMERA);
        final MultiToggleImageButton flashToggle
            = (MultiToggleImageButton) mButtonCache.get(BUTTON_FLASH);

        if (cameraToggle == null) {
            throw new IllegalStateException("Camera button could not be initialized.");
        }

        if (resIdImages > 0) {
            cameraToggle.overrideImageIds(resIdImages);
        }
        int index = mSettingsManager.getStringValueIndex(SettingsManager.SETTING_CAMERA_ID);
        cameraToggle.setState(index, false);
        cameraToggle.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener() {
                @Override
                public void stateChanged(View view, int state) {
                    mSettingsManager.setStringValueIndex(SettingsManager.SETTING_CAMERA_ID, state);
                    int cameraId = Integer.parseInt(mSettingsManager.get(
                        SettingsManager.SETTING_CAMERA_ID));
                    if (cb != null) {
                        cb.onStateChanged(cameraId);
                    }
                    if (flashToggle != null) {
                        flashToggle.setVisibility(state == 0 ? View.VISIBLE : View.INVISIBLE);
                    }
                }
            });

        if (flashToggle != null) {
            flashToggle.setVisibility(index == 0 ? View.VISIBLE : View.INVISIBLE);
        }

        return cameraToggle;
    }

    /**
     * Initialize an hdr plus button.
     */
    private ImageButton getHdrPlusButton(final ButtonCallback cb, int resIdImages) {
        MultiToggleImageButton hdrPlusToggle
            = (MultiToggleImageButton) mButtonCache.get(BUTTON_HDRPLUS);

        if (hdrPlusToggle == null) {
            throw new IllegalStateException("Hdr plus button could not be initialized.");
        }

        if (resIdImages > 0) {
            hdrPlusToggle.overrideImageIds(resIdImages);
        }
        int index = mSettingsManager.getStringValueIndex(SettingsManager.SETTING_CAMERA_HDR);
        hdrPlusToggle.setState(index, false);
        hdrPlusToggle.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener() {
                @Override
                public void stateChanged(View view, int state) {
                    mSettingsManager.setStringValueIndex(SettingsManager.SETTING_CAMERA_HDR, state);
                    if (cb != null) {
                        cb.onStateChanged(state);
                    }
                }
            });

        return hdrPlusToggle;
    }
}