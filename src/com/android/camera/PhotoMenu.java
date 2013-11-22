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

import android.content.res.Resources;
import android.hardware.Camera.Parameters;
import android.util.Log;
import android.view.View;

import com.android.camera.settings.SettingsManager;
import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.CountdownTimerPopup;
import com.android.camera.ui.ListPrefSettingPopup;
import com.android.camera.ui.PieItem;
import com.android.camera.ui.PieItem.OnClickListener;
import com.android.camera.ui.PieRenderer;
import com.android.camera2.R;

import java.util.Locale;

public class PhotoMenu extends PieController
        implements CountdownTimerPopup.Listener,
        ListPrefSettingPopup.Listener {
    private static String TAG = "PhotoMenu";

    private final String mSettingOff;

    private PhotoUI mUI;
    private AbstractSettingPopup mPopup;
    private CameraActivity mActivity;

    public PhotoMenu(CameraActivity activity, PhotoUI ui, PieRenderer pie) {
        super(activity, pie);
        mUI = ui;
        mSettingOff = activity.getString(R.string.setting_off_value);
        mActivity = activity;
    }

    private int getPrefIndex(ListPreference pref) {
        SettingsManager settingsManager = mActivity.getSettingsManager();
        String value = settingsManager.getValueFromPreference(pref);
        return pref.findIndexOfValue(value);
    }

    public void initialize(PreferenceGroup group) {
        super.initialize(group);
        mPopup = null;
        PieItem item = null;
        final Resources res = mActivity.getResources();
        Locale locale = res.getConfiguration().locale;
        // The order is from left to right in the menu.

        // HDR.
        if (group.findPreference(CameraSettings.KEY_CAMERA_HDR) != null) {
            item = makeSwitchItem(CameraSettings.KEY_CAMERA_HDR, true);
            mRenderer.addItem(item);
        }
        // Exposure compensation.
        if (group.findPreference(CameraSettings.KEY_EXPOSURE) != null) {
            item = makeItem(CameraSettings.KEY_EXPOSURE);
            item.setLabel(res.getString(R.string.pref_exposure_label));
            mRenderer.addItem(item);
        }
        // More settings.
        PieItem more = makeItem(R.drawable.ic_settings_holo_light);
        more.setLabel(res.getString(R.string.camera_menu_more_label));
        mRenderer.addItem(more);

        // Countdown timer.
        final ListPreference ctpref =
                group.findPreference(CameraSettings.KEY_TIMER);
        final ListPreference beeppref =
                group.findPreference(CameraSettings.KEY_TIMER_SOUND_EFFECTS);
        item = makeItem(R.drawable.ic_timer);
        item.setLabel(res.getString(R.string.pref_camera_timer_title).toUpperCase(locale));
        item.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(PieItem item) {
                CountdownTimerPopup timerPopup =
                        (CountdownTimerPopup) mActivity.getLayoutInflater().inflate(
                                R.layout.countdown_setting_popup, null, false);
                timerPopup.initialize(ctpref, beeppref);
                timerPopup.setSettingChangedListener(PhotoMenu.this);
                mUI.dismissPopup();
                mPopup = timerPopup;
                mUI.showPopup(mPopup);
            }
        });
        more.addItem(item);
        // White balance.
        if (group.findPreference(CameraSettings.KEY_WHITE_BALANCE) != null) {
            item = makeItem(CameraSettings.KEY_WHITE_BALANCE);
            item.setLabel(res.getString(R.string.pref_camera_whitebalance_label));
            more.addItem(item);
        }
        // Scene mode.
        if (group.findPreference(CameraSettings.KEY_SCENE_MODE) != null) {
            IconListPreference pref = (IconListPreference) group.findPreference(
                    CameraSettings.KEY_SCENE_MODE);
            pref.setUseSingleIcon(true);
            item = makeItem(CameraSettings.KEY_SCENE_MODE);
            more.addItem(item);
        }

        final ToggleImageButton cameraToggle
                = (ToggleImageButton) mActivity.findViewById(R.id.camera_toggle_button);
        final MultiToggleImageButton flashToggle
                = (MultiToggleImageButton) mActivity.findViewById(R.id.flash_toggle_button);
        final ToggleImageButton hdrPlusToggle
                = (ToggleImageButton) mActivity.findViewById(R.id.hdr_plus_toggle_button);

        final ListPreference pref = mPreferenceGroup.findPreference(CameraSettings.KEY_CAMERA_ID);
        if (pref != null) {
            int selectedPref = Integer.parseInt(mActivity.getSettingsManager()
                    .getValueFromPreference(pref));
            cameraToggle.setState(selectedPref, false);
            flashToggle.setVisibility(selectedPref == 0 ? View.VISIBLE : View.INVISIBLE);
            cameraToggle.setOnStateChangeListener(new ToggleImageButton.OnStateChangeListener() {
                    @Override
                    public void stateChanged(View view, boolean state) {
                        // Find next camera
                        int index = getPrefIndex(pref);
                        CharSequence[] values = pref.getEntryValues();
                        index = (index + 1) % values.length;
                        mActivity.getSettingsManager()
                                .setValueIndexFromPreference(pref, index);
                        mListener.onCameraPickerClicked(index);
                        flashToggle.setVisibility(index == 0 ? View.VISIBLE : View.INVISIBLE);
                    }
            });
        }

        final ListPreference flashPref
                = mPreferenceGroup.findPreference(CameraSettings.KEY_FLASH_MODE);
        if (flashPref != null) {
            // Set initial state
            int index = getPrefIndex(flashPref);
            flashToggle.setState(index, false);
            flashToggle.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener() {
                    @Override
                    public void stateChanged(View view, int state) {
                        mActivity.getSettingsManager()
                                .setValueIndexFromPreference(flashPref, state);
                        onSettingChanged(flashPref);
                    }
            });
        }

        // HDR+ (GCam).
        final ListPreference hdrPlusPref
                = group.findPreference(CameraSettings.KEY_CAMERA_HDR_PLUS);
        if (hdrPlusPref != null) {
            String prefValue = mActivity.getSettingsManager().getValueFromPreference(hdrPlusPref);
            int index = hdrPlusPref.findIndexOfValue(prefValue);
            hdrPlusToggle.setState(index, false);
            hdrPlusToggle.setOnStateChangeListener(new ToggleImageButton.OnStateChangeListener() {
                @Override
                public void stateChanged(View view, boolean state) {
                    // Find next camera
                    int index = getPrefIndex(hdrPlusPref);
                    CharSequence[] values = hdrPlusPref.getEntryValues();
                    index = (index + 1) % values.length;
                    mActivity.getSettingsManager()
                            .setValueIndexFromPreference(hdrPlusPref, index);
                    onSettingChanged(hdrPlusPref);
                }
            });
        }
    }

    @Override
    // Hit when an item in a popup gets selected
    public void onListPrefChanged(ListPreference pref) {
        if (mPopup != null) {
            mUI.dismissPopup();
        }
        onSettingChanged(pref);
    }

    public void popupDismissed() {
        if (mPopup != null) {
            mPopup = null;
        }
    }

    // Return true if the preference has the specified key but not the value.
    private boolean notSame(ListPreference pref, String key, String value) {
        SettingsManager settingsManager = mActivity.getSettingsManager();
        String prefValue = settingsManager.getValueFromPreference(pref);
        return (key.equals(pref.getKey()) && !value.equals(prefValue));
    }

    private void setPreference(String key, String value) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        SettingsManager settingsManager = mActivity.getSettingsManager();
        String prefValue = settingsManager.getValueFromPreference(pref);
        if (pref != null && !value.equals(prefValue)) {
            settingsManager.setValueFromPreference(pref, value);
            reloadPreferences();
        }
    }

    @Override
    public void onSettingChanged(ListPreference pref) {
        // Reset the scene mode if HDR is set to on. Reset HDR if scene mode is
        // set to non-auto.
        if (notSame(pref, CameraSettings.KEY_CAMERA_HDR, mSettingOff)) {
            setPreference(CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_AUTO);
        } else if (notSame(pref, CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_AUTO)) {
            setPreference(CameraSettings.KEY_CAMERA_HDR, mSettingOff);
        }
        super.onSettingChanged(pref);
    }
}
