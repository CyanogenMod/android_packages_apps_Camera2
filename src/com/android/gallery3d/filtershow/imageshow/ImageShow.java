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

package com.android.gallery3d.filtershow.imageshow;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.LinearLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.presets.ImagePreset;

import java.io.File;

public class ImageShow extends View implements OnGestureListener,
        ScaleGestureDetector.OnScaleGestureListener,
        OnDoubleTapListener {

    private static final String LOGTAG = "ImageShow";
    private static final boolean ENABLE_ZOOMED_COMPARISON = false;

    protected Paint mPaint = new Paint();
    protected int mTextSize;
    protected int mTextPadding;

    protected ImageLoader mImageLoader = null;

    protected int mBackgroundColor;

    private GestureDetector mGestureDetector = null;
    private ScaleGestureDetector mScaleGestureDetector = null;

    protected Rect mImageBounds = new Rect();
    private boolean mOriginalDisabled = false;
    private boolean mTouchShowOriginal = false;
    private long mTouchShowOriginalDate = 0;
    private final long mTouchShowOriginalDelayMin = 200; // 200ms
    private int mShowOriginalDirection = 0;
    private static int UNVEIL_HORIZONTAL = 1;
    private static int UNVEIL_VERTICAL = 2;

    private Point mTouchDown = new Point();
    private Point mTouch = new Point();
    private boolean mFinishedScalingOperation = false;

    private int mOriginalTextMargin;
    private int mOriginalTextSize;
    private String mOriginalText;
    private boolean mZoomIn = false;
    Point mOriginalTranslation = new Point();
    float mOriginalScale;
    float mStartFocusX, mStartFocusY;
    private enum InteractionMode {
        NONE,
        SCALE,
        MOVE
    }
    InteractionMode mInteractionMode = InteractionMode.NONE;

    protected GeometryMetadata getGeometry() {
        return new GeometryMetadata(getImagePreset().mGeoData);
    }

    private FilterShowActivity mActivity = null;

    public FilterShowActivity getActivity() {
        return mActivity;
    }

    public boolean hasModifications() {
        if (getImagePreset() == null) {
            return false;
        }
        return getImagePreset().hasModifications();
    }

    public void resetParameter() {
        // TODO: implement reset
    }

    public void onNewValue(int parameter) {
        invalidate();
        mActivity.enableSave(hasModifications());
    }

    public ImageShow(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setupImageShow(context);
    }

    public ImageShow(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupImageShow(context);

    }

    public ImageShow(Context context) {
        super(context);
        setupImageShow(context);
    }

    private void setupImageShow(Context context) {
        Resources res = context.getResources();
        mTextSize = res.getDimensionPixelSize(R.dimen.photoeditor_text_size);
        mTextPadding = res.getDimensionPixelSize(R.dimen.photoeditor_text_padding);
        mOriginalTextMargin = res.getDimensionPixelSize(R.dimen.photoeditor_original_text_margin);
        mOriginalTextSize = res.getDimensionPixelSize(R.dimen.photoeditor_original_text_size);
        mBackgroundColor = res.getColor(R.color.background_screen);
        mOriginalText = res.getString(R.string.original_picture_text);
        setupGestureDetector(context);
        mActivity = (FilterShowActivity) context;
        MasterImage.getImage().addObserver(this);
    }

    public void setupGestureDetector(Context context) {
        mGestureDetector = new GestureDetector(context, this);
        mScaleGestureDetector = new ScaleGestureDetector(context, this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(parentWidth, parentHeight);
    }

    public ImageFilter getCurrentFilter() {
        return MasterImage.getImage().getCurrentFilter();
    }

    public Rect getImageBounds() {
        Rect dst = new Rect();
        getImagePreset().mGeoData.getPhotoBounds().roundOut(dst);
        return dst;
    }

    public Rect getImageCropBounds() {
        return GeometryMath.roundNearest(getImagePreset().mGeoData.getPreviewCropBounds());
    }

    /* consider moving the following 2 methods into a subclass */
    /**
     * This function calculates a Image to Screen Transformation matrix
     *
     * @param reflectRotation set true if you want the rotation encoded
     * @return Image to Screen transformation matrix
     */
    protected Matrix getImageToScreenMatrix(boolean reflectRotation) {
        GeometryMetadata geo = getImagePreset().mGeoData;
        if (geo == null || mImageLoader == null
                || mImageLoader.getOriginalBounds() == null) {
            return new Matrix();
        }
        Matrix m = geo.getOriginalToScreen(reflectRotation,
                mImageLoader.getOriginalBounds().width(),
                mImageLoader.getOriginalBounds().height(), getWidth(), getHeight());
        Point translate = MasterImage.getImage().getTranslation();
        float scaleFactor = MasterImage.getImage().getScaleFactor();
        m.postTranslate(translate.x, translate.y);
        m.postScale(scaleFactor, scaleFactor, getWidth() / 2.0f, getHeight() / 2.0f);
        return m;
    }

    /**
     * This function calculates a to Screen Image Transformation matrix
     *
     * @param reflectRotation set true if you want the rotation encoded
     * @return Screen to Image transformation matrix
     */
    protected Matrix getScreenToImageMatrix(boolean reflectRotation) {
        Matrix m = getImageToScreenMatrix(reflectRotation);
        Matrix invert = new Matrix();
        m.invert(invert);
        return invert;
    }

    public ImagePreset getImagePreset() {
        return MasterImage.getImage().getPreset();
    }

    @Override
    public void onDraw(Canvas canvas) {
        MasterImage.getImage().setImageShowSize(getWidth(), getHeight());

        float cx = canvas.getWidth()/2.0f;
        float cy = canvas.getHeight()/2.0f;
        float scaleFactor = MasterImage.getImage().getScaleFactor();
        Point translation = MasterImage.getImage().getTranslation();

        Matrix scalingMatrix = new Matrix();
        scalingMatrix.postScale(scaleFactor, scaleFactor, cx, cy);
        scalingMatrix.preTranslate(translation.x, translation.y);

        RectF unscaledClipRect = new RectF(mImageBounds);
        scalingMatrix.mapRect(unscaledClipRect, unscaledClipRect);

        canvas.save();

        boolean enablePartialRendering = false;

        // For now, partial rendering is disabled for all filters,
        // so no need to clip.
        if (enablePartialRendering && !unscaledClipRect.isEmpty()) {
            canvas.clipRect(unscaledClipRect);
        }

        canvas.save();
        // TODO: center scale on gesture
        canvas.scale(scaleFactor, scaleFactor, cx, cy);
        canvas.translate(translation.x, translation.y);
        drawImage(canvas, getFilteredImage(), true);
        Bitmap highresPreview = MasterImage.getImage().getHighresImage();
        if (highresPreview != null) {
            drawImage(canvas, highresPreview, false);
        }
        canvas.restore();

        Bitmap partialPreview = MasterImage.getImage().getPartialImage();
        if (partialPreview != null) {
            Rect src = new Rect(0, 0, partialPreview.getWidth(), partialPreview.getHeight());
            Rect dest = new Rect(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(partialPreview, src, dest, mPaint);
        }

        canvas.save();
        canvas.scale(scaleFactor, scaleFactor, cx, cy);
        canvas.translate(translation.x, translation.y);
        drawPartialImage(canvas, getGeometryOnlyImage());
        canvas.restore();

        canvas.restore();
    }

    public void resetImageCaches(ImageShow caller) {
        if (mImageLoader == null) {
            return;
        }
        MasterImage.getImage().updatePresets(true);
    }

    public Bitmap getFiltersOnlyImage() {
        return MasterImage.getImage().getFiltersOnlyImage();
    }

    public Bitmap getGeometryOnlyImage() {
        return MasterImage.getImage().getGeometryOnlyImage();
    }

    public Bitmap getFilteredImage() {
        return MasterImage.getImage().getFilteredImage();
    }

    public void drawImage(Canvas canvas, Bitmap image, boolean updateBounds) {
        if (image != null) {
            Rect s = new Rect(0, 0, image.getWidth(),
                    image.getHeight());

            float scale = GeometryMath.scale(image.getWidth(), image.getHeight(), getWidth(),
                    getHeight());

            float w = image.getWidth() * scale;
            float h = image.getHeight() * scale;
            float ty = (getHeight() - h) / 2.0f;
            float tx = (getWidth() - w) / 2.0f;

            Rect d = new Rect((int) tx, (int) ty, (int) (w + tx),
                    (int) (h + ty));
            if (updateBounds) {
                mImageBounds = d;
            }
            canvas.drawBitmap(image, s, d, mPaint);
        }
    }

    public void drawPartialImage(Canvas canvas, Bitmap image) {
        boolean showsOriginal = MasterImage.getImage().showsOriginal();
        if (!showsOriginal && !mTouchShowOriginal)
            return;
        canvas.save();
        if (image != null) {
            if (mShowOriginalDirection == 0) {
                if (Math.abs(mTouch.y - mTouchDown.y) > Math.abs(mTouch.x - mTouchDown.x)) {
                    mShowOriginalDirection = UNVEIL_VERTICAL;
                } else {
                    mShowOriginalDirection = UNVEIL_HORIZONTAL;
                }
            }

            int px = 0;
            int py = 0;
            if (mShowOriginalDirection == UNVEIL_VERTICAL) {
                px = mImageBounds.width();
                py = (int) (mTouch.y - mImageBounds.top);
            } else {
                px = (int) (mTouch.x - mImageBounds.left);
                py = mImageBounds.height();
                if (showsOriginal) {
                    px = mImageBounds.width();
                }
            }

            Rect d = new Rect(mImageBounds.left, mImageBounds.top,
                    mImageBounds.left + px, mImageBounds.top + py);
            canvas.clipRect(d);
            drawImage(canvas, image, false);
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setStrokeWidth(3);

            if (mShowOriginalDirection == UNVEIL_VERTICAL) {
                canvas.drawLine(mImageBounds.left, mTouch.y,
                        mImageBounds.right, mTouch.y, paint);
            } else {
                canvas.drawLine(mTouch.x, mImageBounds.top,
                        mTouch.x, mImageBounds.bottom, paint);
            }

            Rect bounds = new Rect();
            paint.setAntiAlias(true);
            paint.setTextSize(mOriginalTextSize);
            paint.getTextBounds(mOriginalText, 0, mOriginalText.length(), bounds);
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            canvas.drawText(mOriginalText, mImageBounds.left + mOriginalTextMargin,
                    mImageBounds.top + bounds.height() + mOriginalTextMargin, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(1);
            paint.setColor(Color.WHITE);
            canvas.drawText(mOriginalText, mImageBounds.left + mOriginalTextMargin,
                    mImageBounds.top + bounds.height() + mOriginalTextMargin, paint);
        }
        canvas.restore();
    }

    public void setImageLoader(ImageLoader loader) {
        mImageLoader = loader;
        if (mImageLoader != null) {
            mImageLoader.addListener(this);
            MasterImage.getImage().setImageLoader(mImageLoader);
        }
    }

    private void imageSizeChanged(Bitmap image) {
        if (image == null || getImagePreset() == null)
            return;
        float w = image.getWidth();
        float h = image.getHeight();
        GeometryMetadata geo = getImagePreset().mGeoData;
        RectF pb = geo.getPhotoBounds();
        if (w == pb.width() && h == pb.height()) {
            return;
        }
        RectF r = new RectF(0, 0, w, h);
        getImagePreset().mGeoData.setPhotoBounds(r);
        getImagePreset().mGeoData.setCropBounds(r);

    }

    public void updateImage() {
        invalidate();
        Bitmap bitmap = mImageLoader.getOriginalBitmapLarge();
        if (bitmap != null) {
            imageSizeChanged(bitmap);
        }
    }

    public void imageLoaded() {
        updateImage();
        invalidate();
    }

    public void saveImage(FilterShowActivity filterShowActivity, File file) {
        mImageLoader.saveImage(getImagePreset(), filterShowActivity, file);
    }


    public boolean scaleInProgress() {
        return mScaleGestureDetector.isInProgress();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        int action = event.getAction();
        action = action & MotionEvent.ACTION_MASK;

        mGestureDetector.onTouchEvent(event);
        boolean scaleInProgress = scaleInProgress();
        mScaleGestureDetector.onTouchEvent(event);
        if (mInteractionMode == InteractionMode.SCALE) {
            return true;
        }
        if (!scaleInProgress() && scaleInProgress) {
            // If we were scaling, the scale will stop but we will
            // still issue an ACTION_UP. Let the subclasses know.
            mFinishedScalingOperation = true;
        }

        int ex = (int) event.getX();
        int ey = (int) event.getY();
        if (action == MotionEvent.ACTION_DOWN) {
            mInteractionMode = InteractionMode.MOVE;
            mTouchDown.x = ex;
            mTouchDown.y = ey;
            mTouchShowOriginalDate = System.currentTimeMillis();
            mShowOriginalDirection = 0;
            MasterImage.getImage().setOriginalTranslation(MasterImage.getImage().getTranslation());
        }

        if (action == MotionEvent.ACTION_MOVE && mInteractionMode == InteractionMode.MOVE) {
            mTouch.x = ex;
            mTouch.y = ey;

            float scaleFactor = MasterImage.getImage().getScaleFactor();
            if (scaleFactor > 1 && (!ENABLE_ZOOMED_COMPARISON || event.getPointerCount() == 2)) {
                float translateX = (mTouch.x - mTouchDown.x) / scaleFactor;
                float translateY = (mTouch.y - mTouchDown.y) / scaleFactor;
                Point originalTranslation = MasterImage.getImage().getOriginalTranslation();
                Point translation = MasterImage.getImage().getTranslation();
                translation.x = (int) (originalTranslation.x + translateX);
                translation.y = (int) (originalTranslation.y + translateY);
                constrainTranslation(translation, scaleFactor);
                MasterImage.getImage().setTranslation(translation);
                mTouchShowOriginal = false;
            } else if (enableComparison() && !mOriginalDisabled
                    && (System.currentTimeMillis() - mTouchShowOriginalDate
                            > mTouchShowOriginalDelayMin)
                    && event.getPointerCount() == 1) {
                mTouchShowOriginal = true;
            }
        }

        if (action == MotionEvent.ACTION_UP) {
            mInteractionMode = InteractionMode.NONE;
            mTouchShowOriginal = false;
            mTouchDown.x = 0;
            mTouchDown.y = 0;
            mTouch.x = 0;
            mTouch.y = 0;
            if (MasterImage.getImage().getScaleFactor() <= 1) {
                MasterImage.getImage().setScaleFactor(1);
                MasterImage.getImage().resetTranslation();
            }
        }
        invalidate();
        return true;
    }

    protected boolean enableComparison() {
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent arg0) {
        mZoomIn = !mZoomIn;
        float scale = 1.0f;
        if (mZoomIn) {
            scale = MasterImage.getImage().getMaxScaleFactor();
        }
        if (scale != MasterImage.getImage().getScaleFactor()) {
            MasterImage.getImage().setScaleFactor(scale);
            float translateX = (getWidth() / 2 - arg0.getX());
            float translateY = (getHeight() / 2 - arg0.getY());
            Point translation = MasterImage.getImage().getTranslation();
            translation.x = (int) (mOriginalTranslation.x + translateX);
            translation.y = (int) (mOriginalTranslation.y + translateY);
            constrainTranslation(translation, scale);
            MasterImage.getImage().setTranslation(translation);
            invalidate();
        }
        return true;
    }

    private void constrainTranslation(Point translation, float scale) {
        float maxTranslationX = getWidth() / scale;
        float maxTranslationY = getHeight() / scale;
        if (Math.abs(translation.x) > maxTranslationX) {
            translation.x = (int) (Math.signum(translation.x) *
                    maxTranslationX);
            if (Math.abs(translation.y) > maxTranslationY) {
                translation.y = (int) (Math.signum(translation.y) *
                        maxTranslationY);
            }

        }
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent arg0) {
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent arg0) {
        return false;
    }

    @Override
    public boolean onDown(MotionEvent arg0) {
        return false;
    }

    @Override
    public boolean onFling(MotionEvent startEvent, MotionEvent endEvent, float arg2, float arg3) {
        if (mActivity == null) {
            return false;
        }
        if (endEvent.getPointerCount() == 2) {
            return false;
        }
        return true;
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

    public boolean useUtilityPanel() {
        return false;
    }

    public void openUtilityPanel(final LinearLayout accessoryViewList) {
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        MasterImage img = MasterImage.getImage();
        float scaleFactor = img.getScaleFactor();

        scaleFactor = scaleFactor * detector.getScaleFactor();
        if (scaleFactor > MasterImage.getImage().getMaxScaleFactor()) {
            scaleFactor = MasterImage.getImage().getMaxScaleFactor();
        }
        if (scaleFactor < 0.5) {
            scaleFactor = 0.5f;
        }
        MasterImage.getImage().setScaleFactor(scaleFactor);
        scaleFactor = img.getScaleFactor();
        float focusx = detector.getFocusX();
        float focusy = detector.getFocusY();
        float translateX = (focusx - mStartFocusX) / scaleFactor;
        float translateY = (focusy - mStartFocusY) / scaleFactor;
        Point translation = MasterImage.getImage().getTranslation();
        translation.x = (int) (mOriginalTranslation.x + translateX);
        translation.y = (int) (mOriginalTranslation.y + translateY);
        constrainTranslation(translation, scaleFactor);
        MasterImage.getImage().setTranslation(translation);

        invalidate();
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        Point pos = MasterImage.getImage().getTranslation();
        mOriginalTranslation.x = pos.x;
        mOriginalTranslation.y = pos.y;
        mOriginalScale = MasterImage.getImage().getScaleFactor();
        mStartFocusX = detector.getFocusX();
        mStartFocusY = detector.getFocusY();
        mInteractionMode = InteractionMode.SCALE;
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        mInteractionMode = InteractionMode.NONE;
        if (MasterImage.getImage().getScaleFactor() < 1) {
            MasterImage.getImage().setScaleFactor(1);
            invalidate();
        }
    }

    public boolean didFinishScalingOperation() {
        if (mFinishedScalingOperation) {
            mFinishedScalingOperation = false;
            return true;
        }
        return false;
    }

}
