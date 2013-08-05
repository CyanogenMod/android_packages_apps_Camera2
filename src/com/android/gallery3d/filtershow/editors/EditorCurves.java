/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.filtershow.editors;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.filters.FilterCurvesRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.ui.ImageCurves;

public class EditorCurves extends Editor {
    public static final int ID = R.id.imageCurves;
    ImageCurves mImageCurves;

    public EditorCurves() {
        super(ID);
    }

    @Override
    public void createEditor(Context context, FrameLayout frameLayout) {
        super.createEditor(context, frameLayout);
        mView = mImageShow = mImageCurves = new ImageCurves(context);
        mImageCurves.setEditor(this);
    }

    @Override
    public void reflectCurrentFilter() {
        super.reflectCurrentFilter();
        FilterRepresentation rep = getLocalRepresentation();
        if (rep != null && getLocalRepresentation() instanceof FilterCurvesRepresentation) {
            FilterCurvesRepresentation drawRep = (FilterCurvesRepresentation) rep;
            mImageCurves.setFilterDrawRepresentation(drawRep);
        }
    }

    @Override
    public boolean showsSeekBar() {
        return false;
    }
}
