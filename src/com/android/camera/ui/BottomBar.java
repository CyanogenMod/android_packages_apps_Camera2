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
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.view.MotionEvent;

import com.android.camera2.R;

/**
 * BottomBar swaps its width and height on rotation. In addition, it also changes
 * gravity and layout orientation based on the new orientation. Specifically, in
 * landscape it aligns to the right side of its parent and lays out its children
 * vertically, whereas in portrait, it stays at the bottom of the parent and has
 * a horizontal layout orientation.
 *
 * In addition to adjusting itself, this class also makes sure its children are
 * always spaced evenly in the new orientation.
 */
public class BottomBar extends FrameLayout
        implements PreviewStatusListener.PreviewSizeChangedListener {
    private static final String TAG = "BottomBar";
    private final int mPaddingStart;
    private final int mPaddingEnd;
    private int mWidth;
    private int mHeight;
    private float mOffsetShorterEdge;
    private float mOffsetLongerEdge;

    private final int mOptimalHeight;
    private boolean mOverLayBottomBar;

    public BottomBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            mPaddingStart = getPaddingTop();
            mPaddingEnd = getPaddingBottom();
        } else {
            // Portrait mode
            mPaddingStart = getPaddingLeft();
            mPaddingEnd = getPaddingRight();
        }
        mOptimalHeight = getResources().getDimensionPixelSize(R.dimen.bottom_bar_height_optimal);
    }

    /**
     * Sets the bottom bar buttons given a layout id
     */
    public void setButtonLayout(int buttonLayoutId) {
        removeAllViews();
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(buttonLayoutId, this, true);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mWidth = MeasureSpec.getSize(widthMeasureSpec);
        mHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (mWidth == 0 || mHeight == 0) {
            return;
        }

        if (mOffsetShorterEdge != 0 && mOffsetLongerEdge != 0) {
            float previewAspectRatio =
                    mOffsetLongerEdge / mOffsetShorterEdge;
            if (previewAspectRatio < 1.0) {
                previewAspectRatio = 1.0f/previewAspectRatio;
            }
            float screenAspectRatio = (float) mWidth / (float) mHeight;
            if (screenAspectRatio < 1.0) {
                screenAspectRatio = 1.0f/screenAspectRatio;
            }
            if (previewAspectRatio >= screenAspectRatio) {
                mOverLayBottomBar = true;
                setAlpha(0.5f);
            } else {
                mOverLayBottomBar = false;
                setAlpha(1.0f);
            }
        }

        // Calculates the width and height needed for the bar.
        int barWidth, barHeight;
        if (mWidth > mHeight) {
            ((LayoutParams) getLayoutParams()).gravity = Gravity.RIGHT;
            if ((mOffsetLongerEdge == 0 && mOffsetShorterEdge == 0) || mOverLayBottomBar) {
                barWidth = mOptimalHeight;
                barHeight = mHeight;
            } else {
                barWidth = (int) (mWidth - mOffsetLongerEdge);
                barHeight = mHeight;
            }
        } else {
            ((LayoutParams) getLayoutParams()).gravity = Gravity.BOTTOM;
            if ((mOffsetLongerEdge == 0 && mOffsetShorterEdge == 0) || mOverLayBottomBar) {
                barWidth = mWidth;
                barHeight = mOptimalHeight;
            } else {
                barWidth = mWidth;
                barHeight = (int) (mHeight - mOffsetLongerEdge);
            }
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(barWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY));
    }

    private void adjustBottomBar(float scaledTextureWidth,
                                 float scaledTextureHeight) {
        setOffset(scaledTextureWidth, scaledTextureHeight);
    }

    @Override
    public void onPreviewSizeChanged(float scaledTextureWidth,
                                     float scaledTextureHeight) {
        adjustBottomBar(scaledTextureWidth, scaledTextureHeight);
    }

    private void setOffset(float scaledTextureWidth, float scaledTextureHeight) {
        float offsetLongerEdge, offsetShorterEdge;
        if (scaledTextureHeight > scaledTextureWidth) {
            offsetLongerEdge = scaledTextureHeight;
            offsetShorterEdge = scaledTextureWidth;
        } else {
            offsetLongerEdge = scaledTextureWidth;
            offsetShorterEdge = scaledTextureHeight;
        }
        if (mOffsetLongerEdge != offsetLongerEdge || mOffsetShorterEdge != offsetShorterEdge) {
            mOffsetLongerEdge = offsetLongerEdge;
            mOffsetShorterEdge = offsetShorterEdge;
            requestLayout();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
    }

    /**
     * Custom layout call that aims to space all children evenly in the given rect.
     */
    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int childCount = getChildCount();
        if (childCount == 0) {
            return;
        }
        // Convert parent coordinates into child coordinates
        right -= left;
        bottom -= top;
        left = 0;
        top = 0;

        // Evenly space all children
        if (bottom < right) {
            // Portrait mode
            int centerY = (top + bottom) / 2;
            int perChildHorizontalSpace = (right - left - mPaddingStart - mPaddingEnd) / childCount;
            for (int i = 0; i < childCount; i++) {
                int centerX = mPaddingStart + perChildHorizontalSpace * i
                        + perChildHorizontalSpace / 2;
                View child = getChildAt(i);
                int childWidth = child.getMeasuredWidth();
                int childHeight = child.getMeasuredHeight();
                child.layout(centerX - childWidth / 2, centerY - childHeight / 2,
                        centerX + childWidth / 2, centerY + childHeight / 2);
            }
        } else {
            // Landscape
            int centerX = (left + right) / 2;
            int perChildVerticalSpace = (bottom - top - mPaddingStart - mPaddingEnd) / childCount;
            // Layout children from bottom up so that they remain nearly the same position
            // as when they were in portrait
            for (int i = 0; i < childCount; i++) {
                int centerY = mPaddingStart + perChildVerticalSpace * (childCount - 1 - i)
                        + perChildVerticalSpace / 2;
                View child = getChildAt(i);
                int childWidth = child.getMeasuredWidth();
                int childHeight = child.getMeasuredHeight();
                child.layout(centerX - childWidth / 2, centerY - childHeight / 2,
                        centerX + childWidth / 2, centerY + childHeight / 2);
            }
        }
    }

    // prevent touches on bottom bar (not its children)
    // from triggering a touch event on preview area
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }
}
