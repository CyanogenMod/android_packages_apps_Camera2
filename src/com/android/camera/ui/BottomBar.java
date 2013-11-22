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
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;

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
public class BottomBar extends FrameLayout {
    private final int mPaddingStart;
    private final int mPaddingEnd;

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
    }

    /**
     * Adjust layout orientation, width, height and gravity based on new orientation.
     */
    private void adjustSelf(Configuration configuration) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        int shortEdge;
        if (lp.width != FrameLayout.LayoutParams.MATCH_PARENT) {
            shortEdge = lp.width;
        } else {
            shortEdge = lp.height;
        }
        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            lp.width = FrameLayout.LayoutParams.MATCH_PARENT;
            lp.height = shortEdge;
            lp.gravity = Gravity.BOTTOM;
            setLayoutParams(lp);
        } else {
            lp.height = FrameLayout.LayoutParams.MATCH_PARENT;
            lp.width = shortEdge;
            lp.gravity = Gravity.RIGHT;
            setLayoutParams(lp);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        adjustSelf(config);
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

}
