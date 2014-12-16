/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.camera.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;

import com.android.camera2.R;

/**
 * Displays a dialog that allows people to choose whether they like to enable
 * location recording or not. Please instantiate this class programmatically.
 */
public class LocationDialogLayout extends FrameLayout {

    public interface LocationDialogListener {
        public void onConfirm(boolean locationRecordingEnabled);
    }

    private LocationDialogListener mListener;
    private CheckBox mCheckBox;
    private View mConfirmButton;
    private int mLastOrientation;
    private boolean mLocationRecordingEnabled;

    /**
     * Constructs a new LocationDialogLayout object.
     *
     * @param context The application context.
     * @param defaultLocationRecordingEnabled Whether to enable location
     *            recording by default.
     */
    public LocationDialogLayout(Context context, boolean defaultLocationRecordingEnabled) {
        super(context);
        mLocationRecordingEnabled = defaultLocationRecordingEnabled;
        mLastOrientation = context.getResources().getConfiguration().orientation;
        setBackgroundResource(R.color.fullscreen_dialog_background_color);
        inflate(context, R.layout.location_dialog_content, this);
        updateSubviewReferences();
    }

    public void setListener(LocationDialogListener listener) {
        mListener = listener;
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (config.orientation == mLastOrientation) {
            return;
        }
        mLastOrientation = config.orientation;

        removeAllViews();
        inflate(getContext(), R.layout.location_dialog_content, this);
        updateSubviewReferences();
    }

    private void updateSubviewReferences() {
        mCheckBox = (CheckBox) findViewById(R.id.check_box);
        mCheckBox.setChecked(mLocationRecordingEnabled);
        mCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mLocationRecordingEnabled = isChecked;
            }
        });

        mConfirmButton = findViewById(R.id.confirm_button);
        mConfirmButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onConfirm(mLocationRecordingEnabled);
                }
            }
        });
    }
}
