package com.android.gallery3d.filtershow;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View.MeasureSpec;
import android.widget.LinearLayout;

import com.android.gallery3d.R;

public class CenteredLinearLayout extends LinearLayout {
    private final int mMaxWidth;

    public CenteredLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.CenteredLinearLayout);
        mMaxWidth = a.getDimensionPixelSize(R.styleable.CenteredLinearLayout_max_width, 0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        Resources r = getContext().getResources();
        float value = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, parentWidth,
                r.getDisplayMetrics());
        if (mMaxWidth > 0 && parentWidth > mMaxWidth) {
            int measureMode = MeasureSpec.getMode(widthMeasureSpec);
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(mMaxWidth, measureMode);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

}
