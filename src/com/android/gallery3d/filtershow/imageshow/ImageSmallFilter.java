
package com.android.gallery3d.filtershow.imageshow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.presets.ImagePreset;

public class ImageSmallFilter extends ImageShow implements View.OnClickListener {

    private static final String LOGTAG = "ImageSmallFilter";
    private FilterShowActivity mController = null;
    private ImageFilter mImageFilter = null;
    private boolean mShowTitle = true;
    private boolean mSetBorder = false;
    protected final Paint mPaint = new Paint();
    protected boolean mIsSelected = false;
    protected boolean mNextIsSelected = false;
    private ImageSmallFilter mPreviousImageSmallFilter = null;

    // TODO: move this to xml.
    protected final int mMargin = 12;
    protected final int mTextMargin = 8;
    protected final int mBackgroundColor = Color.argb(255, 30, 32, 40);
    protected final int mSelectedBackgroundColor = Color.WHITE;
    protected final int mTextColor = Color.WHITE;

    public ImageSmallFilter(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnClickListener(this);
    }

    public ImageSmallFilter(Context context) {
        super(context);
        setOnClickListener(this);
    }

    public void setImageFilter(ImageFilter filter) {
        mImageFilter = filter;
        mImagePreset = new ImagePreset();
        mImagePreset.setName(filter.getName());
        mImagePreset.add(mImageFilter);
    }

    public void setPreviousImageSmallFilter(ImageSmallFilter previous) {
        mPreviousImageSmallFilter = previous;
    }

    @Override
    public void setSelected(boolean value) {
        if (mIsSelected != value) {
            invalidate();
            if (mPreviousImageSmallFilter != null) {
                mPreviousImageSmallFilter.setNextSelected(value);
            }
        }
        mIsSelected = value;
    }

    public void setNextSelected(boolean value) {
        if (mNextIsSelected != value) {
            invalidate();
        }
        mNextIsSelected = value;
    }

    public void setBorder(boolean value) {
        mSetBorder = value;
    }

    public void setController(FilterShowActivity activity) {
        mController = activity;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        int h = mTextSize + mTextPadding;
        setMeasuredDimension(parentHeight - h, parentHeight);
    }

    @Override
    public void onClick(View v) {
        if (mController != null) {
            if (mImageFilter != null) {
                mController.useImageFilter(this, mImageFilter, mSetBorder);
            } else if (mImagePreset != null) {
                mController.useImagePreset(this, mImagePreset);
            }
        }
    }

    @Override
    public void updateImage() {
        // We don't want to warn listeners here that the image size has changed, because
        // we'll be working with the small image...
        mForegroundImage = getOriginalFrontBitmap();
    }

    @Override
    protected Bitmap getOriginalFrontBitmap() {
        if (mImageLoader == null) {
            return null;
        }
        return mImageLoader.getOriginalBitmapSmall();
    }

    public void setShowTitle(boolean value) {
        mShowTitle = value;
        invalidate();
    }

    @Override
    public boolean showTitle() {
        return mShowTitle;
    }

    @Override
    public boolean showControls() {
        return false;
    }

    @Override
    public boolean showHires() {
        return false;
    }

    @Override
    public void onDraw(Canvas canvas) {
        getFilteredImage();
        canvas.drawColor(mBackgroundColor);
        Rect d = new Rect(0, mMargin, getWidth() - mMargin, getWidth());
        float textWidth = mPaint.measureText(getImagePreset().name());
        int h = mTextSize + 2 * mTextPadding;
        int x = (int) ((getWidth() - textWidth) / 2);
        int y = getHeight();
        if (mIsSelected) {
            mPaint.setColor(mSelectedBackgroundColor);
            canvas.drawRect(0, 0, getWidth(), getWidth() + mMargin, mPaint);
        }
        if (mNextIsSelected) {
            mPaint.setColor(mSelectedBackgroundColor);
            canvas.drawRect(getWidth() - mMargin, 0, getWidth(), getWidth() + mMargin, mPaint);
        }
        drawImage(canvas, mFilteredImage, d);
        mPaint.setTextSize(mTextSize);
        mPaint.setColor(mTextColor);
        canvas.drawText(getImagePreset().name(), x, y - mTextMargin, mPaint);
    }

    public void drawImage(Canvas canvas, Bitmap image, Rect d) {
        if (image != null) {
            int iw = image.getWidth();
            int ih = image.getHeight();
            int iy = (int) ((ih - iw) / 2.0f);
            int ix = 0;
            if (iw > ih) {
                iy = 0;
                ix = (int) ((iw - ih) / 2.0f);
            }
            Rect s = new Rect(ix, iy, ix + iw, iy + iw);
            canvas.drawBitmap(image, s, d, mPaint);
        }
    }

}
