
package com.android.gallery3d.filtershow.ui;

import com.android.gallery3d.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageButton;

public class ImageButtonTitle extends ImageButton {
    private static final String LOGTAG = "ImageButtonTitle";
    private String mText = null;
    private static int mTextSize = 24;
    private static int mTextPadding = 20;
    private static Paint gPaint = new Paint();

    public ImageButtonTitle(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.ImageButtonTitle);

        mText = a.getString(R.styleable.ImageButtonTitle_android_text);
    }

    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mText != null) {
            gPaint.setARGB(255, 255, 255, 255);
            gPaint.setTextSize(mTextSize);
            float textWidth = gPaint.measureText(mText);
            int x = (int) ((getWidth() - textWidth) / 2);
            int y = getHeight() - mTextPadding;

            canvas.drawText(mText, x, y, gPaint);
        }
    }

}
