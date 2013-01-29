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

package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PathMeasure;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.EditorDraw;

import java.util.Arrays;

public class ImageFilterDraw extends ImageFilter {
    private static final String LOGTAG = "ImageFilterDraw";
    public final static char SIMPLE_STYLE = 0;
    public final static char BRUSH_STYLE = 1;
    Bitmap mOverlayBitmap; // this accelerates interaction
    int mCachedStrokes = -1;
    int mCurrentStyle = 0;
    DrawStyle[] mDrawings = new DrawStyle[] {
            new SimpleDraw(), new Brush() };

    public void setStyle(char style) {
        mCurrentStyle = style;
    }

    public static interface DrawStyle {
        public DrawStyle clone();
        public void setSize(float radius);
        public void setColor(int color);
        public void startStroke(float x, float y);
        public void stroke(float x, float y);
        public void endStroke(float x, float y);
        public int getNumberOfStrokes();
        public void clearCurren();
        public void paintCurrentStroke(Canvas canvas, Matrix toScrMatrix, boolean highQuality);
        public int paintLast(int from, Canvas canvas, Matrix toScrMatrix, boolean highQuality);
        public boolean same(DrawStyle o);
        public boolean empty();
    };

    class SimpleDraw implements DrawStyle {
        private Path[] mPaths = new Path[0];
        private int[] mColors = new int[0];
        private float[] mRadius = new float[0];
        private int mStrokeCnt = 0;

        private Path mCurrentPath;
        private float mCurrentRadius;
        private int mCurrentColor;

        @Override
        public DrawStyle clone() {
            SimpleDraw ret = new SimpleDraw();
            ret.mPaths = new Path[mPaths.length];
            for (int i = 0; i < ret.mPaths.length; i++) {
                ret.mPaths[i] = new Path(mPaths[i]);
            }
            ret.mColors = Arrays.copyOf(mColors, mColors.length);
            ret.mRadius = Arrays.copyOf(mRadius, mRadius.length);
            ret.mStrokeCnt = mStrokeCnt;
            return ret;
        }

        @Override
        public boolean empty() {
            return mStrokeCnt == -1;
        }

        @Override
        public void setSize(float radius) {
            mCurrentRadius = radius;
        }

        @Override
        public void setColor(int color) {
            mCurrentColor = color;
        }

        @Override
        public void startStroke(float x, float y) {
            mCurrentPath = new Path();
            mCurrentPath.moveTo(x, y);
        }

        @Override
        public void stroke(float x, float y) {
            if (mCurrentPath != null) {
                mCurrentPath.lineTo(x, y);
            }
        }

        @Override
        public void endStroke(float x, float y) {
            if (mCurrentPath != null) {
                mCurrentPath.lineTo(x, y);
                Path[] np = new Path[mStrokeCnt + 1];
                for (int i = 0; i < mStrokeCnt; i++) {
                    np[i] = mPaths[i];
                }
                np[mStrokeCnt] = mCurrentPath;
                mColors = Arrays.copyOf(mColors, mColors.length + 1);
                mRadius = Arrays.copyOf(mRadius, mRadius.length + 1);
                mRadius[mStrokeCnt] = mCurrentRadius;
                mColors[mStrokeCnt] = mCurrentColor;
                mPaths = np;
                mStrokeCnt++;
            }
        }

        @Override
        public void clearCurren(){
            mCurrentPath = null;
        }

        @Override
        public void paintCurrentStroke(Canvas canvas, Matrix toScrMatrix, boolean highQuality) {
            Path path = mCurrentPath;
            if (path == null) {
                return;
            }
            Paint paint = new Paint();

            paint.setStyle(Style.STROKE);
            paint.setColor(mCurrentColor);
            paint.setStrokeWidth(toScrMatrix.mapRadius(mCurrentRadius));

            // don this way because a bug in path.transform(matrix)
            Path mCacheTransPath = new Path();
            mCacheTransPath.addPath(path, toScrMatrix);

            canvas.drawPath(mCacheTransPath, paint);
        }

        @Override
        public int paintLast(int from, Canvas canvas, Matrix toScrMatrix, boolean highQuality) {
            Paint paint = new Paint();
            Matrix m = new Matrix();
            canvas.save();
            canvas.concat(toScrMatrix);
            paint.setStyle(Style.STROKE);
            for (int i = from; i < mStrokeCnt; i++) {
                paint.setColor(mColors[i]);
                paint.setStrokeWidth(mRadius[i]);
                canvas.drawPath(mPaths[i], paint);
            }
            canvas.restore();
            return mStrokeCnt;
        }

