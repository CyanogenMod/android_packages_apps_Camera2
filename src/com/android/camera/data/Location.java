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

package com.android.camera.data;

import java.util.Locale;

/**
 * Encapsulate latitude and longitude into a single location object.
 *
 * TODO: Add tests. Consider removing "ZERO" location and using UNKNOWN.
 */
public final class Location {
    public static final Location UNKNOWN = new Location(Double.NaN, Double.NaN);
    public static final Location ZERO = new Location(0.0, 0.0);

    private final double mLatitude;
    private final double mLongitude;

    private Location(double latitude, double longitude) {
        mLatitude = latitude;
        mLongitude = longitude;
    }

    public double getLatitude() {
        return mLatitude;
    }

    public double getLongitude() {
        return mLongitude;
    }

    public String getLocationString() {
        return String.format(Locale.getDefault(), "%f, %f", mLatitude,
              mLongitude);
    }

    public boolean isValid() {
        return !this.equals(UNKNOWN) && !this.equals(ZERO)
              && (mLatitude >= -90.0 && mLongitude <= 90.0)
              && (mLongitude >= -180.0 && mLongitude <= 180.0);
    }

    @Override
    public String toString() {
        return "Location: " + getLocationString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Location location = (Location) o;

        if (Double.compare(location.mLatitude, mLatitude) != 0) {
            return false;
        }
        if (Double.compare(location.mLongitude, mLongitude) != 0) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(mLatitude);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(mLongitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    public static Location from(double latitude, double longitude) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)
              || Double.isInfinite(latitude) || Double.isInfinite(longitude)
              || (latitude == 0.0 && longitude == 0.0)) {
            return UNKNOWN;
        }

        return new Location(latitude, longitude);
    }
}
