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

package com.android.camera.app;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.android.camera2.R;

/**
 * Shows controls at the bottom of the screen for editing, viewing a photo
 * sphere image and creating a tiny planet from a photo sphere image.
 */
class FilmstripBottomControls implements CameraAppUI.BottomControls {

    private Listener mListener;
    private ViewGroup mLayout;
    private ImageButton mEditButton;
    private ImageButton mViewButton;
    private ImageButton mTinyPlanetButton;
    private ImageButton mDeleteButton;
    private ImageButton mShareButton;

    public FilmstripBottomControls(ViewGroup bottomControlsLayout) {
        mLayout = bottomControlsLayout;
        setupEditButton();
        setupViewButton();
        setupTinyPlanetButton();
        setupDeleteButton();
        setupShareButton();
    }

    @Override
    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            mLayout.setVisibility(View.VISIBLE);
        } else {
            mLayout.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void setEditButtonVisibility(boolean visible) {
        setVisibility(mEditButton, visible);
    }

    @Override
    public void setEditEnabled(boolean enabled) {
        mEditButton.setEnabled(enabled);
    }

    @Override
    public void setViewerButtonVisibility(int state) {
        if (state == VIEWER_NONE) {
            setVisibility(mViewButton, false);
            return;
        }
        mViewButton.setImageResource(getViewButtonResource(state));
        setVisibility(mViewButton, true);

    }

    @Override
    public void setViewEnabled(boolean enabled) {
        mViewButton.setEnabled(enabled);
    }

    @Override
    public void setTinyPlanetButtonVisibility(final boolean visible) {
        setVisibility(mTinyPlanetButton, visible);
    }

    @Override
    public void setTinyPlanetEnabled(boolean enabled) {
        mTinyPlanetButton.setEnabled(enabled);
    }

    @Override
    public void setDeleteButtonVisibility(boolean visible) {
        setVisibility(mDeleteButton, visible);
    }

    @Override
    public void setDeleteEnabled(boolean enabled) {
        mDeleteButton.setEnabled(enabled);
    }

    @Override
    public void setShareButtonVisibility(boolean visible) {
        setVisibility(mShareButton, visible);
    }

    @Override
    public void setShareEnabled(boolean enabled) {
        mShareButton.setEnabled(enabled);
    }

    /**
     * Sets the visibility of the given view.
     */
    private static void setVisibility(final View view, final boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    private int getViewButtonResource(int state) {
        switch (state) {
            case VIEWER_REFOCUS:
                return R.drawable.ic_refocus_normal;
            case VIEWER_PHOTO_SPHERE:
                return R.drawable.ic_view_photosphere;
            default:
                return R.drawable.ic_control_play;
        }
    }

    private void setupEditButton() {
        mEditButton = (ImageButton) mLayout.findViewById(R.id.filmstrip_bottom_control_edit);
        mEditButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener != null) {
                    mListener.onEdit();
                }
            }
        });
    }

    private void setupViewButton() {
        mViewButton = (ImageButton) mLayout.findViewById(R.id.filmstrip_bottom_control_view);
        mViewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener != null) {
                    mListener.onExternalViewer();
                }
            }
        });
    }

    private void setupTinyPlanetButton() {
        mTinyPlanetButton =
                (ImageButton) mLayout.findViewById(R.id.filmstrip_bottom_control_tiny_planet);
        mTinyPlanetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener != null) {
                    mListener.onTinyPlanet();
                }
            }
        });
    }

    private void setupDeleteButton() {
        mDeleteButton = (ImageButton) mLayout.findViewById(R.id.filmstrip_bottom_control_delete);
        mDeleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener != null) {
                    mListener.onDelete();
                }
            }
        });
    }

    private void setupShareButton() {
        mShareButton = (ImageButton) mLayout.findViewById(R.id.filmstrip_bottom_control_share);
        mShareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener != null) {
                    mListener.onShare();
                }
            }
        });
    }
}
