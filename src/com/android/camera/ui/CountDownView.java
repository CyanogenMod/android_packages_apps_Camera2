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

import java.util.Locale;

import android.content.Context;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.camera.debug.Log;
import com.android.camera2.R;

/**
 * This class manages the looks of the countdown.
 */
public class CountDownView extends FrameLayout {

    private static final Log.Tag TAG = new Log.Tag("CountDownView");
    private static final int SET_TIMER_TEXT = 1;
    private static final long ANIMATION_DURATION_MS = 800;
    private TextView mRemainingSecondsView;
    private int mRemainingSecs = 0;
    private OnCountDownStatusListener mListener;
    private final Handler mHandler = new MainHandler();
    private final RectF mPreviewArea = new RectF();

    /**
     * Listener that gets notified when the countdown status has
     * been updated (i.e. remaining seconds changed, or finished).
     */
    public interface OnCountDownStatusListener {
        /**
         * Gets notified when the remaining seconds for the countdown
         * has changed.
         *
         * @param remainingSeconds seconds remained for countdown
         */
        public void onRemainingSecondsChanged(int remainingSeconds);

        /**
         * Gets called when countdown is finished.
         */
        public void onCountDownFinished();
    }

    public CountDownView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Returns whether countdown is on-going.
     */
    public boolean isCountingDown() {
        return mRemainingSecs > 0;
    };

    /**
     * Responds to preview area change by centering th countdown UI in the new
     * preview area.
     */
    public void onPreviewAreaChanged(RectF previewArea) {
        mPreviewArea.set(previewArea);
    }

    private void remainingSecondsChanged(int newVal) {
        mRemainingSecs = newVal;
        if (mListener != null) {
            mListener.onRemainingSecondsChanged(mRemainingSecs);
        }

        if (newVal == 0) {
            // Countdown has finished.
            setVisibility(View.INVISIBLE);
            if (mListener != null) {
                mListener.onCountDownFinished();
            }
        } else {
            Locale locale = getResources().getConfiguration().locale;
            String localizedValue = String.format(locale, "%d", newVal);
            mRemainingSecondsView.setText(localizedValue);
            // Fade-out animation.
            startFadeOutAnimation();
            // Schedule the next remainingSecondsChanged() call in 1 second
            mHandler.sendEmptyMessageDelayed(SET_TIMER_TEXT, 1000);
        }
    }

    private void startFadeOutAnimation() {
        int textWidth = mRemainingSecondsView.getMeasuredWidth();
        int textHeight = mRemainingSecondsView.getMeasuredHeight();
        mRemainingSecondsView.setScaleX(1f);
        mRemainingSecondsView.setScaleY(1f);
        mRemainingSecondsView.setTranslationX(mPreviewArea.centerX() - textWidth / 2);
        mRemainingSecondsView.setTranslationY(mPreviewArea.centerY() - textHeight / 2);
        mRemainingSecondsView.setPivotX(textWidth / 2);
        mRemainingSecondsView.setPivotY(textHeight / 2);
        mRemainingSecondsView.setAlpha(1f);
        float endScale = 2.5f;
        mRemainingSecondsView.animate().scaleX(endScale).scaleY(endScale)
                .alpha(0f).setDuration(ANIMATION_DURATION_MS).start();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mRemainingSecondsView = (TextView) findViewById(R.id.remaining_seconds);
    }

    /**
     * Sets a listener that gets notified when the status of countdown has changed.
     */
    public void setCountDownStatusListener(OnCountDownStatusListener listener) {
        mListener = listener;
    }

    /**
     * Starts showing countdown in the UI.
     *
     * @param sec duration of the countdown, in seconds
     */
    public void startCountDown(int sec) {
        if (sec <= 0) {
            Log.w(TAG, "Invalid input for countdown timer: " + sec + " seconds");
            return;
        }
        if (isCountingDown()) {
            cancelCountDown();
        }
        setVisibility(View.VISIBLE);
        remainingSecondsChanged(sec);
    }

    /**
     * Cancels the on-going countdown in the UI, if any.
     */
    public void cancelCountDown() {
        if (mRemainingSecs > 0) {
            mRemainingSecs = 0;
            mHandler.removeMessages(SET_TIMER_TEXT);
            setVisibility(View.INVISIBLE);
        }
    }

    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message message) {
            if (message.what == SET_TIMER_TEXT) {
                remainingSecondsChanged(mRemainingSecs -1);
            }
        }
    }
}