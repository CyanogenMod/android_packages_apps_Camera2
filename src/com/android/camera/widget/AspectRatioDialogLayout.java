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
import android.widget.FrameLayout;

import com.android.camera2.R;

public class AspectRatioDialogLayout extends FrameLayout {
    private View mConfirmButton;
    private AspectRatioSelector mAspectRatioSelector;
    private int mLastOrientation;
    private AspectRatioChangedListener mListener;
    private boolean mInitialized;
    private AspectRatioSelector.AspectRatio mAspectRatio;

    public interface AspectRatioChangedListener {
        public void onAspectRatioChanged(AspectRatioSelector.AspectRatio aspectRatio);
    }

    public AspectRatioDialogLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLastOrientation = getResources().getConfiguration().orientation;
    }

    @Override
    public void onFinishInflate() {
        updateViewReference();
    }

    private void updateViewReference() {
        mAspectRatioSelector = (AspectRatioSelector) findViewById(R.id.aspect_ratio_selector);
        mConfirmButton = findViewById(R.id.confirm_button);
        mConfirmButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onAspectRatioChanged(mAspectRatioSelector.getAspectRatio());
                }
            }
        });
        if (mInitialized) {
            mAspectRatioSelector.setAspectRatio(mAspectRatio);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (config.orientation == mLastOrientation) {
            return;
        }
        mLastOrientation = config.orientation;
        mAspectRatio = mAspectRatioSelector.getAspectRatio();
        removeAllViews();
        inflate(getContext(), R.layout.aspect_ratio_dialog_content, this);
        updateViewReference();
    }

    public void setAspectRatio(AspectRatioSelector.AspectRatio aspectRatio) {
        mAspectRatioSelector.setAspectRatio(aspectRatio);
    }

    public void initialize(AspectRatioChangedListener listener,
            AspectRatioSelector.AspectRatio aspectRatio) {
        mInitialized = true;
        mListener = listener;
        mAspectRatio = aspectRatio;
        if (mAspectRatioSelector != null) {
            mAspectRatioSelector.setAspectRatio(mAspectRatio);
        }
    }
}
