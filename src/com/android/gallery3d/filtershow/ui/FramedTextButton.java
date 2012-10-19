package com.android.gallery3d.filtershow.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.ImageButton;

import com.android.gallery3d.R;

public class FramedTextButton extends ImageButton {
    private static final String LOGTAG = "FramedTextButton";
    private String mText = null;
    private static int mTextSize = 24;
    private static int mTextPadding = 20;
    private static Paint gPaint = new Paint();
    private Context mContext = null;

    public static void setTextSize(int value) {
        mTextSize = value;
    }

    public static void setTextPadding(int value) {
        mTextPadding = value;
    }

    public void setText(String text) {
        mText = text;
        invalidate();
    }

    public void setTextFrom(int itemId) {
        switch (itemId) {
            case R.id.curve_menu_rgb: {
                setText(mContext.getString(R.string.curves_channel_rgb));
                break;
            }
            case R.id.curve_menu_red: {
                setText(mContext.getString(R.string.curves_channel_red));
                break;
            }
            case R.id.curve_menu_green: {
                setText(mContext.getString(R.string.curves_channel_green));
                break;
            }
            case R.id.curve_menu_blue: {
                setText(mContext.getString(R.string.curves_channel_blue));
                break;
            }
        }
        invalidate();
    }

    public FramedTextButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.ImageButtonTitle);

        mText = a.getString(R.styleable.ImageButtonTitle_android_text);
    }

    public String getText(){
        return mText;
    }

    @Override
    public void onDraw(Canvas canvas) {
        gPaint.setARGB(255, 255, 255, 255);
        gPaint.setStrokeWidth(2);
        gPaint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(mTextPadding, mTextPadding, getWidth() - mTextPadding,
                getHeight() - mTextPadding, gPaint);
        if (mText != null) {
            gPaint.setTextSize(mTextSize);
            float textWidth = gPaint.measureText(mText);
            Rect bounds = new Rect();
            gPaint.getTextBounds(mText, 0, mText.length(), bounds);
            int x = (int) ((getWidth() - textWidth) / 2);
            int y = (getHeight() + bounds.height()) / 2;

            canvas.drawText(mText, x, y, gPaint);
        }
    }

}
