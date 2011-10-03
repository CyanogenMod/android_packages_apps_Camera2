/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.gallery3d.util;

import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import java.util.HashMap;
import java.util.Map;

/**
 * This class manages the visibility of the progress spinner in the action bar for an
 * Activity. It filters out short-lived appearances of the progress spinner by only
 * showing the spinner if it hasn't been hidden again before the end of a specified
 * delay period. It also enforces a minimum display time once the spinner is made visible.
 * This meant to cut down on the frequent "flashes" of the progress spinner.
 */
public class SpinnerVisibilitySetter {

    private static final int SHOW_SPINNER_REQUESTED = 0;
    private static final int HIDE_SPINNER_REQUESTED = 1;
    private static final int SHOW_SPINNER_DELAY_REACHED = 2;
    private static final int HIDE_SPINNER_DELAY_REACHED = 3;

    // Amount of time after a show request that the progress spinner is actually made visible.
    // This means that any show/hide requests that happen subsequently within this period
    // of time will be ignored.
    private static final long SPINNER_DISPLAY_DELAY = 1000;

    // The minimum amount of time the progress spinner must be visible before it can be hidden.
    private static final long MIN_SPINNER_DISPLAY_TIME = 2000;

    private boolean mPendingVisibilityRequest = false;
    private boolean mActiveVisibilityRequest = false;
    private long mSpinnerVisibilityStartTime;

    Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case SHOW_SPINNER_REQUESTED:
                    mPendingVisibilityRequest = true;
                    sendEmptyMessageDelayed(SHOW_SPINNER_DELAY_REACHED, SPINNER_DISPLAY_DELAY);
                    break;
                case HIDE_SPINNER_REQUESTED:
                    mPendingVisibilityRequest = false;
                    if (!mActiveVisibilityRequest) {
                        // We haven't requested to show the spinner so no need to decide
                        // when to hide it.
                        break;
                    }

                    long currTime = SystemClock.uptimeMillis();
                    if (currTime - mSpinnerVisibilityStartTime > MIN_SPINNER_DISPLAY_TIME) {
                        // The spinner has already been visible longer than the requisite min
                        // display time. Send the hide message immediately.
                        sendEmptyMessage(HIDE_SPINNER_DELAY_REACHED);
                    } else {
                        // The spinner is visible but hasn't been visible for long enough yet.
                        // Send a delayed hide message.
                        sendEmptyMessageAtTime(HIDE_SPINNER_DELAY_REACHED,
                                mSpinnerVisibilityStartTime + MIN_SPINNER_DISPLAY_TIME);
                    }
                    break;
                case SHOW_SPINNER_DELAY_REACHED:
                    if (mPendingVisibilityRequest) {
                        mPendingVisibilityRequest = false;
                        mActiveVisibilityRequest = true;

                        // Even though the spinner isn't visible quite yet, lets set this
                        // here to avoid possible cross-thread synchronization issues.
                        mSpinnerVisibilityStartTime = SystemClock.uptimeMillis();
                        mActivity.runOnUiThread(new SetProgressVisibilityRunnable(true));
                    }
                    break;
                case HIDE_SPINNER_DELAY_REACHED:
                    mActiveVisibilityRequest = false;
                    mActivity.runOnUiThread(new SetProgressVisibilityRunnable(false));
                    break;
            }
        }
    };
    static final Map<Activity, SpinnerVisibilitySetter> sInstanceMap =
            new HashMap<Activity, SpinnerVisibilitySetter>();
    private Activity mActivity;

    private SpinnerVisibilitySetter(Activity activity) {
        mActivity = activity;
    }

    public static SpinnerVisibilitySetter getInstance(Activity activity) {
        synchronized(sInstanceMap) {
            if (sInstanceMap.get(activity) == null) {
                sInstanceMap.put(activity, new SpinnerVisibilitySetter(activity));
            }
            return sInstanceMap.get(activity);
        }
    }

    public void setSpinnerVisibility(boolean visible) {
        mHandler.sendEmptyMessage(visible ? SHOW_SPINNER_REQUESTED : HIDE_SPINNER_REQUESTED);
    }

    private class SetProgressVisibilityRunnable implements Runnable {
        boolean mVisible;

        public SetProgressVisibilityRunnable(boolean visible) {
            mVisible = visible;
        }

        @Override
        public void run() {
            mActivity.setProgressBarIndeterminateVisibility(mVisible);
        }
    }
}
