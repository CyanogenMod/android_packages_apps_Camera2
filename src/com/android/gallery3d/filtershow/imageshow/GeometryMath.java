/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.filtershow.imageshow;

public class GeometryMath {
    protected static float clamp(float i, float low, float high) {
        return Math.max(Math.min(i, high), low);
    }

    protected static float[] shortestVectorFromPointToLine(float[] point, float[] l1, float[] l2) {
        float x1 = l1[0];
        float x2 = l2[0];
        float y1 = l1[1];
        float y2 = l2[1];
        float xdelt = x2 - x1;
        float ydelt = y2 - y1;
        if (xdelt == 0 && ydelt == 0)
            return null;
        float u = ((point[0] - x1) * xdelt + (point[1] - y1) * ydelt)
                / (xdelt * xdelt + ydelt * ydelt);
        float[] ret = {
                (x1 + u * (x2 - x1)), (y1 + u * (y2 - y1))
        };
        return ret;
    }

    //A . B
    protected static float dotProduct(float[] a, float[] b){
        return a[0] * b[0] + a[1] * b[1];
    }

    protected static float[] normalize(float[] a){
        float length = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1]);
        float[] b = { a[0] / length, a[1] / length };
        return b;
    }

    //A onto B
    protected static float scalarProjection(float[] a, float[] b){
        float length = (float) Math.sqrt(b[0] * b[0] + b[1] * b[1]);
        return dotProduct(a, b) / length;
    }

    protected static float[] getVectorFromPoints(float [] point1, float [] point2){
        float [] p = { point2[0] - point1[0], point2[1] - point1[1] };
        return p;
    }

    protected static float[] getUnitVectorFromPoints(float [] point1, float [] point2){
        float [] p = { point2[0] - point1[0], point2[1] - point1[1] };
        float length = (float) Math.sqrt(p[0] * p[0] + p[1] * p[1]);
        p[0] = p[0] / length;
        p[1] = p[1] / length;
        return p;
    }
}
