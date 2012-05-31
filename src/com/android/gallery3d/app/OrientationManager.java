/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.app;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.provider.Settings;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.ViewConfiguration;

import com.android.gallery3d.ui.OrientationSource;

import java.util.ArrayList;

public class OrientationManager implements OrientationSource {
    private static final String TAG = "OrientationManager";

    public interface Listener {
        public void onOrientationCompensationChanged();
    }

    // Orientation hysteresis amount used in rounding, in degrees
    private static final int ORIENTATION_HYSTERESIS = 5;

    private Activity mActivity;
    private ArrayList<Listener> mListeners;
    private MyOrientationEventListener mOrientationListener;
    // The degrees of the device rotated clockwise from its natural orientation.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    // If the framework orientation is locked.
    private boolean mOrientationLocked = false;
    // The orientation compensation: if the framwork orientation is locked, the
    // device orientation and the framework orientation may be different, so we
    // need to rotate the UI. For example, if this value is 90, the UI
    // components should be rotated 90 degrees counter-clockwise.
    private int mOrientationCompensation = 0;

    // This is true if "Settings -> Display -> Rotation Lock" is checked. We
    // don't allow the orientation to be unlocked if the value is true.
    private boolean mRotationLockedSetting = false;

    public OrientationManager(Activity activity) {
        mActivity = activity;
        mListeners = new ArrayList<Listener>();
        mOrientationListener = new MyOrientationEventListener(activity);
    }

    public void resume() {
        ContentResolver resolver = mActivity.getContentResolver();
        mRotationLockedSetting = Settings.System.getInt(
                resolver, Settings.System.ACCELEROMETER_ROTATION, 0) != 1;
        mOrientationListener.enable();
    }

    public void pause() {
        mOrientationListener.disable();
    }

    public void addListener(Listener listener) {
        synchronized (mListeners) {
            mListeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Orientation handling
    //
    //  We can choose to lock the framework orientation or not. If we lock the
    //  framework orientation, we calculate a a compensation value according to
    //  current device orientation and send it to listeners. If we don't lock
    //  the framework orientation, we always set the compensation value to 0.
    ////////////////////////////////////////////////////////////////////////////

    // Lock the framework orientation to the current device orientation
    public void lockOrientation() {
        if (mOrientationLocked) return;
        mOrientationLocked = true;
        if (mActivity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            Log.d(TAG, "lock orientation to landscape");
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            Log.d(TAG, "lock orientation to portrait");
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        updateCompensation();
    }

    // Unlock the framework orientation, so it can change when the device
    // rotates.
    public void unlockOrientation() {
        if (!mOrientationLocked) return;
        if (mRotationLockedSetting) return;
        mOrientationLocked = false;
        Log.d(TAG, "unlock orientation");
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        disableCompensation();
    }

    // Calculate the compensation value and send it to listeners.
    private void updateCompensation() {
        if (mOrientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
            return;
        }

        int orientationCompensation =
            (mOrientation + getDisplayRotation(mActivity)) % 360;

        if (mOrientationCompensation != orientationCompensation) {
            mOrientationCompensation = orientationCompensation;
            notifyListeners();
        }
    }

    // Make the compensation value 0 and send it to listeners.
    private void disableCompensation() {
        if (mOrientationCompensation != 0) {
            mOrientationCompensation = 0;
            notifyListeners();
        }
    }

    private void notifyListeners() {
        synchronized (mListeners) {
            for (int i = 0, n = mListeners.size(); i < n; i++) {
                mListeners.get(i).onOrientationCompensationChanged();
            }
        }
    }

    // This listens to the device orientation, so we can update the compensation.
    private class MyOrientationEventListener extends OrientationEventListener {
        public MyOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            mOrientation = roundOrientation(orientation, mOrientation);
            // If the framework orientation is locked, we update the
            // compensation value and notify the listeners.
            if (mOrientationLocked) updateCompensation();
        }
    }

    @Override
    public int getDisplayRotation() {
        return getDisplayRotation(mActivity);
    }

    @Override
    public int getCompensation() {
        return mOrientationCompensation;
    }

    private static int roundOrientation(int orientation, int orientationHistory) {
        boolean changeOrientation = false;
        if (orientationHistory == OrientationEventListener.ORIENTATION_UNKNOWN) {
            changeOrientation = true;
        } else {
            int dist = Math.abs(orientation - orientationHistory);
            dist = Math.min(dist, 360 - dist);
            changeOrientation = (dist >= 45 + ORIENTATION_HYSTERESIS);
        }
        if (changeOrientation) {
            return ((orientation + 45) / 90 * 90) % 360;
        }
        return orientationHistory;
    }

    private static int getDisplayRotation(Activity activity) {
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        switch (rotation) {
            case Surface.ROTATION_0: return 0;
            case Surface.ROTATION_90: return 90;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 270;
        }
        return 0;
    }
}
