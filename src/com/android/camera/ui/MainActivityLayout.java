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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.android.camera.debug.Log;

public class MainActivityLayout extends FrameLayout {

    private MotionEvent mDown;
    private final Log.Tag TAG = new Log.Tag("MainActivityLayout");
    private boolean mRequestToInterceptTouchEvents = false;
    private View mTouchReceiver = null;

    public MainActivityLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mDown = MotionEvent.obtain(ev);
            mTouchReceiver = null;
            mRequestToInterceptTouchEvents = false;
            return false;
        } else if (mRequestToInterceptTouchEvents) {
            mRequestToInterceptTouchEvents = false;
            onTouchEvent(mDown);
            return true;
        } else if (ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            // Do not intercept touch once child is in zoom mode
            return false;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mTouchReceiver != null) {
            mTouchReceiver.setVisibility(VISIBLE);
            return mTouchReceiver.dispatchTouchEvent(ev);
        }
        return false;
    }

    public void redirectTouchEventsTo(View touchReceiver) {
        if (touchReceiver == null) {
            Log.e(TAG, "Cannot redirect touch to a null receiver.");
            return;
        }
        mTouchReceiver = touchReceiver;
        mRequestToInterceptTouchEvents = true;
    }
}
