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
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;

import com.android.camera2.R;

public class LocationDialogLayout extends FrameLayout {

    public interface LocationTaggingSelectionListener {
        public void onLocationTaggingSelected(boolean selected);
    }

    private View mConfirmButton;
    private CheckBox mCheckBox;
    private int mLastOrientation;
    private LocationTaggingSelectionListener mListener;
    private boolean mCheckBoxChecked = true;

    public LocationDialogLayout(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mLastOrientation = context.getResources().getConfiguration().orientation;
    }

    @Override
    public void onFinishInflate() {
        updateViewReference();
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        // TODO: Extract the orientation checking logic in a super class as it
        // is also used in the aspect ratio dialog.
        if (config.orientation == mLastOrientation) {
            return;
        }
        mLastOrientation = config.orientation;
        removeAllViews();
        inflate(getContext(), R.layout.location_dialog_content, this);
        updateViewReference();
    }

    private void updateViewReference() {
        mCheckBox = (CheckBox) findViewById(R.id.check_box);
        mCheckBox.setChecked(mCheckBoxChecked);
        mCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mCheckBoxChecked = isChecked;
            }
        });

        mConfirmButton = findViewById(R.id.confirm_button);
        mConfirmButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onLocationTaggingSelected(mCheckBoxChecked);
                }
            }
        });
    }

    public void setLocationTaggingSelectionListener(LocationTaggingSelectionListener listener) {
        mListener = listener;
    }

}
