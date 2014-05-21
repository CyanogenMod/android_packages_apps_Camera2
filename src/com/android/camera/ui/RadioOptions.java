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
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.android.camera2.R;

/**
 * A LienearLayout for a set of {@link android.view.View}s,
 * one of which can be selected at any time.
 */
public class RadioOptions extends TopRightWeightedLayout {
    /**
     * Listener for responding to {@link android.view.View} click events.
     */
    public interface OnOptionClickListener {
        /**
         * Override to respond to  {@link android.view.View} click events.
         * @param v {@link android.view.View} that was clicked.
         */
        public void onOptionClicked(View v);
    }

    private Drawable mBackground;
    private OnOptionClickListener mOnOptionClickListener;

    /**
     * Set the OnOptionClickListener.
     * @params listener The listener to set.
     */
    public void setOnOptionClickListener(OnOptionClickListener listener) {
        mOnOptionClickListener = listener;
    }

    /**
     * Constructor that is called when inflating a view from XML.
     * @params context The Context the view is running in, through which it can access the current theme, resources, etc.
     * @params attrs The attributes of the XML tag that is inflating the view.
     */
    public RadioOptions(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(
            attrs,
            R.styleable.RadioOptions,
            0, 0);
        int drawableId = a.getResourceId(R.styleable.RadioOptions_selected_drawable, 0);
        if (drawableId > 0) {
            mBackground = context.getResources()
                .getDrawable(drawableId);
        }
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        updateListeners();
    }

    /**
     * Update each child {@link android.view.View}'s {@link android.view.View.OnClickListener}.
     * Call this if the child views are added after the OnOptionClickListener,
     * e.g. if the child views are added programatically.
     */
    public void updateListeners() {
        View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View button) {
                setSelectedOptionByView(button);
            }
        };

        for (int i = 0; i < getChildCount(); i++) {
            View button = getChildAt(i);
            button.setOnClickListener(onClickListener);
        }
    }

    /**
     * Sets a child {@link android.view.View} as selected by tag.
     * @param tag Tag that identifies a child {@link android.view.View}. No effect if view not found.
     */
    public void setSelectedOptionByTag(Object tag) {
        View button = findViewWithTag(tag);
        setSelectedOptionByView(button);
    }

    /**
     * Sets a child {@link android.view.View} as selected by id.
     * @param id Resource ID  that identifies a child {@link android.view.View}. No effect if view not found.
     */
    public void setSeletedOptionById(int id) {
        View button = findViewById(id);
        setSelectedOptionByView(button);
    }

    private void setSelectedOptionByView(View view) {
        if (view != null) {
            // Reset all button states.
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).setBackground(null);
            }

            // Highlight the appropriate button.
            view.setBackground(mBackground);
            if (mOnOptionClickListener != null) {
                mOnOptionClickListener.onOptionClicked(view);
            }
        }
    }
}