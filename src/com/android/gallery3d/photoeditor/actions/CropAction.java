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
import android.graphics.RectF;
import android.util.AttributeSet;

import com.android.gallery3d.photoeditor.filters.CropFilter;

/**
 * An action handling crop effect.
 */
public class CropAction extends EffectAction {

    private static final float DEFAULT_CROP = 0.2f;

    private CropFilter filter;
    private CropView cropView;

    public CropAction(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void doBegin() {
        filter = new CropFilter();

        cropView = factory.createCropView();
        cropView.setOnCropChangeListener(new CropView.OnCropChangeListener() {

            @Override
            public void onCropChanged(RectF cropBounds, boolean fromUser) {
                if (fromUser) {
                    filter.setCropBounds(cropBounds);
                    notifyFilterChanged(filter, false);
                }
            }
        });

        RectF bounds = new RectF(DEFAULT_CROP, DEFAULT_CROP, 1 - DEFAULT_CROP, 1 - DEFAULT_CROP);
        cropView.setCropBounds(bounds);
        filter.setCropBounds(bounds);
        notifyFilterChanged(filter, false);
    }

    @Override
    public void doEnd() {
        cropView.setOnCropChangeListener(null);
        notifyFilterChanged(filter, true);
    }
}
