/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.photoeditor.actions;

import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Vector;

/**
 * Doodle that consists of a color and doodling path for drawing.
 */
public class Doodle implements Parcelable {

    private final int color;
    private final Path normalizedPath = new Path();
    private final Vector<PointF> points = new Vector<PointF>();

    /**
     * Creates paint for doodles.
     */
    public static Paint createPaint() {
        Paint paint = new Paint(Paint.DITHER_FLAG | Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(15);
        return paint;
    }

    public Doodle(int color, PointF startPoint) {
        this.color = Color.argb(192, Color.red(color), Color.green(color), Color.blue(color));
        normalizedPath.moveTo(startPoint.x, startPoint.y);
        points.add(startPoint);
    }

    /**
     * Adds control points whose coordinates range from 0 to 1 to construct the doodle path.
     *
     * @return true if the constructed path is in (0, 0, 1, 1) bounds; otherwise, false.
     */
    public boolean addControlPoint(PointF point) {
        PointF last = points.lastElement();
        normalizedPath.quadTo(last.x, last.y, (last.x + point.x) / 2, (last.y + point.y) / 2);
        points.add(point);

        RectF r = new RectF();
        normalizedPath.computeBounds(r, false);
        return r.intersects(0, 0, 1, 1);
    }

    public int getColor() {
        return color;
    }

    public boolean isEmpty() {
        return normalizedPath.isEmpty();
    }

    /**
     * Gets the drawing path from the normalized doodle path.
     */
    public void getDrawingPath(Matrix matrix, Path path) {
        path.set(normalizedPath);
        path.transform(matrix);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(color);
        dest.writeInt(points.size());
        for (PointF point : points) {
            dest.writeParcelable(point, 0);
        }
    }

    public static final Parcelable.Creator<Doodle> CREATOR = new Parcelable.Creator<Doodle>() {

        @Override
        public Doodle createFromParcel(Parcel source) {
            int color = source.readInt();
            int size = source.readInt();
            if (size > 0) {
                Doodle doodle = new Doodle(color, (PointF) source.readParcelable(null));
                for (int i = 1; i < size; i++) {
                    doodle.addControlPoint((PointF) source.readParcelable(null));
                }
                return doodle;
            }
            return new Doodle(color, new PointF(0, 0));
        }

        @Override
        public Doodle[] newArray(int size) {
            return new Doodle[size];
        }};
}
