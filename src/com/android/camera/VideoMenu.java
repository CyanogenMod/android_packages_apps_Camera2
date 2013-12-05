/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.util.Log;
import android.view.View;

import com.android.camera.settings.SettingsManager;
import com.android.camera2.R;

public class VideoMenu {
    private static String TAG = "CAM_VideoMenu";

    private CameraActivity mActivity;
    private VideoMenuListener mListener;

    // TODO: remove this class and generalize button
    // initialization into a global manager/factory.
    public VideoMenu(CameraActivity activity, VideoMenuListener listener) {
        mActivity = activity;
        mListener = listener;
    }

    public interface VideoMenuListener {
        public void onCameraPickerClicked(int cameraId);
    }

    public void initialize() {
        int index;
        final SettingsManager settingsManager = mActivity.getSettingsManager();

        final MultiToggleImageButton flashToggle
                = (MultiToggleImageButton) mActivity.findViewById(R.id.flash_toggle_button);
        index = settingsManager.getStringValueIndex(SettingsManager.SETTING_FLASH_MODE);
        flashToggle.overrideImageIds(R.array.video_flashmode_icons);
        flashToggle.setState(index, false);
        flashToggle.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener() {
                @Override
                public void stateChanged(View view, int state) {
                    settingsManager.setStringValueIndex(SettingsManager.SETTING_FLASH_MODE, state);
                    }
            });

        final ToggleImageButton cameraToggle
                = (ToggleImageButton) mActivity.findViewById(R.id.camera_toggle_button);
        index = settingsManager.getStringValueIndex(SettingsManager.SETTING_CAMERA_ID);
        cameraToggle.setState(index, false);
        flashToggle.setVisibility(index == 0 ? View.VISIBLE : View.INVISIBLE);
        cameraToggle.setOnStateChangeListener(new ToggleImageButton.OnStateChangeListener() {
                @Override
                public void stateChanged(View view, boolean state) {
                    settingsManager.setStringValueIndex(SettingsManager.SETTING_CAMERA_ID,
                        (state ? 1 : 0));
                    int cameraId = Integer.parseInt(settingsManager.get(
                        SettingsManager.SETTING_CAMERA_ID));
                    mListener.onCameraPickerClicked(cameraId);
                    flashToggle.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                }
            });

        final ToggleImageButton hdrPlusToggle
                = (ToggleImageButton) mActivity.findViewById(R.id.hdr_plus_toggle_button);
        hdrPlusToggle.setVisibility(View.INVISIBLE);
    }
}
