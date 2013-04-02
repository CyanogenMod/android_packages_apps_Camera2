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
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.imageshow.ImageRotate;
import com.android.gallery3d.filtershow.imageshow.MasterImage;

public class EditorRotate extends Editor implements EditorInfo {
    public static final String LOGTAG = "EditorRotate";
    public static final int ID = R.id.editorRotate;
    ImageRotate mImageRotate;

    public EditorRotate() {
        super(ID);
    }

    @Override
    public void createEditor(Context context, FrameLayout frameLayout) {
        super.createEditor(context, frameLayout);
        if (mImageRotate == null) {
            mImageRotate = new ImageRotate(context);
        }
        mView = mImageShow = mImageRotate;
        mImageRotate.setImageLoader(MasterImage.getImage().getImageLoader());
        mImageRotate.setEditor(this);
        mImageRotate.syncLocalToMasterGeometry();
    }

    @Override
    public void openUtilityPanel(final LinearLayout accessoryViewList) {
        final Button button = (Button) accessoryViewList.findViewById(R.id.applyEffect);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                mImageRotate.rotate();
                button.setText(mContext.getString(getTextId()) + " " + mImageRotate.getLocalValue());
                mImageRotate.saveAndSetPreset();
            }
        });
    }

    @Override
    public int getTextId() {
        return R.string.rotate;
    }

    @Override
    public int getOverlayId() {
        return R.drawable.filtershow_button_geometry_rotate;
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
