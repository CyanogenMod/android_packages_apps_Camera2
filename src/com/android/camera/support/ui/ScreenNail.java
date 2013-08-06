package com.android.camera.support.ui;


import android.graphics.RectF;

import com.android.camera.support.glrenderer.GLCanvas;

public interface ScreenNail {
    public int getWidth();
    public int getHeight();
    public void draw(GLCanvas canvas, int x, int y, int width, int height);

    // We do not need to draw this ScreenNail in this frame.
    public void noDraw();

    // This ScreenNail will not be used anymore. Release related resources.
    public void recycle();

    // This is only used by TileImageView to back up the tiles not yet loaded.
    public void draw(GLCanvas canvas, RectF source, RectF dest);
}
