
package com.android.gallery3d.filtershow.imageshow;

import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.HistoryAdapter;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.presets.ImagePreset;
import com.android.gallery3d.filtershow.ui.SliderListener;
import com.android.gallery3d.filtershow.ui.SliderController;
import com.android.gallery3d.R;
import com.android.gallery3d.R.id;
import com.android.gallery3d.R.layout;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.widget.ArrayAdapter;

public class ImageShow extends View implements SliderListener {

    private static final String LOGTAG = "ImageShow";

    protected Paint mPaint = new Paint();
    private static int mTextSize = 24;
    private static int mTextPadding = 20;

    protected ImagePreset mImagePreset = null;
    protected ImageLoader mImageLoader = null;
    private ImageFilter mCurrentFilter = null;

    private Bitmap mBackgroundImage = null;
    protected Bitmap mForegroundImage = null;
    protected Bitmap mFilteredImage = null;

    protected SliderController mSliderController = new SliderController();

    private HistoryAdapter mAdapter = null;

    protected Rect mImageBounds = null;
    protected float mImageRotation = 0;
    protected float mImageRotationZoomFactor = 0;

    private boolean mShowControls = false;
    private boolean mShowOriginal = false;
    private String mToast = null;
    private boolean mShowToast = false;
    private boolean mImportantToast = false;

    private Handler mHandler = new Handler();

    public void onNewValue(int value) {
        if (mCurrentFilter != null) {
            mCurrentFilter.setParameter(value);
        }
        mImageLoader.resetImageForPreset(getImagePreset(), this);
        invalidate();
    }

    public ImageShow(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSliderController.setListener(this);
        mAdapter = new HistoryAdapter(context, R.layout.filtershow_history_operation_row,
                R.id.rowTextView);
    }

    public ImageShow(Context context) {
        super(context);
        mSliderController.setListener(this);
        mAdapter = new HistoryAdapter(context, R.layout.filtershow_history_operation_row,
                R.id.rowTextView);
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(parentWidth, parentHeight);
        mSliderController.setWidth(parentWidth);
        mSliderController.setHeight(parentHeight);
    }

    public void setCurrentFilter(ImageFilter filter) {
        mCurrentFilter = filter;
    }

