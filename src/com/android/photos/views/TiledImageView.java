/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.photos.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.opengl.GLSurfaceView.Renderer;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import android.widget.FrameLayout;
import com.android.gallery3d.glrenderer.GLES20Canvas;
import com.android.photos.views.TiledImageRenderer.TileSource;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class TiledImageView extends FrameLayout implements OnScaleGestureListener {

    private BlockingGLTextureView mTextureView;
    private float mLastX, mLastY;

    private static class ImageRendererWrapper {
        // Guarded by locks
        float scale;
        int centerX, centerY;
        int rotation;
        TileSource source;

        // GL thread only
        TiledImageRenderer image;
    }

    // TODO: left/right paging
    private ImageRendererWrapper mRenderers[] = new ImageRendererWrapper[1];
    private ImageRendererWrapper mFocusedRenderer;

    // -------------------------
    // Guarded by mLock
    // -------------------------
    private Object mLock = new Object();
    private ScaleGestureDetector mScaleGestureDetector;

    public TiledImageView(Context context) {
        this(context, null);
    }

    public TiledImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTextureView = new BlockingGLTextureView(context);
        addView(mTextureView, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        mTextureView.setRenderer(new TileRenderer());
        setTileSource(new ColoredTiles());
        mScaleGestureDetector = new ScaleGestureDetector(context, this);
    }

    public void destroy() {
        mTextureView.destroy();
    }

    public void setTileSource(TileSource source) {
        synchronized (mLock) {
            for (int i = 0; i < mRenderers.length; i++) {
                ImageRendererWrapper renderer = mRenderers[i];
                if (renderer == null) {
                    renderer = mRenderers[i] = new ImageRendererWrapper();
                }
                renderer.source = source;
                renderer.centerX = renderer.source.getImageWidth() / 2;
                renderer.centerY = renderer.source.getImageHeight() / 2;
                renderer.rotation = 0;
                renderer.scale = 0;
                renderer.image = new TiledImageRenderer(this);
                updateScaleIfNecessaryLocked(renderer);
            }
        }
        mFocusedRenderer = mRenderers[0];
        invalidate();
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        // Don't need the lock because this will only fire inside of onTouchEvent
        mFocusedRenderer.scale *= detector.getScaleFactor();
        invalidate();
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        final boolean pointerUp = action == MotionEvent.ACTION_POINTER_UP;
        final int skipIndex = pointerUp ? event.getActionIndex() : -1;

        // Determine focal point
        float sumX = 0, sumY = 0;
        final int count = event.getPointerCount();
        for (int i = 0; i < count; i++) {
            if (skipIndex == i) continue;
            sumX += event.getX(i);
            sumY += event.getY(i);
        }
        final int div = pointerUp ? count - 1 : count;
        float x = sumX / div;
        float y = sumY / div;

        synchronized (mLock) {
            mScaleGestureDetector.onTouchEvent(event);
            switch (action) {
            case MotionEvent.ACTION_MOVE:
                mFocusedRenderer.centerX += (mLastX - x) / mFocusedRenderer.scale;
                mFocusedRenderer.centerY += (mLastY - y) / mFocusedRenderer.scale;
                invalidate();
                break;
            }
        }

        mLastX = x;
        mLastY = y;
        return true;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right,
            int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        synchronized (mLock) {
            for (ImageRendererWrapper renderer : mRenderers) {
                updateScaleIfNecessaryLocked(renderer);
            }
        }
    }

    private void updateScaleIfNecessaryLocked(ImageRendererWrapper renderer) {
        if (renderer.scale > 0 || getWidth() == 0) return;
        renderer.scale = Math.min(
                (float) getWidth() / (float) renderer.source.getImageWidth(),
                (float) getHeight() / (float) renderer.source.getImageHeight());
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        mTextureView.render();
        super.dispatchDraw(canvas);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        mTextureView.invalidate();
    }

    private class TileRenderer implements Renderer {

        private GLES20Canvas mCanvas;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            mCanvas = new GLES20Canvas();
            for (ImageRendererWrapper renderer : mRenderers) {
                renderer.image.setModel(renderer.source, renderer.rotation);
            }
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            mCanvas.setSize(width, height);
            for (ImageRendererWrapper renderer : mRenderers) {
                renderer.image.setViewSize(width, height);
            }
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            mCanvas.clearBuffer();
            synchronized (mLock) {
                for (ImageRendererWrapper renderer : mRenderers) {
                    renderer.image.setModel(renderer.source, renderer.rotation);
                    renderer.image.setPosition(renderer.centerX, renderer.centerY, renderer.scale);
                }
            }
            for (ImageRendererWrapper renderer : mRenderers) {
                renderer.image.draw(mCanvas);
            }
        }

    }

    private static class ColoredTiles implements TileSource {
        private static int[] COLORS = new int[] {
            Color.RED,
            Color.BLUE,
            Color.YELLOW,
            Color.GREEN,
            Color.CYAN,
            Color.MAGENTA,
            Color.WHITE,
        };

        private Paint mPaint = new Paint();
        private Canvas mCanvas = new Canvas();

        @Override
        public int getTileSize() {
            return 256;
        }

        @Override
        public int getImageWidth() {
            return 16384;
        }

        @Override
        public int getImageHeight() {
            return 8192;
        }

        @Override
        public Bitmap getTile(int level, int x, int y, Bitmap bitmap) {
            int tileSize = getTileSize();
            if (bitmap == null) {
                bitmap = Bitmap.createBitmap(tileSize, tileSize,
                        Bitmap.Config.ARGB_8888);
            }
            mCanvas.setBitmap(bitmap);
            mCanvas.drawColor(COLORS[level]);
            mPaint.setColor(Color.BLACK);
            mPaint.setTextSize(20);
            mPaint.setTextAlign(Align.CENTER);
            mCanvas.drawText(x + "x" + y, 128, 128, mPaint);
            tileSize <<= level;
            x /= tileSize;
            y /= tileSize;
            mCanvas.drawText(x + "x" + y + " @ " + level, 128, 30, mPaint);
            mCanvas.setBitmap(null);
            return bitmap;
        }
    }
}
