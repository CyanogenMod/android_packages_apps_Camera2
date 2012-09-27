
package com.android.gallery3d.filtershow.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;

import android.util.Log;

public class SliderController {
    private static final String LOGTAG = "SliderController";

    private float mCenterX;
    private float mCenterY;
    private float mCurrentX;
    private float mCurrentY;
    private int mValue = 100;
    int mOriginalValue = 0;

    private int mWidth = 0;
    private int mHeight = 0;

    private String mToast = null;

    private Paint mPaint = new Paint();

    private SliderListener mListener = null;

    private MODES mMode = MODES.NONE;
    private static int mTextSize = 128;

    private enum MODES {
        NONE, DOWN, UP, MOVE
    }

    public void onDraw(Canvas canvas) {
        if (mMode == MODES.NONE || mMode == MODES.UP) {
            return;
        }
        drawToast(canvas);
    }

    public void drawToast(Canvas canvas) {
        if (mToast != null) {
            canvas.save();
            mPaint.setTextSize(mTextSize);
            float textWidth = mPaint.measureText(mToast);
            int toastX = (int) ((getWidth() - textWidth) / 2.0f);
            int toastY = (int) (getHeight() / 3.0f);

            mPaint.setARGB(255, 0, 0, 0);
            canvas.drawText(mToast, toastX - 2, toastY - 2, mPaint);
            canvas.drawText(mToast, toastX - 2, toastY, mPaint);
            canvas.drawText(mToast, toastX, toastY - 2, mPaint);
            canvas.drawText(mToast, toastX + 2, toastY + 2, mPaint);
            canvas.drawText(mToast, toastX + 2, toastY, mPaint);
            canvas.drawText(mToast, toastX, toastY + 2, mPaint);
            mPaint.setARGB(255, 255, 255, 255);
            canvas.drawText(mToast, toastX, toastY, mPaint);
            canvas.restore();
        }
    }

    protected int computeValue() {
        int delta = (int) (100 * (getCurrentX() - getCenterX()) / (float) getWidth());
        int value = mOriginalValue + delta;
        value = Math.max(0, Math.min(value, 100));
        setValue(value);
        mToast = "" + value;
        return value;
    }

    public void setValue(int value) {
        mValue = value;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public void setWidth(int value) {
        mWidth = value;
    }

    public void setHeight(int value) {
        mHeight = value;
    }

    public float getCurrentX() {
        return mCurrentX;
    }

    public float getCurrentY() {
        return mCurrentY;
    }

    public float getCenterX() {
        return mCenterX;
    }

    public float getCenterY() {
        return mCenterY;
    }

    public void setActionDown(float x, float y) {
        mCenterX = x;
        mCenterY = y;
        mCurrentX = x;
        mCurrentY = y;
        mMode = MODES.DOWN;
    }

    public void setActionMove(float x, float y) {
        mCurrentX = x;
        mCurrentY = y;
        mMode = MODES.MOVE;
        computeValue();
        if (mListener != null) {
            mListener.onNewValue(mValue);
        }
    }

    public void setActionUp() {
        mMode = MODES.UP;
        mOriginalValue = computeValue();
    }

    public void setNoAction() {
        mMode = MODES.NONE;
    }

    public void setListener(SliderListener listener) {
        mListener = listener;
    }

    public boolean onTouchEvent(MotionEvent event) {
        setNoAction();
        switch (event.getActionMasked()) {
            case (MotionEvent.ACTION_DOWN): {
                setActionDown(event.getX(), event.getY());
                break;
            }
            case (MotionEvent.ACTION_UP): {
                setActionUp();
                break;
            }
            case (MotionEvent.ACTION_CANCEL): {
                setActionUp();
                break;
            }
            case (MotionEvent.ACTION_MOVE): {
                setActionMove(event.getX(), event.getY());
                break;
            }
        }
        return true;
    }

}
