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

package com.android.camera.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

/**
 * This class defines a generic cling that provides on-screen instructions. A convenient
 * method is provided here to automatically adjust the position of the cling to
 * always be consistent with the reference view. The use of the convenient method
 * is optional.
 */
public class Cling extends TextView {

    private View mReferenceView = null;
    private final int[] mLocation = new int[2];
    private final OnLayoutChangeListener mLayoutChangeListener =
            new OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    mDelayDrawingUntilNextLayout = false;
                    // Reference view has changed layout.
                    adjustPosition();
                }
            };
    private boolean mDelayDrawingUntilNextLayout = false;

    public Cling(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public Cling(Context context) {
        super(context);
    }

    /**
     * Layout on top of a reference view.
     */
    public void setReferenceView(View v) {
        if (v == null) {
            if (mReferenceView != null) {
                // Clear up existing listeners
                mReferenceView.removeOnLayoutChangeListener(mLayoutChangeListener);
                mReferenceView = null;
            }
            return;
        }
        mReferenceView = v;
        mReferenceView.addOnLayoutChangeListener(mLayoutChangeListener);
        if (mReferenceView.getVisibility() == GONE) {
            mDelayDrawingUntilNextLayout = true;
        } else {
            adjustPosition();
        }
    }

    /**
     * Adjust the translation of the cling to stay on top of the reference view.
     */
    public void adjustPosition() {
        if (mReferenceView == null) {
            return;
        }
        mReferenceView.getLocationInWindow(mLocation);
        int refCenterX = mLocation[0] + mReferenceView.getWidth() / 2;
        int refTopY = mLocation[1];
        // Align center with the reference view and move on top
        int left = refCenterX - getWidth() / 2;
        int top = refTopY - getHeight();

        getLocationInWindow(mLocation);
        int currentLeft = mLocation[0] - (int) getTranslationX();
        int currentTop = mLocation[1] - (int) getTranslationY();

        setTranslationX(left - currentLeft);
        setTranslationY(top - currentTop);
    }

    @Override
    public void draw(Canvas canvas) {
        if (mDelayDrawingUntilNextLayout) {
            return;
        }
        super.draw(canvas);
    }
}
