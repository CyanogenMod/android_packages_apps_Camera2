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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.android.camera.app.CameraAppUI;
import com.android.camera.debug.Log;
import com.android.camera.util.CameraUtil;
import com.android.camera.widget.FilmstripLayout;
import com.android.camera2.R;

public class MainActivityLayout extends FrameLayout {

    private final Log.Tag TAG = new Log.Tag("MainActivityLayout");
    // Only check for intercepting touch events within first 500ms
    private static final int SWIPE_TIME_OUT = 500;

    private ModeListView mModeList;
    private FilmstripLayout mFilmstripLayout;
    private boolean mCheckToIntercept;
    private MotionEvent mDown;
    private final int mSlop;
    private boolean mRequestToInterceptTouchEvents = false;
    private View mTouchReceiver = null;
    private final boolean mIsCaptureIntent;
    private CameraAppUI.NonDecorWindowSizeChangedListener mNonDecorWindowSizeChangedListener = null;

    // TODO: This can be removed once we come up with a new design for b/13751653.
    @Deprecated
    private boolean mSwipeEnabled = true;

    public MainActivityLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        Activity activity = (Activity) context;
        Intent intent = activity.getIntent();
        String action = intent.getAction();
        mIsCaptureIntent = (MediaStore.ACTION_IMAGE_CAPTURE.equals(action)
                || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(action)
                || MediaStore.ACTION_VIDEO_CAPTURE.equals(action));
    }

    /**
     * Enables or disables the swipe for modules not supporting the new swipe
     * logic yet.
     */
    @Deprecated
    public void setSwipeEnabled(boolean enabled) {
        mSwipeEnabled = enabled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mCheckToIntercept = true;
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
            mCheckToIntercept = false;
            return false;
        } else {
            // TODO: This can be removed once we come up with a new design for b/13751653.
            if (!mCheckToIntercept) {
                return false;
            }
            if (ev.getEventTime() - ev.getDownTime() > SWIPE_TIME_OUT) {
                return false;
            }
            if (mIsCaptureIntent || !mSwipeEnabled) {
                return false;
            }
            int deltaX = (int) (ev.getX() - mDown.getX());
            int deltaY = (int) (ev.getY() - mDown.getY());
            if (ev.getActionMasked() == MotionEvent.ACTION_MOVE
                    && Math.abs(deltaX) > mSlop) {
                // Intercept right swipe
                if (deltaX >= Math.abs(deltaY) * 2) {
                    mTouchReceiver = mModeList;
                    onTouchEvent(mDown);
                    return true;
                }
                // Intercept left swipe
                else if (deltaX < -Math.abs(deltaY) * 2) {
                    mTouchReceiver = mFilmstripLayout;
                    onTouchEvent(mDown);
                    return true;
                }
            }
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

    @Override
    public void onFinishInflate() {
        mModeList = (ModeListView) findViewById(R.id.mode_list_layout);
        mFilmstripLayout = (FilmstripLayout) findViewById(R.id.filmstrip_layout);
    }

    public void redirectTouchEventsTo(View touchReceiver) {
        if (touchReceiver == null) {
            Log.e(TAG, "Cannot redirect touch to a null receiver.");
            return;
        }
        mTouchReceiver = touchReceiver;
        mRequestToInterceptTouchEvents = true;
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mNonDecorWindowSizeChangedListener != null) {
            mNonDecorWindowSizeChangedListener.onNonDecorWindowSizeChanged(
                    MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec),
                    CameraUtil.getDisplayRotation());
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Sets a listener that gets notified when the layout size is changed. This
     * size is the size of main activity layout. It is also the size of the window
     * excluding the system decor such as status bar and nav bar.
     */
    public void setNonDecorWindowSizeChangedListener(
            CameraAppUI.NonDecorWindowSizeChangedListener listener) {
        mNonDecorWindowSizeChangedListener = listener;
        if (mNonDecorWindowSizeChangedListener != null) {
            mNonDecorWindowSizeChangedListener.onNonDecorWindowSizeChanged(
                    getMeasuredWidth(), getMeasuredHeight(),
                    CameraUtil.getDisplayRotation());
        }
    }
}