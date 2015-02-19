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

package com.android.camera.app;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Handler;
import android.provider.Settings;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.WindowManager;

import com.android.camera.debug.Log;
import com.android.camera.util.AndroidServices;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.CameraUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * The implementation of {@link com.android.camera.app.OrientationManager}
 * by {@link android.view.OrientationEventListener}.
 */
public class OrientationManagerImpl implements OrientationManager {
    private static final Log.Tag TAG = new Log.Tag("OrientMgrImpl");

    // DeviceOrientation hysteresis amount used in rounding, in degrees
    private static final int ORIENTATION_HYSTERESIS = 5;

    private final Activity mActivity;

    // The handler used to invoke listener callback.
    private final Handler mHandler;

    private final MyOrientationEventListener mOrientationListener;

    // We keep the last known orientation. So if the user first orient
    // the camera then point the camera to floor or sky, we still have
    // the correct orientation.
    private DeviceOrientation mLastDeviceOrientation = DeviceOrientation.CLOCKWISE_0;

    // If the framework orientation is locked.
    private boolean mOrientationLocked = false;

    // This is true if "Settings -> Display -> Rotation Lock" is checked. We
    // don't allow the orientation to be unlocked if the value is true.
    private boolean mRotationLockedSetting = false;

    private final List<OnOrientationChangeListener> mListeners =
            new ArrayList<OnOrientationChangeListener>();

    private final boolean mIsDefaultToPortrait;

    /**
     * Instantiates a new orientation manager.
     *
     * @param activity The main activity object.
     * @param handler The handler used to invoke listener callback.
     */
    public OrientationManagerImpl(Activity activity, Handler handler) {
        mActivity = activity;
        mOrientationListener = new MyOrientationEventListener(activity);
        mHandler = handler;
        mIsDefaultToPortrait = isDefaultToPortrait(activity);
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

    @Override
    public DeviceNaturalOrientation getDeviceNaturalOrientation() {
        return mIsDefaultToPortrait ? DeviceNaturalOrientation.PORTRAIT :
                DeviceNaturalOrientation.LANDSCAPE;
    }

    @Override
    public DeviceOrientation getDeviceOrientation() {
        return mLastDeviceOrientation;
    }

    @Override
    public DeviceOrientation getDisplayRotation() {
        return DeviceOrientation.from((360 - CameraUtil.getDisplayRotation()) % 360);
    }

    @Override
    public void addOnOrientationChangeListener(OnOrientationChangeListener listener) {
        if (mListeners.contains(listener)) {
            return;
        }
        mListeners.add(listener);
    }

    @Override
    public void removeOnOrientationChangeListener(OnOrientationChangeListener listener) {
        if (!mListeners.remove(listener)) {
            Log.v(TAG, "Removing non-existing listener.");
        }
    }

    @Override
    public boolean isInLandscape() {
        int roundedOrientationDegrees = mLastDeviceOrientation.getDegrees();
        if (mIsDefaultToPortrait) {
            if (roundedOrientationDegrees % 180 == 90) {
                return true;
            }
        } else {
            if (roundedOrientationDegrees % 180 == 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isInPortrait() {
        return !isInLandscape();
    }

    @Override
    public void lockOrientation() {
        if (mOrientationLocked || mRotationLockedSetting) {
            return;
        }
        mOrientationLocked = true;
        if (ApiHelper.HAS_ORIENTATION_LOCK) {
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        } else {
            mActivity.setRequestedOrientation(calculateCurrentScreenOrientation());
        }
    }

    @Override
    public void unlockOrientation() {
        if (!mOrientationLocked || mRotationLockedSetting) {
            return;
        }
        mOrientationLocked = false;
        Log.d(TAG, "unlock orientation");
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
    }

    @Override
    public boolean isOrientationLocked() {
        return (mOrientationLocked || mRotationLockedSetting);
    }

    private int calculateCurrentScreenOrientation() {
        int displayRotation = getDisplayRotation(mActivity);
        // Display rotation >= 180 means we need to use the REVERSE landscape/portrait
        boolean standard = displayRotation < 180;
        if (mActivity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            return standard
                    ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        } else {
            if (displayRotation == 90 || displayRotation == 270) {
                // If displayRotation = 90 or 270 then we are on a landscape
                // device. On landscape devices, portrait is a 90 degree
                // clockwise rotation from landscape, so we need
                // to flip which portrait we pick as display rotation is counter clockwise
                standard = !standard;
            }
            return standard
                    ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    : ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
        }
    }

    // This listens to the device orientation, so we can update the compensation.
    private class MyOrientationEventListener extends OrientationEventListener {
        public MyOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (orientation == ORIENTATION_UNKNOWN) {
                return;
            }

            final DeviceOrientation roundedDeviceOrientation =
                    roundOrientation(mLastDeviceOrientation, orientation);
            if (roundedDeviceOrientation == mLastDeviceOrientation) {
                return;
            }
            Log.v(TAG, "orientation changed (from:to) " + mLastDeviceOrientation +
                    ":" + roundedDeviceOrientation);
            mLastDeviceOrientation = roundedDeviceOrientation;

            for (final OnOrientationChangeListener listener : mListeners) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onOrientationChanged(OrientationManagerImpl.this, roundedDeviceOrientation);
                    }
                });
            }
        }
    }

    private static DeviceOrientation roundOrientation(DeviceOrientation oldDeviceOrientation,
                                                      int newRawOrientation) {
        int dist = Math.abs(newRawOrientation - oldDeviceOrientation.getDegrees());
        dist = Math.min(dist, 360 - dist);
        boolean isOrientationChanged = (dist >= 45 + ORIENTATION_HYSTERESIS);

        if (isOrientationChanged) {
            int newRoundedOrientation = ((newRawOrientation + 45) / 90 * 90) % 360;
            switch (newRoundedOrientation) {
                case 0:
                    return DeviceOrientation.CLOCKWISE_0;
                case 90:
                    return DeviceOrientation.CLOCKWISE_90;
                case 180:
                    return DeviceOrientation.CLOCKWISE_180;
                case 270:
                    return DeviceOrientation.CLOCKWISE_270;
            }
        }
        return oldDeviceOrientation;
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

    /**
     * Calculate the default orientation of the device based on the width and
     * height of the display when rotation = 0 (i.e. natural width and height)
     *
     * @param context current context
     * @return whether the default orientation of the device is portrait
     */
    private static boolean isDefaultToPortrait(Context context) {
        Display currentDisplay = AndroidServices.instance().provideWindowManager()
                .getDefaultDisplay();
        Point displaySize = new Point();
        currentDisplay.getSize(displaySize);
        int orientation = currentDisplay.getRotation();
        int naturalWidth, naturalHeight;
        if (orientation == Surface.ROTATION_0 || orientation == Surface.ROTATION_180) {
            naturalWidth = displaySize.x;
            naturalHeight = displaySize.y;
        } else {
            naturalWidth = displaySize.y;
            naturalHeight = displaySize.x;
        }
        return naturalWidth < naturalHeight;
    }
}
