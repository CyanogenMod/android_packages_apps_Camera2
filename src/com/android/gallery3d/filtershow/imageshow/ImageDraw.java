
package com.android.gallery3d.filtershow.imageshow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.android.gallery3d.filtershow.editors.EditorDraw;
import com.android.gallery3d.filtershow.filters.FilterDrawRepresentation;
import com.android.gallery3d.filtershow.filters.ImageFilterDraw;

public class ImageDraw extends ImageShow {

    private static final String LOGTAG = "ImageDraw";
    private int mCurrentColor = Color.RED;
    final static float INITAL_STROKE_RADIUS = 40;
    private float mCurrentSize = INITAL_STROKE_RADIUS;
    private byte mType = 0;
    private FilterDrawRepresentation mFRep;
    private EditorDraw mEditorDraw;

    public ImageDraw(Context context, AttributeSet attrs) {
        super(context, attrs);
        resetParameter();
    }

    public ImageDraw(Context context) {
        super(context);
        resetParameter();
    }

    public void setEditor(EditorDraw editorDraw) {
        mEditorDraw = editorDraw;
    }
    public void setFilterDrawRepresentation(FilterDrawRepresentation fr) {
        mFRep = fr;
    }

    public Drawable getIcon(Context context) {

        return null;
    }

    @Override
    public void resetParameter() {
        if (mFRep != null) {
            mFRep.clear();
        }
    }

    public void setColor(int color) {
        mCurrentColor = color;
    }

    public void setSize(int size) {
        mCurrentSize = size;
    }

    public void setStyle(byte style) {
        mType = (byte) (style % ImageFilterDraw.NUMBER_OF_STYLES);
    }

    public int getStyle() {
        return mType;
    }

    public int getSize() {
        return (int) mCurrentSize;
    }

    @Override
    public void updateImage() {
        super.updateImage();
        invalidate();
    }

    float[] mTmpPoint = new float[2]; // so we do not malloc
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getPointerCount() != 1) {
            return false;
        }

        ImageFilterDraw filter = (ImageFilterDraw) getCurrentFilter();

        if (event.getAction() == MotionEvent.ACTION_DOWN) {

            mTmpPoint[0] = event.getX();
            mTmpPoint[1] = event.getY();
            mToOrig.mapPoints(mTmpPoint);
            mFRep.startNewSection(mType, mCurrentColor, mCurrentSize, mTmpPoint[0], mTmpPoint[1]);

        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            int historySize = event.getHistorySize();
            final int pointerCount = event.getPointerCount();
            for (int h = 0; h < historySize; h++) {
                int p = 0;
                {
                    mTmpPoint[0] = event.getHistoricalX(p, h);
                    mTmpPoint[1] = event.getHistoricalY(p, h);
                    mToOrig.mapPoints(mTmpPoint);
                    mFRep.addPoint(mTmpPoint[0], mTmpPoint[1]);
                }
            }
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            mTmpPoint[0] = event.getX();
            mTmpPoint[1] = event.getY();
            mToOrig.mapPoints(mTmpPoint);
            mFRep.endSection(mTmpPoint[0], mTmpPoint[1]);
            this.resetImageCaches(this);

        }
        mEditorDraw.commitLocalRepresentation();
        invalidate();
        return true;
    }

    Matrix mRotateToScreen;
    Matrix mToScreen;
    Matrix mToOrig = new Matrix();
    private void calcScreenMapping() {

        GeometryMetadata geo = getImagePreset().mGeoData;
        mToScreen = geo.getOriginalToScreen(false,
                mImageLoader.getOriginalBounds().width(),
                mImageLoader.getOriginalBounds().height(), getWidth(), getHeight());
        mRotateToScreen = geo.getOriginalToScreen(true,
                mImageLoader.getOriginalBounds().width(),
                mImageLoader.getOriginalBounds().height(), getWidth(), getHeight());
        mRotateToScreen.invert(mToOrig);
        mToOrig.invert(mRotateToScreen);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        calcScreenMapping();

    }

}
