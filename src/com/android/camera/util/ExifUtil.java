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

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

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
     * Populate the EXIF object with info pulled from a given capture result.
     *
     * @param image A {@link TaskImageContainer.TaskImage} from which to extract info from.
     * @param captureResult A {@link CaptureResultProxy} from which to extract info from.
     * @param location optionally a location that should be added to the EXIF.
     */
    public void populateExif(Optional<TaskImageContainer.TaskImage> image,
                             Optional<CaptureResultProxy> captureResult,
                             Optional<Location> location) {
        addExifVersionToExif();
        addTimestampToExif();
        addMakeAndModelToExif();
        if (image.isPresent()) {
            addImageDataToExif(image.get());
        }
        if (captureResult.isPresent()) {
            addCaptureResultToExif(captureResult.get());
        }
        if (location.isPresent()) {
            addLocationToExif(location.get());
        }
    }

    /**
     * Adds the given location to the EXIF object.
     *
     * @param location The location to add.
     */
    public void addLocationToExif(Location location) {
        final Long ALTITUDE_PRECISION = 1L; // GPS altitude isn't particularly accurate (determined empirically)

        mExif.addGpsTags(location.getLatitude(), location.getLongitude());
        mExif.addGpsDateTimeStampTag(location.getTime());

        if (location.hasAltitude()) {
            double altitude = location.getAltitude();
            addExifTag(ExifInterface.TAG_GPS_ALTITUDE, rational(altitude, ALTITUDE_PRECISION));
            short altitudeRef = altitude < 0 ? ExifInterface.GpsAltitudeRef.SEA_LEVEL_NEGATIVE
                    : ExifInterface.GpsAltitudeRef.SEA_LEVEL;
            addExifTag(ExifInterface.TAG_GPS_ALTITUDE_REF, altitudeRef);
        }
    }

    private void addExifVersionToExif() {
        addExifTag(ExifInterface.TAG_EXIF_VERSION, ExifInterface.EXIF_VERSION);
    }

    private void addTimestampToExif() {
        final Long MS_TO_S = 1000L; // Milliseconds per second
        final String subSecondFormat = "000";

        Long timestampMs = System.currentTimeMillis();
        TimeZone timezone = Calendar.getInstance().getTimeZone();
        mExif.addDateTimeStampTag(ExifInterface.TAG_DATE_TIME, timestampMs, timezone);
        mExif.addDateTimeStampTag(ExifInterface.TAG_DATE_TIME_DIGITIZED, timestampMs, timezone);
        mExif.addDateTimeStampTag(ExifInterface.TAG_DATE_TIME_ORIGINAL, timestampMs, timezone);

        Long subSeconds = timestampMs % MS_TO_S;
        String subSecondsString = new DecimalFormat(subSecondFormat).format(subSeconds);
        addExifTag(ExifInterface.TAG_SUB_SEC_TIME, subSecondsString);
        addExifTag(ExifInterface.TAG_SUB_SEC_TIME_ORIGINAL, subSecondsString);
        addExifTag(ExifInterface.TAG_SUB_SEC_TIME_DIGITIZED, subSecondsString);
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

    private void addCaptureResultToExif(CaptureResultProxy result) {
        final Long NS_TO_S = 1000000000L; // Nanoseconds per second
        final Long SHUTTER_SPEED_VALUE_PRECISION = 1000L;
        final Long F_NUMBER_PRECISION = 100L;
        final Long APERTURE_VALUE_PRECISION = 100L;
        final Long FOCAL_LENGTH_PRECISION = 1000L; // micrometer precision

        // Exposure time
        Long exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
        addExifTag(ExifInterface.TAG_EXPOSURE_TIME, ratio(exposureTimeNs, NS_TO_S));

        // Shutter speed value (APEX unit, see Jeita EXIF 2.2 spec Annex C).
        if (exposureTimeNs != null) {
            Double exposureTime = (double) exposureTimeNs / NS_TO_S;
            Double shutterSpeedValue = -log2(exposureTime);
            addExifTag(ExifInterface.TAG_SHUTTER_SPEED_VALUE, rational(shutterSpeedValue, SHUTTER_SPEED_VALUE_PRECISION));
        }

        // ISO
        addExifTag(ExifInterface.TAG_ISO_SPEED_RATINGS, result.get(CaptureResult.SENSOR_SENSITIVITY));

        // F-stop number
        Float fNumber = result.get(CaptureResult.LENS_APERTURE);
        addExifTag(ExifInterface.TAG_F_NUMBER, rational(fNumber, F_NUMBER_PRECISION));

        // Aperture value (APEX unit, see Jeita EXIF 2.2 spec Annex C).
        if (fNumber != null) {
            Double apertureValue = 2 * log2(fNumber);
            addExifTag(ExifInterface.TAG_APERTURE_VALUE, rational(apertureValue, APERTURE_VALUE_PRECISION));
        }

        // Focal length
        Float focalLength = result.get(CaptureResult.LENS_FOCAL_LENGTH);
        addExifTag(ExifInterface.TAG_FOCAL_LENGTH, rational(focalLength, FOCAL_LENGTH_PRECISION));

        // Flash mode
        Integer flashMode = result.get(CaptureResult.FLASH_MODE);
        if (flashMode == CaptureResult.FLASH_MODE_OFF) {
            addExifTag(ExifInterface.TAG_FLASH, ExifInterface.Flash.DID_NOT_FIRE);
        } else {
            addExifTag(ExifInterface.TAG_FLASH, ExifInterface.Flash.FIRED);
        }

        // White balance
        Integer whiteBalanceMode = result.get(CaptureResult.CONTROL_AWB_MODE);
        if (whiteBalanceMode == CaptureResult.CONTROL_AWB_MODE_OFF) {
            addExifTag(ExifInterface.TAG_WHITE_BALANCE, ExifInterface.WhiteBalance.MANUAL);
        } else {
            addExifTag(ExifInterface.TAG_WHITE_BALANCE, ExifInterface.WhiteBalance.AUTO);
        }

    }

    private void addExifTag(int tagId, Object val) {
        if (val != null) {
            mExif.setTag(mExif.buildTag(tagId, val));
        }
    }

    private Rational ratio(Long numerator, Long denominator) {
        if (numerator != null && denominator != null) {
            return new Rational(numerator, denominator);
        }
        return null;
    }
    private Rational rational(Float value, Long precision) {
        if (value != null && precision != null) {
            return new Rational((long) (value * precision), precision);
        }
        return null;
    }

    private Rational rational(Double value, Long precision) {
        if (value != null && precision != null) {
            return new Rational((long) (value * precision), precision);
        }
        return null;
    }

    private Double log2(Float value) {
        if (value != null) {
            return Math.log(value) / LOG_2;
        }
        return null;
    }

    private Double log2(Double value) {
        if (value != null) {
            return Math.log(value) / LOG_2;
        }
        return null;
    }
}
