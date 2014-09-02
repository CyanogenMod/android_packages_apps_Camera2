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

import android.graphics.Point;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple size class until we are 'L' only and can use android.util.Size.
 */
public class Size {
    private final int width;
    private final int height;

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

    public Size(Point point) {
        this.width = point.x;
        this.height = point.y;
    }

    public Size(android.util.Size size) {
        this.width = size.getWidth();
        this.height = size.getHeight();
    }

    public Size(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Override
    public String toString() {
        return width + " x " + height;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Size)) {
            return false;
        }

        Size otherSize = (Size) other;
        return otherSize.width == this.width && otherSize.height == this.height;
    }
}
