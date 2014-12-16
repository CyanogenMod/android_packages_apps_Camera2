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
import android.widget.FrameLayout;

import com.android.camera.exif.Rational;
import com.android.camera.settings.ResolutionUtil;
import com.android.camera2.R;

/**
 * Displays a dialog that allows people to choose aspect ratio. Please
 * instantiate this class programmatically.
 */
public class AspectRatioDialogLayout extends FrameLayout {

    private AspectRatioDialogListener mListener;

    private View mAspectRatio4x3Button;
    private View mAspectRatio16x9Button;
    private View mConfirmButton;

    private Rational mAspectRatio;
    private int mLastOrientation;

    /**
     * Constructs a new AspectRatioDialogLayout object.
     *
     * @param context The application context.
     * @param defaultAspectRatio The default aspect ratio to choose.
     */
    public AspectRatioDialogLayout(Context context, Rational defaultAspectRatio) {
        super(context);
        mAspectRatio = defaultAspectRatio;
        mLastOrientation = context.getResources().getConfiguration().orientation;
        setBackgroundResource(R.color.fullscreen_dialog_background_color);
        inflate(context, R.layout.aspect_ratio_dialog_content, this);
        updateSubviewReferences();
    }

    public void setListener(AspectRatioDialogListener listener) {
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
        inflate(getContext(), R.layout.aspect_ratio_dialog_content, this);
        updateSubviewReferences();
    }

    private void updateSubviewReferences() {
        mAspectRatio4x3Button = findViewById(R.id.aspect_ratio_4x3_button);
        mAspectRatio16x9Button = findViewById(R.id.aspect_ratio_16x9_button);
        mConfirmButton = findViewById(R.id.confirm_button);

        // Set aspect ratio after references to views are established.
        setAspectRatio(mAspectRatio);

        // Hook onclick events.
        mAspectRatio4x3Button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setAspectRatio(ResolutionUtil.ASPECT_RATIO_4x3);
            }
        });
        mAspectRatio16x9Button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setAspectRatio(ResolutionUtil.ASPECT_RATIO_16x9);
            }
        });
        mConfirmButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onConfirm(mAspectRatio);
                }
            }
        });
    }

    private void setAspectRatio(Rational aspectRatio) {
        mAspectRatio = aspectRatio;

        if (mAspectRatio.equals(ResolutionUtil.ASPECT_RATIO_4x3)) {
            // Select 4x3 view and unselect 16x9 view.
            mAspectRatio4x3Button.setSelected(true);
            mAspectRatio16x9Button.setSelected(false);
        } else if (mAspectRatio.equals(ResolutionUtil.ASPECT_RATIO_16x9)) {
            // Select 16x9 view and unselect 4x3 view.
            mAspectRatio16x9Button.setSelected(true);
            mAspectRatio4x3Button.setSelected(false);
        }
    }

    public interface AspectRatioDialogListener {
        public void onConfirm(Rational chosenAspectRatio);
    }
}
