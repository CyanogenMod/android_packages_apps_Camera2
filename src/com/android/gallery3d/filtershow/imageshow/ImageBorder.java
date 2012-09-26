
package com.android.gallery3d.filtershow.imageshow;

import com.android.gallery3d.filtershow.presets.ImagePreset;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.NinePatchDrawable;
import android.util.AttributeSet;

public class ImageBorder extends ImageShow {
    Paint gPaint = new Paint();
    private ImageShow mMasterImageShow = null;

    public ImageBorder(Context context) {
        super(context);
    }

    public ImageBorder(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setMaster(ImageShow master) {
        mMasterImageShow = master;
    }

    public ImagePreset getImagePreset() {
        return mMasterImageShow.getImagePreset();
    }

    public void setImagePreset(ImagePreset preset, boolean addToHistory) {
        mMasterImageShow.setImagePreset(preset, addToHistory);
    }

    public boolean showTitle() {
        return false;
    }

    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        /*
         * gPaint.setAntiAlias(true); gPaint.setFilterBitmap(true);
         * gPaint.setDither(true); // Bitmap bmp =
         * BitmapFactory.decodeResource(getResources(), //
         * R.drawable.border_scratch); // canvas.drawBitmap(bmp, new Rect(0, 0,
         * bmp.getWidth(), // bmp.getHeight()), mImageBounds, new Paint()); if
         * (mImageBounds != null) { NinePatchDrawable npd = (NinePatchDrawable)
         * getContext() .getResources().getDrawable(R.drawable.border_scratch2);
         * npd.setBounds(mImageBounds); npd.draw(canvas); }
         */
    }
}
