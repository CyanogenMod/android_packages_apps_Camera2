
package com.android.gallery3d.filtershow.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Path.FillType;
import android.view.MotionEvent;

public class PieSlider {
    private float mCenterX;
    private float mCenterY;
    private float mCurrentX;
    private float mCurrentY;
    private int mStartAngle = 210;
    private int mEndAngle = 120;
    private int mValue = 100;

    private Paint mPaint = new Paint();
    private Path mBasePath = new Path();
    private Path mCirclePath = new Path();
    private RectF mOvalRect = new RectF();
    private Rect mTextBounds = new Rect();

    private PieSliderListener mListener = null;

    private MODES mMode = MODES.NONE;
    private static int mMenuRadius = 140;
    private static int mTextSize = 32;
    private static int mFanDistance = 2 * mMenuRadius;
    private static int mTextDistance = (int) (2.2f * mMenuRadius);
    private static int mLineDistance = (int) (2.5f * mMenuRadius);

    private enum MODES {
        NONE, DOWN, UP, MOVE
    }

    public void onDraw(Canvas canvas) {
        if (mMode == MODES.NONE || mMode == MODES.UP) {
            return;
        }
        drawFan(canvas);
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
    }

    public void setNoAction() {
        mMode = MODES.NONE;
    }

    private void drawFan(Canvas canvas) {
        mPaint.setARGB(200, 200, 200, 200);
        mPaint.setStrokeWidth(4);
        mBasePath.reset();
        mCirclePath.reset();

        int mf = 2;

        mOvalRect.left = mCenterX - mFanDistance;
        mOvalRect.top = mCenterY - mFanDistance;
        mOvalRect.right = mOvalRect.left + mf * mFanDistance;
        mOvalRect.bottom = mOvalRect.top + mf * mFanDistance;

        canvas.save();
        mBasePath.moveTo(mCenterX, mCenterY);
        mBasePath.arcTo(mOvalRect, mStartAngle, mEndAngle);

        mCirclePath.moveTo(mCenterX, mCenterY);
        mOvalRect.left = mCenterX - mMenuRadius;
        mOvalRect.top = mCenterY - mMenuRadius;
        mOvalRect.right = mOvalRect.left + mf * mMenuRadius;
        mOvalRect.bottom = mOvalRect.top + mf * mMenuRadius;
        mCirclePath.arcTo(mOvalRect, mStartAngle, mEndAngle);

        mBasePath.setFillType(FillType.EVEN_ODD);
        mBasePath.addPath(mCirclePath);
        mBasePath.close();

        canvas.drawPath(mBasePath, mPaint);
        canvas.restore();

        canvas.save();
        canvas.rotate(-60, mCenterX, mCenterY);
        canvas.drawLine(mCenterX, mCenterY - mMenuRadius, mCenterX, mCenterY - mLineDistance,
                mPaint);
        canvas.restore();
        canvas.save();
        canvas.rotate(mEndAngle - 60, mCenterX, mCenterY);
        canvas.drawLine(mCenterX, mCenterY - mMenuRadius, mCenterX, mCenterY - mLineDistance,
                mPaint);
        canvas.restore();

        canvas.save();
        canvas.rotate(mEndAngle / 2 - 60, mCenterX, mCenterY);
        String txt = "" + mValue;
        mPaint.setTextSize(mTextSize);
        mPaint.getTextBounds(txt, 0, txt.length(), mTextBounds);
        mPaint.setARGB(255, 20, 20, 20);
        canvas.drawText(txt, mCenterX - mTextBounds.width() / 2 - 2, mCenterY - mTextDistance - 2,
                mPaint);
        mPaint.setARGB(200, 200, 200, 200);
        canvas.drawText(txt, mCenterX - mTextBounds.width() / 2, mCenterY - mTextDistance, mPaint);
        canvas.restore();
    }

    private void computeValue() {
        float dX = mCurrentX - mCenterX;
        float dY = mCurrentY - mCenterY;
        float distance = (float) Math.sqrt(dX * dX + dY * dY);

        if (mCenterY > mCurrentY && distance > mMenuRadius) {
            float angle = (float) (Math.atan2(dX, dY) * 180 / Math.PI);
            if (angle < 0) {
                // from -90 to -180
                angle = -angle;
                angle = Math.max(90, angle);
                angle = Math.min(180, angle);
                angle -= 90;
            } else {
                angle = Math.max(90, angle);
                angle = Math.min(180, angle);
                angle -= 90;
                angle = 90 + (90 - angle);
            }
            angle /= 180.0f;
            angle = Math.max(0, angle);
            angle = Math.min(1, angle);
            mEndAngle = (int) (120 * angle);
            mValue = (int) (100 * (mEndAngle / 120.0f));
        }
    }

    public void setListener(PieSliderListener listener) {
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
