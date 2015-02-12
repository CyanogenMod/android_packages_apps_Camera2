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

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/**
 * Drawing utilities for rendering debug artifacts.
 */
public class DebugBoundsRenderer {
    /**
     * Draw a bounding box with indicators in the corners and crosshairs in
     * the provided canvas object.
     */
    public static void drawBounds(Canvas canvas, Paint paint, float size, Rect rect) {
        drawBounds(canvas, paint, size, rect.left, rect.top, rect.right, rect.bottom);
    }

    /**
     * Draw a bounding box with indicators in the corners and crosshairs in
     * the provided canvas object.
     */
    public static void drawBounds(Canvas canvas, Paint paint, float size, RectF rect) {
        drawBounds(canvas, paint, size, rect.left, rect.top, rect.right, rect.bottom);
    }

    /**
     * Draw a bounding box with indicators in the corners and crosshairs in
     * the provided canvas object.
     */
    public static void drawBounds(Canvas canvas, Paint paint, float size, float x1, float y1,
          float x2, float y2) {
        // Top left
        // horizontal
        canvas.drawLine(x1, y1, x1 + size, y1, paint);
        // vertical
        canvas.drawLine(x1, y1, x1, y1 + size, paint);

        // top right
        // horizontal
        canvas.drawLine(x2 - size, y1, x2, y1, paint);
        // vertical
        canvas.drawLine(x2, y1, x2, y1 + size, paint);

        // bottom right
        // horizontal
        canvas.drawLine(x2 - size, y2, x2, y2, paint);
        // vertical
        canvas.drawLine(x2, y2- size, x2, y2, paint);

        // bottom left
        // horizontal
        canvas.drawLine(x1, y2, x1 + size, y2, paint);
        // vertical
        canvas.drawLine(x1, y2 - size, x1, y2, paint);

        // crosshairs in the center
        float cX = (x1 + x2) / 2;
        float cY = (y1 + y2) / 2;
        float halfSize = size / 2;
        canvas.drawLine(cX - halfSize, cY, cX + halfSize, cY, paint);
        canvas.drawLine(cX, cY - halfSize, cX, cY + halfSize, paint);
    }
}
