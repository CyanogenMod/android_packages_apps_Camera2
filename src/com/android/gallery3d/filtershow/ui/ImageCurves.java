
package com.android.gallery3d.filtershow.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.android.gallery3d.filtershow.filters.ImageFilterCurves;
import com.android.gallery3d.filtershow.imageshow.ImageSlave;
import com.android.gallery3d.filtershow.presets.ImagePreset;

public class ImageCurves extends ImageSlave {

    private static final String LOGTAG = "ImageCurves";
    Paint gPaint = new Paint();
    Spline[] mSplines = new Spline[4];
    Path gPathSpline = new Path();

    private int mCurrentCurveIndex = 0;
    private boolean mDidAddPoint = false;
    private boolean mDidDelete = false;
    private ControlPoint mCurrentControlPoint = null;
    private ImagePreset mLastPreset = null;
    int[] redHistogram = new int[256];
    int[] greenHistogram = new int[256];
    int[] blueHistogram = new int[256];
    Path gHistoPath = new Path();

    public ImageCurves(Context context) {
        super(context);
        resetCurve();
    }

    public ImageCurves(Context context, AttributeSet attrs) {
        super(context, attrs);
        resetCurve();
    }

    public void nextChannel() {
        mCurrentCurveIndex = ((mCurrentCurveIndex + 1) % 4);
        invalidate();
    }

    @Override
    public boolean showTitle() {
        return false;
    }

    public void reloadCurve() {
        if (getMaster() != null) {
            String filterName = getFilterName();
            ImageFilterCurves filter = (ImageFilterCurves) getImagePreset()
                    .getFilter(filterName);
            if (filter == null) {
                resetCurve();
                return;
            }
            for (int i = 0; i < 4; i++) {
                Spline spline = filter.getSpline(i);
                if (spline != null) {
                    mSplines[i] = new Spline(spline);
                }
            }
            applyNewCurve();
        }
    }

    @Override
    public void resetParameter() {
        super.resetParameter();
        resetCurve();
        mLastPreset = null;
        invalidate();
    }

    public void resetCurve() {
        Spline spline = new Spline();

        spline.addPoint(0.0f, 1.0f);
        spline.addPoint(1.0f, 0.0f);

        for (int i = 0; i < 4; i++) {
            mSplines[i] = new Spline(spline);
        }
        if (getMaster() != null) {
            applyNewCurve();
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        gPaint.setAntiAlias(true);

        if (getImagePreset() != mLastPreset) {
            new ComputeHistogramTask().execute(mFilteredImage);
            mLastPreset = getImagePreset();
        }

        if (mCurrentCurveIndex == Spline.RGB || mCurrentCurveIndex == Spline.RED) {
            drawHistogram(canvas, redHistogram, Color.RED, PorterDuff.Mode.SCREEN);
        }
        if (mCurrentCurveIndex == Spline.RGB || mCurrentCurveIndex == Spline.GREEN) {
            drawHistogram(canvas, greenHistogram, Color.GREEN, PorterDuff.Mode.SCREEN);
        }
        if (mCurrentCurveIndex == Spline.RGB || mCurrentCurveIndex == Spline.BLUE) {
            drawHistogram(canvas, blueHistogram, Color.BLUE, PorterDuff.Mode.SCREEN);
        }
        // We only display the other channels curves when showing the RGB curve
        if (mCurrentCurveIndex == Spline.RGB) {
            for (int i = 0; i < 4; i++) {
                Spline spline = mSplines[i];
                if (i != mCurrentCurveIndex && !spline.isOriginal()) {
                    // And we only display a curve if it has more than two
                    // points
                    spline.draw(canvas, Spline.colorForCurve(i), getWidth(), getHeight(), false);
                }
            }
        }
        // ...but we always display the current curve.
        mSplines[mCurrentCurveIndex]
                .draw(canvas, Spline.colorForCurve(mCurrentCurveIndex), getWidth(), getHeight(),
                        true);
        drawToast(canvas);

    }

    private int pickControlPoint(float x, float y) {
        int pick = 0;
        float px = mSplines[mCurrentCurveIndex].getPoint(0).x;
        float py = mSplines[mCurrentCurveIndex].getPoint(0).y;
        double delta = Math.sqrt((px - x) * (px - x) + (py - y) * (py - y));
        for (int i = 1; i < mSplines[mCurrentCurveIndex].getNbPoints(); i++) {
            px = mSplines[mCurrentCurveIndex].getPoint(i).x;
            py = mSplines[mCurrentCurveIndex].getPoint(i).y;
            double currentDelta = Math.sqrt((px - x) * (px - x) + (py - y)
                    * (py - y));
            if (currentDelta < delta) {
                delta = currentDelta;
                pick = i;
            }
        }

        if (!mDidAddPoint && (delta * getWidth() > 100)
                && (mSplines[mCurrentCurveIndex].getNbPoints() < 10)) {
            return -1;
        }

        return pick;
    }

    private String getFilterName() {
        return "Curves";
    }

    @Override
    public synchronized boolean onTouchEvent(MotionEvent e) {
        float posX = e.getX() / getWidth();
        float posY = e.getY();
        float margin = Spline.curveHandleSize() / 2;
        if (posY < margin) {
            posY = margin;
        }
        if (posY > getHeight() - margin) {
            posY = getHeight() - margin;
        }
        posY = (posY - margin) / (getHeight() - 2 * margin);

        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            applyNewCurve();
            mCurrentControlPoint = null;
            String name = "Curves";
            ImagePreset copy = new ImagePreset(getImagePreset(), name);

            copy.setIsFx(false);
            mImageLoader.getHistory().insert(copy, 0);

            invalidate();
            mDidAddPoint = false;
            if (mDidDelete) {
                mDidDelete = false;
            }
            return true;
        }

        if (mDidDelete) {
            return true;
        }

        int pick = pickControlPoint(posX, posY);
        if (mCurrentControlPoint == null) {
            if (pick == -1) {
                mCurrentControlPoint = new ControlPoint(posX, posY);
                mSplines[mCurrentCurveIndex].addPoint(mCurrentControlPoint);
                mDidAddPoint = true;
            } else {
                mCurrentControlPoint = mSplines[mCurrentCurveIndex].getPoint(pick);
            }
        }

        if (mSplines[mCurrentCurveIndex].isPointContained(posX, pick)) {
            mCurrentControlPoint.x = posX;
            mCurrentControlPoint.y = posY;
        } else if (pick != -1) {
            mSplines[mCurrentCurveIndex].deletePoint(pick);
            mDidDelete = true;
        }
        applyNewCurve();
        invalidate();
        return true;
    }

