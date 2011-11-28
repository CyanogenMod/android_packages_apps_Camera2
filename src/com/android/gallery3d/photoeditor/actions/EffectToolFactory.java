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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.gallery3d.R;
import com.android.gallery3d.photoeditor.PhotoView;

/**
 * Factory to create tools that will be used by effect actions.
 */
public class EffectToolFactory {

    public enum ScaleType {
        LIGHT, SHADOW, COLOR, GENERIC
    }

    private final ViewGroup effectToolPanel;
    private final LayoutInflater inflater;
    private ViewGroup effectToolFullscreen;

    public EffectToolFactory(ViewGroup effectToolPanel, LayoutInflater inflater) {
        this.effectToolPanel = effectToolPanel;
        this.inflater = inflater;
    }

    public PhotoView getPhotoView() {
        return (PhotoView) effectToolPanel.getRootView().findViewById(R.id.photo_view);
    }

    public void removeTools() {
        // Remove all created tools.
        if (effectToolFullscreen != null) {
            ((ViewGroup) effectToolFullscreen.getParent()).removeView(effectToolFullscreen);
            effectToolFullscreen = null;
        }
        ((ViewGroup) effectToolPanel.getParent()).removeView(effectToolPanel);
    }

    private View createFullscreenTool(int toolId) {
        // Create full screen effect tool on top of photo-view and place it within the same
        // view group that contains photo-view.
        PhotoView photoView = getPhotoView();
        ViewGroup parent = (ViewGroup) photoView.getParent();
        effectToolFullscreen = (ViewGroup) inflater.inflate(
                R.layout.photoeditor_effect_tool_fullscreen, parent, false);
        parent.addView(effectToolFullscreen, parent.indexOfChild(photoView) + 1);
        FullscreenToolView tool = (FullscreenToolView) inflater.inflate(
                toolId, effectToolFullscreen, false);
        tool.setPhotoBounds(photoView.getPhotoBounds());
        effectToolFullscreen.addView(tool);
        return tool;
    }

    private View createPanelTool(int toolId) {
        View tool = inflater.inflate(toolId, effectToolPanel, false);
        effectToolPanel.addView(tool);
        return tool;
    }

    private int getScalePickerBackground(ScaleType type) {
        switch (type) {
            case LIGHT:
                return R.drawable.photoeditor_scale_seekbar_light;

            case SHADOW:
                return R.drawable.photoeditor_scale_seekbar_shadow;

            case COLOR:
                return R.drawable.photoeditor_scale_seekbar_color;
        }
        return R.drawable.photoeditor_scale_seekbar_generic;
    }

    public ScaleSeekBar createScalePicker(ScaleType type) {
        ScaleSeekBar scalePicker = (ScaleSeekBar) createPanelTool(
                R.layout.photoeditor_scale_seekbar);
        scalePicker.setBackgroundResource(getScalePickerBackground(type));
        return scalePicker;
    }

    public ColorSeekBar createColorPicker() {
        return (ColorSeekBar) createPanelTool(R.layout.photoeditor_color_seekbar);
    }

    public DoodleView createDoodleView() {
        return (DoodleView) createFullscreenTool(R.layout.photoeditor_doodle_view);
    }

    public TouchView createTouchView() {
        return (TouchView) createFullscreenTool(R.layout.photoeditor_touch_view);
    }

    public FlipView createFlipView() {
        return (FlipView) createFullscreenTool(R.layout.photoeditor_flip_view);
    }

    public RotateView createRotateView() {
        return (RotateView) createFullscreenTool(R.layout.photoeditor_rotate_view);
    }

    public CropView createCropView() {
        return (CropView) createFullscreenTool(R.layout.photoeditor_crop_view);
    }
}
