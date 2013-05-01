/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.gallery3d.filtershow.imageshow.GeometryMetadata;
import com.android.gallery3d.filtershow.imageshow.ImageStraighten;
import com.android.gallery3d.filtershow.imageshow.MasterImage;

public class EditorStraighten extends Editor implements EditorInfo {
    public static final int ID = R.id.editorStraighten;
    ImageStraighten mImageStraighten;
    GeometryMetadata mGeometryMetadata;

    public EditorStraighten() {
        super(ID);
        mShowParameter = SHOW_VALUE_INT;
    }

    // TODO use filter reflection like
    @Override
    public String calculateUserMessage(Context context, String effectName, Object parameterValue) {
        String apply = context.getString(R.string.apply_effect);
        apply += " " + effectName;
        return apply.toUpperCase();
    }

    @Override
    public void createEditor(Context context, FrameLayout frameLayout) {
        super.createEditor(context, frameLayout);
        if (mImageStraighten == null) {
            mImageStraighten = new ImageStraighten(context);
        }
        mView = mImageShow = mImageStraighten;
        mImageStraighten.setImageLoader(MasterImage.getImage().getImageLoader());
        mImageStraighten.setEditor(this);
        mImageStraighten.syncLocalToMasterGeometry();
    }

    @Override
    public int getTextId() {
        return R.string.straighten;
    }

    @Override
    public int getOverlayId() {
        return R.drawable.filtershow_button_geometry_straighten;
    }

    @Override
    public boolean getOverlayOnly() {
        return true;
    }

    @Override
    public boolean showsSeekBar() {
        return false;
    }

    @Override
    public boolean showsPopupIndicator() {
        return false;
    }
}
