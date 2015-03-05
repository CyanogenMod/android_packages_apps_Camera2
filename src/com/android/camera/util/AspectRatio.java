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

package com.android.camera.util;

import android.graphics.Rect;

import com.google.common.base.Objects;

import java.math.BigInteger;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Contains precise (integer) logic for handling aspect ratios as rational
 * numbers.
 */
@ParametersAreNonnullByDefault
public final class AspectRatio {
    private static final AspectRatio ASPECT_RATIO_4x3 = AspectRatio.of(4, 3);
    private static final AspectRatio ASPECT_RATIO_16x9 = AspectRatio.of(16, 9);

    private final int mWidth;
    private final int mHeight;

    /**
     * @param width The width of the aspect ratio, after simplification.
     * @param height The height of the aspect ratio, after simplification.
     */
    private AspectRatio(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public static AspectRatio of(int width, int height) {
        int gcd = BigInteger.valueOf(width).gcd(BigInteger.valueOf(height)).intValue();
        int simplifiedWidth = width / gcd;
        int simplifiedHeight = height / gcd;
        return new AspectRatio(simplifiedWidth, simplifiedHeight);
    }

    public static AspectRatio of(Size size) {
        return of(size.width(), size.height());
    }

    public static AspectRatio of4x3() {
        return ASPECT_RATIO_4x3;
    }

    public static AspectRatio of16x9() {
        return ASPECT_RATIO_16x9;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getWidth() {
        return mWidth;
    }

    public float toFloat() {
        return (float) mWidth / (float) mHeight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof AspectRatio))
            return false;

        AspectRatio that = (AspectRatio) o;

        if (mHeight != that.mHeight)
            return false;
        if (mWidth != that.mWidth)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mWidth, mHeight);
    }

    @Override
    public String toString() {
        return String.format("AspectRatio[%d:%d]", getWidth(), getHeight());
    }

    /**
     * @return The transpose of this aspect ratio.
     */
    public AspectRatio transpose() {
        return of(mHeight, mWidth);
    }

    /**
     * @return The landscape version of this aspect ratio.
     */
    public AspectRatio asLandscape() {
        if (isLandscape()) {
            return this;
        } else {
            return transpose();
        }
    }

    /**
     * @return The portrait version of this aspect ratio.
     */
    public AspectRatio asPortrait() {
        if (isPortrait()) {
            return this;
        } else {
            return transpose();
        }
    }

    /**
     * @return The version of this aspect ratio in the same orientation
     *         (portrait vs. landscape) of the other.
     */
    public AspectRatio withOrientationOf(AspectRatio other) {
        if (other.isPortrait()) {
            return asPortrait();
        } else {
            return asLandscape();
        }
    }

    /**
     * @return True if this aspect ratio is wider than the other.
     */
    public boolean isWiderThan(AspectRatio other) {
        // this.mWidth other.mWidth
        // ----------- > ------------
        // this.mHeight other.mHeight
        return this.mWidth * other.mHeight > other.mWidth * this.mHeight;
    }

    /**
     * @return True if this aspect ratio is taller than the other.
     */
    public boolean isTallerThan(AspectRatio other) {
        // this.mWidth other.mWidth
        // ----------- < ------------
        // this.mHeight other.mHeight
        return this.mWidth * other.mHeight < other.mWidth * this.mHeight;
    }

    /**
     * @return The largest centered region of area with this aspect ratio. For
     *         non-integer values, the returned rectangle coordinates are the
     *         *floor* of the result.
     */
    public Rect getLargestCenterCrop(Size area) {
        AspectRatio original = of(area);

        if (this.isWiderThan(original)) {
            // Crop off the top and bottom...
            int cropHeight = area.width() * mHeight / mWidth;
            int cropTop = (area.height() - cropHeight) / 2;
            int cropBottom = cropTop + cropHeight;
            int cropLeft = 0;
            int cropRight = area.width();
            return new Rect(cropLeft, cropTop, cropRight, cropBottom);
        } else {
            // Crop off the left and right...
            int cropWidth = area.height() * mWidth / mHeight;
            int cropLeft = (area.width() - cropWidth) / 2;
            int cropRight = cropLeft + cropWidth;
            int cropTop = 0;
            int cropBottom = area.height();
            return new Rect(cropLeft, cropTop, cropRight, cropBottom);
        }
    }

    /**
     * @return True if this aspect ratio is in landscape orientation. Square
     *         aspect ratios are both portrait *and* landscape.
     */
    private boolean isLandscape() {
        return mWidth >= mHeight;
    }

    /**
     * @return True if this aspect ratio is in portrait orientation. Square
     *         aspect ratios are both portrait *and* landscape.
     */
    private boolean isPortrait() {
        return mWidth <= mHeight;
    }

}
