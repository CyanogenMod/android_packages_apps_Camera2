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

import java.util.WeakHashMap;

/**
 * This class manages the visibility of the progress spinner in the action bar for an
 * Activity. It filters out short-lived appearances of the progress spinner by only
 * showing the spinner if it hasn't been hidden again before the end of a specified
 * delay period. It also enforces a minimum display time once the spinner is made visible.
 * This meant to cut down on the frequent "flashes" of the progress spinner.
 */
public class SpinnerVisibilitySetter {

    private static final int MSG_SHOW_SPINNER = 1;
    private static final int MSG_HIDE_SPINNER = 2;

    // Amount of time after a show request that the progress spinner is actually made visible.
    // This means that any show/hide requests that happen subsequently within this period
    // of time will be ignored.
    private static final long SPINNER_DISPLAY_DELAY = 1000;

    // The minimum amount of time the progress spinner must be visible before it can be hidden.
    private static final long MIN_SPINNER_DISPLAY_TIME = 2000;

    static final WeakHashMap<Activity, SpinnerVisibilitySetter> sInstanceMap =
            new WeakHashMap<Activity, SpinnerVisibilitySetter>();

    private long mSpinnerVisibilityStartTime = -1;
    private Activity mActivity;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_SHOW_SPINNER:
                    removeMessages(MSG_SHOW_SPINNER);
                    if (mSpinnerVisibilityStartTime >= 0) break;
                    mSpinnerVisibilityStartTime = SystemClock.elapsedRealtime();
                    mActivity.setProgressBarIndeterminateVisibility(true);
                    break;
                case MSG_HIDE_SPINNER:
                    removeMessages(MSG_HIDE_SPINNER);
                    if (mSpinnerVisibilityStartTime < 0) break;
                    long t = SystemClock.elapsedRealtime() - mSpinnerVisibilityStartTime;
                    if (t >= MIN_SPINNER_DISPLAY_TIME) {
                        mSpinnerVisibilityStartTime = -1;
                        mActivity.setProgressBarIndeterminateVisibility(false);
                    } else {
                        sendEmptyMessageDelayed(MSG_HIDE_SPINNER, MIN_SPINNER_DISPLAY_TIME - t);
                    }
                    break;
            }
        }
    };

    /**
     *  Gets the <code>SpinnerVisibilitySetter</code> for the given <code>activity</code>.
     *
     *  This method must be called from the main thread.
     */
    public static SpinnerVisibilitySetter getInstance(Activity activity) {
        synchronized(sInstanceMap) {
            SpinnerVisibilitySetter setter = sInstanceMap.get(activity);
            if (setter == null) {
                setter = new SpinnerVisibilitySetter(activity);
                sInstanceMap.put(activity, setter);
            }
            return setter;
        }
    }

    private SpinnerVisibilitySetter(Activity activity) {
        mActivity = activity;
    }

    public void setSpinnerVisibility(boolean visible) {
        if (visible) {
            mHandler.removeMessages(MSG_HIDE_SPINNER);
            mHandler.sendEmptyMessageDelayed(MSG_SHOW_SPINNER, SPINNER_DISPLAY_DELAY);
        } else {
            mHandler.removeMessages(MSG_SHOW_SPINNER);
            mHandler.sendEmptyMessage(MSG_HIDE_SPINNER);
        }
    }
}
