/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */
package com.android.camera;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

public class CameraServiceSensor implements SensorEventListener {
    private SensorManager mSensorManager;
    private Sensor mRotationSensor;
    private Sensor mOrientationSensor;
    private IRotationListener mRotationEvent;
    private boolean mPortrait;
    private boolean mLandscape;
    private boolean mSatisfies;
    private Handler mHandler;
    private Runnable mClearRunnable;
    private static final int DELAY_TIMER = 500;

    private long mTimerDelay;

    public static interface IRotationListener {
        public void onRotationChanged(boolean satisfies);
    }

    public CameraServiceSensor(Context ctx, IRotationListener rotationEventListener) {
        mSensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        mRotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mOrientationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        mRotationEvent = rotationEventListener;
        mPortrait = false;
        mLandscape = false;
        mSatisfies = false;
        mHandler = new Handler();
        mClearRunnable = new Runnable() {
            @Override
            public void run() {
                mRotationEvent.onRotationChanged(mSatisfies);
            }
        };
    }

    public void resume() {
        final int DELAY = 100 * 1000;
        mSensorManager.registerListener(this, mRotationSensor, DELAY);
        mSensorManager.registerListener(this, mOrientationSensor, DELAY);
    }

    public void pause() {
        mSensorManager.unregisterListener(this, mRotationSensor);
        mSensorManager.unregisterListener(this, mOrientationSensor);
        mPortrait = false;
        mLandscape = false;
        mSatisfies = false;
    }

    public void destroy() {
        mSensorManager.unregisterListener(this, mRotationSensor);
        mSensorManager.unregisterListener(this, mOrientationSensor);

        mHandler.removeCallbacks(mClearRunnable);
        mHandler = null;
    }

    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(
                    rotationMatrix , event.values);

            float[] floatValues= new float[3];
            SensorManager.getOrientation(rotationMatrix, floatValues);

            int[] values = new int[3];

            for (int i = 0; i < floatValues.length; i++) {
                values[i] = convertToInt(floatValues[i]);
            }


            String text = "Rotation: x:" + values[0] + ", y:" + values[1] + ", z:" + values[2];

            checkRotationChangedLandscape(isInLandscapeMode(values));
        } else if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
            String text = "Orientation x:" + (int)event.values[0]
                    + ", y:" + (int)event.values[1]
                    + ", z:" + (int)event.values[2];

            checkRotationChangedPortrait(isInPortraitMode(event.values));

        }
    }

    private void checkRotationChangedPortrait(boolean satisfies) {
        if (mPortrait != satisfies) {
            mPortrait = satisfies;
            checkRotationChanged();
        }
    }

    private void checkRotationChangedLandscape(boolean satisfies) {
        if (mLandscape != satisfies) {
            mLandscape = satisfies;
            checkRotationChanged();
        }
    }

    private void checkRotationChanged() {
        boolean satisfies = mPortrait || mLandscape;
        if (satisfies != mSatisfies) {
            mSatisfies = satisfies;
            mTimerDelay = System.currentTimeMillis();
            mHandler.removeCallbacks(mClearRunnable);
            if (!mSatisfies) {
                mHandler.postDelayed(mClearRunnable, DELAY_TIMER);
            } else {
                mRotationEvent.onRotationChanged(mSatisfies);
            }
        }
    }

    private static int convertToInt(float v) {
        return (int)((v * 180) / Math.PI);
    }

    private static boolean isInLandscapeMode(int[] values) {
        if (Math.abs(values[1]) <= 10
                && values[2] <= -46 && values[2] >= -116) {
            return true;
        }

        return false;
    }

    private static boolean isInPortraitMode(float[] values) {
        if (Math.abs(values[2]) <= 15
                && values[1] <= -40 && values[1] >= -130) {
            return true;
        }

        return false;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
