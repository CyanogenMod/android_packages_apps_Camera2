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

package com.android.gallery3d.photoeditor.actions;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.SeekBar;

/**
 * Seek-bar that has a draggable thumb to set and get the normalized scale value from 0 to 1.
 */
class ScaleSeekBar extends AbstractSeekBar {

    /**
     * Listens to scale changes.
     */
    public interface OnScaleChangeListener {

        void onProgressChanged(float progress, boolean fromUser);
    }

    public ScaleSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);

        setMax(100);
    }

    public void setOnScaleChangeListener(final OnScaleChangeListener listener) {
        setOnSeekBarChangeListener((listener == null) ? null : new OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                listener.onProgressChanged((float) progress / getMax(), fromUser);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    public void setProgress(float progress) {
        setProgress((int) (progress * getMax()));
    }
}