        @Override
        public boolean same(DrawStyle o) {
            if (!(o instanceof SimpleDraw)) {
                return false;
            }
            SimpleDraw sd = (SimpleDraw) o;
            boolean same;
            same = Arrays.equals(mRadius, sd.mRadius);
            if (!same) {
                return false;
            }
            same = Arrays.equals(mColors, sd.mColors);
            if (!same) {
                return false;
            }
            for (int i = 0; i < mPaths.length; i++) {
                if (!mPaths[i].equals(sd.mPaths)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int getNumberOfStrokes() {
            return mStrokeCnt;
        }
    }

    class Brush implements DrawStyle {
        private Path[] mPaths = new Path[0];
        private int[] mColors = new int[0];
        private float[] mRadius = new float[0];
        private int mStrokeCnt = 0;

        private Path mCurrentPath;
        private float mCurrentRadius;
        private int mCurrentColor;

        @Override
        public DrawStyle clone() {
            Brush ret = new Brush();
            ret.mPaths = new Path[mPaths.length];
            for (int i = 0; i < ret.mPaths.length; i++) {
                ret.mPaths[i] = new Path(mPaths[i]);
            }
            ret.mColors = Arrays.copyOf(mColors, mColors.length);
            ret.mRadius = Arrays.copyOf(mRadius, mRadius.length);
            ret.mStrokeCnt = mStrokeCnt;
            return ret;
        }

        @Override
        public boolean empty() {
            return mStrokeCnt == -1;
        }

        @Override
        public void setSize(float radius) {
            mCurrentRadius = radius;
        }

        @Override
        public void setColor(int color) {
            mCurrentColor = color;
        }

        @Override
        public void startStroke(float x, float y) {
            mCurrentPath = new Path();
            mCurrentPath.moveTo(x, y);
        }

        @Override
        public void stroke(float x, float y) {
            if (mCurrentPath != null) {
                mCurrentPath.lineTo(x, y);
            }
        }

        @Override
        public void endStroke(float x, float y) {
            if (mCurrentPath != null) {
                mCurrentPath.lineTo(x, y);
                Path[] np = new Path[mStrokeCnt + 1];
                for (int i = 0; i < mStrokeCnt; i++) {
                    np[i] = mPaths[i];
                }
                np[mStrokeCnt] = mCurrentPath;
                mColors = Arrays.copyOf(mColors, mColors.length + 1);
                mRadius = Arrays.copyOf(mRadius, mRadius.length + 1);
                mRadius[mStrokeCnt] = mCurrentRadius;
                mColors[mStrokeCnt] = mCurrentColor;
                mPaths = np;
                mStrokeCnt++;
                clearCurren();
            }
        }

        @Override
        public void clearCurren() {
            mCurrentPath = null;
        }

        @Override
        public void paintCurrentStroke(Canvas canvas, Matrix toScrMatrix, boolean highQuality) {
            Path path = mCurrentPath;
            if (path == null) {
                return;
            }
            Paint paint = new Paint();
            paint.setStyle(Style.STROKE);

            float scale = toScrMatrix.mapRadius(1);
            Path mCacheTransPath = new Path();
            mCacheTransPath.addPath(path, toScrMatrix);
            draw(canvas, paint, mCurrentColor, toScrMatrix.mapRadius(mCurrentRadius),
                    mCacheTransPath);
        }

        @Override
        public int paintLast(int from, Canvas canvas, Matrix toScrMatrix, boolean highQuality) {
            Paint paint = new Paint();

            Matrix m = new Matrix();
            canvas.save();
            canvas.concat(toScrMatrix);
            paint.setStyle(Style.STROKE);
            for (int i = from; i < mStrokeCnt; i++) {

                draw(canvas, paint, mColors[i], mRadius[i], mPaths[i]);
            }
            canvas.restore();
            return mStrokeCnt;
        }

        PathMeasure mPathMeasure = new PathMeasure();

        void draw(Canvas canvas, Paint paint, int color, float size, Path path) {

            mPathMeasure.setPath(path, false);
            float[] pos = new float[2];
            float[] tan = new float[2];
            paint.setColor(color);
            float len = mPathMeasure.getLength();
            for (float i = 0; i < len; i += (size) / 2) {
                mPathMeasure.getPosTan(i, pos, tan);
                canvas.drawCircle(pos[0], pos[1], size, paint);
            }

        }

        @Override
        public boolean same(DrawStyle o) {
            if (!(o instanceof Brush)) {
                return false;
            }
            Brush sd = (Brush) o;
            boolean same;
            same = Arrays.equals(mRadius, sd.mRadius);
            if (!same) {
                return false;
            }
            same = Arrays.equals(mColors, sd.mColors);
            if (!same) {
                return false;
            }
            for (int i = 0; i < mPaths.length; i++) {
                if (!mPaths[i].equals(sd.mPaths)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int getNumberOfStrokes() {
            return mStrokeCnt;
        }
    }

    public void startSection(int color, float size, float x, float y) {
        mDrawings[mCurrentStyle].setColor(color);
        mDrawings[mCurrentStyle].setSize(size);
        mDrawings[mCurrentStyle].startStroke(x, y);
    }

    public void addPoint(float x, float y) {
        mDrawings[mCurrentStyle].stroke(x, y);
    }

    public void endSection(float x, float y) {
        mDrawings[mCurrentStyle].endStroke(x, y);
    }

    public ImageFilterDraw() {
        mName = "Image Draw";
    }

    public void drawData(Canvas canvas, Matrix originalRotateToScreen, boolean highQuality) {
        Paint paint = new Paint();
        if (highQuality) {
            paint.setAntiAlias(true);
        }
        paint.setStyle(Style.STROKE);
        paint.setColor(Color.RED);
        paint.setStrokeWidth(40);
        boolean empty = true;
        for (int i = 0; i < mDrawings.length; i++) {
            empty &= mDrawings[i].empty();
        }
        if (empty) {
            return;
        }
        if (highQuality) {
            for (int i = 0; i < mDrawings.length; i++) {
                mDrawings[i].paintLast(0, canvas, originalRotateToScreen, highQuality);
            }

            return;
        }
        if (mOverlayBitmap == null ||
                mOverlayBitmap.getWidth() != canvas.getWidth() ||
                mOverlayBitmap.getHeight() != canvas.getHeight()) {

            mOverlayBitmap = Bitmap.createBitmap(
                    canvas.getWidth(), canvas.getHeight(), Bitmap.Config.ARGB_8888);
            mCachedStrokes = 0;
        }
        if (mCachedStrokes < mDrawings[mCurrentStyle].getNumberOfStrokes()) {
            fillBuffer(originalRotateToScreen);
        }
        canvas.drawBitmap(mOverlayBitmap, 0, 0, paint);
    }

    public void fillBuffer(Matrix originalRotateToScreen) {
        Canvas drawCache = new Canvas(mOverlayBitmap);
        for (int i = 0; i < mDrawings.length; i++) {

            mCachedStrokes = mDrawings[i].paintLast(
                mCachedStrokes, drawCache, originalRotateToScreen, false);
        }
    }

    @Override
    public int getButtonId() {
        return R.id.drawOnImageButton;
    }

    @Override
    public int getTextId() {
        return R.string.imageDraw;
    }

    @Override
    public int getEditingViewId() {
        return EditorDraw.ID;
    }

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilterDraw filter = (ImageFilterDraw) super.clone();

        filter.mDrawings = mDrawings.clone();
        return filter;
    }

    public boolean isNil() {
        for (int i = 0; i < mDrawings.length; i++) {
            if (mDrawings[i].getNumberOfStrokes() != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean same(ImageFilter filter) {
        boolean isSuperSame = super.same(filter);
        if (!isSuperSame || !(filter instanceof ImageFilterDraw)) {
            return false;
        }

        ImageFilterDraw dfilter = (ImageFilterDraw) filter;
        boolean same = true;
        for (int i = 0; i < mDrawings.length; i++) {
            same &= mDrawings[i].same(dfilter.mDrawings[i]);
        }
        return same;
    }

    public void clear() {
        mDrawings[mCurrentStyle].clearCurren();
    }

    public void draw(Canvas canvas, Matrix originalRotateToScreen) {
        for (int i = 0; i < mDrawings.length; i++) {
            mDrawings[i].paintCurrentStroke(canvas, originalRotateToScreen, false);
        }
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        short[] rect = new short[4];

        Matrix m = new Matrix();
        m.setScale(scaleFactor, scaleFactor);

        drawData(new Canvas(bitmap), m, highQuality);

        return bitmap;
    }
}
