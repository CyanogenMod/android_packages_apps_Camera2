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

package com.android.gallery3d.filtershow.imageshow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;

import com.android.gallery3d.filtershow.HistoryAdapter;
import com.android.gallery3d.filtershow.PanelController;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.presets.ImagePreset;

public class ImageSlave extends ImageShow {
    private ImageShow mMasterImageShow = null;

    public ImageSlave(Context context) {
        super(context);
    }

    public ImageSlave(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageShow getMaster() {
        return mMasterImageShow;
    }

    public void setMaster(ImageShow master) {
        mMasterImageShow = master;
    }

    @Override
    public HistoryAdapter getHistory() {
        return mMasterImageShow.getHistory();
    }

    @Override
    public void resetImageCaches(ImageShow caller) {
        mMasterImageShow.resetImageCaches(caller);
    }

    @Override
    public ImagePreset getImagePreset() {
        return mMasterImageShow.getImagePreset();
    }

    @Override
    public Rect getDisplayedImageBounds() {
        return mMasterImageShow.getDisplayedImageBounds();
    }

    @Override
    public void setImagePreset(ImagePreset preset, boolean addToHistory) {
        mMasterImageShow.setImagePreset(preset, addToHistory);
    }

    @Override
    public void setCurrentFilter(ImageFilter filter) {
        mMasterImageShow.setCurrentFilter(filter);
    }

    @Override
    public ImageFilter getCurrentFilter() {
        return mMasterImageShow.getCurrentFilter();
    }

    @Override
    public Bitmap getFilteredImage() {
        return mMasterImageShow.getFilteredImage();
    }

    @Override
    public void updateImage() {
        mMasterImageShow.updateImage();
    }

    @Override
    public void updateImagePresets(boolean force) {
        mMasterImageShow.updateImagePresets(force);
    }

    @Override
    public void requestFilteredImages() {
        mMasterImageShow.requestFilteredImages();
    }

    @Override
    public boolean showTitle() {
        return false;
    }

    @Override
    public float getImageRotation() {
        return mMasterImageShow.getImageRotation();
    }

    @Override
    public float getImageRotationZoomFactor() {
        return mMasterImageShow.getImageRotationZoomFactor();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    @Override
    public void setPanelController(PanelController controller) {
        mMasterImageShow.setPanelController(controller);
    }

    @Override
    public PanelController getPanelController() {
        return mMasterImageShow.getPanelController();
    }

}
