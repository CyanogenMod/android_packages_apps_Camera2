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
import android.hardware.Camera;

import java.util.ArrayList;
import java.util.List;

/**
 * An immutable simple size container.
 */
public class Size {

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

    private final Point val;

    /**
     * Constructor.
     */
    public Size(int width, int height) {
        val = new Point(width, height);
    }

    /**
     * Copy constructor.
     */
    public Size(Size other) {
        if (other == null) {
            val = new Point(0, 0);
        } else {
            val = new Point(other.width(), other.height());
        }
    }

    /**
     * Constructor from a source {@link android.hardware.Camera.Size}.
     *
     * @param other The source size.
     */
    public Size(Camera.Size other) {
        if (other == null) {
            val = new Point(0, 0);
        } else {
            val = new Point(other.width, other.height);
        }
    }

    /**
     * Constructor from a source {@link android.graphics.Point}.
     *
     * @param p The source size.
     */
    public Size(Point p) {
        if (p == null) {
            val = new Point(0, 0);
        } else {
            val = new Point(p);
        }
    }

    public int width() {
        return val.x;
    }

    public int height() {
        return val.y;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Size) {
            Size other = (Size) o;
            return val.equals(other.val);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return val.hashCode();
    }

    @Override
    public String toString() {
        return "Size: (" + this.width() + " x " + this.height() + ")";
    }
}
