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

package com.android.camera.util;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;

import com.android.camera.debug.Log;

/**
 * Workaround for secure-lockscreen double-onResume() bug:
 * <p>
 * If started from the secure-lockscreen, the activity may be quickly started,
 * resumed, paused, stopped, and then started and resumed again. This is
 * problematic for launch time from the secure-lockscreen because we typically open the
 * camera in onResume() and close it in onPause(). These camera operations take
 * a long time to complete. To workaround it, this class filters out
 * high-frequency onResume()->onPause() sequences if the current intent
 * indicates that we have started from the secure-lockscreen.
 * </p>
 * <p>
 * Subclasses should override the appropriate on[Create|Start...]Tasks() method
 * in place of the original.
 * </p>
 * <p>
 * Sequences of onResume() followed quickly by onPause(), when the activity is
 * started from a secure-lockscreen will result in a quick no-op.<br>
 * </p>
 */
public abstract class QuickActivity extends Activity {
    private static final Log.Tag TAG = new Log.Tag("QuickActivity");

    /**
     * The amount of time to wait before running onResumeTasks when started from
     * the lockscreen.
     */
    private static final long ON_RESUME_DELAY_MILLIS = 20;
    /** A reference to the main handler on which to run lifecycle methods. */
    private Handler mMainHandler;
    private boolean mPaused;
    /**
     * True if the last call to onResume() resulted in a delayed call to
     * mOnResumeTasks which was then canceled due to an immediate onPause().
     * This allows optimizing the common case in which the subsequent
     * call to onResume() should execute onResumeTasks() immediately.
     */
    private boolean mCanceledResumeTasks = false;

    /**
     * A runnable for deferring tasks to be performed in onResume() if starting
     * from the lockscreen.
     */
    private final Runnable mOnResumeTasks = new Runnable() {
            @Override
        public void run() {
            logLifecycle("onResumeTasks", true);
            if (mPaused) {
                onResumeTasks();
                mPaused = false;
                mCanceledResumeTasks = false;
            }
            logLifecycle("onResumeTasks", false);
        }
    };

    @Override
    protected final void onNewIntent(Intent intent) {
        logLifecycle("onNewIntent", true);
        Log.v(TAG, "Intent Action = " + intent.getAction());
        setIntent(intent);
        super.onNewIntent(intent);
        onNewIntentTasks(intent);
        logLifecycle("onNewIntent", false);
    }

    @Override
    protected final void onCreate(Bundle bundle) {
        logLifecycle("onCreate", true);
        Log.v(TAG, "Intent Action = " + getIntent().getAction());
        super.onCreate(bundle);

        mMainHandler = new Handler(getMainLooper());

        onCreateTasks(bundle);

        mPaused = true;

        logLifecycle("onCreate", false);
    }

    @Override
    protected final void onStart() {
        logLifecycle("onStart", true);
        onStartTasks();
        super.onStart();
        logLifecycle("onStart", false);
    }

    @Override
    protected final void onResume() {
        logLifecycle("onResume", true);
        mMainHandler.removeCallbacks(mOnResumeTasks);
        if (delayOnResumeOnStart() && !mCanceledResumeTasks) {
            mMainHandler.postDelayed(mOnResumeTasks, ON_RESUME_DELAY_MILLIS);
        } else {
            if (mPaused) {
                onResumeTasks();
                mPaused = false;
                mCanceledResumeTasks = false;
            }
        }
        super.onResume();
        logLifecycle("onResume", false);
    }

    @Override
    protected final void onPause() {
        logLifecycle("onPause", true);
        mMainHandler.removeCallbacks(mOnResumeTasks);
        if (!mPaused) {
            onPauseTasks();
            mPaused = true;
        } else {
            mCanceledResumeTasks = true;
        }
        super.onPause();
        logLifecycle("onPause", false);
    }

    @Override
    protected final void onStop() {
        if (isChangingConfigurations()) {
            Log.v(TAG, "changing configurations");
        }
        logLifecycle("onStop", true);
        onStopTasks();
        super.onStop();
        logLifecycle("onStop", false);
    }

    @Override
    protected final void onRestart() {
        logLifecycle("onRestart", true);
        super.onRestart();
        // TODO Support onRestartTasks() and handle the workaround for that too.
        logLifecycle("onRestart", false);
    }

    @Override
    protected final void onDestroy() {
        logLifecycle("onDestroy", true);
        onDestroyTasks();
        super.onDestroy();
        logLifecycle("onDestroy", false);
    }

    private void logLifecycle(String methodName, boolean start) {
        String prefix = start ? "START" : "END";
        Log.v(TAG, prefix + " " + methodName + ": Activity = " + toString());
    }

    private boolean delayOnResumeOnStart() {
        String action = getIntent().getAction();
        boolean isSecureLockscreenCamera =
                MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(action);
        return isSecureLockscreenCamera;
    }

    /**
     * Subclasses should override this in place of {@link Activity#onNewIntent}.
     */
    protected void onNewIntentTasks(Intent newIntent) {
    }

    /**
     * Subclasses should override this in place of {@link Activity#onCreate}.
     */
    protected void onCreateTasks(Bundle savedInstanceState) {
    }

    /**
     * Subclasses should override this in place of {@link Activity#onStart}.
     */
    protected void onStartTasks() {
    }

    /**
     * Subclasses should override this in place of {@link Activity#onResume}.
     */
    protected void onResumeTasks() {
    }

    /**
     * Subclasses should override this in place of {@link Activity#onPause}.
     */
    protected void onPauseTasks() {
    }

    /**
     * Subclasses should override this in place of {@link Activity#onStop}.
     */
    protected void onStopTasks() {
    }

    /**
     * Subclasses should override this in place of {@link Activity#onDestroy}.
     */
    protected void onDestroyTasks() {
    }
}
