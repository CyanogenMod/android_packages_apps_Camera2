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
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.photoeditor.PhotoView;

/**
 * Tool kit used by effect actions to retrieve tools, including managing tool creation/removal.
 */
public class EffectToolKit {

    public enum ScaleType {
        LIGHT, SHADOW, COLOR, GENERIC
    }

    private final LayoutInflater inflater;
    private final PhotoView photoView;
    private final ViewGroup toolPanel;
    private final ViewGroup toolFullscreen;

    public EffectToolKit(View root, CharSequence label) {
        inflater = (LayoutInflater) root.getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        // Create effect tool panel as the first child of effects-bar.
        ViewGroup effectsBar = (ViewGroup) root.findViewById(R.id.effects_bar);
        toolPanel = (ViewGroup) inflater.inflate(
                R.layout.photoeditor_effect_tool_panel, effectsBar, false);
        ((TextView) toolPanel.findViewById(R.id.effect_label)).setText(label);
        effectsBar.addView(toolPanel, 0);

        // Create effect tool full-screen on top of photo-view and place it within the same
        // view group that contains photo-view.
        photoView = (PhotoView) root.findViewById(R.id.photo_view);
        ViewGroup parent = (ViewGroup) photoView.getParent();
        toolFullscreen = (ViewGroup) inflater.inflate(
                R.layout.photoeditor_effect_tool_fullscreen, parent, false);
        parent.addView(toolFullscreen, parent.indexOfChild(photoView) + 1);
    }

    public PhotoView getPhotoView() {
        return photoView;
    }

    /**
     * Cancel pending touch events and stop dispatching further touch events to tools.
     */
    public void cancel() {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancelEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        toolFullscreen.dispatchTouchEvent(cancelEvent);
        toolPanel.dispatchTouchEvent(cancelEvent);
        cancelEvent.recycle();
        View.OnTouchListener listener = new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Consume all further touch events and don't dispatch them.
                return true;
            }
        };
        toolFullscreen.setOnTouchListener(listener);
        toolPanel.setOnTouchListener(listener);
    }

    /**
     * Close to remove all created tools.
     */
    public void close() {
        ((ViewGroup) toolFullscreen.getParent()).removeView(toolFullscreen);
        ((ViewGroup) toolPanel.getParent()).removeView(toolPanel);
    }

    private View addFullscreenTool(int toolId) {
        FullscreenToolView tool = (FullscreenToolView) inflater.inflate(
                toolId, toolFullscreen, false);
        tool.setPhotoBounds(getPhotoView().getPhotoBounds());
        toolFullscreen.addView(tool);
        return tool;
    }

    private View addPanelTool(int toolId) {
        // Add the tool right above the effect-label in the panel.
        View tool = inflater.inflate(toolId, toolPanel, false);
        toolPanel.addView(tool, toolPanel.indexOfChild(toolPanel.findViewById(R.id.effect_label)));
        return tool;
    }

    private Drawable getScalePickerProgressDrawable(Resources res, ScaleType type) {
        switch (type) {
            case LIGHT:
                return res.getDrawable(R.drawable.photoeditor_scale_seekbar_light);

            case SHADOW:
                return res.getDrawable(R.drawable.photoeditor_scale_seekbar_shadow);

            case COLOR:
                return res.getDrawable(R.drawable.photoeditor_scale_seekbar_color);
        }
        return res.getDrawable(R.drawable.photoeditor_scale_seekbar_generic);
    }

    public ScaleSeekBar addScalePicker(ScaleType type) {
        ScaleSeekBar scalePicker = (ScaleSeekBar) addPanelTool(
                R.layout.photoeditor_scale_seekbar);
        scalePicker.setProgressDrawable(getScalePickerProgressDrawable(
                toolPanel.getResources(), type));
        return scalePicker;
    }

    public ColorSeekBar addColorPicker() {
        return (ColorSeekBar) addPanelTool(R.layout.photoeditor_color_seekbar);
    }

    public DoodleView addDoodleView() {
        return (DoodleView) addFullscreenTool(R.layout.photoeditor_doodle_view);
    }

    public TouchView addTouchView() {
        return (TouchView) addFullscreenTool(R.layout.photoeditor_touch_view);
    }

    public FlipView addFlipView() {
        return (FlipView) addFullscreenTool(R.layout.photoeditor_flip_view);
    }

    public RotateView addRotateView() {
        return (RotateView) addFullscreenTool(R.layout.photoeditor_rotate_view);
    }

    public CropView addCropView() {
        return (CropView) addFullscreenTool(R.layout.photoeditor_crop_view);
    }
}
