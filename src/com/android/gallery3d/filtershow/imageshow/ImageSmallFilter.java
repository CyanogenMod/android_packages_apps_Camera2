
package com.android.gallery3d.filtershow.imageshow;

import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.presets.ImagePreset;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.MeasureSpec;

public class ImageSmallFilter extends ImageShow implements View.OnClickListener {

    private FilterShowActivity mController = null;
    private ImageFilter mImageFilter = null;

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

    public void setController(FilterShowActivity activity) {
        mController = activity;
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(parentHeight, parentHeight);
    }

    /*
     * protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
     * setMeasuredDimension(256, 256); }
     */

    public void onClick(View v) {
        if (mController != null) {
            if (mImageFilter != null) {
                mController.useImageFilter(mImageFilter);
            } else if (mImagePreset != null) {
                mController.useImagePreset(mImagePreset);
            }
        }
    }

    public Bitmap getOriginalFrontBitmap() {
        if (mImageLoader == null) {
            return null;
        }
        return mImageLoader.getOriginalBitmapSmall();
    }

    public boolean showTitle() {
        return true;
    }

    public boolean showControls() {
        return false;
    }

    public boolean showHires() {
        return false;
    }
}
