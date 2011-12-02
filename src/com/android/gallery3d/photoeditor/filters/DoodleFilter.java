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

package com.android.gallery3d.photoeditor.filters;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.effect.Effect;
import android.media.effect.EffectFactory;
import android.os.Parcel;

import com.android.gallery3d.photoeditor.Photo;
import com.android.gallery3d.photoeditor.actions.Doodle;

import java.util.Vector;

/**
 * Doodle filter applied to the image.
 */
public class DoodleFilter extends Filter {

    public static final Creator<DoodleFilter> CREATOR = creatorOf(DoodleFilter.class);

    private final Vector<Doodle> doodles = new Vector<Doodle>();

    public void addDoodle(Doodle doodle) {
        doodles.add(doodle);
    }

    @Override
    public void process(Photo src, Photo dst) {
        Bitmap bitmap = Bitmap.createBitmap(src.width(), src.height(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Matrix matrix = new Matrix();
        matrix.setRectToRect(new RectF(0, 0, 1, 1),
                new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()), Matrix.ScaleToFit.FILL);

        Path drawingPath = new Path();
        Paint paint = Doodle.createPaint();
        for (Doodle doodle : doodles) {
            paint.setColor(doodle.getColor());
            doodle.getDrawingPath(matrix, drawingPath);
            canvas.drawPath(drawingPath, paint);
        }

        Effect effect = getEffect(EffectFactory.EFFECT_BITMAPOVERLAY);
        effect.setParameter("bitmap", bitmap);
        effect.apply(src.texture(), src.width(), src.height(), dst.texture());
    }

    @Override
    protected void writeToParcel(Parcel out) {
        out.writeInt(doodles.size());
        for (Doodle doodle : doodles) {
            out.writeParcelable(doodle, 0);
        }
    }

    @Override
    protected void readFromParcel(Parcel in) {
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            doodles.add((Doodle) in.readParcelable(Doodle.class.getClassLoader()));
        }
    }
}
