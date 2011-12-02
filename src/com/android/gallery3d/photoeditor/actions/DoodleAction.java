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

import com.android.gallery3d.photoeditor.filters.DoodleFilter;

/**
 * An action handling doodle effect.
 */
public class DoodleAction extends EffectAction {

    private static final int DEFAULT_COLOR_INDEX = 4;

    public DoodleAction(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void prepare() {
        // Directly draw on doodle-view because running the doodle filter isn't fast enough.
        final DoodleFilter filter = new DoodleFilter();
        disableFilterOutput();

        final DoodleView doodleView = toolKit.addDoodleView();
        doodleView.setOnDoodleChangeListener(new DoodleView.OnDoodleChangeListener() {

            @Override
            public void onDoodleChanged(Doodle doodle) {
                // Check if the user draws within photo bounds and makes visible changes on photo.
                if (doodle.inBounds()) {
                    notifyChanged(filter);
                }
            }

            @Override
            public void onDoodleFinished(Doodle doodle) {
                if (doodle.inBounds()) {
                    filter.addDoodle(doodle);
                    notifyChanged(filter);
                }
            }
        });

        ColorSeekBar colorPicker = toolKit.addColorPicker();
        colorPicker.setOnColorChangeListener(new ColorSeekBar.OnColorChangeListener() {

            @Override
            public void onColorChanged(int color, boolean fromUser) {
                if (fromUser) {
                    doodleView.setColor(color);
                }
            }
        });
        colorPicker.setColorIndex(DEFAULT_COLOR_INDEX);
        doodleView.setColor(colorPicker.getColor());
    }
}
