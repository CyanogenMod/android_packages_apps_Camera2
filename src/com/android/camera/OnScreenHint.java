/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.android.camera.debug.Log;
import com.android.camera.util.AndroidServices;
import com.android.camera2.R;

/**
 * A on-screen hint is a view containing a little message for the user and will
 * be shown on the screen continuously.  This class helps you create and show
 * those.
 *
 * <p>
 * When the view is shown to the user, appears as a floating view over the
 * application.
 * <p>
 * The easiest way to use this class is to call one of the static methods that
 * constructs everything you need and returns a new {@code OnScreenHint} object.
 */
public class OnScreenHint {
    static final Log.Tag TAG = new Log.Tag("OnScreenHint");

    View mView;
    View mNextView;

    private final WindowManager.LayoutParams mParams =
            new WindowManager.LayoutParams();
    private final WindowManager mWM;
    private final Handler mHandler = new Handler();

    /**
     * Construct an empty OnScreenHint object.
     *
     * @param activity An activity from which to create a {@link WindowManager}
     *        to create and attach a view. This must be an Activity, not an
     *        application context, otherwise app will crash upon display of the
     *        hint due to adding a view to a application {@link WindowManager}
     *        that doesn't allow view attachment.
     */
    private OnScreenHint(Activity activity) {
        mWM = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);

        mParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        mParams.format = PixelFormat.TRANSLUCENT;
        mParams.windowAnimations = R.style.Animation_OnScreenHint;
        mParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
        mParams.setTitle("OnScreenHint");
    }

    /**
     * Show the view on the screen.
     */
    public void show() {
        if (mNextView == null) {
            throw new RuntimeException("View is not initialized");
        }
        mHandler.post(mShow);
    }

    /**
     * Close the view if it's showing.
     */
    public void cancel() {
        mHandler.post(mHide);
    }

    /**
     * Make a standard hint that just contains a text view.
     *
     * @param activity An activity from which to create a {@link WindowManager}
     *        to create and attach a view. This must be an Activity, not an
     *        application context, otherwise app will crash upon display of the
     *        hint due to adding a view to a application {@link WindowManager}
     *        that doesn't allow view attachment.
     * @param text The text to show.  Can be formatted text.
     *
     */
    public static OnScreenHint makeText(Activity activity, CharSequence text) {
        OnScreenHint result = new OnScreenHint(activity);

        LayoutInflater inflate = (LayoutInflater) activity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        View v = inflate.inflate(R.layout.on_screen_hint, null);
        TextView tv = (TextView) v.findViewById(R.id.message);
        tv.setText(text);

        result.mNextView = v;

        return result;
    }

    /**
     * Update the text in a OnScreenHint that was previously created using one
     * of the makeText() methods.
     * @param s The new text for the OnScreenHint.
     */
    public void setText(CharSequence s) {
        if (mNextView == null) {
            throw new RuntimeException("This OnScreenHint was not "
                    + "created with OnScreenHint.makeText()");
        }
        TextView tv = (TextView) mNextView.findViewById(R.id.message);
        if (tv == null) {
            throw new RuntimeException("This OnScreenHint was not "
                    + "created with OnScreenHint.makeText()");
        }
        tv.setText(s);
    }

    private synchronized void handleShow() {
        if (mView != mNextView) {
            // remove the old view if necessary
            handleHide();
            mView = mNextView;
            if (mView.getParent() != null) {
                mWM.removeView(mView);
            }

            mWM.addView(mView, mParams);
        }
    }

    private synchronized void handleHide() {
        if (mView != null) {
            // note: checking parent() just to make sure the view has
            // been added...  i have seen cases where we get here when
            // the view isn't yet added, so let's try not to crash.
            if (mView.getParent() != null) {
                mWM.removeView(mView);
            }
            mView = null;
        }
    }

    private final Runnable mShow = new Runnable() {
        @Override
        public void run() {
            handleShow();
        }
    };

    private final Runnable mHide = new Runnable() {
        @Override
        public void run() {
            handleHide();
        }
    };
}
