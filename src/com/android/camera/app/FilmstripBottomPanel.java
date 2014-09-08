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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.camera.util.Gusterpolator;
import com.android.camera.widget.Cling;
import com.android.camera.widget.ExternalViewerButton;
import com.android.camera2.R;

/**
 * Shows controls at the bottom of the screen for editing, viewing a photo
 * sphere image and creating a tiny planet from a photo sphere image.
 */
class FilmstripBottomPanel implements CameraAppUI.BottomPanel {
    private static final int ANIM_DURATION = 150;

    private final AppController mController;
    private final ViewGroup mLayout;
    private Listener mListener;
    private final View mControlLayout;
    private ImageButton mEditButton;
    private ExternalViewerButton mViewButton;
    private ImageButton mDeleteButton;
    private ImageButton mShareButton;
    private final View mMiddleFiller;
    private View mProgressLayout;
    private TextView mProgressText;
    private View mProgressErrorLayout;
    private TextView mProgressErrorText;
    private ProgressBar mProgressBar;
    private boolean mTinyPlanetEnabled;

    public FilmstripBottomPanel(AppController controller, ViewGroup bottomControlsLayout) {
        mController = controller;
        mLayout = bottomControlsLayout;
        mMiddleFiller = mLayout.findViewById(R.id.filmstrip_bottom_control_middle_filler);
        mControlLayout = mLayout.findViewById(R.id.bottom_control_panel);
        setupEditButton();
        setupViewButton();
        setupDeleteButton();
        setupShareButton();
        setupProgressUi();
    }

    @Override
    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public void setClingForViewer(int viewerType, Cling cling) {
        mViewButton.setClingForViewer(viewerType, cling);
    }

    @Override
    public void clearClingForViewer(int viewerType) {
        mViewButton.clearClingForViewer(viewerType);
    }

    @Override
    public Cling getClingForViewer(int viewerType) {
        return mViewButton.getClingForViewer(viewerType);
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
        mViewButton.setState(state);
        updateMiddleFillerLayoutVisibility();
    }

    @Override
    public void setViewEnabled(boolean enabled) {
        mViewButton.setEnabled(enabled);
    }

    @Override
    public void setTinyPlanetEnabled(boolean enabled) {
        mTinyPlanetEnabled = enabled;
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

    @Override
    public void setProgressText(CharSequence text) {
        mProgressText.setText(text);
    }

    @Override
    public void setProgress(int progress) {
        mProgressBar.setProgress(progress);
    }

    @Override
    public void showProgressError(CharSequence message) {
        hideControls();
        hideProgress();
        mProgressErrorLayout.setVisibility(View.VISIBLE);
        mProgressErrorText.setText(message);
    }

    @Override
    public void hideProgressError() {
        mProgressErrorLayout.setVisibility(View.INVISIBLE);
    }

    @Override
    public void showProgress() {
        mProgressLayout.setVisibility(View.VISIBLE);
        hideProgressError();
    }

    @Override
    public void hideProgress() {
        mProgressLayout.setVisibility(View.INVISIBLE);
    }

    @Override
    public void showControls() {
        mControlLayout.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideControls() {
        mControlLayout.setVisibility(View.INVISIBLE);
    }

    private void setupEditButton() {
        mEditButton = (ImageButton) mLayout.findViewById(R.id.filmstrip_bottom_control_edit);
        mEditButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mTinyPlanetEnabled) {
                    mController.openContextMenu(mEditButton);
                } else if (mListener != null) {
                    mListener.onEdit();
                }
            }
        });
        mController.registerForContextMenu(mEditButton);
        mEditButton.setLongClickable(false);
    }

    private void setupViewButton() {
        mViewButton = (ExternalViewerButton) mLayout.findViewById(
                R.id.filmstrip_bottom_control_view);
        mViewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener != null) {
                    mListener.onExternalViewer();
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

    private void setupProgressUi() {
        mProgressLayout = mLayout.findViewById(R.id.bottom_progress_panel);
        mProgressText = (TextView) mLayout.findViewById(R.id.bottom_session_progress_text);
        mProgressBar = (ProgressBar) mLayout.findViewById(R.id.bottom_session_progress_bar);
        mProgressBar.setMax(100);
        mProgressLayout.setVisibility(View.INVISIBLE);
        mProgressErrorText = (TextView) mLayout.findViewById(R.id.bottom_progress_error_text);
        mProgressErrorLayout = mLayout.findViewById(R.id.bottom_progress_error_panel);
        mProgressErrorLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onProgressErrorClicked();
                }
            }
        });
    }

    /**
     * Updates the visibility of the middle filler view in the center. The
     * middle filler view should be visible when edit button and viewer buttons
     * are both visible.
     */
    private void updateMiddleFillerLayoutVisibility() {
        if (mEditButton.getVisibility() == View.VISIBLE &&
                mViewButton.getVisibility() == View.VISIBLE) {
            mMiddleFiller.setVisibility(View.INVISIBLE);
        } else {
            mMiddleFiller.setVisibility(View.GONE);
        }
    }

    public void show() {
        ObjectAnimator animator = ObjectAnimator
                .ofFloat(mLayout, "translationY", mLayout.getHeight(), 0.0f);
        animator.setDuration(ANIM_DURATION);
        animator.setInterpolator(Gusterpolator.INSTANCE);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mViewButton.updateClingVisibility();
            }
        });
        mViewButton.hideClings();
        animator.start();
    }

    public void hide() {
        int offset = mLayout.getHeight();
        if (mLayout.getTranslationY() < offset) {
            ObjectAnimator animator = ObjectAnimator
                    .ofFloat(mLayout, "translationY", mLayout.getTranslationY(), offset);
            animator.setDuration(ANIM_DURATION);
            animator.setInterpolator(Gusterpolator.INSTANCE);
            mViewButton.hideClings();
            animator.start();
        }
    }
}
