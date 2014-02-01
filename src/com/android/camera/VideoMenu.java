/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013 The CyanogenMod Project
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
import android.view.LayoutInflater;

import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.ListPrefSettingPopup;
import com.android.camera.ui.MoreSettingPopup;
import com.android.camera.ui.PieItem;
import com.android.camera.ui.PieItem.OnClickListener;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.TimeIntervalPopup;
import com.android.camera2.R;

import java.util.Locale;

public class VideoMenu extends PieController
        implements MoreSettingPopup.Listener,
        ListPrefSettingPopup.Listener,
        TimeIntervalPopup.Listener {

    private static String TAG = "CAM_VideoMenu";

    private VideoUI mUI;
    private String[] mOtherKeys;
    private AbstractSettingPopup mPopup;

    private static final int POPUP_NONE = 0;
    private static final int POPUP_FIRST_LEVEL = 1;
    private static final int POPUP_SECOND_LEVEL = 2;
    private int mPopupStatus;
    private CameraActivity mActivity;

    public VideoMenu(CameraActivity activity, VideoUI ui, PieRenderer pie) {
        super(activity, pie);
        mUI = ui;
        mActivity = activity;
    }

    public void initialize(PreferenceGroup group) {
        super.initialize(group);
        mPopup = null;
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
        // hdr
        if (group.findPreference(CameraSettings.KEY_VIDEO_HDR) != null) {
            item = makeSwitchItem(CameraSettings.KEY_VIDEO_HDR, true);
            enhance.addItem(item);
        }
        // beautify
        if (group.findPreference(CameraSettings.KEY_BEAUTY_MODE) != null) {
            item = makeSwitchItem(CameraSettings.KEY_BEAUTY_MODE, true);
            enhance.addItem(item);
        }
        // color effect
        final ListPreference colorPref =
                group.findPreference(CameraSettings.KEY_VIDEOCAMERA_COLOR_EFFECT);
        if (colorPref != null) {
            item = makeItem(R.drawable.ic_tint);
            item.setLabel(res.getString(
                    R.string.pref_camera_coloreffect_title).toUpperCase(locale));
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(PieItem item) {
                    ListPrefSettingPopup popup =
                            (ListPrefSettingPopup) mActivity.getLayoutInflater().inflate(
                            R.layout.list_pref_setting_popup, null, false);
                    popup.initialize(colorPref);
                    popup.setSettingChangedListener(VideoMenu.this);
                    mUI.dismissPopup();
                    mPopup = popup;
                    mPopupStatus = POPUP_SECOND_LEVEL;
                    mUI.showPopup(mPopup);
                }
            });
            enhance.addItem(item);
        }

        // more settings
        PieItem more = makeItem(R.drawable.ic_settings_holo_light);
        more.setLabel(res.getString(R.string.camera_menu_settings_label));
        mRenderer.addItem(more);
        // flash
        if (group.findPreference(CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE) != null) {
            item = makeItem(CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE);
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
        // time lapse
        final ListPreference tlPref =
                group.findPreference(CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL);
        item = makeItem(R.drawable.ic_timer);
        item.setLabel(res.getString(
                R.string.pref_video_time_lapse_frame_interval_title).toUpperCase(locale));
        item.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(PieItem item) {
                TimeIntervalPopup timeInterval =
                        (TimeIntervalPopup) mActivity.getLayoutInflater().inflate(
                        R.layout.time_interval_popup, null, false);
                timeInterval.initialize((IconListPreference) tlPref);
                timeInterval.setSettingChangedListener(VideoMenu.this);
                mUI.dismissPopup();
                mPopup = timeInterval;
                mPopupStatus = POPUP_SECOND_LEVEL;
                mUI.showPopup(mPopup);
            }
        });
        more.addItem(item);
        // video size
        final ListPreference sizePref = group.findPreference(CameraSettings.KEY_VIDEO_QUALITY);
        if (sizePref != null) {
            item = makeItem(R.drawable.ic_imagesize);
            item.setLabel(res.getString(
                    R.string.pref_video_quality_title).toUpperCase(locale));
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(PieItem item) {
                    ListPrefSettingPopup popup =
                            (ListPrefSettingPopup) mActivity.getLayoutInflater().inflate(
                            R.layout.list_pref_setting_popup, null, false);
                    popup.initialize(sizePref);
                    popup.setSettingChangedListener(VideoMenu.this);
                    mUI.dismissPopup();
                    mPopup = popup;
                    mPopupStatus = POPUP_SECOND_LEVEL;
                    mUI.showPopup(mPopup);
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
        // extra settings popup
        mOtherKeys = new String[] {
                CameraSettings.KEY_STORAGE,
                CameraSettings.KEY_VIDEO_ENCODER,
                CameraSettings.KEY_AUDIO_ENCODER,
                CameraSettings.KEY_FOCUS_TIME,
                CameraSettings.KEY_JPEG_QUALITY
        };
        item = makeItem(R.drawable.ic_settings_holo_light);
        item.setLabel(res.getString(R.string.camera_menu_more_label).toUpperCase(locale));
        item.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(PieItem item) {
                if (mPopup == null || mPopupStatus != POPUP_FIRST_LEVEL) {
                    LayoutInflater inflater = mActivity.getLayoutInflater();
                    MoreSettingPopup popup = (MoreSettingPopup) inflater.inflate(
                            R.layout.more_setting_popup, null, false);
                    popup.initialize(mPreferenceGroup, mOtherKeys);
                    popup.setSettingChangedListener(VideoMenu.this);
                    mPopup = popup;
                    mPopupStatus = POPUP_FIRST_LEVEL;
                }
                mUI.showPopup(mPopup);
            }
        });
        more.addItem(item);
    }

    @Override
    // Hit when an item in the second-level popup gets selected
    public void onListPrefChanged(ListPreference pref) {
        if (mPopup != null) {
            if (mPopupStatus == POPUP_SECOND_LEVEL) {
                mUI.dismissPopup();
            }
        }
        onSettingChanged(pref);
    }

    public void popupDismissed() {
        // if the 2nd level popup gets dismissed
        if (mPopup != null) {
            if (mPopupStatus == POPUP_SECOND_LEVEL) {
                mPopup = null;
            }
        }
    }

    @Override
    // Hit when an item in the first-level popup gets selected, then bring up
    // the second-level popup
    public void onPreferenceClicked(ListPreference pref) {
        if (mPopupStatus != POPUP_FIRST_LEVEL) return;

        LayoutInflater inflater = mActivity.getLayoutInflater();
        ListPrefSettingPopup basic = (ListPrefSettingPopup) inflater.inflate(
                R.layout.list_pref_setting_popup, null, false);
        basic.initialize(pref);
        basic.setSettingChangedListener(this);
        mUI.dismissPopup();
        mPopup = basic;
        mUI.showPopup(mPopup);
        mPopupStatus = POPUP_SECOND_LEVEL;
    }

}
