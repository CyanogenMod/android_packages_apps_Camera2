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
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.android.camera2.R;

class MainActivityLayout extends FrameLayout {

    // Only check for intercepting touch events within first 500ms
    private static final int SWIPE_TIME_OUT = 500;

    private ModeListView mModeList;
    private boolean mCheckToIntercept;
    private MotionEvent mDown;
    private final int mSlop;

    public MainActivityLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mCheckToIntercept = true;
            mDown = MotionEvent.obtain(ev);
            return false;
        } else if (ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            // Do not intercept touch once child is in zoom mode
            mCheckToIntercept = false;
            return false;
        } else {
            if (!mCheckToIntercept) {
                return false;
            }
            if (ev.getEventTime() - ev.getDownTime() > SWIPE_TIME_OUT) {
                return false;
            }
            int deltaX = (int) (ev.getX() - mDown.getX());
            int deltaY = (int) (ev.getY() - mDown.getY());
            if (ev.getActionMasked() == MotionEvent.ACTION_MOVE
                    && deltaX > mSlop) {
                // Intercept right swipe
                if (Math.abs(deltaX) >= Math.abs(deltaY) * 2) {
                    onTouchEvent(mDown);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // TODO: This also needs to be modified with a better touch flow.
        // Pass the right swipe to mode switcher.
        return mModeList.onTouchEvent(ev);
    }

    @Override
    protected boolean fitSystemWindows(Rect insets) {
        mModeList.setPadding(0, 0, insets.right, insets.bottom);
        return super.fitSystemWindows(insets);
    }

    @Override
    public void onFinishInflate() {
        mModeList = (ModeListView) findViewById(R.id.mode_list_layout);
    }
}
