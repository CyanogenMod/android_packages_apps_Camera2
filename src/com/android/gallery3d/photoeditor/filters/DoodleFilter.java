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

import com.android.gallery3d.photoeditor.Photo;
import com.android.gallery3d.photoeditor.actions.DoodlePaint;

import java.util.Vector;

/**
 * Doodle filter applied to the image.
 */
public class DoodleFilter extends Filter {

    private static class ColorPath {
        private final int color;
        private final Path path;

        ColorPath(int color, Path path) {
            this.color = color;
            this.path = path;
        }
    }

    private final Vector<ColorPath> doodles = new Vector<ColorPath>();

    /**
     * Signals once at least a doodle drawn within photo bounds; this filter is regarded as invalid
     * (no-op on the photo) until not all its doodling is out of bounds.
     */
    public void setDoodledInPhotoBounds() {
        validate();
    }

    /**
     * The path coordinates used here should range from 0 to 1.
     */
    public void addPath(Path path, int color) {
        doodles.add(new ColorPath(color, path));
    }

    @Override
    public void process(Photo src, Photo dst) {
        Bitmap bitmap = Bitmap.createBitmap(src.width(), src.height(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Matrix matrix = new Matrix();
        matrix.setRectToRect(new RectF(0, 0, 1, 1),
                new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()), Matrix.ScaleToFit.FILL);

        Path drawingPath = new Path();
        Paint paint = new DoodlePaint();
        for (ColorPath doodle : doodles) {
            paint.setColor(doodle.color);
            drawingPath.set(doodle.path);
            drawingPath.transform(matrix);
            canvas.drawPath(drawingPath, paint);
        }

        Effect effect = getEffect(EffectFactory.EFFECT_BITMAPOVERLAY);
        effect.setParameter("bitmap", bitmap);
        effect.apply(src.texture(), src.width(), src.height(), dst.texture());
    }
}