    public synchronized void applyNewCurve() {
        // update image
        if (getImagePreset() != null) {
            String filterName = getFilterName();
            ImageFilterCurves filter = (ImageFilterCurves) getImagePreset()
                    .getFilter(filterName);
            if (filter == null) {
                filter = new ImageFilterCurves();
                filter.setName(filterName);
                ImagePreset copy = new ImagePreset(getImagePreset());
                copy.add(filter);
                setImagePreset(copy, false);
            }

            if (filter != null) {
                for (int i = 0; i < 4; i++) {
                    filter.setSpline(new Spline(mSplines[i]), i);
                }
            }
            mImageLoader.resetImageForPreset(getImagePreset(), this);
            invalidate();
        }
    }

    class ComputeHistogramTask extends AsyncTask<Bitmap, Void, int[]> {
        @Override
        protected int[] doInBackground(Bitmap... params) {
            int[] histo = new int[256 * 3];
            Bitmap bitmap = params[0];
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            int[] pixels = new int[w * h];
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) {
                    int index = j * w + i;
                    int r = Color.red(pixels[index]);
                    int g = Color.green(pixels[index]);
                    int b = Color.blue(pixels[index]);
                    histo[r]++;
                    histo[256 + g]++;
                    histo[512 + b]++;
                }
            }
            return histo;
        }

        @Override
        protected void onPostExecute(int[] result) {
            System.arraycopy(result, 0, redHistogram, 0, 256);
            System.arraycopy(result, 256, greenHistogram, 0, 256);
            System.arraycopy(result, 512, blueHistogram, 0, 256);
            invalidate();
        }
    }

    private void drawHistogram(Canvas canvas, int[] histogram, int color, PorterDuff.Mode mode) {
        int max = 0;
        for (int i = 0; i < histogram.length; i++) {
            if (histogram[i] > max) {
                max = histogram[i];
            }
        }
        float w = getWidth();
        float h = getHeight();
        float wl = w / histogram.length;
        float wh = (0.3f * h) / max;
        Paint paint = new Paint();
        paint.setARGB(100, 255, 255, 255);
        paint.setStrokeWidth((int) Math.ceil(wl));

        Paint paint2 = new Paint();
        paint2.setColor(color);
        paint2.setStrokeWidth(6);
        paint2.setXfermode(new PorterDuffXfermode(mode));
        gHistoPath.reset();
        gHistoPath.moveTo(0, h);
        boolean firstPointEncountered = false;
        float prev = 0;
        float last = 0;
        for (int i = 0; i < histogram.length; i++) {
            float x = i * wl;
            float l = histogram[i] * wh;
            if (l != 0) {
                float v = h - (l + prev) / 2.0f;
                if (!firstPointEncountered) {
                    gHistoPath.lineTo(x, h);
                    firstPointEncountered = true;
                }
                gHistoPath.lineTo(x, v);
                prev = l;
                last = x;
            }
        }
        gHistoPath.lineTo(last, h);
        gHistoPath.lineTo(w, h);
        gHistoPath.close();
        canvas.drawPath(gHistoPath, paint2);
        paint2.setStrokeWidth(2);
        paint2.setStyle(Paint.Style.STROKE);
        paint2.setARGB(255, 200, 200, 200);
        canvas.drawPath(gHistoPath, paint2);
    }
}
