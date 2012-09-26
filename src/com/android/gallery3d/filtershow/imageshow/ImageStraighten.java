
package com.android.gallery3d.filtershow.imageshow;

import com.android.gallery3d.filtershow.presets.ImagePreset;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

public class ImageStraighten extends ImageShow {
    private ImageShow mMasterImageShow = null;
    private float mImageRotation = 0;
    private float mImageRotationZoomFactor = 0;

    private float mMinAngle = -45;
    private float mMaxAngle = 45;
    private float mBaseAngle = 0;
    private float mAngle = 0;
    private float mCenterX;
    private float mCenterY;
    private float mTouchCenterX;
    private float mTouchCenterY;
    private float mCurrentX;
    private float mCurrentY;

    private enum MODES {
        NONE, DOWN, UP, MOVE
    }

    private MODES mMode = MODES.NONE;

    private static final String LOGTAG = "ImageStraighten";
    private static final Paint gPaint = new Paint();

    public ImageStraighten(Context context) {
        super(context);
    }

    public ImageStraighten(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void updateAngle() {
        mMasterImageShow.setImageRotation(mImageRotation, mImageRotationZoomFactor);
    }

    public void setMaster(ImageShow master) {
        mMasterImageShow = master;
    }

    public boolean showTitle() {
        return false;
    }

    public ImagePreset getImagePreset() {
        return mMasterImageShow.getImagePreset();
    }

    public void setImagePreset(ImagePreset preset, boolean addToHistory) {
        mMasterImageShow.setImagePreset(preset, addToHistory);
    }

    // ///////////////////////////////////////////////////////////////////////////
    // touch event handler

    public void setActionDown(float x, float y) {
        mTouchCenterX = x;
        mTouchCenterY = y;
        mCurrentX = x;
        mCurrentY = y;
        mBaseAngle = mAngle;
        mMode = MODES.DOWN;
    }

    public void setActionMove(float x, float y) {
        mCurrentX = x;
        mCurrentY = y;
        mMode = MODES.MOVE;
        computeValue();
    }

    public void setActionUp() {
        mMode = MODES.UP;
    }

    public void setNoAction() {
        mMode = MODES.NONE;
    }

    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case (MotionEvent.ACTION_DOWN):
                setActionDown(event.getX(), event.getY());
                break;
            case (MotionEvent.ACTION_UP):
                setActionUp();
                break;
            case (MotionEvent.ACTION_MOVE):
                setActionMove(event.getX(), event.getY());
                break;
            default:
                setNoAction();
        }
        mImageRotation = mAngle;
        updateAngle();
        invalidate();
        return true;
    }

    private float angleFor(float dx, float dy) {
        return (float) (Math.atan2(dx, dy) * 180 / Math.PI);
    }

    private void computeValue() {
        if (mCurrentX == mTouchCenterX && mCurrentY == mTouchCenterY) {
            return;
        }
        float dX1 = mTouchCenterX - mCenterX;
        float dY1 = mTouchCenterY - mCenterY;
        float dX2 = mCurrentX - mCenterX;
        float dY2 = mCurrentY - mCenterY;

        float angleA = angleFor(dX1, dY1);
        float angleB = angleFor(dX2, dY2);
        float angle = (angleB - angleA) % 360;
        mAngle = (mBaseAngle - angle) % 360;
        mAngle = Math.max(mMinAngle, mAngle);
        mAngle = Math.min(mMaxAngle, mAngle);
    }

    // ///////////////////////////////////////////////////////////////////////////

    public void onNewValue(int value) {
        mImageRotation = value;
        invalidate();
    }

    public void onDraw(Canvas canvas) {
        mCenterX = getWidth() / 2;
        mCenterY = getHeight() / 2;
        drawStraighten(canvas);
    }

    public void drawStraighten(Canvas canvas) {
        gPaint.setAntiAlias(true);
        gPaint.setFilterBitmap(true);
        gPaint.setDither(true);
        gPaint.setARGB(255, 255, 255, 255);

        // canvas.drawARGB(255, 255, 0, 0);

        // TODO: have the concept of multiple image passes (geometry, filter)
        // so that we can fake the rotation, etc.
        Bitmap image = null; // mMasterImageShow.mFilteredImage;
        if (image == null) {
            image = mMasterImageShow.mForegroundImage;
        }
        if (image == null) {
            return;
        }

        double iw = image.getWidth();
        float zoom = (float) (getWidth() / iw);
        // iw = getWidth(); // we will apply the zoom
        double ih = image.getHeight();
        if (ih > iw) {
            zoom = (float) (getHeight() / ih);
        }
        float offset = (float) ((getHeight() - ih) / 2.0f);

        canvas.save();
        float dx = (float) ((getWidth() - iw) / 2.0f);
        float dy = offset;

        canvas.rotate(mImageRotation, mCenterX, mCenterY);
        canvas.scale(zoom, zoom, mCenterX, mCenterY);
        canvas.translate(dx, dy);
        canvas.drawBitmap(image, 0, 0, gPaint);

        canvas.restore();

        double deg = mImageRotation;
        if (deg < 0) {
            deg = -deg;
        }
        double a = Math.toRadians(deg);
        double sina = Math.sin(a);
        double cosa = Math.cos(a);

        double rw = image.getWidth();
        double rh = image.getHeight();
        double h1 = rh * rh / (rw * sina + rh * cosa);
        double h2 = rh * rw / (rw * cosa + rh * sina);
        double hh = Math.min(h1, h2);
        double ww = hh * rw / rh;

        float left = (float) ((rw - ww) * 0.5f);
        float top = (float) ((rh - hh) * 0.5f);
        float right = (float) (left + ww);
        float bottom = (float) (top + hh);

        RectF boundsRect = new RectF(left, top, right, bottom);
        Matrix m = new Matrix();
        m.setScale(zoom, zoom, mCenterX, mCenterY);
        m.preTranslate(dx, dy);
        m.mapRect(boundsRect);

        gPaint.setARGB(128, 0, 0, 0);
        gPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, getWidth(), boundsRect.top, gPaint);
        canvas.drawRect(0, boundsRect.bottom, getWidth(), getHeight(), gPaint);
        canvas.drawRect(0, boundsRect.top, boundsRect.left, boundsRect.bottom,
                gPaint);
        canvas.drawRect(boundsRect.right, boundsRect.top, getWidth(),
                boundsRect.bottom, gPaint);

        Path path = new Path();
        path.addRect(boundsRect, Path.Direction.CCW);
        gPaint.setARGB(255, 255, 255, 255);
        gPaint.setStrokeWidth(3);
        gPaint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(path, gPaint);
        gPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        mImageRotationZoomFactor = (float) (rw / boundsRect.width());

        ImagePreset copy = new ImagePreset(getImagePreset());
        Log.v(LOGTAG, "creating a new image preset with rotation " + mImageRotation
                + " and zoom factor: " + mImageRotationZoomFactor);

        copy.setStraightenRotation(mImageRotation, mImageRotationZoomFactor);
        setImagePreset(copy);

        if (mMode == MODES.MOVE) {
            canvas.save();
            canvas.clipPath(path);
            int n = 16;
            float step = getWidth() / n;
            float p = 0;
            for (int i = 1; i < n; i++) {
                p = i * step;
                gPaint.setARGB(60, 255, 255, 255);
                canvas.drawLine(p, 0, p, getHeight(), gPaint);
                canvas.drawLine(0, p, getWidth(), p, gPaint);
            }
            canvas.restore();
        }
    }

}
