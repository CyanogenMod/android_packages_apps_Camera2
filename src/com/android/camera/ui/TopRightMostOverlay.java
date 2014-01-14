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
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.android.camera2.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out a single child to match a reference layout's topmost or
 * rightmost child.
 */
public class TopRightMostOverlay extends FrameLayout {
    private View mReferenceView;

    public TopRightMostOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private View.OnLayoutChangeListener mLayoutChangeListener =
        new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                requestLayout();
            }
        };

    public void setReferenceViewParent(ViewGroup parent) {
        if (parent == null) {
            throw new IllegalArgumentException("Reference parent cannot be null.");
        }

        if (parent.getChildCount() > 0) {
            int orientation = getContext().getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                // Get right most view.
                mReferenceView = parent.getChildAt(parent.getChildCount()-1);
            } else {
                // Get top most view.
                mReferenceView = parent.getChildAt(0);
            }
            mReferenceView.addOnLayoutChangeListener(mLayoutChangeListener);
            requestLayout();
        }
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mReferenceView != null && getChildCount() > 0) {
            View child = getChildAt(0);
            // Match the reference view dimensions exactly.
            child.layout(mReferenceView.getLeft(), mReferenceView.getTop(),
                         mReferenceView.getRight(), mReferenceView.getBottom());
        }
    }
}
