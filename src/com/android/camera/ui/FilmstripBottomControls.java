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

package com.android.camera.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import com.android.camera.CameraActivity;
import com.android.camera2.R;

/**
 * Shows controls at the bottom of the screen for editing, viewing a photo
 * sphere image and creating a tiny planet from a photo sphere image.
 */
public class FilmstripBottomControls extends RelativeLayout
        implements CameraActivity.OnActionBarVisibilityListener {

    /**
     * Classes implementing this interface can listen for events on the bottom
     * controls.
     */
    public static interface BottomControlsListener {
        /**
         * Called when the user pressed the "view" button to e.g. view a photo
         * sphere or RGBZ image.
         */
        public void onView();

        /**
         * Called when the user pressed the "edit" button.
         */
        public void onEdit();

        /**
         * Called when the user pressed the "tiny planet" button.
         */
        public void onTinyPlanet();
    }

    /** Values for the view state of the button. */
    public static final int VIEW_NONE = 0;
    public static final int VIEW_PHOTO_SPHERE = 1;
    public static final int VIEW_RGBZ = 2;

    private BottomControlsListener mListener;
    private ImageButton mEditButton;
    private ImageButton mViewButton;
    private ImageButton mTinyPlanetButton;

    public FilmstripBottomControls(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mEditButton = (ImageButton)
                findViewById(R.id.filmstrip_bottom_control_edit);
        mEditButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener != null) {
                    mListener.onEdit();
                }
            }
        });

        mViewButton = (ImageButton)
                findViewById(R.id.filmstrip_bottom_control_view);
        mViewButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener != null) {
                    mListener.onView();
                }
            }
        });

        mTinyPlanetButton = (ImageButton)
                findViewById(R.id.filmstrip_bottom_control_tiny_planet);
        mTinyPlanetButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener != null) {
                    mListener.onTinyPlanet();
                }
            }
        });
    }

    /**
     * Sets a new or replaces an existing listener for bottom control events.
     */
    public void setListener(BottomControlsListener listener) {
        mListener = listener;
    }

    /**
     * Sets the visibility of the edit button.
     */
    public void setEditButtonVisibility(boolean visible) {
        setVisibility(mEditButton, visible);
    }

    /**
     * Sets the visibility of the view-photosphere button.
     *
     * @param one of {@link #VIEW_NONE}, {@link #VIEW_PHOTO_SPHERE},
     *            {@link #VIEW_RGBZ}.
     */
    public void setViewButtonVisibility(int state) {
        if (state == VIEW_NONE) {
            setVisibility(mViewButton, false);
            return;
        }
        mViewButton.setImageResource(getViewButtonResource(state));
        setVisibility(mViewButton, true);

    }

    /**
     * Sets the visibility of the tiny-planet button.
     */
    public void setTinyPlanetButtonVisibility(final boolean visible) {
        setVisibility(mTinyPlanetButton, visible);
    }

    /**
     * Sets the visibility of the given view.
     */
    private static void setVisibility(final View view, final boolean visible) {
        view.post(new Runnable() {
            @Override
            public void run() {
                view.setVisibility(visible ? View.VISIBLE
                        : View.INVISIBLE);
            }
        });
    }

    private int getViewButtonResource(int state) {
        switch (state) {
            case VIEW_RGBZ:
                return R.drawable.ic_view_rgbz;
            case VIEW_PHOTO_SPHERE:
            default:
                return R.drawable.ic_view_photosphere;
        }
    }

    @Override
    public void onActionBarVisibilityChanged(boolean isVisible) {
        // TODO: Fade in and out
        setVisibility(isVisible ? VISIBLE : INVISIBLE);
    }
}
