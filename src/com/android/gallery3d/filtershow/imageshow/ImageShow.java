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
import android.os.Handler;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.HistoryAdapter;
import com.android.gallery3d.filtershow.ImageStateAdapter;
import com.android.gallery3d.filtershow.PanelController;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.presets.ImagePreset;
import com.android.gallery3d.filtershow.ui.SliderController;
import com.android.gallery3d.filtershow.ui.SliderListener;

import java.io.File;

public class ImageShow extends View implements OnGestureListener,
                                               OnDoubleTapListener,
                                               SliderListener,
                                               OnSeekBarChangeListener {

    private static final String LOGTAG = "ImageShow";

    protected Paint mPaint = new Paint();
    protected static int mTextSize = 24;
    protected static int mTextPadding = 20;

    protected ImagePreset mImagePreset = null;
    protected ImageLoader mImageLoader = null;
    private ImageFilter mCurrentFilter = null;
    private boolean mDirtyGeometry = true;

    private Bitmap mBackgroundImage = null;
    private final boolean USE_BACKGROUND_IMAGE = false;
    private static int mBackgroundColor = Color.RED;

    // TODO: remove protected here, it should be private
    protected Bitmap mForegroundImage = null;
    protected Bitmap mFilteredImage = null;

    private final boolean USE_SLIDER_GESTURE = false; // set to true to have
                                                      // slider gesture
    protected SliderController mSliderController = new SliderController();

    private GestureDetector mGestureDetector = null;

    private HistoryAdapter mHistoryAdapter = null;
    private ImageStateAdapter mImageStateAdapter = null;

    protected GeometryMetadata getGeometry() {
        return new GeometryMetadata(getImagePreset().mGeoData);
    }

    public void setGeometry(GeometryMetadata d) {
        getImagePreset().mGeoData.set(d);
    }

    private boolean mShowControls = false;
    private boolean mShowOriginal = false;
    private String mToast = null;
    private boolean mShowToast = false;
    private boolean mImportantToast = false;

    protected float mTouchX = 0;
    protected float mTouchY = 0;

    private SeekBar mSeekBar = null;
    private PanelController mController = null;

    private FilterShowActivity mActivity = null;

    public static void setDefaultBackgroundColor(int value) {
        mBackgroundColor = value;
    }

    public static void setTextSize(int value) {
        mTextSize = value;
    }

    public static void setTextPadding(int value) {
        mTextPadding = value;
    }

    private final Handler mHandler = new Handler();

    public void select() {
        if (getCurrentFilter() != null) {
            int parameter = getCurrentFilter().getParameter();
            int maxp = getCurrentFilter().getMaxParameter();
            int minp = getCurrentFilter().getMinParameter();
            updateSeekBar(parameter,minp,maxp);
        }
        if (mSeekBar != null) {
            mSeekBar.setOnSeekBarChangeListener(this);
        }
    }

    private int parameterToUI(int parameter,int minp,int maxp,int uimax){
        return (uimax*(parameter-minp))/(maxp-minp);
    }

    private int uiToParameter(int ui,int minp,int maxp,int uimax){
        return  ((maxp-minp)*ui)/uimax+minp;
    }
    public void updateSeekBar(int parameter,int minp,int maxp) {
        if (mSeekBar == null) {
            return;
        }
        int seekMax  = mSeekBar.getMax();
        int progress = parameterToUI(parameter,minp,maxp,seekMax);
        mSeekBar.setProgress(progress);
        if (getPanelController() != null) {
            getPanelController().onNewValue(parameter);
        }
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
        if (currentFilter!=null) {
            onNewValue(currentFilter.getDefaultParameter());
        }
        if (USE_SLIDER_GESTURE) {
            mSliderController.reset();
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
        int maxp = 100;
        int minp = -100;
        if (getCurrentFilter() != null) {
            getCurrentFilter().setParameter(parameter);
            maxp = getCurrentFilter().getMaxParameter();
            minp = getCurrentFilter().getMinParameter();
        }
        if (getImagePreset() != null) {
            mImageLoader.resetImageForPreset(getImagePreset(), this);
            getImagePreset().fillImageStateAdapter(mImageStateAdapter);
        }
        if (getPanelController() != null) {
            getPanelController().onNewValue(parameter);
        }
        updateSeekBar(parameter,minp,maxp);
        invalidate();
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
        if (USE_SLIDER_GESTURE) {
            mSliderController.setListener(this);
        }
        mHistoryAdapter = new HistoryAdapter(context, R.layout.filtershow_history_operation_row,
                R.id.rowTextView);
        mImageStateAdapter = new ImageStateAdapter(context,
                R.layout.filtershow_imagestate_row);
        setupGestureDetector(context);
        mActivity = (FilterShowActivity) context;
    }

    public ImageShow(Context context) {
        super(context);
        if (USE_SLIDER_GESTURE) {
            mSliderController.setListener(this);
        }
        mHistoryAdapter = new HistoryAdapter(context, R.layout.filtershow_history_operation_row,
                R.id.rowTextView);
        setupGestureDetector(context);
        mActivity = (FilterShowActivity) context;
    }

    public void setupGestureDetector(Context context) {
        mGestureDetector = new GestureDetector(context, this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(parentWidth, parentHeight);
        if (USE_SLIDER_GESTURE) {
            mSliderController.setWidth(parentWidth);
            mSliderController.setHeight(parentHeight);
        }
    }

    public void setSeekBar(SeekBar seekBar) {
        mSeekBar = seekBar;
    }

    public void setCurrentFilter(ImageFilter filter) {
        mCurrentFilter = filter;
    }

    public ImageFilter getCurrentFilter() {
        return mCurrentFilter;
    }

    public void setAdapter(HistoryAdapter adapter) {
        mHistoryAdapter = adapter;
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

    public ImagePreset getImagePreset() {
        return mImagePreset;
    }

    protected Bitmap getOriginalFrontBitmap() {
        if (mImageLoader != null) {
            return mImageLoader.getOriginalBitmapLarge();
        }
        return null;
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

    @Override
    public void onDraw(Canvas canvas) {
        drawBackground(canvas);
        getFilteredImage();
        drawImage(canvas, mFilteredImage);

        if (showTitle() && getImagePreset() != null) {
            mPaint.setARGB(200, 0, 0, 0);
            mPaint.setTextSize(mTextSize);

            Rect textRect = new Rect(0, 0, getWidth(), mTextSize + mTextPadding);
            canvas.drawRect(textRect, mPaint);
            mPaint.setARGB(255, 200, 200, 200);
            canvas.drawText(getImagePreset().name(), mTextPadding,
                    1.5f * mTextPadding, mPaint);
        }

        if (showControls()) {
            if (USE_SLIDER_GESTURE) {
                mSliderController.onDraw(canvas);
            }
        }

        drawToast(canvas);
    }

    public void getFilteredImage() {
        Bitmap filteredImage = null;
        if (mImageLoader != null) {
            filteredImage = mImageLoader.getImageForPreset(this,
                    getImagePreset(), showHires());
        }

        if (filteredImage == null) {
            // if no image for the current preset, use the previous one
            filteredImage = mFilteredImage;
        } else {
            mFilteredImage = filteredImage;
        }

        if (mShowOriginal || mFilteredImage == null) {
            mFilteredImage = mForegroundImage;
        }
    }

    public void drawImage(Canvas canvas, Bitmap image) {
        if (image != null) {
            Rect s = new Rect(0, 0, image.getWidth(),
                    image.getHeight());
            float ratio = image.getWidth()
                    / (float) image.getHeight();
            float w = getWidth();
            float h = w / ratio;
            float ty = (getHeight() - h) / 2.0f;
            float tx = 0;
            if (ratio < 1.0f || (getHeight() < w)) {
                h = getHeight();
                w = h * ratio;
                tx = (getWidth() - w) / 2.0f;
                ty = 0;
            }
            Rect d = new Rect((int) tx, (int) ty, (int) (w + tx),
                    (int) (h + ty));

            canvas.drawBitmap(image, s, d, mPaint);
        }
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

    public void setImagePreset(ImagePreset preset) {
        setImagePreset(preset, true);
    }

    public void setImagePreset(ImagePreset preset, boolean addToHistory) {
        mImagePreset = preset;
        if (getImagePreset() != null) {
            if (addToHistory) {
                mHistoryAdapter.addHistoryItem(getImagePreset());
            }
            getImagePreset().setEndpoint(this);
            updateImage();
        }
        mImagePreset.fillImageStateAdapter(mImageStateAdapter);
        invalidate();
    }

    public void setImageLoader(ImageLoader loader) {
        mImageLoader = loader;
        if (mImageLoader != null) {
            mImageLoader.addListener(this);
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
        setDirtyGeometryFlag();
    }

    public void updateImage() {
        mForegroundImage = getOriginalFrontBitmap();
        imageSizeChanged(mForegroundImage); // TODO: should change to filtered
        setDirtyGeometryFlag();
    }

    public void updateFilteredImage(Bitmap bitmap) {
        mFilteredImage = bitmap;
    }

    public void saveImage(FilterShowActivity filterShowActivity, File file) {
        mImageLoader.saveImage(getImagePreset(), filterShowActivity, file);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        if (USE_SLIDER_GESTURE) {
            mSliderController.onTouchEvent(event);
        }
        mGestureDetector.onTouchEvent(event);
        invalidate();
        return true;
    }

    // listview stuff

    public HistoryAdapter getHistory() {
        return mHistoryAdapter;
    }

    public ArrayAdapter getImageStateAdapter() {
        return mImageStateAdapter;
    }

    public void onItemClick(int position) {
        setImagePreset(new ImagePreset(mHistoryAdapter.getItem(position)), false);
        // we need a copy from the history
        mHistoryAdapter.setCurrentPreset(position);
    }

    public void showOriginal(boolean show) {
        mShowOriginal = show;
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
        if (getCurrentFilter()!=null){
            int maxp = getCurrentFilter().getMaxParameter();
            int minp = getCurrentFilter().getMinParameter();
            parameter = uiToParameter(progress,minp,maxp,arg0.getMax());
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
                || (mActivity.isShowingHistoryPanel() && endEvent.getX() > startEvent.getX())){
            mActivity.toggleHistoryPanel();
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
}
