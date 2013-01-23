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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.PanelController;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.presets.ImagePreset;
import com.android.gallery3d.filtershow.ui.SliderListener;

import java.io.File;

public class ImageShow extends View implements OnGestureListener,
        OnDoubleTapListener,
        SliderListener,
        OnSeekBarChangeListener {

    protected MasterImage mMasterImage = MasterImage.getImage();
    private static final String LOGTAG = "ImageShow";

    protected Paint mPaint = new Paint();
    protected static int mTextSize = 24;
    protected static int mTextPadding = 20;

    protected ImageLoader mImageLoader = null;
    private boolean mDirtyGeometry = false;

    private Bitmap mBackgroundImage = null;
    private final boolean USE_BACKGROUND_IMAGE = false;
    private static int mBackgroundColor = Color.RED;

    private GestureDetector mGestureDetector = null;

    protected Rect mImageBounds = new Rect();

    private boolean mTouchShowOriginal = false;
    private long mTouchShowOriginalDate = 0;
    private final long mTouchShowOriginalDelayMin = 200; // 200ms
    private final long mTouchShowOriginalDelayMax = 300; // 300ms
    private int mShowOriginalDirection = 0;
    private static int UNVEIL_HORIZONTAL = 1;
    private static int UNVEIL_VERTICAL = 2;

    private int mTouchDownX = 0;
    private int mTouchDownY = 0;
    protected float mTouchX = 0;
    protected float mTouchY = 0;

    private static int mOriginalTextMargin = 8;
    private static int mOriginalTextSize = 26;
    private static String mOriginalText = "Original";

    protected GeometryMetadata getGeometry() {
        return new GeometryMetadata(getImagePreset().mGeoData);
    }

    public void setGeometry(GeometryMetadata d) {
        getImagePreset().mGeoData.set(d);
    }

    private boolean mShowControls = false;
    private String mToast = null;
    private boolean mShowToast = false;
    private boolean mImportantToast = false;

    private SeekBar mSeekBar = null;
    private PanelController mController = null;

    private FilterShowActivity mActivity = null;

    public static void setDefaultBackgroundColor(int value) {
        mBackgroundColor = value;
    }

    public FilterShowActivity getActivity() {
        return mActivity;
    }

    public int getDefaultBackgroundColor() {
        return mBackgroundColor;
    }

    public static void setTextSize(int value) {
        mTextSize = value;
    }

    public static void setTextPadding(int value) {
        mTextPadding = value;
    }

    public static void setOriginalTextMargin(int value) {
        mOriginalTextMargin = value;
    }

    public static void setOriginalTextSize(int value) {
        mOriginalTextSize = value;
    }

    public static void setOriginalText(String text) {
        mOriginalText = text;
    }

    private final Handler mHandler = new Handler();

    public void select() {
        if (mSeekBar != null) {
            mSeekBar.setOnSeekBarChangeListener(this);
        }
        if (getCurrentFilter() != null) {
            int parameter = getCurrentFilter().getParameter();
            int maxp = getCurrentFilter().getMaxParameter();
            int minp = getCurrentFilter().getMinParameter();
            updateSeekBar(parameter, minp, maxp);
        }
    }

    private int parameterToUI(int parameter, int minp, int maxp, int uimax) {
        return (uimax * (parameter - minp)) / (maxp - minp);
    }

    private int uiToParameter(int ui, int minp, int maxp, int uimax) {
        return ((maxp - minp) * ui) / uimax + minp;
    }

    public void updateSeekBar(int parameter, int minp, int maxp) {
        if (mSeekBar == null) {
            return;
        }
        int seekMax = mSeekBar.getMax();
        int progress = parameterToUI(parameter, minp, maxp, seekMax);
        mSeekBar.setProgress(progress);
    }

    public void unselect() {

    }

    public boolean hasModifications() {
        if (getImagePreset() == null) {
            return false;
        }
        return getImagePreset().hasModifications();
    }

    public void resetParameter() {
        ImageFilter currentFilter = getCurrentFilter();
        if (currentFilter != null) {
            updateSeekBar(currentFilter.getDefaultParameter(),
                    getCurrentFilter().getMinParameter(), getCurrentFilter().getMaxParameter());
        }
    }

    public void setPanelController(PanelController controller) {
        mController = controller;
    }

    public PanelController getPanelController() {
        return mController;
    }

    @Override
    public void onNewValue(int parameter) {
        int maxp = ImageFilter.DEFAULT_MAX_PARAMETER;
        int minp = ImageFilter.DEFAULT_MIN_PARAMETER;
        if (getCurrentFilter() != null) {
            if (getCurrentFilter().getParameter() == parameter) {
                return;
            }
            getCurrentFilter().setParameter(parameter);
            maxp = getCurrentFilter().getMaxParameter();
            minp = getCurrentFilter().getMinParameter();
        }
        if (getImagePreset() != null) {
            getImagePreset().fillImageStateAdapter(mMasterImage.getState());
        }
        if (getPanelController() != null) {
            getPanelController().onNewValue(parameter);
        }
        invalidate();
        mActivity.enableSave(hasModifications());
    }

    @Override
    public void onTouchDown(float x, float y) {
        mTouchX = x;
        mTouchY = y;
        invalidate();
    }

    @Override
    public void onTouchUp() {
    }

    public ImageShow(Context context, AttributeSet attrs) {
        super(context, attrs);

        setupGestureDetector(context);
        mActivity = (FilterShowActivity) context;
        mMasterImage.addObserver(this);
    }

    public ImageShow(Context context) {
        super(context);

        setupGestureDetector(context);
        mActivity = (FilterShowActivity) context;
        mMasterImage.addObserver(this);
    }

    public void setupGestureDetector(Context context) {
        mGestureDetector = new GestureDetector(context, this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(parentWidth, parentHeight);
    }

    public void setSeekBar(SeekBar seekBar) {
        mSeekBar = seekBar;
    }

    public ImageFilter getCurrentFilter() {
        return mMasterImage.getCurrentFilter();
    }

    public void showToast(String text) {
        showToast(text, false);
    }

    public void showToast(String text, boolean important) {
        mToast = text;
        mShowToast = true;
        mImportantToast = important;
        invalidate();

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mShowToast = false;
                invalidate();
            }
        }, 400);
    }

    public Rect getImageBounds() {
        Rect dst = new Rect();
        getImagePreset().mGeoData.getPhotoBounds().roundOut(dst);
        return dst;
    }

    public Rect getImageCropBounds() {
        return GeometryMath.roundNearest(getImagePreset().mGeoData.getPreviewCropBounds());
    }

    public Rect getDisplayedImageBounds() {
        return mImageBounds;
    }

    public ImagePreset getImagePreset() {
        return mMasterImage.getPreset();
    }

    public void drawToast(Canvas canvas) {
        if (mShowToast && mToast != null) {
            Paint paint = new Paint();
            paint.setTextSize(128);
            float textWidth = paint.measureText(mToast);
            int toastX = (int) ((getWidth() - textWidth) / 2.0f);
            int toastY = (int) (getHeight() / 3.0f);

            paint.setARGB(255, 0, 0, 0);
            canvas.drawText(mToast, toastX - 2, toastY - 2, paint);
            canvas.drawText(mToast, toastX - 2, toastY, paint);
            canvas.drawText(mToast, toastX, toastY - 2, paint);
            canvas.drawText(mToast, toastX + 2, toastY + 2, paint);
            canvas.drawText(mToast, toastX + 2, toastY, paint);
            canvas.drawText(mToast, toastX, toastY + 2, paint);
            if (mImportantToast) {
                paint.setARGB(255, 200, 0, 0);
            } else {
                paint.setARGB(255, 255, 255, 255);
            }
            canvas.drawText(mToast, toastX, toastY, paint);
        }
    }

    public void defaultDrawImage(Canvas canvas) {
        drawImage(canvas, getFilteredImage());
        drawPartialImage(canvas, getGeometryOnlyImage());
    }

    @Override
    public void onDraw(Canvas canvas) {
        drawBackground(canvas);
        defaultDrawImage(canvas);

        if (showTitle() && getImagePreset() != null) {
            mPaint.setARGB(200, 0, 0, 0);
            mPaint.setTextSize(mTextSize);

            Rect textRect = new Rect(0, 0, getWidth(), mTextSize + mTextPadding);
            canvas.drawRect(textRect, mPaint);
            mPaint.setARGB(255, 200, 200, 200);
            canvas.drawText(getImagePreset().name(), mTextPadding,
                    1.5f * mTextPadding, mPaint);
        }

        drawToast(canvas);
    }

    public void resetImageCaches(ImageShow caller) {
        if (mImageLoader == null) {
            return;
        }
        mMasterImage.updatePresets(true);
    }

    public Bitmap getFiltersOnlyImage() {
        return mMasterImage.getFiltersOnlyImage();
    }

    public Bitmap getGeometryOnlyImage() {
        return mMasterImage.getGeometryOnlyImage();
    }

    public Bitmap getFilteredImage() {
        return mMasterImage.getFilteredImage();
    }

    public void drawImage(Canvas canvas, Bitmap image) {
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
            mImageBounds = d;
            canvas.drawBitmap(image, s, d, mPaint);
        }
    }

    public void drawPartialImage(Canvas canvas, Bitmap image) {
        if (!mTouchShowOriginal)
            return;
        canvas.save();
        if (image != null) {
            if (mShowOriginalDirection == 0) {
                if ((mTouchY - mTouchDownY) > (mTouchX - mTouchDownX)) {
                    mShowOriginalDirection = UNVEIL_VERTICAL;
                } else {
                    mShowOriginalDirection = UNVEIL_HORIZONTAL;
                }
            }

            int px = 0;
            int py = 0;
            if (mShowOriginalDirection == UNVEIL_VERTICAL) {
                px = mImageBounds.width();
                py = (int) (mTouchY - mImageBounds.top);
            } else {
                px = (int) (mTouchX - mImageBounds.left);
                py = mImageBounds.height();
            }

            Rect d = new Rect(mImageBounds.left, mImageBounds.top,
                    mImageBounds.left + px, mImageBounds.top + py);
            canvas.clipRect(d);
            drawImage(canvas, image);
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);

            if (mShowOriginalDirection == UNVEIL_VERTICAL) {
                canvas.drawLine(mImageBounds.left, mTouchY - 1,
                        mImageBounds.right, mTouchY - 1, paint);
            } else {
                canvas.drawLine(mTouchX - 1, mImageBounds.top,
                        mTouchX - 1, mImageBounds.bottom, paint);
            }

            Rect bounds = new Rect();
            paint.setTextSize(mOriginalTextSize);
            paint.getTextBounds(mOriginalText, 0, mOriginalText.length(), bounds);
            paint.setColor(Color.BLACK);
            canvas.drawText(mOriginalText, mImageBounds.left + mOriginalTextMargin + 1,
                    mImageBounds.top + bounds.height() + mOriginalTextMargin + 1, paint);
            paint.setColor(Color.WHITE);
            canvas.drawText(mOriginalText, mImageBounds.left + mOriginalTextMargin,
                    mImageBounds.top + bounds.height() + mOriginalTextMargin, paint);
        }
        canvas.restore();
    }

    public void drawBackground(Canvas canvas) {
        if (USE_BACKGROUND_IMAGE) {
            if (mBackgroundImage == null) {
                mBackgroundImage = mImageLoader.getBackgroundBitmap(getResources());
            }
            if (mBackgroundImage != null) {
                Rect s = new Rect(0, 0, mBackgroundImage.getWidth(),
                        mBackgroundImage.getHeight());
                Rect d = new Rect(0, 0, getWidth(), getHeight());
                canvas.drawBitmap(mBackgroundImage, s, d, mPaint);
            }
        } else {
            canvas.drawColor(mBackgroundColor);
        }
    }

    public ImageShow setShowControls(boolean value) {
        mShowControls = value;
        if (mShowControls) {
            if (mSeekBar != null) {
                mSeekBar.setVisibility(View.VISIBLE);
            }
        } else {
            if (mSeekBar != null) {
                mSeekBar.setVisibility(View.INVISIBLE);
            }
        }
        return this;
    }

    public boolean showControls() {
        return mShowControls;
    }

    public boolean showHires() {
        return true;
    }

    public boolean showTitle() {
        return false;
    }



    public void setImageLoader(ImageLoader loader) {
        mImageLoader = loader;
        if (mImageLoader != null) {
            mImageLoader.addListener(this);
            mMasterImage.setImageLoader(mImageLoader);
        }
    }

    private void setDirtyGeometryFlag() {
        mDirtyGeometry = true;
    }

    protected void clearDirtyGeometryFlag() {
        mDirtyGeometry = false;
    }

    protected boolean getDirtyGeometryFlag() {
        return mDirtyGeometry;
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

    public boolean updateGeometryFlags() {
        return true;
    }

    public void updateImage() {
        invalidate();
        if (!updateGeometryFlags()) {
            return;
        }
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

    public void saveToUri(Bitmap f, Uri u, String m, FilterShowActivity filterShowActivity) {
        mImageLoader.saveToUri(f, u, m, filterShowActivity);
    }

    public void returnFilteredResult(FilterShowActivity filterShowActivity) {
        mImageLoader.returnFilteredResult(getImagePreset(), filterShowActivity);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        if (mGestureDetector != null) {
            mGestureDetector.onTouchEvent(event);
        }
        int ex = (int) event.getX();
        int ey = (int) event.getY();
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mTouchDownX = ex;
            mTouchDownY = ey;
            mTouchShowOriginalDate = System.currentTimeMillis();
            mShowOriginalDirection = 0;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            mTouchX = ex;
            mTouchY = ey;
            if (!mActivity.isShowingHistoryPanel()
                    && (System.currentTimeMillis() - mTouchShowOriginalDate
                            > mTouchShowOriginalDelayMin)) {
                mTouchShowOriginal = true;
            }
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            mTouchShowOriginal = false;
            mTouchDownX = 0;
            mTouchDownY = 0;
            mTouchX = 0;
            mTouchY = 0;
        }
        invalidate();
        return true;
    }

    // listview stuff
    public void showOriginal(boolean show) {
        invalidate();
    }

    public float getImageRotation() {
        return getImagePreset().mGeoData.getRotation();
    }

    public float getImageRotationZoomFactor() {
        return getImagePreset().mGeoData.getScaleFactor();
    }

    public void setImageRotation(float r) {
        getImagePreset().mGeoData.setRotation(r);
    }

    public void setImageRotationZoomFactor(float f) {
        getImagePreset().mGeoData.setScaleFactor(f);
    }

    public void setImageRotation(float imageRotation,
            float imageRotationZoomFactor) {
        float r = getImageRotation();
        if (imageRotation != r) {
            invalidate();
        }
        setImageRotation(imageRotation);
        setImageRotationZoomFactor(imageRotationZoomFactor);
    }

    @Override
    public void onProgressChanged(SeekBar arg0, int progress, boolean arg2) {
        int parameter = progress;
        if (getCurrentFilter() != null) {
            int maxp = getCurrentFilter().getMaxParameter();
            int minp = getCurrentFilter().getMinParameter();
            parameter = uiToParameter(progress, minp, maxp, arg0.getMax());
        }

        onNewValue(parameter);
    }

    @Override
    public void onStartTrackingTouch(SeekBar arg0) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onStopTrackingTouch(SeekBar arg0) {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean onDoubleTap(MotionEvent arg0) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent arg0) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent arg0) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean onDown(MotionEvent arg0) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean onFling(MotionEvent startEvent, MotionEvent endEvent, float arg2, float arg3) {
        if ((!mActivity.isShowingHistoryPanel() && startEvent.getX() > endEvent.getX())
                || (mActivity.isShowingHistoryPanel() && endEvent.getX() > startEvent.getX())) {
            if (!mTouchShowOriginal
                    || (mTouchShowOriginal &&
                            (System.currentTimeMillis() - mTouchShowOriginalDate
                            < mTouchShowOriginalDelayMax))) {
                mActivity.toggleHistoryPanel();
            }
        }
        return true;
    }

    @Override
    public void onLongPress(MotionEvent arg0) {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean onScroll(MotionEvent arg0, MotionEvent arg1, float arg2, float arg3) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void onShowPress(MotionEvent arg0) {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean onSingleTapUp(MotionEvent arg0) {
        // TODO Auto-generated method stub
        return false;
    }

    public boolean useUtilityPanel() {
        return false;
    }

    public void openUtilityPanel(final LinearLayout accessoryViewList) {
        // TODO Auto-generated method stub
    }

}
