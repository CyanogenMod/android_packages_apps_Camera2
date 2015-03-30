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

import android.annotation.TargetApi;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build.VERSION_CODES;
import android.text.TextUtils;

import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple size class until we are 'L' only and can use android.util.Size.
 */
public class Size {
    public static final String LIST_DELIMITER = ",";

    private final int width;
    private final int height;

    public Size(Point point) {
        this.width = point.x;
        this.height = point.y;
    }

    @TargetApi(VERSION_CODES.LOLLIPOP)
    public Size(android.util.Size size) {
        this.width = size.getWidth();
        this.height = size.getHeight();
    }

    public Size(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public static Size of(Rect rectangle) {
        return new Size(rectangle.width(), rectangle.height());
    }

    /**
     * Constructor from a source {@link android.hardware.Camera.Size}.
     *
     * @param other The source size.
     */
    public Size(Camera.Size other) {
        this.width = other.width;
        this.height = other.height;
    }

    public Size(com.android.ex.camera2.portability.Size size) {
        this.width = size.width();
        this.height = size.height();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    @Override
    public String toString() {
        return width + "x" + height;
    }

    public Size transpose() {
        return new Size(height, width);
    }

    /**
     * @return The landscape version of this size.
     */
    public Size asLandscape() {
        if (isLandscape()) {
            return this;
        } else {
            return transpose();
        }
    }

    /**
     * @return The portrait version of this size.
     */
    public Size asPortrait() {
        if (isPortrait()) {
            return this;
        } else {
            return transpose();
        }
    }

    public long area() {
        return width * height;
    }

    /** Returns width/height. */
    public float aspectRatio() {
        return (float) width / height;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Size)) {
            return false;
        }

        Size otherSize = (Size) other;
        return otherSize.width == this.width && otherSize.height == this.height;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(width, height);
    }

    public com.android.ex.camera2.portability.Size toPortabilitySize() {
        return new com.android.ex.camera2.portability.Size(width, height);
    }

    @TargetApi(VERSION_CODES.LOLLIPOP)
    public static Size[] convert(android.util.Size[] sizes) {
        Size[] converted = new Size[sizes.length];
        for (int i = 0; i < sizes.length; ++i) {
            converted[i] = new Size(sizes[i].getWidth(), sizes[i].getHeight());
        }
        return converted;
    }

    public static List<Size> convert(List<com.android.ex.camera2.portability.Size> sizes) {
        ArrayList<Size> converted = new ArrayList<>(sizes.size());
        for (com.android.ex.camera2.portability.Size size : sizes) {
            converted.add(new Size(size.width(), size.height()));
        }
        return converted;
    }

    /**
     * Encode List of this class as comma-separated list of integers.
     *
     * @param sizes List of this class to encode.
     * @return encoded string.
     */
    public static String listToString(List<Size> sizes) {
        ArrayList<Integer> flatSizes = new ArrayList<>();
        for (Size s : sizes) {
            flatSizes.add(s.width());
            flatSizes.add(s.height());
        }
        return TextUtils.join(LIST_DELIMITER, flatSizes);
    }

    /**
     * Decode comma-separated even-length list of integers into a List of this class.
     *
     * @param encodedSizes encoded string.
     * @return List of this class.
     */
    public static List<Size> stringToList(String encodedSizes) {
        String[] flatSizes = TextUtils.split(encodedSizes, LIST_DELIMITER);
        ArrayList<Size> list = new ArrayList<>();
        for (int i = 0; i < flatSizes.length; i += 2) {
            int width = Integer.parseInt(flatSizes[i]);
            int height = Integer.parseInt(flatSizes[i + 1]);
            list.add(new Size(width, height));
        }
        return list;
    }

    /**
     * An helper method to build a list of this class from a list of
     * {@link android.hardware.Camera.Size}.
     *
     * @param cameraSizes Source.
     * @return The built list.
     */
    public static List<Size> buildListFromCameraSizes(List<Camera.Size> cameraSizes) {
        ArrayList<Size> list = new ArrayList<Size>(cameraSizes.size());
        for (Camera.Size cameraSize : cameraSizes) {
            list.add(new Size(cameraSize));
        }
        return list;
    }

    /**
     * @return True if this size is in landscape orientation. Square
     *         sizes are both portrait *and* landscape.
     */
    private boolean isLandscape() {
        return width >= height;
    }

    /**
     * @return True if this size is in portrait orientation. Square
     *         sizes are both portrait *and* landscape.
     */
    private boolean isPortrait() {
        return height >= width;
    }
}
