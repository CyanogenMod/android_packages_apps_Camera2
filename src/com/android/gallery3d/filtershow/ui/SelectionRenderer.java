/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.gallery3d.filtershow.ui;

import android.graphics.Canvas;
import android.graphics.Paint;

public class SelectionRenderer {

    public static void drawSelection(Canvas canvas, int left, int top, int right, int bottom,
            int stroke, Paint paint) {
        canvas.drawRect(left, top, right, top + stroke, paint);
        canvas.drawRect(left, bottom - stroke, right, bottom, paint);
        canvas.drawRect(left, top, left + stroke, bottom, paint);
        canvas.drawRect(right - stroke, top, right, bottom, paint);
    }

}
