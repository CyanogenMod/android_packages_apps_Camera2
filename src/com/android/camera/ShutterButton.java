/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.text.method.Touch;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.android.camera.debug.Log;
import com.android.camera.ui.TouchCoordinate;

import java.util.List;
import java.util.ArrayList;

/**
 * A button designed to be used for the on-screen shutter button.
 * It's currently an {@code ImageView} that can call a delegate when the
 * pressed state changes.
 */
public class ShutterButton extends ImageView {
    private static final Log.Tag TAG = new Log.Tag("ShutterButton");
    public static final float ALPHA_WHEN_ENABLED = 1f;
    public static final float ALPHA_WHEN_DISABLED = 0.2f;
    private boolean mTouchEnabled = true;
    private TouchCoordinate mTouchCoordinate;
    /**
     * A callback to be invoked when a ShutterButton's pressed state changes.
     */
    public interface OnShutterButtonListener {
        /**
         * Called when a ShutterButton has been pressed.
         *
         * @param pressed The ShutterButton that was pressed.
         */
        void onShutterButtonFocus(boolean pressed);
        void onShutterCoordinate(TouchCoordinate coord);
        void onShutterButtonClick();
    }

    private List<OnShutterButtonListener> mListeners
        = new ArrayList<OnShutterButtonListener>();
    private boolean mOldPressed;

    public ShutterButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Add an {@link OnShutterButtonListener} to a set of listeners.
     */
    public void addOnShutterButtonListener(OnShutterButtonListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    /**
     * Remove an {@link OnShutterButtonListener} from a set of listeners.
     */
    public void removeOnShutterButtonListener(OnShutterButtonListener listener) {
        if (mListeners.contains(listener)) {
            mListeners.remove(listener);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        if (mTouchEnabled) {
            if (m.getActionMasked() == MotionEvent.ACTION_UP) {
                mTouchCoordinate = new TouchCoordinate(m.getX(), m.getY(), this.getMeasuredWidth(),
                        this.getMeasuredHeight());
            }
            return super.dispatchTouchEvent(m);
        } else {
            return false;
        }
    }

    public void enableTouch(boolean enable) {
        mTouchEnabled = enable;
    }

    /**
     * Hook into the drawable state changing to get changes to isPressed -- the
     * onPressed listener doesn't always get called when the pressed state
     * changes.
     */
    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        final boolean pressed = isPressed();
        if (pressed != mOldPressed) {
            if (!pressed) {
                // When pressing the physical camera button the sequence of
                // events is:
                //    focus pressed, optional camera pressed, focus released.
                // We want to emulate this sequence of events with the shutter
                // button. When clicking using a trackball button, the view
                // system changes the drawable state before posting click
                // notification, so the sequence of events is:
                //    pressed(true), optional click, pressed(false)
                // When clicking using touch events, the view system changes the
                // drawable state after posting click notification, so the
                // sequence of events is:
                //    pressed(true), pressed(false), optional click
                // Since we're emulating the physical camera button, we want to
                // have the same order of events. So we want the optional click
                // callback to be delivered before the pressed(false) callback.
                //
                // To do this, we delay the posting of the pressed(false) event
                // slightly by pushing it on the event queue. This moves it
                // after the optional click notification, so our client always
                // sees events in this sequence:
                //     pressed(true), optional click, pressed(false)
                post(new Runnable() {
                    @Override
                    public void run() {
                        callShutterButtonFocus(pressed);
                    }
                });
            } else {
                callShutterButtonFocus(pressed);
            }
            mOldPressed = pressed;
        }
    }

    private void callShutterButtonFocus(boolean pressed) {
        for (OnShutterButtonListener listener : mListeners) {
            listener.onShutterButtonFocus(pressed);
        }
    }

    @Override
    public boolean performClick() {
        boolean result = super.performClick();
        if (getVisibility() == View.VISIBLE) {
            for (OnShutterButtonListener listener : mListeners) {
                listener.onShutterCoordinate(mTouchCoordinate);
                mTouchCoordinate = null;
                listener.onShutterButtonClick();
            }
        }
        return result;
    }
}
