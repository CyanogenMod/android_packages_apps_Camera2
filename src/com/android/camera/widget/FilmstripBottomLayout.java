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

package com.android.camera.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import com.android.camera.filmstrip.BottomControls;
import com.android.camera2.R;

/**
 * Shows controls at the bottom of the screen for editing, viewing a photo
 * sphere image and creating a tiny planet from a photo sphere image.
 */
public class FilmstripBottomLayout extends RelativeLayout
        implements BottomControls {

    private Listener mListener;
    private ImageButton mEditButton;
    private ImageButton mViewButton;
    private ImageButton mTinyPlanetButton;

    public FilmstripBottomLayout(Context context, AttributeSet attrs) {
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

    @Override
    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public void setEditButtonVisibility(boolean visible) {
        setVisibility(mEditButton, visible);
    }

    @Override
    public void setViewButtonVisibility(int state) {
        if (state == VIEW_NONE) {
            setVisibility(mViewButton, false);
            return;
        }
        mViewButton.setImageResource(getViewButtonResource(state));
        setVisibility(mViewButton, true);

    }

    @Override
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
