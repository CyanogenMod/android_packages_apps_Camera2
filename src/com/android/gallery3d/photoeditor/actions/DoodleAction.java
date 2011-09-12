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
import android.graphics.Path;
import android.util.AttributeSet;

import com.android.gallery3d.photoeditor.filters.DoodleFilter;

/**
 * An action handling doodle effect.
 */
public class DoodleAction extends EffectAction {

    private static final int DEFAULT_COLOR_INDEX = 4;

    private DoodleFilter filter;
    private ColorSeekBar colorPicker;
    private DoodleView doodleView;

    public DoodleAction(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void doBegin() {
        filter = new DoodleFilter();

        colorPicker = factory.createColorPicker();
        colorPicker.setOnColorChangeListener(new ColorSeekBar.OnColorChangeListener() {

            @Override
            public void onColorChanged(int color, boolean fromUser) {
                if (fromUser) {
                    doodleView.setColor(color);
                }
            }
        });
        colorPicker.setColorIndex(DEFAULT_COLOR_INDEX);

        doodleView = factory.createDoodleView();
        doodleView.setOnDoodleChangeListener(new DoodleView.OnDoodleChangeListener() {

            @Override
            public void onDoodleInPhotoBounds() {
                // Notify the user has drawn within photo bounds and made visible changes on photo.
                filter.setDoodledInPhotoBounds();
                notifyFilterChanged(filter, false);
            }

            @Override
            public void onDoodleFinished(Path path, int color) {
                filter.addPath(path, color);
                notifyFilterChanged(filter, false);
            }
        });
        doodleView.setColor(colorPicker.getColor());
    }

    @Override
    public void doEnd() {
        colorPicker.setOnColorChangeListener(null);
        doodleView.setOnDoodleChangeListener(null);
        notifyFilterChanged(filter, true);
    }
}
