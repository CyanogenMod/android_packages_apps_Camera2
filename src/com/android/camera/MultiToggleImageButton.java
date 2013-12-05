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

package com.android.camera;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.ImageButton;
import android.view.View;

import com.android.camera2.R;

/*
 * A toggle button that supports two or more states with images rendererd on top
 * for each state.
 * The button is initialized in an XML layout file with an array reference of
 * image ids (e.g. imageIds="@array/camera_flashmode_icons").
 * Each image in the referenced array represents a single integer state.
 * Every time the user touches the button it gets set to next state in line,
 * with the corresponding image drawn onto the face of the button.
 * State wraps back to 0 on user touch when button is already at n-1 state.
 */
public class MultiToggleImageButton extends ImageButton {
    /*
     * Listener inteface for button state changes.
     */
    public interface OnStateChangeListener {
        /*
         * @param view the MultiToggleImageButton that received the touch event
         * @param state the new state the button is in
         */
        public abstract void stateChanged(View view, int state);
    }

    private OnStateChangeListener mOnStateChangeListener;
    private int mState;
    private int[] mImageIds;

    public MultiToggleImageButton(Context context) {
        super(context);
        init();
    }

    public MultiToggleImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
        parseAttributes(context, attrs);
        setState(0);
    }

    public MultiToggleImageButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
        parseAttributes(context, attrs);
        setState(0);
    }

    /*
     * Set the state change listener.
     *
     * @param onStateChangeListener the listener to set
     */
    public void setOnStateChangeListener(OnStateChangeListener onStateChangeListener) {
        mOnStateChangeListener = onStateChangeListener;
    }

    /*
     * Get the current button state.
     *
     */
    public int getState() {
        return mState;
    }

    /*
     * Set the current button state, thus causing the state change listener to
     * get called.
     *
     * @param state the desired state
     */
    public void setState(int state) {
        setState(state, true);
    }

    /*
     * Set the current button state.
     *
     * @param state the desired state
     * @param callListener should the state change listener be called?
     */
    public void setState(int state, boolean callListener) {
        mState = state;
        setImageResource(mImageIds[mState]);
        if (callListener && mOnStateChangeListener != null) {
            mOnStateChangeListener.stateChanged(this, getState());
        }
    }

    private void nextState() {
        int state = mState + 1;
        if (state >= mImageIds.length) {
            state = 0;
        }
        setState(state);
    }

    protected void init() {
        this.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nextState();
            }
        });
    }

    private void parseAttributes(Context context, AttributeSet attrs) {
        TypedArray a = context.getTheme().obtainStyledAttributes(
            attrs,
            R.styleable.MultiToggleImageButton,
            0, 0);
        int resId = a.getResourceId(R.styleable.MultiToggleImageButton_imageIds, 0);
        overrideImageIds(resId);
        a.recycle();
    }

    /**
     * Override the image ids of this button.
     */
    public void overrideImageIds(int resId) {
        TypedArray ids = null;
        try {
            ids = getResources().obtainTypedArray(resId);
            mImageIds = new int[ids.length()];
            for (int i = 0; i < ids.length(); i++) {
                mImageIds[i] = ids.getResourceId(i, 0);
            }
        } finally {
            if (ids != null) {
                ids.recycle();
            }
        }
    }
}