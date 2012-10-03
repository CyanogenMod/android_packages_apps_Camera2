package com.android.gallery3d.filtershow.imageshow;

import com.android.gallery3d.filtershow.presets.ImagePreset;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;

public class ImageZoom extends ImageSlave implements OnGestureListener, OnDoubleTapListener {
    private boolean mTouchDown = false;
    private boolean mZoomedIn = false;
    private Rect mZoomBounds = null;
    private GestureDetector mGestureDetector = null;

    public ImageZoom(Context context) {
        super(context);
        setupGestureDetector(context);
    }

    public ImageZoom(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupGestureDetector(context);
    }

    public void setupGestureDetector(Context context) {
        mGestureDetector = new GestureDetector(context, this);
    }

    public boolean onTouchEvent(MotionEvent event) {
        boolean ret = mGestureDetector.onTouchEvent(event);
        ret = super.onTouchEvent(event);
        return ret;
    }

    public void onTouchDown(float x, float y) {
        super.onTouchDown(x, y);
        if (mZoomedIn || mTouchDown) {
            return;
        }
        mTouchDown = true;
        Rect originalBounds = mImageLoader.getOriginalBounds();
        Rect imageBounds = getImageBounds();
        float touchX = x - imageBounds.left;
        float touchY = y - imageBounds.top;

        float w = originalBounds.width();
        float h = originalBounds.height();
        float ratio = w / h;
        int mw = getWidth() / 2;
        int mh = getHeight() / 2;
        int cx = (int) (w / 2);
        int cy = (int) (h / 2);
        cx = (int) (touchX / imageBounds.width() * w);
        cy = (int) (touchY / imageBounds.height() * h);
        int left = cx - mw;
        int top = cy - mh;
        mZoomBounds = new Rect(left, top, left + mw * 2, top + mh * 2);
    }

    public void onTouchUp() {
        mTouchDown = false;
    }

    public void onDraw(Canvas canvas) {
        drawBackground(canvas);
        Bitmap filteredImage = null;
        if ((mZoomedIn ||mTouchDown) && mImageLoader != null) {
            filteredImage = mImageLoader.getScaleOneImageForPreset(this, getImagePreset(), mZoomBounds, false);
        } else {
            getFilteredImage();
            filteredImage = mFilteredImage;
        }
        drawImage(canvas, filteredImage);
        if (showControls()) {
            mSliderController.onDraw(canvas);
        }

        drawToast(canvas);
    }

    // TODO: move back some of that touch handling to a superclass / refactor
    // SlideController into a more generic gesture detector
    @Override
    public boolean onDown(MotionEvent arg0) {
        return false;
    }

    @Override
    public boolean onFling(MotionEvent arg0, MotionEvent arg1, float arg2, float arg3) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent arg0) {
    }

    @Override
    public boolean onScroll(MotionEvent arg0, MotionEvent arg1, float arg2, float arg3) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent arg0) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent arg0) {
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent arg0) {
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent arg0) {
        mZoomedIn = !mZoomedIn;
        invalidate();
        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent arg0) {
        return false;
    }
}