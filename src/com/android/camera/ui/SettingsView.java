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
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.TextView;

import com.android.camera2.R;
import com.android.camera.util.FeedbackHelper;

import java.lang.Integer;
import java.util.ArrayList;


/**
 * SettingsView class displays all global settings in the form
 * of a list.  Each setting launches a dialog or a toggle.
 */
public class SettingsView extends ListView {
    private static final String TAG = "SettingsView";

    private final int mHeadingIconResId = R.drawable.ic_settings_normal;
    private final int mHeadingTextResId = R.string.mode_settings;
    private final int mHeadingIconBlockColor = R.color.settings_mode_color;

    private Context mContext;
    private SettingsViewListener mListener;
    private AlertDialog.Builder mDialogBuilder;

    private SettingsAdapter mAdapter;

    private FeedbackHelper mFeedbackHelper;

    private static final int LOCATION_SETTING = 1;
    private static final int PICTURE_SIZE_SETTING = 2;
    private static final int VIDEO_RES_SETTING = 3;
    private static final int DEFAULT_CAMERA_SETTING = 4;
    private static final int SEND_FEEDBACK_SETTING = 5;

    private static final int[] mAllSettings = {LOCATION_SETTING, PICTURE_SIZE_SETTING,
            VIDEO_RES_SETTING, DEFAULT_CAMERA_SETTING, SEND_FEEDBACK_SETTING,};

    public class SettingsAdapter extends ArrayAdapter<Integer> {
        private SettingsAdapter(Context context, int viewId, ArrayList<Integer> settings) {
            super(context, viewId, settings);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            int settingId = getItem(position);
            ((TextView) view).setText(getResId(settingId));
            return view;
        }
    }

    public SettingsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        ArrayList<Integer> listContent = new ArrayList();
        for (int settingId: mAllSettings) {
            if (settingId != SEND_FEEDBACK_SETTING || FeedbackHelper.feedbackAvailable()) {
                listContent.add(settingId);
            }
        }
        mAdapter = new SettingsAdapter(mContext, R.layout.settings_selector, listContent);
        setAdapter(mAdapter);

        setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int pos, long id) {
                onSettingSelected((Integer) getItemAtPosition(pos));
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

    public void setFeedbackHelper(FeedbackHelper feedback) {
        mFeedbackHelper = feedback;
    }

    private void onSettingSelected(int settingId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

        switch (settingId) {
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
            case SEND_FEEDBACK_SETTING:
                mFeedbackHelper.startFeedback();
                mDialogBuilder = null;
                break;
            default:
                throw new IllegalArgumentException();
        }
        if (mDialogBuilder != null) {
            AlertDialog alert = mDialogBuilder.create();
            alert.show();
        }
    }

    private int getResId(int settingId) {
        switch (settingId) {
            case LOCATION_SETTING:
                return R.string.setting_location;
            case PICTURE_SIZE_SETTING:
                return R.string.setting_picture_size;
            case VIDEO_RES_SETTING:
                return R.string.setting_video_resolution;
            case DEFAULT_CAMERA_SETTING:
                return R.string.setting_default_camera;
            case SEND_FEEDBACK_SETTING:
                return R.string.setting_send_feedback;
            default:
                throw new IllegalArgumentException();
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
