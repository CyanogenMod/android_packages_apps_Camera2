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

package com.android.camera.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;
import android.view.View;

import com.android.camera2.R;

/**
 * This class encapsulates the logic of drawing different states of the icon in
 * mode drawer for when it is highlighted (to indicate the current module), or when
 * it is selected by the user. It handles the internal state change like a state
 * list drawable. The advantage over a state list drawable is that in the class
 * multiple states can be rendered using the same drawable with some color modification,
 * whereas a state list drawable would require a different drawable for each state.
 */
public class ModeIconView extends View {

    private boolean mHighlightIsOn = false;
    private final GradientDrawable mBackground;
    private final GradientDrawable mHighlightDrawable;
    private final int mIconBackgroundSize;
    private int mHighlightColor;
    private final int mBackgroundDefaultColor;
    private final int mIconDrawableSize;
    private Drawable mIconDrawable = null;

    public ModeIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mBackgroundDefaultColor = getResources().getColor(R.color.mode_selector_icon_background);
        mIconBackgroundSize = getResources().getDimensionPixelSize(
                R.dimen.mode_selector_icon_block_width);
        mBackground = (GradientDrawable) getResources()
                .getDrawable(R.drawable.mode_icon_background).mutate();
        mBackground.setBounds(0, 0, mIconBackgroundSize, mIconBackgroundSize);
        mHighlightDrawable = (GradientDrawable) getResources()
                .getDrawable(R.drawable.mode_icon_highlight).mutate();
        mHighlightDrawable.setBounds(0, 0, mIconBackgroundSize, mIconBackgroundSize);
        mIconDrawableSize = getResources().getDimensionPixelSize(
                R.dimen.mode_selector_icon_drawable_size);
    }

    /**
     * Sets the drawable that shows the icon of the mode.
     *
     * @param drawable drawable of the mode icon
     */
    public void setIconDrawable(Drawable drawable) {
        mIconDrawable = drawable;
        // Center icon in the background.
        if (mIconDrawable != null) {
            mIconDrawable.setBounds(mIconBackgroundSize / 2 - mIconDrawableSize / 2,
                    mIconBackgroundSize / 2 - mIconDrawableSize / 2,
                    mIconBackgroundSize / 2 + mIconDrawableSize / 2,
                    mIconBackgroundSize / 2 + mIconDrawableSize / 2);
            invalidate();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        mBackground.draw(canvas);
        if (mHighlightIsOn) {
            mHighlightDrawable.draw(canvas);
        }
        if (mIconDrawable != null) {
            mIconDrawable.draw(canvas);
        }

    }

    /**
     * This gets called when the selected state is changed. When selected, the background
     * drawable will use a solid pre-defined color to indicate selection.
     *
     * @param selected true when selected, false otherwise.
     */
    public void setSelected(boolean selected) {
        if (selected) {
            mBackground.setColor(mHighlightColor);
            mHighlightIsOn = false;
        } else {
            mBackground.setColor(mBackgroundDefaultColor);
        }
        invalidate();
    }

    /**
     * This gets called when the highlighted state is changed. When highlighted,
     * a ring shaped drawable of a solid pre-defined color will be drawn on top
     * of the background drawable to indicate highlight state.
     *
     * @param highlighted true when highlighted, false otherwise.
     */
    public void setHighlighted(boolean highlighted) {
        mHighlightIsOn = highlighted;
        invalidate();
    }

    /**
     * Sets the color that will be used in the drawable for highlight state.
     *
     * @param highlightColor color for the highlight state
     */
    public void setHighlightColor(int highlightColor) {
        mHighlightColor = highlightColor;
        mHighlightDrawable.setColor(highlightColor);
    }
}
