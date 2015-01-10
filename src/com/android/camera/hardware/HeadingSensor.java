/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.camera.hardware;

import com.android.camera.debug.Log;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * A virtual sensor that reports device heading based on information
 * provided by accelerometer sensor or magnetic sensor.
 */
public class HeadingSensor implements SensorEventListener {
    private static final Log.Tag TAG = new Log.Tag("HeadingSensor");

    /** Invalid heading values. */
    public static final int INVALID_HEADING = -1;
    /** Current device heading. */
    private int mHeading = INVALID_HEADING;

    /** Device sensor manager. */
    private final SensorManager mSensorManager;
    /** Accelerometer. */
    private final Sensor mAccelerometerSensor;
    /** Compass. */
    private final Sensor mMagneticSensor;

    /** Accelerometer data. */
    private final float[] mGData = new float[3];
    /** Magnetic sensor data. */
    private final float[] mMData = new float[3];
    /** Temporary rotation matrix. */
    private final float[] mRotationMatrix = new float[16];

    /**
     * Constructs a heading sensor.
     *
     * @param sensorManager A {#link android.hardware.SensorManager} that
     *                      provides access to device sensors.
     */
    public HeadingSensor(SensorManager sensorManager) {
        mSensorManager = sensorManager;
        mAccelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagneticSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    /**
     * Returns current device heading.
     *
     * @return current device heading in degrees. INVALID_HEADING if sensors
     *         are not available.
     */
    public int getCurrentHeading() {
        return mHeading;
    }

    /**
     * Activates corresponding device sensors to start calculating device heading.
     * This would increase power consumption.
     */
    public void activate() {
        // Get events from the accelerometer and magnetic sensor.
        if (mAccelerometerSensor != null) {
            mSensorManager.registerListener(this, mAccelerometerSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (mMagneticSensor != null) {
            mSensorManager.registerListener(this, mMagneticSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    /**
     * Deactivates corresponding device sensors to stop calculating device heading.
     */
    public void deactivate() {
        // Unregister the sensors.
        if (mAccelerometerSensor != null) {
            mSensorManager.unregisterListener(this, mAccelerometerSensor);
        }
        if (mMagneticSensor != null) {
            mSensorManager.unregisterListener(this, mMagneticSensor);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // This is literally the same as the GCamModule implementation.
        int type = event.sensor.getType();
        float[] data;
        if (type == Sensor.TYPE_ACCELEROMETER) {
            data = mGData;
        } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            data = mMData;
        } else {
            Log.w(TAG, String.format("Unexpected sensor type %s", event.sensor.getName()));
            return;
        }
        for (int i = 0; i < 3; i++) {
            data[i] = event.values[i];
        }
        float[] orientation = new float[3];
        SensorManager.getRotationMatrix(mRotationMatrix, null, mGData, mMData);
        SensorManager.getOrientation(mRotationMatrix, orientation);
        mHeading = (int) (orientation[0] * 180f / Math.PI) % 360;

        if (mHeading < 0) {
            mHeading += 360;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
