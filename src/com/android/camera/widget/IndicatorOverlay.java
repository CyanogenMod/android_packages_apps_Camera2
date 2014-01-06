/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.camera.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.android.camera.module.ModulesInfo;
import com.android.camera.settings.SettingsManager;
import com.android.camera.ui.PreviewStatusListener;

import com.android.camera2.R;

/**
 * IndicatorOverlay is a relative layout which positions indicators in
 * a vertical column in the bottom right corner of the preview that is visible
 * above the bottom bar.  This overlay takes its horizontal dimension from
 * the preview, and its vertical dimension relative to the bottom bar.
 *
 * Indicators are only visible if they are in a non-default state.  The
 * visibility of an indicator is set when an indicator's setting changes.
 */
public class IndicatorOverlay extends RelativeLayout
    implements PreviewStatusListener.PreviewAreaSizeChangedListener,
               SettingsManager.OnSettingChangedListener {
    private final static String TAG = "IndicatorOverlay";

    private int mPreviewWidth;
    private int mPreviewHeight;

    private ImageView mFlashIndicator;
    private ImageView mHdrIndicator;

    private TypedArray mFlashIndicatorPhotoIcons;
    private TypedArray mFlashIndicatorVideoIcons;
    private TypedArray mHdrIndicatorIcons;

    public IndicatorOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        mFlashIndicatorPhotoIcons
            = context.getResources().obtainTypedArray(R.array.camera_flashmode_icons);
        mFlashIndicatorVideoIcons
            = context.getResources().obtainTypedArray(R.array.video_flashmode_icons);
        mHdrIndicatorIcons
            = context.getResources().obtainTypedArray(R.array.pref_camera_hdr_plus_icons);
    }

    @Override
    public void onFinishInflate() {
        mFlashIndicator = (ImageView) findViewById(R.id.flash_indicator);
        mHdrIndicator = (ImageView) findViewById(R.id.hdr_indicator);
    }

    /**
     * Sets all indicators to the correct resource and visibility
     * based on the current settings.
     */
    public void syncIndicators(SettingsManager settingsManager, int modeIndex) {
        // Sync the flash indicator.
        if (modeIndex == ModulesInfo.MODULE_VIDEO) {
            setIndicatorState(settingsManager, SettingsManager.SETTING_VIDEOCAMERA_FLASH_MODE,
                              mFlashIndicator, mFlashIndicatorVideoIcons);
        } else {
            setIndicatorState(settingsManager, SettingsManager.SETTING_FLASH_MODE,
                              mFlashIndicator, mFlashIndicatorPhotoIcons);
        }

        // Sync the hdr indicator.
        setIndicatorState(settingsManager, SettingsManager.SETTING_CAMERA_HDR,
                          mHdrIndicator, mHdrIndicatorIcons);
    }

    /**
     * Sets the image resource and visibility of the indicator
     * based on the indicator's corresponding setting state.
     */
    private void setIndicatorState(SettingsManager settingsManager, int id,
            ImageView imageView, TypedArray iconArray) {

        // Set the correct image src.
        String value = settingsManager.get(id);
        int valueIndex = settingsManager.getStringValueIndex(id);
        if (valueIndex < 0) {
            // This can happen when the setting is camera dependent
            // and the camera is not yet open.  CameraAppUI.onChangeCamera()
            // will call this again when the camera is open.
            Log.w(TAG, "The setting for this indicator is not available.");
            imageView.setVisibility(View.INVISIBLE);
            return;
        }
        Drawable drawable = iconArray.getDrawable(valueIndex);
        if (drawable == null) {
            throw new IllegalStateException("Indicator drawable is null.");
        }
        imageView.setImageDrawable(drawable);

        // Set the indicator visible if not in default state.
        if (settingsManager.isDefault(id)) {
            imageView.setVisibility(View.INVISIBLE);
        } else {
            imageView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onSettingChanged(SettingsManager settingsManager, int id) {
        switch (id) {
            case SettingsManager.SETTING_FLASH_MODE: {
                setIndicatorState(settingsManager, id, mFlashIndicator, mFlashIndicatorPhotoIcons);
                break;
            }
            case SettingsManager.SETTING_VIDEOCAMERA_FLASH_MODE: {
                setIndicatorState(settingsManager, id, mFlashIndicator, mFlashIndicatorVideoIcons);
                break;
            }
            case SettingsManager.SETTING_CAMERA_HDR: {
                setIndicatorState(settingsManager, id, mHdrIndicator, mHdrIndicatorIcons);
                break;
            }
            default: {
                // Do nothing.
            }
        }
    }

    @Override
    public void onPreviewAreaSizeChanged(float scaledWidth,
                                         float scaledHeight) {
        mPreviewWidth = (int) scaledWidth;
        mPreviewHeight = (int) scaledHeight;
        setLayoutDimensions();
    }

    /**
     * The overlay takes its horizontal dimension from the preview.  The vertical
     * dimension of the overlay is determined by its parent layout, which has
     * knowledge of the bottom bar dimensions.
     */
    public void setLayoutDimensions() {
        if (mPreviewWidth == 0 || mPreviewHeight == 0) {
            return;
        }

        boolean isPortrait = Configuration.ORIENTATION_PORTRAIT
            == getResources().getConfiguration().orientation;

        ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) getLayoutParams();
        if (isPortrait) {
            params.width = mPreviewWidth;
        } else {
            params.height = mPreviewHeight;
        }
        setLayoutParams(params);
    }
}
