/* Copyright (c) 2013, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.android.camera;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.view.MotionEvent;
import android.view.View;
import android.util.Log;

/**
 * A button designed to be used for the on-screen recording
 * pausing-continue button.
 */
public class PauseButton extends ImageView {

    public interface OnPauseButtonListener {
        void onButtonPause();
        void onButtonContinue();
    }

    public PauseButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setSelected(false);
    }

    public void setPaused(boolean paused) {
        setSelected(paused);
    }

    @Override
    public boolean performClick() {
        boolean result = super.performClick();
        if (isSelected()) {
            setSelected(false);
            if (mListener != null && getVisibility() == View.VISIBLE) {
                mListener.onButtonContinue();
            }
        } else {
            setSelected(true);
            if (mListener != null && getVisibility() == View.VISIBLE) {
                mListener.onButtonPause();
            }
        }
        return result;
    }

    public void setOnPauseButtonListener(OnPauseButtonListener listener) {
        mListener = listener;
    }

    private OnPauseButtonListener mListener;
}
