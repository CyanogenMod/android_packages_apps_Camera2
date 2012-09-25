
package com.android.gallery3d.filtershow.ui;

import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.filters.ImageFilterCurves;
import com.android.gallery3d.filtershow.imageshow.ImageShow;
import com.android.gallery3d.filtershow.presets.ImagePreset;
import com.android.gallery3d.filtershow.ui.ControlPoint;
import com.android.gallery3d.filtershow.ui.Spline;
import com.android.gallery3d.R;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

public class ImageCurves extends ImageShow {

    private static final String LOGTAG = "ImageCurves";
    Paint gPaint = new Paint();
    Spline mSpline = null;
    Path gPathSpline = new Path();
    float[] mAppliedCurve = new float[256];
    private boolean mDidAddPoint = false;
    private boolean mDidDelete = false;
    private ImageShow mMasterImageShow = null;
    private ControlPoint mCurrentControlPoint = null;
    private boolean mUseRed = true;
    private boolean mUseGreen = true;
    private boolean mUseBlue = true;

    public ImageCurves(Context context) {
        super(context);
        resetCurve();
    }

    public ImageCurves(Context context, AttributeSet attrs) {
        super(context, attrs);
        resetCurve();
    }

    public void setMaster(ImageShow master) {
        mMasterImageShow = master;
    }

    public boolean showTitle() {
        return false;
    }

    public void setUseRed(boolean value) {
        mUseRed = value;
    }

    public void setUseGreen(boolean value) {
        mUseGreen = value;
    }

    public void setUseBlue(boolean value) {
        mUseBlue = value;
    }

    public void reloadCurve() {
        if (mMasterImageShow != null) {
            String filterName = getFilterName();
            ImageFilterCurves filter = (ImageFilterCurves) getImagePreset()
                    .getFilter(filterName);
            if (filter == null) {
                resetCurve();
                return;
            }
            mSpline = new Spline(filter.getSpline());
            applyNewCurve();
        }
    }

    public void resetCurve() {
        mSpline = new Spline();

        mSpline.addPoint(0.0f, 1.0f);
        mSpline.addPoint(1.0f, 0.0f);
        if (mMasterImageShow != null) {
            applyNewCurve();
        }
    }

    public ImagePreset getImagePreset() {
        return mMasterImageShow.getImagePreset();
    }

    public void setImagePreset(ImagePreset preset, boolean addToHistory) {
        mMasterImageShow.setImagePreset(preset, addToHistory);
    }

    public float getImageRotation() {
        return mMasterImageShow.getImageRotation();
    }

    public float getImageRotationZoomFactor() {
        return mMasterImageShow.getImageRotationZoomFactor();
    }

    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        gPaint.setAntiAlias(true);
        gPaint.setFilterBitmap(true);
        gPaint.setDither(true);

        drawGrid(canvas);
        drawSpline(canvas);

