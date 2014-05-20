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

package com.android.camera;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;

import com.android.camera.app.CameraApp;
import com.android.camera.app.CameraAppUI;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera2.R;

/**
 * This class centralizes the logic of how bottom bar should be laid out and how
 * preview should be transformed. The two things that could affect bottom bar layout
 * and preview transform are: window size and preview aspect ratio. Once these two
 * things are set, the layout of bottom bar and preview rect will be calculated
 * and can then be queried anywhere inside the app.
 *
 * Note that this helper assumes that preview TextureView will be laid out full
 * screen, meaning all its ascendants are laid out with MATCH_PARENT flags. If
 * or when this assumption is no longer the case, we need to revisit this logic.
 */
public class CaptureLayoutHelper implements CameraAppUI.NonDecorWindowSizeChangedListener,
        PreviewStatusListener.PreviewAspectRatioChangedListener {

    private final int mBottomBarMinHeight;
    private final int mBottomBarMaxHeight;
    private final int mBottomBarOptimalHeight;

    private int mWindowWidth = 0;
    private int mWindowHeight = 0;
    /** Aspect ratio of preview. It could be 0, meaning match the screen aspect ratio,
     * or a float value no less than 1f.
     */
    private float mAspectRatio = TextureViewHelper.MATCH_SCREEN;
    private PositionConfiguration mPositionConfiguration = null;
    private int mRotation = 0;
    private boolean mShowBottomBar = true;

    /**
     * PositionConfiguration contains the layout info for bottom bar and preview
     * rect, as well as whether bottom bar should be overlaid on top of preview.
     */
    public static final class PositionConfiguration {
        /**
         * This specifies the rect of preview on screen.
         */
        public final RectF mPreviewRect = new RectF();
        /**
         * This specifies the rect where bottom bar should be laid out in.
         */
        public final RectF mBottomBarRect = new RectF();
        /**
         * This indicates whether bottom bar should overlay itself on top of preview.
         */
        public boolean mBottomBarOverlay = false;
    }

    public CaptureLayoutHelper(int bottomBarMinHeight, int bottomBarMaxHeight,
            int bottomBarOptimalHeight) {
        mBottomBarMinHeight = bottomBarMinHeight;
        mBottomBarMaxHeight = bottomBarMaxHeight;
        mBottomBarOptimalHeight = bottomBarOptimalHeight;
    }

    @Override
    public void onPreviewAspectRatioChanged(float aspectRatio) {
        if (mAspectRatio == aspectRatio) {
            return;
        }
        mAspectRatio = aspectRatio;
        updatePositionConfiguration();
    }

    /**
     * Sets whether bottom bar will show or not. This will affect the calculation
     * of uncovered preview area, which is used to lay out mode list, mode options,
     * etc.
     */
    public void setShowBottomBar(boolean showBottomBar) {
        mShowBottomBar = showBottomBar;
    }

    /**
     * Updates bottom bar rect and preview rect. This gets called whenever
     * preview aspect ratio changes or main activity layout size changes.
     */
    private void updatePositionConfiguration() {
        if (mWindowWidth == 0 || mWindowHeight == 0) {
            return;
        }
        mPositionConfiguration = getPositionConfiguration(mWindowWidth, mWindowHeight, mAspectRatio,
                mRotation);
    }

    /**
     * Returns the rect that bottom bar should be laid out in. If not enough info
     * has been provided to calculate this, return an empty rect. Note that the rect
     * returned is relative to the content layout of the activity. It may need to be
     * translated based on the parent view's location.
     */
    public RectF getBottomBarRect() {
        if (mPositionConfiguration == null) {
            updatePositionConfiguration();
        }
        // Not enough info to create a position configuration.
        if (mPositionConfiguration == null) {
            return new RectF();
        }
        return new RectF(mPositionConfiguration.mBottomBarRect);
    }

    /**
     * Returns the rect that preview should occupy based on aspect ratio. If not
     * enough info has been provided to calculate this, return an empty rect. Note
     * that the rect returned is relative to the content layout of the activity.
     * It may need to be translated based on the parent view's location.
     */
    public RectF getPreviewRect() {
        if (mPositionConfiguration == null) {
            updatePositionConfiguration();
        }
        // Not enough info to create a position configuration.
        if (mPositionConfiguration == null) {
            return new RectF();
        }
        return new RectF(mPositionConfiguration.mPreviewRect);
    }

    /**
     * This returns the rect that is available to display the preview, and
     * capture buttons
     * 
     * @return the rect.
     */
    public RectF getFullscreenRect() {
        return new RectF(0, 0, mWindowWidth, mWindowHeight);
    }

    /**
     * Returns the sub-rect of the preview that is not being blocked by the
     * bottom bar. This can be used to lay out mode options, settings button,
     * etc. If not enough info has been provided to calculate this, return an
     * empty rect. Note that the rect returned is relative to the content layout
     * of the activity. It may need to be translated based on the parent view's
     * location.
     */
    public RectF getUncoveredPreviewRect() {
        if (mPositionConfiguration == null) {
            updatePositionConfiguration();
        }
        // Not enough info to create a position configuration.
        if (mPositionConfiguration == null) {
            return new RectF();
        }

        if (!RectF.intersects(mPositionConfiguration.mBottomBarRect,
                mPositionConfiguration.mPreviewRect) || !mShowBottomBar) {
            return mPositionConfiguration.mPreviewRect;
        }

        if (mWindowHeight > mWindowWidth) {
            // Portrait.
            if (mRotation >= 180) {
                // Reverse portrait, bottom bar align top.
                return new RectF(mPositionConfiguration.mPreviewRect.left,
                        mPositionConfiguration.mBottomBarRect.bottom,
                        mPositionConfiguration.mPreviewRect.right,
                        mPositionConfiguration.mPreviewRect.bottom);
            } else {
                return new RectF(mPositionConfiguration.mPreviewRect.left,
                        mPositionConfiguration.mPreviewRect.top,
                        mPositionConfiguration.mPreviewRect.right,
                        mPositionConfiguration.mBottomBarRect.top);
            }
        } else {
            if (mRotation >= 180) {
                // Reverse landscape, bottom bar align left.
                return new RectF(mPositionConfiguration.mBottomBarRect.right,
                        mPositionConfiguration.mPreviewRect.top,
                        mPositionConfiguration.mPreviewRect.right,
                        mPositionConfiguration.mPreviewRect.bottom);
            } else {
                return new RectF(mPositionConfiguration.mPreviewRect.left,
                        mPositionConfiguration.mPreviewRect.top,
                        mPositionConfiguration.mBottomBarRect.left,
                        mPositionConfiguration.mPreviewRect.bottom);
            }
        }
    }

    /**
     * Returns whether the bottom bar should be transparent and overlaid on top
     * of the preview.
     */
    public boolean shouldOverlayBottomBar() {
        if (mPositionConfiguration == null) {
            updatePositionConfiguration();
        }
        // Not enough info to create a position configuration.
        if (mPositionConfiguration == null) {
            return false;
        }
        return mPositionConfiguration.mBottomBarOverlay;
    }

    @Override
    public void onNonDecorWindowSizeChanged(int width, int height, int rotation) {
        mWindowWidth = width;
        mWindowHeight = height;
        mRotation = rotation;
        updatePositionConfiguration();
    }

    /**
     * Calculates the layout rect of bottom bar and the size of preview based on
     * activity layout width, height and aspect ratio.
     *
     * @param width width of the main activity layout, excluding system decor such
     *              as status bar, nav bar, etc.
     * @param height height of the main activity layout, excluding system decor
     *               such as status bar, nav bar, etc.
     * @param previewAspectRatio aspect ratio of the preview
     * @param rotation rotation from the natural orientation
     * @return a custom position configuration that contains bottom bar rect,
     *         preview rect and whether bottom bar should be overlaid.
     */
    private PositionConfiguration getPositionConfiguration(int width, int height,
            float previewAspectRatio, int rotation) {
        boolean landscape = width > height;

        // If the aspect ratio is defined as fill the screen, then preview should
        // take the screen rect.
        PositionConfiguration config = new PositionConfiguration();
        if (previewAspectRatio == TextureViewHelper.MATCH_SCREEN) {
            config.mPreviewRect.set(0, 0, width, height);
            config.mBottomBarOverlay = true;
            if (landscape) {
                config.mBottomBarRect.set(width - mBottomBarOptimalHeight, 0, width, height);
            } else {
                config.mBottomBarRect.set(0, height - mBottomBarOptimalHeight, width, height);
            }
        } else {
            if (previewAspectRatio < 1) {
                previewAspectRatio = 1 / previewAspectRatio;
            }
            // Get the bottom bar width and height.
            float barSize;
            int longerEdge = Math.max(width, height);
            int shorterEdge = Math.min(width, height);

            // Check the remaining space if fit short edge.
            float spaceNeededAlongLongerEdge = shorterEdge * previewAspectRatio;
            float remainingSpaceAlongLongerEdge = longerEdge - spaceNeededAlongLongerEdge;

            float previewShorterEdge;
            float previewLongerEdge;
            if (remainingSpaceAlongLongerEdge <= 0) {
                // Preview aspect ratio > screen aspect ratio: fit longer edge.
                previewLongerEdge = longerEdge;
                previewShorterEdge = longerEdge / previewAspectRatio;
                barSize = mBottomBarOptimalHeight;
                config.mBottomBarOverlay = true;

                if (landscape) {
                    config.mPreviewRect.set(0, height / 2 - previewShorterEdge / 2, previewLongerEdge,
                            height / 2 + previewShorterEdge / 2);
                    config.mBottomBarRect.set(width - barSize, height / 2 - previewShorterEdge / 2,
                            width, height / 2 + previewShorterEdge / 2);
                } else {
                    config.mPreviewRect.set(width / 2 - previewShorterEdge / 2, 0,
                            width / 2 + previewShorterEdge / 2, previewLongerEdge);
                    config.mBottomBarRect.set(width / 2 - previewShorterEdge / 2, height - barSize,
                            width / 2 + previewShorterEdge / 2, height);
                }
            } else if (previewAspectRatio > 14f / 9f) {
                // If the preview aspect ratio is large enough, simply offset the
                // preview to the bottom/right.
                // TODO: This logic needs some refinement.
                barSize = mBottomBarOptimalHeight;
                previewShorterEdge = shorterEdge;
                previewLongerEdge = shorterEdge * previewAspectRatio;
                config.mBottomBarOverlay = true;
                if (landscape) {
                    float right = width;
                    float left = right - previewLongerEdge;
                    config.mPreviewRect.set(left, 0, right, previewShorterEdge);
                    config.mBottomBarRect.set(width - barSize, 0, width, height);
                } else {
                    float bottom = height;
                    float top = bottom - previewLongerEdge;
                    config.mPreviewRect.set(0, top, previewShorterEdge, bottom);
                    config.mBottomBarRect.set(0, height - barSize, width, height);
                }
            } else if (remainingSpaceAlongLongerEdge <= mBottomBarMinHeight) {
                // Need to scale down the preview to fit in the space excluding the bottom bar.
                previewLongerEdge = longerEdge - mBottomBarMinHeight;
                previewShorterEdge = previewLongerEdge / previewAspectRatio;
                barSize = mBottomBarMinHeight;
                config.mBottomBarOverlay = false;
                if (landscape) {
                    config.mPreviewRect.set(0, height / 2 - previewShorterEdge / 2, previewLongerEdge,
                            height / 2 + previewShorterEdge / 2);
                    config.mBottomBarRect.set(width - barSize, height / 2 - previewShorterEdge / 2,
                            width, height / 2 + previewShorterEdge / 2);
                } else {
                    config.mPreviewRect.set(width / 2 - previewShorterEdge / 2, 0,
                            width / 2 + previewShorterEdge / 2, previewLongerEdge);
                    config.mBottomBarRect.set(width / 2 - previewShorterEdge / 2, height - barSize,
                            width / 2 + previewShorterEdge / 2, height);
                }
            } else {
                // Fit shorter edge.
                barSize = remainingSpaceAlongLongerEdge <= mBottomBarMaxHeight ?
                        remainingSpaceAlongLongerEdge : mBottomBarMaxHeight;
                previewShorterEdge = shorterEdge;
                previewLongerEdge = shorterEdge * previewAspectRatio;
                config.mBottomBarOverlay = false;
                if (landscape) {
                    float right = width - barSize;
                    float left = right - previewLongerEdge;
                    config.mPreviewRect.set(left, 0, right, previewShorterEdge);
                    config.mBottomBarRect.set(width - barSize, 0, width, height);
                } else {
                    float bottom = height - barSize;
                    float top = bottom - previewLongerEdge;
                    config.mPreviewRect.set(0, top, previewShorterEdge, bottom);
                    config.mBottomBarRect.set(0, height - barSize, width, height);
                }
            }
        }

        if (rotation >= 180) {
            // Rotate 180 degrees.
            Matrix rotate = new Matrix();
            rotate.setRotate(180, width / 2, height / 2);

            rotate.mapRect(config.mPreviewRect);
            rotate.mapRect(config.mBottomBarRect);
        }

        // Round the rect first to avoid rounding errors later on.
        round(config.mBottomBarRect);
        round(config.mPreviewRect);

        return config;
    }

    /**
     * Round the float coordinates in the given rect, and store the rounded value
     * back in the rect.
     */
    public static void round(RectF rect) {
        if (rect == null) {
            return;
        }
        float left = Math.round(rect.left);
        float top = Math.round(rect.top);
        float right = Math.round(rect.right);
        float bottom = Math.round(rect.bottom);
        rect.set(left, top, right, bottom);
    }
}
