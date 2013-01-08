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
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Xfermode;
import android.graphics.Paint.Style;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.imageshow.GeometryMetadata;

import java.util.Arrays;
import java.util.Vector;

public class ImageFilterDraw extends ImageFilter {
    private static final String LOGTAG = "ImageFilterDraw";

    final float STROKE_RADIUS = 40;
    Bitmap mOverlayBitmap; // this accelerates interaction
    int   mCachedStrokes =-1;
    SimpleDraw mSimpleDraw = new SimpleDraw();

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
    };

    class SimpleDraw implements DrawStyle {
        private Path[] mPaths = new Path[0];
        private int[] mColors = new int[0];
        private float[] mRadius = new float[0];
        private int mStrokeCnt = 0;

        private Path mCurrentPath;
        private float mCurrentRadius;
        private int mCurrentColor;

        public DrawStyle clone() {
            SimpleDraw ret = new SimpleDraw();
            ret.mPaths = new Path[mPaths.length];
            for (int i = 0; i < mPaths.length; i++) {
                ret.mPaths[i] = new Path(mPaths[i]);
            }
            ret.mColors = Arrays.copyOf(mColors, mColors.length);
            ret.mRadius = Arrays.copyOf(mRadius, mRadius.length);
            ret.mStrokeCnt = mStrokeCnt;
            return ret;
        }

        public void setSize(float radius) {
            mCurrentRadius = radius;
        }

        public void setColor(int color) {
            mCurrentColor = color;
        }

        public void startStroke(float x, float y) {
            mCurrentPath = new Path();
            mCurrentPath.moveTo(x, y);
        }

        public void stroke(float x, float y) {
            if (mCurrentPath != null) {
                mCurrentPath.lineTo(x, y);
            }
        }

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
        public void clearCurren(){
            mCurrentPath = null;
        }
        public void paintCurrentStroke(Canvas canvas, Matrix toScrMatrix, boolean highQuality) {
            Path path = mCurrentPath;
            if (path == null)
                return;
            Paint paint = new Paint();

            paint.setStyle(Style.STROKE);
            paint.setColor(mCurrentColor);
            paint.setStrokeWidth(toScrMatrix.mapRadius(mCurrentRadius));

            // don this way because a bug in path.transform(matrix)
            Path mCacheTransPath = new Path();
            mCacheTransPath.addPath(path, toScrMatrix);

            canvas.drawPath(mCacheTransPath, paint);
        }

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

        public int getNumberOfStrokes() {
            return mStrokeCnt;
        }
    }

    public void startSection(int color, float x, float y) {
        mSimpleDraw.setColor(color);
        mSimpleDraw.setSize(STROKE_RADIUS);
        mSimpleDraw.startStroke(x, y);
    }

    public void addPoint(float x, float y) {
        mSimpleDraw.stroke(x, y);
    }

    public void endSection(float x, float y) {
        mSimpleDraw.endStroke(x, y);
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
        if (mSimpleDraw.mStrokeCnt == -1) {
            return;
        }
        if (highQuality) {
            mSimpleDraw.paintLast(0, canvas, originalRotateToScreen, highQuality);
            return;
        }
        if (mOverlayBitmap == null ||
                mOverlayBitmap.getWidth() != canvas.getWidth() ||
                mOverlayBitmap.getHeight() != canvas.getHeight()) {

            mOverlayBitmap = Bitmap.createBitmap(
                    canvas.getWidth(), canvas.getHeight(), Bitmap.Config.ARGB_8888);
            mCachedStrokes = 0;
        }
        if (mCachedStrokes < mSimpleDraw.getNumberOfStrokes()) {
            fillBuffer(originalRotateToScreen);
        }
        canvas.drawBitmap(mOverlayBitmap, 0, 0, paint);
    }

    public void fillBuffer(Matrix originalRotateToScreen) {
        Paint paint = new Paint();
        paint.setStyle(Style.STROKE);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
        paint.setStrokeWidth(STROKE_RADIUS);

        Canvas drawCache = new Canvas(mOverlayBitmap);
        mCachedStrokes = mSimpleDraw.paintLast(
                mCachedStrokes, drawCache, originalRotateToScreen, false);

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
        return R.id.imageDraw;
    }

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilterDraw filter = (ImageFilterDraw) super.clone();

        filter.mSimpleDraw = (SimpleDraw) mSimpleDraw.clone();
        return filter;
    }

    @Override
    public boolean isNil() {
        if (mSimpleDraw.getNumberOfStrokes() != 0) {
            return false;
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
        return mSimpleDraw.same(dfilter.mSimpleDraw);
    }

    public void clear() {
        mSimpleDraw.clearCurren();
    }

    public void draw(Canvas canvas, Matrix originalRotateToScreen) {
        mSimpleDraw.paintCurrentStroke(canvas, originalRotateToScreen, false);
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
