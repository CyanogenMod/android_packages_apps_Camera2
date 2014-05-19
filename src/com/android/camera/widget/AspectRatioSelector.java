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
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import com.android.camera2.R;

public class AspectRatioSelector extends LinearLayout {
    public static enum AspectRatio {
        ASPECT_RATIO_4x3,
        ASPECT_RATIO_16x9
    };

    private AspectRatio mAspectRatio = AspectRatio.ASPECT_RATIO_4x3;
    private View mAspectRatio4x3Button;
    private View mAspectRatio16x9Button;

    public AspectRatioSelector(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onFinishInflate() {
        mAspectRatio4x3Button = findViewById(R.id.aspect_ratio_4x3_button);
        mAspectRatio4x3Button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setAspectRatio(AspectRatio.ASPECT_RATIO_4x3);
            }
        });
        mAspectRatio16x9Button = findViewById(R.id.aspect_ratio_16x9_button);
        mAspectRatio16x9Button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setAspectRatio(AspectRatio.ASPECT_RATIO_16x9);
            }
        });
    }

    public void setAspectRatio(AspectRatio aspectRatio) {
        if (aspectRatio == AspectRatio.ASPECT_RATIO_4x3) {
            // Select 4x3 view.
            mAspectRatio4x3Button.setSelected(true);
            // Unselect 16x9 view.
            mAspectRatio16x9Button.setSelected(false);
        } else if (aspectRatio == AspectRatio.ASPECT_RATIO_16x9) {
            // Select 16x9 view.
            mAspectRatio16x9Button.setSelected(true);
            // Unselect 4x3 view.
            mAspectRatio4x3Button.setSelected(false);
        } else {
            // Log error.
            return;
        }
        mAspectRatio = aspectRatio;
    }

    public AspectRatio getAspectRatio() {
        return mAspectRatio;
    }
}
