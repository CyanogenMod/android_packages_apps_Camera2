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

import android.content.Context;
import android.view.LayoutInflater;

import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.ListPrefSettingPopup;
import com.android.camera.ui.MoreSettingPopup;
import com.android.camera.ui.PieItem;
import com.android.camera.ui.PieItem.OnClickListener;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.TimeIntervalPopup;
import com.android.camera2.R;

public class VideoMenu extends PieController {

    private static String TAG = "CAM_VideoMenu";

    private VideoUI mUI;
    private CameraActivity mActivity;

    public VideoMenu(CameraActivity activity, VideoUI ui, PieRenderer pie) {
        super(activity, pie);
        mUI = ui;
        mActivity = activity;
    }

    public void initialize(PreferenceGroup group) {
        super.initialize(group);
        PieItem item = null;
        // camera switcher
        if (group.findPreference(CameraSettings.KEY_CAMERA_ID) != null) {
            item = makeItem(R.drawable.ic_switch_back);
            IconListPreference lpref = (IconListPreference) group.findPreference(
                    CameraSettings.KEY_CAMERA_ID);
            item.setLabel(lpref.getLabel());
            item.setImageResource(mActivity,
                    ((IconListPreference) lpref).getIconIds()
                    [lpref.findIndexOfValue(lpref.getValue())]);

            final PieItem fitem = item;
            item.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(PieItem item) {
                    // Find the index of next camera.
                    ListPreference pref =
                            mPreferenceGroup.findPreference(CameraSettings.KEY_CAMERA_ID);
                    if (pref != null) {
                        int index = pref.findIndexOfValue(pref.getValue());
                        CharSequence[] values = pref.getEntryValues();
                        index = (index + 1) % values.length;
                        int newCameraId = Integer.parseInt((String) values[index]);
                        fitem.setImageResource(mActivity,
                                ((IconListPreference) pref).getIconIds()[index]);
                        fitem.setLabel(pref.getLabel());
                        mListener.onCameraPickerClicked(newCameraId);
                    }
                }
            });
            mRenderer.addItem(item);
        }
        // flash
        if (group.findPreference(CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE) != null) {
            item = makeItem(CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE);
            mRenderer.addItem(item);
        }
    }
}
