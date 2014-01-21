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
    private View  mViewerWrapperLayout;
    private ImageButton mTinyPlanetButton;
    private ImageButton mDeleteButton;
    private ImageButton mShareButton;
    private View mMiddleFiller;

    public FilmstripBottomControls(ViewGroup bottomControlsLayout) {
        mLayout = bottomControlsLayout;
        mMiddleFiller = mLayout.findViewById(R.id.filmstrip_bottom_control_middle_filler);
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
        mEditButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        updateMiddleFillerLayoutVisibility();
    }

    @Override
    public void setEditEnabled(boolean enabled) {
        mEditButton.setEnabled(enabled);
    }

    @Override
    public void setViewerButtonVisibility(int state) {
        if (state == VIEWER_NONE) {
            mViewButton.setVisibility(View.GONE);
        } else {
            mViewButton.setImageResource(getViewButtonResource(state));
            mViewButton.setVisibility(View.VISIBLE);
        }
        updateMiddleFillerLayoutVisibility();
    }

    @Override
    public void setViewEnabled(boolean enabled) {
        mViewButton.setEnabled(enabled);
    }

    @Override
    public void setTinyPlanetButtonVisibility(final boolean visible) {
        mTinyPlanetButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        updateMiddleFillerLayoutVisibility();
    }

    @Override
    public void setTinyPlanetEnabled(boolean enabled) {
        mTinyPlanetButton.setEnabled(enabled);
    }

    @Override
    public void setDeleteButtonVisibility(boolean visible) {
        mDeleteButton.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public void setDeleteEnabled(boolean enabled) {
        mDeleteButton.setEnabled(enabled);
    }

    @Override
    public void setShareButtonVisibility(boolean visible) {
        mShareButton.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public void setShareEnabled(boolean enabled) {
        mShareButton.setEnabled(enabled);
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
        mViewerWrapperLayout = mLayout.findViewById(R.id.filmstrip_bottom_control_viewer_wrapper);
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

    /**
     * Updates the visibility of the middle filler view in the center and the
     * visibility of wrapper layout of viewer buttons and the tinyplanet button.
     * The middle filler view should be visible when edit button and viewer
     * buttons are both visible. The wrapper layout of viewer buttons and
     * tinyplanet should be gone if no viewer button is shown and the
     * tinyplanet button is invisible.
     */
    private void updateMiddleFillerLayoutVisibility() {
        if (mViewButton.getVisibility() == View.VISIBLE ||
                mTinyPlanetButton.getVisibility() == View.VISIBLE) {
            mViewerWrapperLayout.setVisibility(View.VISIBLE);
        } else {
            mViewerWrapperLayout.setVisibility(View.GONE);
        }

        if (mEditButton.getVisibility() == View.VISIBLE &&
                mViewerWrapperLayout.getVisibility() == View.VISIBLE) {
            mMiddleFiller.setVisibility(View.INVISIBLE);
        } else {
            mMiddleFiller.setVisibility(View.GONE);
        }
    }
}
