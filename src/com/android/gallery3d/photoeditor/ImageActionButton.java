/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.photoeditor;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageButton;

/**
 * Image buttons used in Action-bar and Effects-menu that can be grayed out when set disabled.
 * (Text buttons are automatically grayed out when disabled; however, image buttons are not.)
 */
public class ImageActionButton extends ImageButton {

    private static final float ENABLED_ALPHA = 1;
    private static final float DISABLED_ALPHA = 0.28f;

    public ImageActionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? ENABLED_ALPHA : DISABLED_ALPHA);
    }
}