        drawToast(canvas);
    }

    private void drawGrid(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();

        // Grid
        gPaint.setARGB(128, 150, 150, 150);
        gPaint.setStrokeWidth(1);

        float stepH = h / 9;
        float stepW = w / 9;

        // central diagonal
        gPaint.setARGB(255, 100, 100, 100);
        gPaint.setStrokeWidth(2);
        canvas.drawLine(0, h, w, 0, gPaint);

        gPaint.setARGB(128, 200, 200, 200);
        gPaint.setStrokeWidth(4);
        stepH = h / 3;
        stepW = w / 3;
        for (int j = 1; j < 3; j++) {
            canvas.drawLine(0, j * stepH, w, j * stepH, gPaint);
            canvas.drawLine(j * stepW, 0, j * stepW, h, gPaint);
        }
    }

    private void drawSpline(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();

        gPathSpline.reset();
        for (int x = 0; x < w; x += 11) {
            float fx = x / w;
            ControlPoint drawPoint = mSpline.getPoint(fx);
            float newX = drawPoint.x * w;
            float newY = drawPoint.y * h;
            if (x == 0) {
                gPathSpline.moveTo(newX, newY);
            } else {
                gPathSpline.lineTo(newX, newY);
            }
        }

        gPaint.setStrokeWidth(10);
        gPaint.setStyle(Paint.Style.STROKE);
        gPaint.setARGB(255, 50, 50, 50);
        canvas.drawPath(gPathSpline, gPaint);
        gPaint.setStrokeWidth(5);
        gPaint.setARGB(255, 150, 150, 150);
        canvas.drawPath(gPathSpline, gPaint);

        gPaint.setARGB(255, 150, 150, 150);
        for (int j = 1; j < mSpline.getNbPoints() - 1; j++) {
            ControlPoint point = mSpline.getPoint(j);
            gPaint.setStrokeWidth(10);
            gPaint.setARGB(255, 50, 50, 100);
            canvas.drawCircle(point.x * w, point.y * h, 30, gPaint);
            gPaint.setStrokeWidth(5);
            gPaint.setARGB(255, 150, 150, 200);
            canvas.drawCircle(point.x * w, point.y * h, 30, gPaint);
        }
    }

    private int pickControlPoint(float x, float y) {
        int pick = 0;
        float px = mSpline.getPoint(0).x;
        float py = mSpline.getPoint(0).y;
        double delta = Math.sqrt((px - x) * (px - x) + (py - y) * (py - y));
        for (int i = 1; i < mSpline.getNbPoints(); i++) {
            px = mSpline.getPoint(i).x;
            py = mSpline.getPoint(i).y;
            double currentDelta = Math.sqrt((px - x) * (px - x) + (py - y)
                    * (py - y));
            if (currentDelta < delta) {
                delta = currentDelta;
                pick = i;
            }
        }

        if (!mDidAddPoint && (delta * getWidth() > 100)
                && (mSpline.getNbPoints() < 10)) {
            return -1;
        }

        return pick;// mSpline.getPoint(pick);
    }

    public void showPopupMenu(View v) {
        // TODO: sort out the popup menu UI for curves
        final Context context = v.getContext();
        PopupMenu popupMenu = new PopupMenu(v.getContext(), v);
        popupMenu.getMenuInflater().inflate(R.menu.filtershow_menu_curves,
                popupMenu.getMenu());

        popupMenu
                .setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        Toast.makeText(context, item.toString(),
                                Toast.LENGTH_LONG).show();
                        return true;
                    }
                });

        popupMenu.show();
    }

    private String getFilterName() {
        String filterName = "Curves";
        if (mUseRed && !mUseGreen && !mUseBlue) {
            filterName = "CurvesRed";
        } else if (!mUseRed && mUseGreen && !mUseBlue) {
            filterName = "CurvesGreen";
        } else if (!mUseRed && !mUseGreen && mUseBlue) {
            filterName = "CurvesBlue";
        }
        return filterName;
    }

    @Override
    public synchronized boolean onTouchEvent(MotionEvent e) {
        float posX = e.getX() / getWidth();
        float posY = e.getY() / getHeight();

        /*
         * if (true) { showPopupMenu(this); return true; }
         */

        // ControlPoint point = null;

        // Log.v(LOGTAG, "onTouchEvent - " + e + " action masked : " +
        // e.getActionMasked());

        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            applyNewCurve();
            // Log.v(LOGTAG, "ACTION UP, mCurrentControlPoint set to null!");
            mCurrentControlPoint = null;
            ImagePreset copy = new ImagePreset(getImagePreset());

            if (mUseRed && mUseGreen && mUseBlue) {
                copy.setHistoryName("Curves (RGB)");
            } else if (mUseRed) {
                copy.setHistoryName("Curves (Red)");
            } else if (mUseGreen) {
                copy.setHistoryName("Curves (Green)");
            } else if (mUseBlue) {
                copy.setHistoryName("Curves (Blue)");
            }
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
        // Log.v(LOGTAG, "ACTION DOWN, mCurrentControlPoint is " +
        // mCurrentControlPoint);

        int pick = pickControlPoint(posX, posY);
        // Log.v(LOGTAG, "ACTION DOWN, pick is " + pick);
        if (mCurrentControlPoint == null) {
            if (pick == -1) {
                mCurrentControlPoint = new ControlPoint(posX, posY);
                mSpline.addPoint(mCurrentControlPoint);
                mDidAddPoint = true;
                // Log.v(LOGTAG, "ACTION DOWN - 2, added a new control point! "
                // + mCurrentControlPoint);

            } else {
                mCurrentControlPoint = mSpline.getPoint(pick);
                // Log.v(LOGTAG, "ACTION DOWN - 2, picking up control point " +
                // mCurrentControlPoint + " at pick " + pick);
            }
        }
        // Log.v(LOGTAG, "ACTION DOWN - 3, pick is " + pick);

        if (!((mCurrentControlPoint.x == 0 && mCurrentControlPoint.y == 1) || (mCurrentControlPoint.x == 1 && mCurrentControlPoint.y == 0))) {
            if (mSpline.isPointContained(posX, pick)) {
                mCurrentControlPoint.x = posX;
                mCurrentControlPoint.y = posY;
                // Log.v(LOGTAG, "ACTION DOWN - 4, move control point " +
                // mCurrentControlPoint);
            } else if (pick != -1) {
                // Log.v(LOGTAG, "ACTION DOWN - 4, delete pick " + pick);
                mSpline.deletePoint(pick);
                mDidDelete = true;
            }
        }
        // Log.v(LOGTAG, "ACTION DOWN - 5, DONE");
        applyNewCurve();
        invalidate();
        return true;
    }

    public synchronized void applyNewCurve() {
        ControlPoint[] points = new ControlPoint[256];
        for (int i = 0; i < 256; i++) {
            float v = i / 255.0f;
            ControlPoint p = mSpline.getPoint(v);
            points[i] = p;
        }
        for (int i = 0; i < 256; i++) {
            mAppliedCurve[i] = -1;
        }
        for (int i = 0; i < 256; i++) {
            int index = (int) (points[i].x * 255);
            if (index >= 0 && index <= 255) {
                float v = 1.0f - points[i].y;
                if (v < 0) {
                    v = 0;
                }
                if (v > 1.0f) {
                    v = 1.0f;
                }
                mAppliedCurve[index] = v;
            }
        }
        float prev = 0;
        for (int i = 0; i < 256; i++) {
            if (mAppliedCurve[i] == -1) {
                // need to interpolate...
                int j = i + 1;
                if (j > 255) {
                    j = 255;
                }
                for (; j < 256; j++) {
                    if (mAppliedCurve[j] != -1) {
                        break;
                    }
                }
                if (j > 255) {
                    j = 255;
                }
                // interpolate linearly between i and j - 1
                float start = prev;
                float end = mAppliedCurve[j];
                float delta = (end - start) / (j - i + 1);
                for (int k = i; k < j; k++) {
                    start = start + delta;
                    mAppliedCurve[k] = start;
                }
                i = j;
            }
            prev = mAppliedCurve[i];
        }
        for (int i = 0; i < 256; i++) {
            mAppliedCurve[i] = mAppliedCurve[i] * 255;
        }
        float[] appliedCurve = new float[256];
        for (int i = 0; i < 256; i++) {
            appliedCurve[i] = mAppliedCurve[i];
        }
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
                filter.setSpline(new Spline(mSpline));
                filter.setCurve(appliedCurve);
                filter.setUseRed(mUseRed);
                filter.setUseGreen(mUseGreen);
                filter.setUseBlue(mUseBlue);
            }
            mImageLoader.resetImageForPreset(getImagePreset(), this);
            invalidate();
        }
    }

}
