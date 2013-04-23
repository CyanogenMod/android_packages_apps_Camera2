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
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.crop.CropExtras;
import com.android.gallery3d.filtershow.imageshow.ImageCrop;
import com.android.gallery3d.filtershow.imageshow.MasterImage;

public class EditorCrop extends Editor implements EditorInfo {
    public static final int ID = R.id.editorCrop;
    private static final String LOGTAG = "EditorCrop";

    ImageCrop mImageCrop;
    private String mAspectString = "";
    private boolean mCropActionFlag = false;
    private CropExtras mCropExtras = null;

    public EditorCrop() {
        super(ID);
    }

    @Override
    public void createEditor(Context context, FrameLayout frameLayout) {
        super.createEditor(context, frameLayout);
        if (mImageCrop == null) {
            // TODO: need this for now because there's extra state in ImageCrop.
            // all the state instead should be in the representation.
            // Same thing for the other geometry editors.
            mImageCrop = new ImageCrop(context);
        }
        mView = mImageShow = mImageCrop;
        mImageCrop.setImageLoader(MasterImage.getImage().getImageLoader());
        mImageCrop.setEditor(this);
        mImageCrop.syncLocalToMasterGeometry();
        mImageCrop.setCropActionFlag(mCropActionFlag);
        if (mCropActionFlag) {
            mImageCrop.setExtras(mCropExtras);
            mImageCrop.setAspectString(mAspectString);
            mImageCrop.clear();
        } else {
            mImageCrop.setExtras(null);
        }
    }

    @Override
    public void openUtilityPanel(final LinearLayout accessoryViewList) {
        Button view = (Button) accessoryViewList.findViewById(R.id.applyEffect);
        view.setText(mContext.getString(R.string.crop));
        view.setOnClickListener(new OnClickListener() {

                @Override
            public void onClick(View arg0) {
                showPopupMenu(accessoryViewList);
            }
        });
    }

    private void showPopupMenu(LinearLayout accessoryViewList) {
        final Button button = (Button) accessoryViewList.findViewById(
                R.id.applyEffect);
        if (button == null) {
            return;
        }
        final PopupMenu popupMenu = new PopupMenu(mImageShow.getActivity(), button);
        popupMenu.getMenuInflater().inflate(R.menu.filtershow_menu_crop, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                mImageCrop.setAspectButton(item.getItemId());
                return true;
            }
        });
        popupMenu.show();
    }

    @Override
    public boolean showsSeekBar() {
        return false;
    }

    @Override
    public int getTextId() {
        return R.string.crop;
    }

    @Override
    public int getOverlayId() {
        return R.drawable.filtershow_button_geometry_crop;
    }

    @Override
    public boolean getOverlayOnly() {
        return true;
    }

    public void setExtras(CropExtras cropExtras) {
        mCropExtras = cropExtras;
    }

    public void setAspectString(String s) {
        mAspectString = s;
    }

    public void setCropActionFlag(boolean b) {
        mCropActionFlag = b;
    }

}
