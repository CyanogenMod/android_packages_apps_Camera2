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

import android.hardware.camera2.CaptureResult;
import android.location.Location;
import android.os.Build;

import com.android.camera.exif.ExifInterface;
import com.android.camera.exif.Rational;
import com.android.camera.one.v2.camera2proxy.CaptureResultProxy;
import com.android.camera.processing.imagebackend.TaskImageContainer;
import com.google.common.base.Optional;

/**
 * Exif utility functions.
 */
public class ExifUtil {
    private static final double LOG_2 = Math.log(2); // natural log of 2

    private final ExifInterface mExif;

    /**
     * Construct a new ExifUtil object.
     * @param exif The EXIF object to populate.
     */
    public ExifUtil(ExifInterface exif) {
        mExif = exif;
    }

    /**
     * Adds the given location to the EXIF object.
     *
     * @param location The location to add.
     */
    public void addLocationToExif(Location location) {
        mExif.addGpsTags(location.getLatitude(), location.getLongitude());
        mExif.addGpsDateTimeStampTag(location.getTime());

        double altitude = location.getAltitude();
        if (altitude == 0) {
            return;
        }
        short altitudeRef = altitude < 0 ? ExifInterface.GpsAltitudeRef.SEA_LEVEL_NEGATIVE
                : ExifInterface.GpsAltitudeRef.SEA_LEVEL;
        mExif.setTag(mExif.buildTag(ExifInterface.TAG_GPS_ALTITUDE_REF, altitudeRef));
    }

    /**
     * Populate the EXIF object with info pulled from a given capture result.
     *
     * @param image A {@link TaskImageContainer.TaskImage} from which to extract info from.
     * @param captureResult A {@link CaptureResultProxy} from which to extract info from.
     */
    public void populateExif(Optional<TaskImageContainer.TaskImage> image,
                             Optional<CaptureResultProxy> captureResult) {
        addMakeAndModelToExif();
        if (image.isPresent()) {
            addImageDataToExif(image.get());
        }
        if (captureResult.isPresent()) {
            addCaptureResultToExif(mExif, captureResult.get());
        }
    }

    private void addMakeAndModelToExif() {
        addExifTag(ExifInterface.TAG_MAKE, Build.MANUFACTURER);
        addExifTag(ExifInterface.TAG_MODEL, Build.MODEL);
    }

    private void addImageDataToExif(TaskImageContainer.TaskImage image) {
        mExif.setTag(mExif.buildTag(ExifInterface.TAG_PIXEL_X_DIMENSION, image.width));
        mExif.setTag(mExif.buildTag(ExifInterface.TAG_PIXEL_Y_DIMENSION, image.height));
        mExif.setTag(mExif.buildTag(ExifInterface.TAG_IMAGE_WIDTH, image.width));
        mExif.setTag(mExif.buildTag(ExifInterface.TAG_IMAGE_LENGTH, image.height));
        mExif.setTag(mExif.buildTag(ExifInterface.TAG_ORIENTATION,
                ExifInterface.getOrientationValueForRotation(image.orientation.getDegrees())));
    }

    private void addCaptureResultToExif(ExifInterface exif, CaptureResultProxy result) {
        final Long NS_TO_S = 1000000000L; // Nanoseconds per second
        final Long SHUTTER_SPEED_VALUE_PRECISION = 100L;
        final Long F_NUMBER_PRECISION = 100L;
        final Long APERTURE_VALUE_PRECISION = 100L;
        final Long FOCAL_LENGTH_PRECISION = 1000L; // micrometer precision

        // Exposure time
        Long exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
        addExifTag(ExifInterface.TAG_EXPOSURE_TIME, new Rational(exposureTimeNs, NS_TO_S));

         // Shutter speed value
        double exposureTime = (double) exposureTimeNs / NS_TO_S;
        double shutterSpeedValue = log2(exposureTime);
        addExifTag(ExifInterface.TAG_SHUTTER_SPEED_VALUE, doubleToRational(shutterSpeedValue, SHUTTER_SPEED_VALUE_PRECISION));

        // ISO
        addExifTag(ExifInterface.TAG_ISO_SPEED_RATINGS, result.get(CaptureResult.SENSOR_SENSITIVITY));

        // F-stop number
        float fNumber = result.get(CaptureResult.LENS_APERTURE);
        addExifTag(ExifInterface.TAG_F_NUMBER, doubleToRational(fNumber, F_NUMBER_PRECISION));

        // Aperture value
        double apertureValue = 2 * log2(fNumber);
        addExifTag(ExifInterface.TAG_APERTURE_VALUE, doubleToRational(apertureValue, APERTURE_VALUE_PRECISION));

        // Focal length
        float focalLength = result.get(CaptureResult.LENS_FOCAL_LENGTH);
        addExifTag(ExifInterface.TAG_FOCAL_LENGTH, doubleToRational(focalLength, FOCAL_LENGTH_PRECISION));
    }

    private void addExifTag(int tagId, Object val) {
        mExif.setTag(mExif.buildTag(tagId, val));
    }

    private Rational doubleToRational(double value, long precision) {
        return new Rational((long) (value * precision), precision);
    }

    private double log2(double value) {
        return Math.log(value) / LOG_2;
    }
}
