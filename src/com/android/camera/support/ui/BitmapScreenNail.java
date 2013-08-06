package com.android.camera.support.ui;

import android.graphics.Bitmap;
import android.graphics.RectF;

import com.android.camera.support.glrenderer.BitmapTexture;
import com.android.camera.support.glrenderer.GLCanvas;

public class BitmapScreenNail implements ScreenNail {
    private final BitmapTexture mBitmapTexture;

    public BitmapScreenNail(Bitmap bitmap) {
        mBitmapTexture = new BitmapTexture(bitmap);
    }

    @Override
    public int getWidth() {
        return mBitmapTexture.getWidth();
    }

    @Override
    public int getHeight() {
        return mBitmapTexture.getHeight();
    }

    @Override
    public void draw(GLCanvas canvas, int x, int y, int width, int height) {
        mBitmapTexture.draw(canvas, x, y, width, height);
    }

    @Override
    public void noDraw() {
        // do nothing
    }

    @Override
    public void recycle() {
        mBitmapTexture.recycle();
    }

    @Override
    public void draw(GLCanvas canvas, RectF source, RectF dest) {
        canvas.drawTexture(mBitmapTexture, source, dest);
    }
}
