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
import android.view.View;

/*
 * A toggle button that supports two states with images rendererd on top for each state.
 */
public class ToggleImageButton extends MultiToggleImageButton {
    /*
     * Listener interface for button state changes.
     */
    public interface OnStateChangeListener {
        /*
         * @param view the ToggleImageButton that received the touch event
         * @param state the new state the button is in
         */
        public abstract void stateChanged(View view, boolean state);
    }

    private OnStateChangeListener mOnStateChangeListener;
        public ToggleImageButton(Context context) {
        super(context);
    }

    public ToggleImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ToggleImageButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /*
     * Set the state change listener.
     * @param onStateChangeListener the listener to set
     */
    public void setOnStateChangeListener(OnStateChangeListener onStateChangeListener) {
        mOnStateChangeListener = onStateChangeListener;
    }

    /*
     * Set the current button state, thus causing the state change listener to get called.
     * @param state the desired state
     */
    public void setState(boolean state) {
        super.setState(state ? 1 : 0);
    }

    @Override
    protected void init() {
        super.init();
        super.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener() {
            @Override
            public void stateChanged(View v, int state) {
                if (mOnStateChangeListener != null) {
                    mOnStateChangeListener.stateChanged(v, (state == 1));
                }
            }
        });
    }
}