    public void setAdapter(HistoryAdapter adapter) {
        mAdapter = adapter;
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

    public ImagePreset getImagePreset() {
        return mImagePreset;
    }

    public Bitmap getOriginalFrontBitmap() {
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

    public void onDraw(Canvas canvas) {
        if (mBackgroundImage == null) {
            mBackgroundImage = mImageLoader.getBackgroundBitmap(getResources());
        }
        if (mBackgroundImage != null) {
            Rect s = new Rect(0, 0, mBackgroundImage.getWidth(),
                    mBackgroundImage.getHeight());
            Rect d = new Rect(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(mBackgroundImage, s, d, mPaint);
        }

        Bitmap filteredImage = null;
        if (mImageLoader != null) {
            filteredImage = mImageLoader.getImageForPreset(this,
                    getImagePreset(), showHires());
//            Log.v(LOGTAG, "getImageForPreset " + getImagePreset() + " is: " + filteredImage);
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

        if (mFilteredImage != null) {
            Rect s = new Rect(0, 0, mFilteredImage.getWidth(),
                    mFilteredImage.getHeight());
            float ratio = mFilteredImage.getWidth()
                    / (float) mFilteredImage.getHeight();
            float w = getWidth();
            float h = w / ratio;
            float ty = (getHeight() - h) / 2.0f;
            float tx = 0;
            // t = 0;
            if (ratio < 1.0f) { // portrait image
                h = getHeight();
                w = h * ratio;
                tx = (getWidth() - w) / 2.0f;
                ty = 0;
            }
            Rect d = new Rect((int) tx, (int) ty, (int) (w + tx),
                    (int) (h + ty));
            mImageBounds = d;

            canvas.drawBitmap(mFilteredImage, s, d, mPaint);
        }

        if (showTitle() && getImagePreset() != null) {
            mPaint.setARGB(200, 0, 0, 0);
            mPaint.setTextSize(mTextSize);

            Rect textRect = new Rect(0, 0, getWidth(), mTextSize + mTextPadding);
            canvas.drawRect(textRect, mPaint);
            mPaint.setARGB(255, 200, 200, 200);
            canvas.drawText(getImagePreset().name(), mTextPadding,
                    10 + mTextPadding, mPaint);
        }
        mPaint.setARGB(255, 150, 150, 150);
        mPaint.setStrokeWidth(4);
        canvas.drawLine(0, 0, getWidth(), 0, mPaint);

        if (showControls()) {
            mSliderController.onDraw(canvas);
        }

        drawToast(canvas);
    }

    public void setShowControls(boolean value) {
        mShowControls = value;
    }

    public boolean showControls() {
        return mShowControls;
    }

    public boolean showHires() {
        return true;
    }

    public boolean showTitle() {
        return true;
    }

    public void setImagePreset(ImagePreset preset) {
        setImagePreset(preset, true);
    }

    public void setImagePreset(ImagePreset preset, boolean addToHistory) {
        mImagePreset = preset;
        if (getImagePreset() != null) {
//            Log.v(LOGTAG, "add " + getImagePreset().name() + " " + getImagePreset());
            if (addToHistory) {
                mAdapter.insert(getImagePreset(), 0);
            }
            getImagePreset().setEndpoint(this);
            updateImage();
        }
//        Log.v(LOGTAG, "invalidate from setImagePreset");
        invalidate();
    }

    public void setImageLoader(ImageLoader loader) {
        mImageLoader = loader;
        if (mImageLoader != null) {
            mImageLoader.addListener(this);
        }
    }

    public void updateImage() {
        mForegroundImage = getOriginalFrontBitmap();
        /*
         * if (mImageLoader != null) {
         * mImageLoader.resetImageForPreset(getImagePreset(), this); }
         */

        /*
         * if (mForegroundImage != null) { Bitmap filteredImage =
         * mForegroundImage.copy(mConfig, true);
         * getImagePreset().apply(filteredImage); invalidate(); }
         */
    }

    public void updateFilteredImage(Bitmap bitmap) {
        mFilteredImage = bitmap;
        // Log.v(LOGTAG, "invalidate from updateFilteredImage");
        // invalidate();
    }

    public void saveImage(FilterShowActivity filterShowActivity) {
        mImageLoader.saveImage(getImagePreset(), filterShowActivity);
    }

    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        mSliderController.onTouchEvent(event);
        // Log.v(LOGTAG, "invalidate from onTouchEvent");
        invalidate();
        return true;
    }

    // listview stuff

    public ArrayAdapter getListAdapter() {
        return mAdapter;
    }

    public void onItemClick(int position) {
        Log.v(LOGTAG, "Click on item " + position);
        Log.v(LOGTAG, "item " + position + " is " + mAdapter.getItem(position));
        setImagePreset(new ImagePreset(mAdapter.getItem(position)), false); // we
                                                                            // need
                                                                            // a
                                                                            // copy
                                                                            // from
                                                                            // the
                                                                            // history
        mAdapter.setCurrentPreset(position);
    }

    public void showOriginal(boolean show) {
        mShowOriginal = show;
        invalidate();
    }

    public float getImageRotation() {
        return mImageRotation;
    }

    public float getImageRotationZoomFactor() {
        return mImageRotationZoomFactor;
    }

    public void setImageRotation(float imageRotation,
            float imageRotationZoomFactor) {
        if (imageRotation != mImageRotation) {
            invalidate();
        }
        mImageRotation = imageRotation;
        mImageRotationZoomFactor = imageRotationZoomFactor;
    }
}
