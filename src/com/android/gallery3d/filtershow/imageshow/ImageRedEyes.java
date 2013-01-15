
package com.android.gallery3d.filtershow.imageshow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.android.gallery3d.filtershow.filters.ImageFilterRedEye;
import com.android.gallery3d.filtershow.filters.RedEyeCandidate;

public class ImageRedEyes extends ImageShow {

    private static final String LOGTAG = "ImageRedEyes";
    private RectF mCurrentRect = null;
    private static float mTouchPadding = 80;

    public static void setTouchPadding(float padding) {
        mTouchPadding = padding;
    }

    public ImageRedEyes(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageRedEyes(Context context) {
        super(context);
    }

    @Override
    public void resetParameter() {
        ImageFilterRedEye filter = (ImageFilterRedEye) getCurrentFilter();
        if (filter != null) {
            filter.clear();
        }
        mCurrentRect = null;
        invalidate();
    }

    @Override
    public void updateImage() {
        super.updateImage();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        float ex = event.getX();
        float ey = event.getY();

        ImageFilterRedEye filter = (ImageFilterRedEye) getCurrentFilter();

        // let's transform (ex, ey) to displayed image coordinates
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mCurrentRect = new RectF();
            mCurrentRect.left = ex - mTouchPadding;
            mCurrentRect.top = ey - mTouchPadding;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            mCurrentRect.right = ex + mTouchPadding;
            mCurrentRect.bottom = ey + mTouchPadding;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (mCurrentRect != null) {
                // transform to original coordinates
                GeometryMetadata geo = getImagePreset().mGeoData;
                Matrix originalToScreen = geo.getOriginalToScreen(true,
                        mImageLoader.getOriginalBounds().width(),
                        mImageLoader.getOriginalBounds().height(),
                        getWidth(), getHeight());
                Matrix originalNoRotateToScreen = geo.getOriginalToScreen(false,
                        mImageLoader.getOriginalBounds().width(),
                        mImageLoader.getOriginalBounds().height(),
                        getWidth(), getHeight());

                Matrix invert = new Matrix();
                originalToScreen.invert(invert);
                RectF r = new RectF(mCurrentRect);
                invert.mapRect(r);
                RectF r2 = new RectF(mCurrentRect);
                invert.reset();
                originalNoRotateToScreen.invert(invert);
                invert.mapRect(r2);
                filter.addRect(r, r2);
                this.resetImageCaches(this);
            }
            mCurrentRect = null;
        }
        invalidate();
        return true;
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint paint = new Paint();
        paint.setStyle(Style.STROKE);
        paint.setColor(Color.RED);
        paint.setStrokeWidth(2);
        if (mCurrentRect != null) {
            paint.setColor(Color.RED);
            RectF drawRect = new RectF(mCurrentRect);
            canvas.drawRect(drawRect, paint);
        }

        GeometryMetadata geo = getImagePreset().mGeoData;
        Matrix originalToScreen = geo.getOriginalToScreen(false,
                mImageLoader.getOriginalBounds().width(),
                mImageLoader.getOriginalBounds().height(), getWidth(), getHeight());
        Matrix originalRotateToScreen = geo.getOriginalToScreen(true,
                mImageLoader.getOriginalBounds().width(),
                mImageLoader.getOriginalBounds().height(), getWidth(), getHeight());

        ImageFilterRedEye filter = (ImageFilterRedEye) getCurrentFilter();
        for (RedEyeCandidate candidate : filter.getCandidates()) {
            RectF rect = candidate.getRect();
            RectF drawRect = new RectF();
            originalToScreen.mapRect(drawRect, rect);
            RectF fullRect = new RectF();
            originalRotateToScreen.mapRect(fullRect, rect);
            paint.setColor(Color.BLUE);
            canvas.drawRect(fullRect, paint);
            canvas.drawLine(fullRect.centerX(), fullRect.top,
                    fullRect.centerX(), fullRect.bottom, paint);
            canvas.drawLine(fullRect.left, fullRect.centerY(),
                    fullRect.right, fullRect.centerY(), paint);
            paint.setColor(Color.GREEN);
            float dw = drawRect.width();
            float dh = drawRect.height();
            float dx = fullRect.centerX() - dw/2;
            float dy = fullRect.centerY() - dh/2;
            drawRect.set(dx, dy, dx + dw, dy + dh);
            canvas.drawRect(drawRect, paint);
            canvas.drawLine(drawRect.centerX(), drawRect.top,
                    drawRect.centerX(), drawRect.bottom, paint);
            canvas.drawLine(drawRect.left, drawRect.centerY(),
                    drawRect.right, drawRect.centerY(), paint);
            canvas.drawCircle(drawRect.centerX(), drawRect.centerY(),
                    mTouchPadding, paint);
        }
    }
}
