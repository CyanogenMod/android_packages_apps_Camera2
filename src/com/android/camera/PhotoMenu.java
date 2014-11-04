/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013-2014 The CyanogenMod Project
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
import android.content.res.Resources;
import android.hardware.Camera.Parameters;
import android.view.LayoutInflater;
import android.widget.Toast;

import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.CountdownTimerPopup;
import com.android.camera.ui.ListPrefSettingPopup;
import com.android.camera.ui.MoreSettingPopup;
import com.android.camera.ui.PieItem;
import com.android.camera.ui.PieItem.OnClickListener;
import com.android.camera.ui.PieRenderer;

import com.android.camera2.R;

import java.util.Locale;

public class PhotoMenu extends PieController
        implements MoreSettingPopup.Listener,
        CountdownTimerPopup.Listener,
        ListPrefSettingPopup.Listener {
    private static String TAG = "CAM_photomenu";

    private final String mSettingOff;

    private PhotoUI mUI;
    private String[] mSettingsKeys;
    private AbstractSettingPopup mPopup;
    private MoreSettingPopup mSettingsPopup;

    private static final int POPUP_NONE = 0;
    private static final int POPUP_FIRST_LEVEL = 1;
    private static final int POPUP_SECOND_LEVEL = 2;
    private int mPopupStatus;

    private CameraActivity mActivity;

    public PhotoMenu(CameraActivity activity, PhotoUI ui, PieRenderer pie) {
        super(activity, pie);
        mUI = ui;
        mSettingOff = activity.getString(R.string.setting_off_value);
        mActivity = activity;
    }

    public void initialize(PreferenceGroup group) {
        super.initialize(group);
        mPopup = null;
        mSettingsPopup = null;
        mPopupStatus = POPUP_NONE;
        PieItem item = null;
        final Resources res = mActivity.getResources();
        Locale locale = res.getConfiguration().locale;
        // the order is from left to right in the menu

        // exposure compensation
        if (group.findPreference(CameraSettings.KEY_EXPOSURE) != null) {
            item = makeItem(CameraSettings.KEY_EXPOSURE);
            item.setLabel(res.getString(R.string.pref_exposure_label));
            mRenderer.addItem(item);
        }
        // enhance
        PieItem enhance = makeItem(R.drawable.ic_enhance);
        enhance.setLabel(res.getString(R.string.camera_menu_enhance_label));
        mRenderer.addItem(enhance);

        // HDR+ (GCam).
        if (group.findPreference(CameraSettings.KEY_CAMERA_HDR_PLUS) != null) {
            item = makeSwitchItem(CameraSettings.KEY_CAMERA_HDR_PLUS, true);
        }
        // hdr
        if (group.findPreference(CameraSettings.KEY_CAMERA_HDR) != null) {
            item = makeSwitchItem(CameraSettings.KEY_CAMERA_HDR, true);
            enhance.addItem(item);
        }
        // beautify
        if (group.findPreference(CameraSettings.KEY_BEAUTY_MODE) != null) {
            item = makeSwitchItem(CameraSettings.KEY_BEAUTY_MODE, true);
            enhance.addItem(item);
        }
        // slow shutter
        final ListPreference slowShutterPref =
                group.findPreference(CameraSettings.KEY_SLOW_SHUTTER);
        if (slowShutterPref != null) {
            item = makeListItem(CameraSettings.KEY_SLOW_SHUTTER);
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(PieItem item) {
                    showListPopup(slowShutterPref);
                }
            });
            enhance.addItem(item);
        }
        // auto scene detection
        if (group.findPreference(CameraSettings.KEY_ASD) != null) {
            item = makeSwitchItem(CameraSettings.KEY_ASD, true);
            enhance.addItem(item);
        }
        // color effect
        final ListPreference colorPref = group.findPreference(CameraSettings.KEY_COLOR_EFFECT);
        if (colorPref != null) {
            item = makeListItem(CameraSettings.KEY_COLOR_EFFECT);
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(PieItem item) {
                    showListPopup(colorPref);
                }
            });
            enhance.addItem(item);
        }

        // more settings
        PieItem more = makeItem(R.drawable.ic_settings_holo_light);
        more.setLabel(res.getString(R.string.camera_menu_settings_label));
        mRenderer.addItem(more);
        // flash
        if (group.findPreference(CameraSettings.KEY_FLASH_MODE) != null) {
            item = makeItem(CameraSettings.KEY_FLASH_MODE);
            item.setLabel(res.getString(R.string.pref_camera_flashmode_label));
            mRenderer.addItem(item);
        }
        // camera switcher
        if (group.findPreference(CameraSettings.KEY_CAMERA_ID) != null) {
            item = makeSwitchItem(CameraSettings.KEY_CAMERA_ID, false);
            final PieItem fitem = item;
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(PieItem item) {
                    // Find the index of next camera.
                    ListPreference pref = mPreferenceGroup
                            .findPreference(CameraSettings.KEY_CAMERA_ID);
                    if (pref != null) {
                        int index = pref.findIndexOfValue(pref.getValue());
                        CharSequence[] values = pref.getEntryValues();
                        index = (index + 1) % values.length;
                        pref.setValueIndex(index);
                        mListener.onCameraPickerClicked(index);
                    }
                    updateItem(fitem, CameraSettings.KEY_CAMERA_ID);
                }
            });
            mRenderer.addItem(item);
        }
        // location
        if (group.findPreference(CameraSettings.KEY_RECORD_LOCATION) != null) {
            item = makeSwitchItem(CameraSettings.KEY_RECORD_LOCATION, true);
            more.addItem(item);
            if (mActivity.isSecureCamera()) {
                // Prevent location preference from getting changed in secure camera mode
                item.setEnabled(false);
            }
        }
        // countdown timer
        final ListPreference ctpref = group.findPreference(CameraSettings.KEY_TIMER);
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
        // image size
        final ListPreference sizePref = group.findPreference(CameraSettings.KEY_PICTURE_SIZE);
        if (sizePref != null) {
            item = makeListItem(CameraSettings.KEY_PICTURE_SIZE);
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(PieItem item) {
                    showListPopup(sizePref);
                }
            });
            more.addItem(item);
        }
        // white balance
        if (group.findPreference(CameraSettings.KEY_WHITE_BALANCE) != null) {
            item = makeItem(CameraSettings.KEY_WHITE_BALANCE);
            item.setLabel(res.getString(R.string.pref_camera_whitebalance_label));
            more.addItem(item);
        }
        // scene mode
        final ListPreference scenePref = group.findPreference(CameraSettings.KEY_SCENE_MODE);
        if (scenePref != null) {
            item = makeListItem(CameraSettings.KEY_SCENE_MODE);
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(PieItem item) {
                    showListPopup(scenePref);
                }
            });
            enhance.addItem(item);
        }
        // extra settings popup
        mSettingsKeys = new String[] {
                CameraSettings.KEY_STORAGE,
                CameraSettings.KEY_POWER_SHUTTER,
                CameraSettings.KEY_MAX_BRIGHTNESS,
                CameraSettings.KEY_FOCUS_MODE,
                CameraSettings.KEY_FOCUS_TIME,
                CameraSettings.KEY_ISO,
                CameraSettings.KEY_JPEG_QUALITY,
                CameraSettings.KEY_AUTOEXPOSURE,
                CameraSettings.KEY_ANTIBANDING,
                CameraSettings.KEY_DENOISE,
                CameraSettings.KEY_BURST_MODE,
                CameraSettings.KEY_HISTOGRAM,
                CameraSettings.KEY_SUPERZOOM
        };
        PieItem settings = makeItem(R.drawable.ic_settings_holo_light);
        settings.setLabel(res.getString(R.string.camera_menu_more_label).toUpperCase(locale));
        settings.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(PieItem item) {
                if (mSettingsPopup == null || mPopupStatus != POPUP_FIRST_LEVEL) {
                    initializeSettingsPopup();
                    mPopupStatus = POPUP_FIRST_LEVEL;
                }
                mUI.showPopup(mSettingsPopup);
            }
        });
        more.addItem(settings);
        // burst mode
        final ListPreference burstPref = group.findPreference(CameraSettings.KEY_BURST_MODE);
        mUI.updateBurstModeIcon(Integer.valueOf(burstPref.getValue()));
    }

    public void popupDismissed() {
        if (mPopupStatus == POPUP_SECOND_LEVEL) {
            initializeSettingsPopup();
            mPopupStatus = POPUP_FIRST_LEVEL;
            mUI.showPopup(mSettingsPopup);
            if (mSettingsPopup != null) {
                mSettingsPopup = null;
            }
        } else {
            initializeSettingsPopup();
            if (mPopup != null) {
                mPopup = null;
            }
        }
    }

    @Override
    public void reloadPreferences() {
        super.reloadPreferences();
        if (mSettingsPopup != null) {
            mSettingsPopup.reloadPreference();
        }
    }

    @Override
    public void overrideSettings(final String ... keyvalues) {
        super.overrideSettings(keyvalues);
        if (mSettingsPopup == null) {
            initializeSettingsPopup();
        }
        mSettingsPopup.overrideSettings(keyvalues);
    }

    @Override
    // Hit when an item in a popup gets selected
    public void onListPrefChanged(ListPreference pref) {
        if (mPopup != null && mSettingsPopup != null) {
            mUI.dismissPopup();
        }
        onSettingChanged(pref);
    }

    @Override
    // Hit when an item in the first-level popup gets selected, then bring up
    // the second-level popup
    public void onPreferenceClicked(ListPreference pref) {
        if (mPopupStatus != POPUP_FIRST_LEVEL) return;

        ListPrefSettingPopup basic =
                (ListPrefSettingPopup) mActivity.getLayoutInflater().inflate(
                R.layout.list_pref_setting_popup, null, false);
        basic.initialize(pref);
        basic.setSettingChangedListener(this);
        mUI.dismissPopup();
        mPopup = basic;
        mUI.showPopup(mPopup);
        mPopupStatus = POPUP_SECOND_LEVEL;
    }

    // Initialize the second-level settings popup
    protected void initializeSettingsPopup() {
        MoreSettingPopup popup =
                (MoreSettingPopup) mActivity.getLayoutInflater().inflate(
                R.layout.more_setting_popup, null, false);
        popup.initialize(mPreferenceGroup, mSettingsKeys);
        popup.setSettingChangedListener(PhotoMenu.this);
        mSettingsPopup = popup;
    }

    // Show a popup options list
    protected void showListPopup(ListPreference pref) {
        ListPrefSettingPopup popup =
                (ListPrefSettingPopup) mActivity.getLayoutInflater().inflate(
                R.layout.list_pref_setting_popup, null, false);
        popup.initialize(pref);
        popup.setSettingChangedListener(PhotoMenu.this);
        mUI.dismissPopup();
        mPopup = popup;
        mUI.showPopup(mPopup);
    }

    // Return true if the preference has the specified key but not the value.
    private static boolean notSame(ListPreference pref, String key, String value) {
        return (key.equals(pref.getKey()) && !value.equals(pref.getValue()));
    }

    private void setPreference(String key, String value) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        if (pref != null && !value.equals(pref.getValue())) {
            pref.setValue(value);
            reloadPreferences();
        }
    }

    @Override
    public void onSettingChanged(ListPreference pref) {
        // Reset the scene mode if HDR is set to on. Reset HDR if scene mode is
        // set to non-auto. Also disable beautify when HDR is active.
        // Disable burst mode if it causes damage or doesn't make sense.
        if (notSame(pref, CameraSettings.KEY_CAMERA_HDR, mSettingOff)) {
            ListPreference scenePref =
                    mPreferenceGroup.findPreference(CameraSettings.KEY_SCENE_MODE);
            if (scenePref != null && notSame(scenePref, CameraSettings.KEY_SCENE_MODE,
                    Parameters.SCENE_MODE_AUTO)) {
                Toast.makeText(mActivity, R.string.hdr_enable_message, Toast.LENGTH_LONG).show();
            }
            setPreference(CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_AUTO);
            setPreference(CameraSettings.KEY_BEAUTY_MODE, mSettingOff);
            setPreference(CameraSettings.KEY_SLOW_SHUTTER, "0");
            setPreference(CameraSettings.KEY_ASD, mSettingOff);
            disableBurstMode();
        } else if (notSame(pref, CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_AUTO) ||
                   notSame(pref, CameraSettings.KEY_ASD, mSettingOff)) {
            setPreference(CameraSettings.KEY_SLOW_SHUTTER, "0");
            if (!notSame(pref, CameraSettings.KEY_ASD, mSettingOff)) {
                setPreference(CameraSettings.KEY_ASD, mSettingOff);
            }
            ListPreference hdrPref =
                    mPreferenceGroup.findPreference(CameraSettings.KEY_CAMERA_HDR);
            if (hdrPref != null && notSame(hdrPref, CameraSettings.KEY_CAMERA_HDR, mSettingOff)) {
                Toast.makeText(mActivity, R.string.scene_enable_message, Toast.LENGTH_LONG).show();
            }
            setPreference(CameraSettings.KEY_CAMERA_HDR, mSettingOff);
        } else if (notSame(pref, CameraSettings.KEY_BEAUTY_MODE, mSettingOff)) {
            setPreference(CameraSettings.KEY_CAMERA_HDR, mSettingOff);
            setPreference(CameraSettings.KEY_SLOW_SHUTTER, "0");
        } else if (notSame(pref, CameraSettings.KEY_SLOW_SHUTTER, "0")) {
            setPreference(CameraSettings.KEY_CAMERA_HDR, mSettingOff);
            setPreference(CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_AUTO);
            setPreference(CameraSettings.KEY_ASD, mSettingOff);
            setPreference(CameraSettings.KEY_BEAUTY_MODE, mSettingOff);
            disableBurstMode();
        } else if (notSame(pref, CameraSettings.KEY_TIMER, "0")) {
            disableBurstMode();
        } else if (pref.getKey().equals(CameraSettings.KEY_BURST_MODE)) {
            setPreference(CameraSettings.KEY_TIMER, "0");
            // Slow shutter is obviously incompatible, and HDR uses the ZSL burst
            // mechanism already on most cameras.
            setPreference(CameraSettings.KEY_CAMERA_HDR, mSettingOff);
            setPreference(CameraSettings.KEY_SLOW_SHUTTER, mSettingOff);
            mUI.updateBurstModeIcon(Integer.valueOf(pref.getValue()));
        }
        super.onSettingChanged(pref);
    }

    private void disableBurstMode() {
        setPreference(CameraSettings.KEY_BURST_MODE, "1");
        mUI.updateBurstModeIcon(1);
    }

}
