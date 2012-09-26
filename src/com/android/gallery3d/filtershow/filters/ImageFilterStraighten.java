
package com.android.gallery3d.filtershow.filters;

import java.nio.ByteBuffer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

public class ImageFilterStraighten extends ImageFilter {
    private Bitmap.Config mConfig = Bitmap.Config.ARGB_8888;
    private float mRotation;
    private float mZoomFactor;

    public String name() {
        return "Straighten";
    }

    public void setRotation(float rotation) {
        mRotation = rotation;
    }

    public void setRotationZoomFactor(float zoomFactor) {
        mZoomFactor = zoomFactor;
    }

    public ImageFilterStraighten(float rotation, float zoomFactor) {
        mRotation = rotation;
        mZoomFactor = zoomFactor;
    }

    public ImageFilter copy() {
        return new ImageFilterStraighten(mRotation, mZoomFactor);
    }

    public void apply(Bitmap bitmap) {
        // TODO: implement bilinear or bicubic here... for now, just use
        // canvas to do a simple implementation...
        // TODO: and be more memory efficient! (do it in native?)

        Bitmap temp = bitmap.copy(mConfig, true);
        Canvas canvas = new Canvas(temp);
        canvas.save();
        Rect bounds = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        float w = temp.getWidth();
        float h = temp.getHeight();
        float mw = temp.getWidth() / 2.0f;
        float mh = temp.getHeight() / 2.0f;

        canvas.scale(mZoomFactor, mZoomFactor, mw, mh);
        canvas.rotate(mRotation, mw, mh);
        canvas.drawBitmap(bitmap, bounds, bounds, new Paint());
        canvas.restore();

        int[] pixels = new int[(int) (w * h)];
        temp.getPixels(pixels, 0, (int) w, 0, 0, (int) w, (int) h);
        bitmap.setPixels(pixels, 0, (int) w, 0, 0, (int) w, (int) h);
        temp.recycle();
        temp = null;
        pixels = null;
    }

}
