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

package com.android.camera.ui;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera.Size;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;

import com.android.camera.CameraActivity;
import com.android.camera2.R;

import java.util.List;

/**
 * SettingsView class displays all global settings in the form
 * of a list.  Each setting launches a dialog or a toggle.
 */
public class SettingsView extends ListView {
    private static final String TAG = "SettingsView";

    private static final int LOCATION_SETTING = 0;
    private static final int PICTURE_SIZE_SETTING = 1;
    private static final int VIDEO_RES_SETTING = 2;
    private static final int DEFAULT_CAMERA_SETTING = 3;

    private static final int MODE_TOTAL = 4;
    private static final float ROWS_TO_SHOW_IN_LANDSCAPE = 4.5f;

    private final int mHeadingIconResId = R.drawable.ic_settings_normal;
    private final int mHeadingTextResId = R.string.mode_settings;
    private final int mHeadingIconBlockColor = R.color.settings_mode_color;

    private final int[] mSettingsTextResId = {
        R.string.setting_location, R.string.setting_picture_size,
        R.string.setting_video_resolution, R.string.setting_default_camera,};

    private Context mContext;
    private SettingsViewListener mListener;
    private AlertDialog.Builder mDialogBuilder;

    private ArrayAdapter mAdapter;
    private CharSequence[] mSettingsItems;

    public SettingsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mSettingsItems = buildItems();
        mAdapter = new ArrayAdapter(mContext, R.layout.settings_selector, mSettingsItems);
        setAdapter(mAdapter);

        setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int pos, long id) {
                onSettingSelected(pos);
            }
            });
    }

    public interface SettingsViewListener {
        public void setLocation(boolean on);

        public String[] getSupportedPictureSizeEntries();
        public void setPictureSize(String size);

        public String[] getSupportedVideoQualityEntries();
        public void setVideoQuality(String resolution);

        public void setDefaultCamera(int index);
    }

    public void setSettingsListener(SettingsViewListener listener) {
        mListener = listener;
    }

    private CharSequence[] buildItems() {
        int total = mSettingsTextResId.length;
        CharSequence[] items = new String[total];

        for (int i = 0; i < total; i++) {
            CharSequence text = getResources().getText(mSettingsTextResId[i]);
            items[i] = text;
        }
        return items;
    }

    private void onSettingSelected(int settingIndex) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

        switch (settingIndex) {
            case LOCATION_SETTING:
                mDialogBuilder = getLocationAlertBuilder(builder, mListener);
                break;
            case PICTURE_SIZE_SETTING:
                mDialogBuilder = getPictureSizeAlertBuilder(builder, mContext, mListener);
                break;
            case VIDEO_RES_SETTING:
                mDialogBuilder = getVideoQualityAlertBuilder(builder, mContext, mListener);
                break;
            case DEFAULT_CAMERA_SETTING:
                mDialogBuilder = getDefaultCameraAlertBuilder(builder, mContext, mListener);
                break;
            default:
                mDialogBuilder = null;
        }
        if (mDialogBuilder != null) {
            AlertDialog alert = mDialogBuilder.create();
            alert.show();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    /**
     * Updates an AlertDialog.Builder for choosing whether to include
     * location on captures.
     */
    public static AlertDialog.Builder getLocationAlertBuilder(AlertDialog.Builder builder,
            final SettingsViewListener listener) {

        builder.setTitle(R.string.remember_location_title)
        .setPositiveButton(R.string.remember_location_yes,
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int arg1) {
                    listener.setLocation(true);
                }
            })
        .setNegativeButton(R.string.remember_location_no,
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int arg1) {
                    listener.setLocation(false);
                }
            });

        return builder;
    }

    /**
     * Updates an AlertDialog.Builder to explain what it means to enable
     * location on captures.
     */
    public static AlertDialog.Builder getFirstTimeLocationAlertBuilder(
            AlertDialog.Builder builder, final SettingsViewListener listener) {

        getLocationAlertBuilder(builder, listener)
        .setMessage(R.string.remember_location_prompt);

        return builder;
    }

    /**
     * Updates an AlertDialog.Builder to allow selection of a supported
     * picture size.
     */
    public static AlertDialog.Builder getPictureSizeAlertBuilder(AlertDialog.Builder builder,
            final Context context, final SettingsViewListener listener) {

        final String[] supported = listener.getSupportedPictureSizeEntries();
        final String[] entries = context.getResources().getStringArray(
            R.array.pref_camera_picturesize_entries);
        final String[] values = context.getResources().getStringArray(
            R.array.pref_camera_picturesize_entryvalues);

        builder.setTitle(R.string.setting_picture_size)
       .setItems(supported, new DialogInterface.OnClickListener() {
               @Override
               public void onClick(DialogInterface dialog, int which) {
                   int index = getIndex(entries, supported[which]);
                   if (index > 0) {
                       listener.setPictureSize(values[index]);
                   }
               }
           });

        return builder;
    }

    /**
     * Updates an AlertDialog.Builder to allow the user to choose a supported
     * video quality.
     */
    public static AlertDialog.Builder getVideoQualityAlertBuilder(AlertDialog.Builder builder,
            final Context context, final SettingsViewListener listener) {

        final String[] supported = listener.getSupportedVideoQualityEntries();
        final String[] entries = context.getResources().getStringArray(
            R.array.pref_video_quality_entries);
        final String[] values = context.getResources().getStringArray(
            R.array.pref_video_quality_entryvalues);

        builder.setTitle(R.string.setting_video_resolution)
        .setItems(supported, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    int index = getIndex(entries, supported[which]);
                    if (index > 0) {
                        listener.setVideoQuality(values[index]);
                    }
                }
            });

        return builder;
    }

    /**
     * Updates an AlertDialog.Builder to allow the user to choose one of the
     * camera modes as a preferred app at startup.
     */
    public static AlertDialog.Builder getDefaultCameraAlertBuilder(AlertDialog.Builder builder,
            final Context context, final SettingsViewListener listener) {

        String[] modes = {context.getString(R.string.mode_camera),
                          context.getString(R.string.mode_video),
                          context.getString(R.string.mode_photosphere),
                          context.getString(R.string.mode_craft),
                          context.getString(R.string.mode_timelapse),
                          context.getString(R.string.mode_wideangle)};

        builder.setTitle(R.string.setting_default_camera)
        .setItems(modes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    listener.setDefaultCamera(which);
                }
            });

        return builder;
    }

    private static int getIndex(String[] entries, String val) {
        for (int i = 0; i < entries.length; i++) {
            if (entries[i].equals(val)) {
                return i;
            }
        }
        return -1;
    }

}
