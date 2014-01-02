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
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * TopRightWeightedLayout is a LinearLayout that reorders its
 * children such that the right most child is the top most child
 * on an orientation change.
 *
 * This container also evenly spaces it's children by maintaining
 * a layout weight of 1, and centers the children in the opposite
 * direction of the layout orientation.
 */
public class TopRightWeightedLayout extends LinearLayout {

    public TopRightWeightedLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onFinishInflate() {
        Configuration configuration = getContext().getResources().getConfiguration();
        checkOrientation(configuration.orientation);
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        checkOrientation(configuration.orientation);
    }

    /**
     * Set the orientation of this layout if it has changed,
     * and center the elements based on the new orientation.
     */
    private void checkOrientation(int orientation) {
        final boolean isHorizontal = LinearLayout.HORIZONTAL == getOrientation();
        final boolean isPortrait = Configuration.ORIENTATION_PORTRAIT == orientation;
        if (isPortrait && !isHorizontal) {
            // Portrait orientation is out of sync, setting to horizontal
            // and reversing children
            setOrientation(LinearLayout.HORIZONTAL);
            reverseChildren();
            for (int i = 0; i < getChildCount(); i++) {
                ViewGroup.LayoutParams params = getChildAt(i).getLayoutParams();
                params.width = 0;
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            }
            requestLayout();
        } else if (!isPortrait && isHorizontal) {
            // Landscape orientation is out of sync, setting to vertical
            // and reversing children
            setOrientation(LinearLayout.VERTICAL);
            reverseChildren();
            for (int i = 0; i < getChildCount(); i++) {
                ViewGroup.LayoutParams params = getChildAt(i).getLayoutParams();
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = 0;
            }
            requestLayout();
        }
    }

    /**
     * Reverse the ordering of the children in this layout.
     * Note: bringChildToFront produced a non-deterministic ordering.
     */
    private void reverseChildren() {
        List<View> children = new ArrayList<View>();
        for (int i = getChildCount() - 1; i >= 0; i--) {
            children.add(getChildAt(i));
        }
        for (View v : children) {
            bringChildToFront(v);
        }
    }
}