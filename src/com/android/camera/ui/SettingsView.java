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
import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.camera.util.FeedbackHelper;
import com.android.camera2.R;

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

    private final Context mContext;
    private SettingsViewController mController;
    private AlertDialog.Builder mDialogBuilder;

    private final SettingsAdapter mAdapter;

    private FeedbackHelper mFeedbackHelper;

    private static final int LOCATION_SETTING = 1;
    private static final int PICTURE_SIZE_SETTING = 2;
    private static final int VIDEO_RES_SETTING = 3;
    private static final int DEFAULT_CAMERA_SETTING = 4;
    private static final int SEND_FEEDBACK_SETTING = 5;

    private static final int[] mAllSettings = {LOCATION_SETTING, PICTURE_SIZE_SETTING,
            VIDEO_RES_SETTING, DEFAULT_CAMERA_SETTING, SEND_FEEDBACK_SETTING,};

    /**
     * A listener for receiving setting selection events.
     */
    private final SettingsViewListener mListener =
        new SettingsViewListener() {
            @Override
            public void onSettingSelected(int settingId) {
                AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

                switch (settingId) {
                    case LOCATION_SETTING:
                        mDialogBuilder = getLocationAlertBuilder(builder, mController);
                        break;
                    case PICTURE_SIZE_SETTING:
                        mDialogBuilder = getPictureSizeAlertBuilder(builder, mContext);
                        break;
                    case VIDEO_RES_SETTING:
                        mDialogBuilder = getVideoQualityAlertBuilder(builder, mContext);
                        break;
                    case DEFAULT_CAMERA_SETTING:
                        mDialogBuilder = getDefaultCameraAlertBuilder(builder, mContext);
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
        };

    private class SettingsAdapter extends ArrayAdapter<Integer> {
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
                mListener.onSettingSelected((Integer) getItemAtPosition(pos));
            }
        });

    }

    /**
     * A listener for responding to setting selection events.
     */
    public interface SettingsViewListener {
        public void onSettingSelected(int settingId);
    }

    /**
     * A controller for formatting the settings dialogs
     * according to the device and application state.
     */
    public interface SettingsViewController {
        public void setLocation(boolean on);

        public void setPictureSize(String size);

        public String[] getSupportedVideoQualityEntries();
        public void setVideoQuality(String resolution);

        public String[] getSupportedDefaultCameras();
        public void setDefaultCamera(int index);
    }

    /**
     * Set a controller responsible for providing the supported
     * options to {@link android.app.AlertDialog.Builder}s and for
     * updating the application state once a dialog option has been
     * selected.
     *
     * If no controller is set, no {@link android.app.AlertDialog}s
     * will be shown.
     */
    public void setController(SettingsViewController controller) {
        mController = controller;
    }

    public void setFeedbackHelper(FeedbackHelper feedback) {
        mFeedbackHelper = feedback;
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
            final SettingsViewController controller) {
        if (controller == null) {
            return null;
        }

        builder.setTitle(R.string.remember_location_title)
        .setPositiveButton(R.string.remember_location_yes,
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int arg1) {
                    controller.setLocation(true);
                }
            })
        .setNegativeButton(R.string.remember_location_no,
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int arg1) {
                    controller.setLocation(false);
                }
            });

        return builder;
    }

    /**
     * Updates an AlertDialog.Builder to explain what it means to enable
     * location on captures.
     */
    public static AlertDialog.Builder getFirstTimeLocationAlertBuilder(
            AlertDialog.Builder builder, SettingsViewController controller) {
        if (controller == null) {
            return null;
        }

        getLocationAlertBuilder(builder, controller)
        .setMessage(R.string.remember_location_prompt);

        return builder;
    }

    /**
     * Updates an AlertDialog.Builder to allow selection of a supported
     * picture size.
     */
    public AlertDialog.Builder getPictureSizeAlertBuilder(AlertDialog.Builder builder,
            final Context context) {
        if (mController == null) {
            return null;
        }

        final String[] entries = context.getResources().getStringArray(
            R.array.pref_camera_picturesize_entries);
        final String[] values = context.getResources().getStringArray(
            R.array.pref_camera_picturesize_entryvalues);

        builder.setTitle(R.string.setting_picture_size)
       .setItems(entries, new DialogInterface.OnClickListener() {
               @Override
               public void onClick(DialogInterface dialog, int which) {
                   int index = getIndex(entries, entries[which]);
                       mController.setPictureSize(values[index]);
               }
           });

        return builder;
    }

    /**
     * Updates an AlertDialog.Builder to allow the user to choose a supported
     * video quality.
     */
    public AlertDialog.Builder getVideoQualityAlertBuilder(AlertDialog.Builder builder,
            final Context context) {
        if (mController == null) {
            return null;
        }

        final String[] supported = mController.getSupportedVideoQualityEntries();
        final String[] entries = context.getResources().getStringArray(
            R.array.pref_video_quality_entries);
        final String[] values = context.getResources().getStringArray(
            R.array.pref_video_quality_entryvalues);

        builder.setTitle(R.string.setting_video_resolution)
                .setItems(supported, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int index = getIndex(entries, supported[which]);
                        mController.setVideoQuality(values[index]);
                    }
                });

        return builder;
    }

    /**
     * Updates an AlertDialog.Builder to allow the user to choose one of the
     * camera modes as a preferred app at startup.
     */
    public AlertDialog.Builder getDefaultCameraAlertBuilder(AlertDialog.Builder builder,
            final Context context) {
        if (mController == null) {
            return null;
        }

        String[] modes = mController.getSupportedDefaultCameras();
        builder.setTitle(R.string.setting_default_camera)
        .setItems(modes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mController.setDefaultCamera(which);
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
