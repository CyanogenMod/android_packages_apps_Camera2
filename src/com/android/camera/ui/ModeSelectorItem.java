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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.camera.util.ApiHelper;
import com.android.camera2.R;

/**
 * This is a package private class, as it is not intended to be visible or used
 * outside of this package.
 *
 * ModeSelectorItem is a FrameLayout that contains an ImageView to display the
 * icon for the corresponding mode, a TextView that explains what the mode is,
 * and a GradientDrawable at the end of the TextView.
 *
 * The purpose of this class is to encapsulate different drawing logic into
 * its own class. There are two drawing mode, <code>TRUNCATE_TEXT_END</code>
 * and <code>TRUNCATE_TEXT_FRONT</code>. They define how we draw the view when
 * we display the view partially.
 */
class ModeSelectorItem extends FrameLayout {
    // Drawing modes that defines how the TextView should be drawn when there
    // is not enough space to draw the whole TextView.
    public static final int TRUNCATE_TEXT_END = 1;
    public static final int TRUNCATE_TEXT_FRONT = 2;

    private static final int SHADE_WIDTH_PIX = 100;

    private TextView mText;
    private ImageView mIcon;
    private int mVisibleWidth;
    private int mMinVisibleWidth;
    private GradientDrawable mGradientShade;

    private int mDrawingMode = TRUNCATE_TEXT_END;
    private int mHeight;
    private int mWidth;
    private int mDefaultBackgroundColor;
    private int mDefaultTextColor;
    private int mIconBlockColor;
    private final int mHighlightTextColor;

    public ModeSelectorItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        mHighlightTextColor = context.getResources()
                .getColor(R.color.mode_selector_text_highlight_color);
    }

    @Override
    public void onFinishInflate() {
        mIcon = (ImageView) findViewById(R.id.selector_icon);
        mText = (TextView) findViewById(R.id.selector_text);
        Typeface typeface;
        if (ApiHelper.HAS_ROBOTO_LIGHT_FONT) {
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL);
        } else {
            // Load roboto_light typeface from assets.
            typeface = Typeface.createFromAsset(getResources().getAssets(),
                    "Roboto-Light.ttf");
        }
        mText.setTypeface(typeface);
        mMinVisibleWidth = getResources()
                .getDimensionPixelSize(R.dimen.mode_selector_icon_block_width);
        mDefaultTextColor = mText.getCurrentTextColor();
    }

    public void setDefaultBackgroundColor(int color) {
        mDefaultBackgroundColor = color;
        setBackgroundColor(color);
    }

    @Override
    public void setBackgroundColor(int color) {
        super.setBackgroundColor(color);
        int startColor = 0x00FFFFFF & color;
        // Gradient shade will draw at the end of the item
        mGradientShade = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {startColor, color});
    }

    public void highlight() {
        mText.setTextColor(mHighlightTextColor);
        setBackgroundColor(mIconBlockColor);
    }

    public void unHighlight() {
        setBackgroundColor(mDefaultBackgroundColor);
        mText.setTextColor(mDefaultTextColor);
    }

    /**
     * When swiping in, we truncate the end of the item if the visible width
     * is not enough to show the whole item. When swiping out, we truncate the
     * front of the text (i.e. offset the text).
     *
     * @param swipeIn whether swiping direction is swiping in (i.e. from left
     *                to right)
     */
    public void onSwipeModeChanged(boolean swipeIn) {
        mDrawingMode = swipeIn ? TRUNCATE_TEXT_END : TRUNCATE_TEXT_FRONT;
        mText.setTranslationX(0);
    }

    public void setIconBackgroundColor(int color) {
        mIconBlockColor = color;
        mIcon.setBackgroundColor(color);
    }

    public void setText(CharSequence text) {
        mText.setText(text);
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mWidth = right - left;
        mHeight = bottom - top;
        if (changed && mVisibleWidth > 0) {
            // Reset mode list to full screen
            setVisibleWidth(mWidth);
            mDrawingMode = TRUNCATE_TEXT_FRONT;
        }
    }

    /**
     * Sets image resource as the icon for the mode.
     *
     * @param resource resource id of the asset to be used as icon
     */
    public void setImageResource(int resource) {
        mIcon.setImageResource(resource);
    }

    /**
     * Sets the visible width preferred for the item. The item can then decide how
     * to draw itself based on the visible width and whether it's being swiped in
     * or out. This function will be called on every frame during animation. It should
     * only do minimal work required to get the animation working.
     *
     * @param newWidth new visible width
     */
    public void setVisibleWidth(int newWidth) {
        newWidth = Math.max(newWidth, 0);
        // Visible width should not be greater than view width
        newWidth = Math.min(newWidth, mWidth);
        mVisibleWidth = newWidth;
        float transX = 0f;
        // If the given width is less than the icon width, we need to translate icon
        if (mVisibleWidth < mMinVisibleWidth) {
            transX = mMinVisibleWidth - mVisibleWidth;
        }
        setTranslationX(-transX);
        invalidate();
    }

    /**
     * Getter for visible width. This function will get called during animation as
     * well.
     *
     * @return The visible width of this item
     */
    public int getVisibleWidth() {
        return mVisibleWidth;
    }

    /**
     * Draw the view based on the drawing mode. Clip the canvas if necessary.
     *
     * @param canvas The Canvas to which the View is rendered.
     */
    @Override
    public void draw(Canvas canvas) {
        int width = Math.max(mVisibleWidth, mMinVisibleWidth);
        int height = canvas.getHeight();
        int shadeStart = -1;

        if (mDrawingMode == TRUNCATE_TEXT_END) {
            if (mVisibleWidth > mMinVisibleWidth) {
                shadeStart = Math.max(mMinVisibleWidth, mVisibleWidth - SHADE_WIDTH_PIX);
            }
        } else {
            if (mVisibleWidth <= mWidth) {
                mText.setTranslationX(mVisibleWidth - mWidth);
            }
        }

        if (width < mWidth) {
            canvas.clipRect(0, 0, width, height);
        }
        super.draw(canvas);
        if (shadeStart > 0 && width < mWidth) {
            mGradientShade.setBounds(shadeStart, 0, width, height);
            mGradientShade.draw(canvas);
        }
    }
}
