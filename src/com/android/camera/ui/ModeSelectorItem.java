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
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;
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
 * its own class. There are two drawing mode, <code>FLY_IN</code>
 * and <code>FLY_OUT</code>. They define how we draw the view when
 * we display the view partially.
 */
class ModeSelectorItem extends FrameLayout {
    private TextView mText;
    private ModeIconView mIcon;
    private int mVisibleWidth = 0;
    private final int mMinVisibleWidth;
    private VisibleWidthChangedListener mListener = null;

    private int mWidth;
    private int mModeId;

    /**
     * A listener that gets notified when the visible width of the current item
     * is changed.
     */
    public interface VisibleWidthChangedListener {
        public void onVisibleWidthChanged(int width);
    }

    public ModeSelectorItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClickable(true);
        mMinVisibleWidth = getResources()
                .getDimensionPixelSize(R.dimen.mode_selector_icon_block_width);
    }

    @Override
    public void onFinishInflate() {
        mIcon = (ModeIconView) findViewById(R.id.selector_icon);
        mText = (TextView) findViewById(R.id.selector_text);
        Typeface typeface;
        if (ApiHelper.HAS_ROBOTO_MEDIUM_FONT) {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        } else {
            // Load roboto_light typeface from assets.
            typeface = Typeface.createFromAsset(getResources().getAssets(),
                    "Roboto-Medium.ttf");
        }
        mText.setTypeface(typeface);
    }

    public void setDefaultBackgroundColor(int color) {
        setBackgroundColor(color);
    }

    /**
     * Sets a listener that receives a callback when the visible width of this
     * selector item changes.
     */
    public void setVisibleWidthChangedListener(VisibleWidthChangedListener listener) {
        mListener = listener;
    }

    @Override
    public void setSelected(boolean selected) {
        mIcon.setSelected(selected);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // Do not dispatch any touch event, so that all the events that are received
        // in onTouchEvent() are only through forwarding.
         return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        super.onTouchEvent(ev);
        return false;
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
        mText.setTranslationX(0);
    }

    public void setText(CharSequence text) {
        mText.setText(text);
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mWidth = right - left;
        if (changed && mVisibleWidth > 0) {
            // Reset mode list to full screen
            setVisibleWidth(mWidth);
        }
    }

    /**
     * Sets image resource as the icon for the mode. By default, all drawables instances
     * loaded from the same resource share a common state; if you modify the state
     * of one instance, all the other instances will receive the same modification.
     * In order to modify properties of this icon drawable without affecting other
     * drawables, here we use a mutable drawable which is guaranteed to not share
     * states with other drawables.
     *
     * @param resource resource id of the asset to be used as icon
     */
    public void setImageResource(int resource) {
        Drawable drawableIcon = getResources().getDrawable(resource);
        if (drawableIcon != null) {
            drawableIcon = drawableIcon.mutate();
        }
        mIcon.setIconDrawable(drawableIcon);
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
        int fullyShownIconWidth = getMaxVisibleWidth();
        newWidth = Math.max(newWidth, 0);
        // Visible width should not be greater than view width
        newWidth = Math.min(newWidth, fullyShownIconWidth);

        if (mVisibleWidth != newWidth) {
            mVisibleWidth = newWidth;
            if (mListener != null) {
                mListener.onVisibleWidthChanged(newWidth);
            }
        }
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
        float transX = 0f;
        // If the given width is less than the icon width, we need to translate icon
        if (mVisibleWidth < mMinVisibleWidth + mIcon.getLeft()) {
            transX = mMinVisibleWidth + mIcon.getLeft() - mVisibleWidth;
        }
        canvas.save();
        canvas.translate(-transX, 0);
        super.draw(canvas);
        canvas.restore();
    }

    /**
     * Sets the color that will be used in the drawable for highlight state.
     *
     * @param highlightColor color for the highlight state
     */
    public void setHighlightColor(int highlightColor) {
        mIcon.setHighlightColor(highlightColor);
    }

    /**
     * @return highlightColor color for the highlight state
     */
    public int getHighlightColor() {
        return mIcon.getHighlightColor();
    }

    /**
     * Gets the maximum visible width of the mode icon. The mode item will be
     * full shown when the mode icon has max visible width.
     */
    public int getMaxVisibleWidth() {
        return mIcon.getLeft() + mMinVisibleWidth;
    }

    /**
     * Gets the position of the icon center relative to the window.
     *
     * @param loc integer array of size 2, to hold the position x and y
     */
    public void getIconCenterLocationInWindow(int[] loc) {
        mIcon.getLocationInWindow(loc);
        loc[0] += mMinVisibleWidth / 2;
        loc[1] += mMinVisibleWidth / 2;
    }

    /**
     * Sets the mode id of the current item.
     *
     * @param modeId id of the mode represented by current item.
     */
    public void setModeId(int modeId) {
        mModeId = modeId;
    }

    /**
     * Gets the mode id of the current item.
     */
    public int getModeId() {
        return mModeId;
    }

    /**
     * @return The {@link ModeIconView} attached to this item.
     */
    public ModeIconView getIcon() {
        return mIcon;
    }

    /**
     * Sets the alpha on the mode text.
     */
    public void setTextAlpha(float alpha) {
        mText.setAlpha(alpha);
    }
}
