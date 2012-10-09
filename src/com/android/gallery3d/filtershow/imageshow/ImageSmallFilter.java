
package com.android.gallery3d.filtershow.imageshow;

import android.content.Context;
import android.graphics.Bitmap;
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
        mImagePreset.add(mImageFilter);
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
        setMeasuredDimension(parentHeight, parentHeight);
    }

    @Override
    public void onClick(View v) {
        if (mController != null) {
            if (mImageFilter != null) {
                mController.useImageFilter(mImageFilter, mSetBorder);
            } else if (mImagePreset != null) {
                mController.useImagePreset(mImagePreset);
            }
        }
    }

    @Override
    public Bitmap getOriginalFrontBitmap() {
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
}
