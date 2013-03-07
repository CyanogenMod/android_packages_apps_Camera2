package com.android.photos.shims;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.ui.BitmapLoader;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.ThreadPool;
import com.android.photos.data.GalleryBitmapPool;
import com.android.photos.drawables.AutoThumbnailDrawable;


public class BitmapJobDrawable extends Drawable implements Runnable {

    private ThumbnailLoader mLoader;
    private MediaItem mItem;
    private Bitmap mBitmap;
    private Paint mPaint = new Paint();
    private Matrix mDrawMatrix = new Matrix();

    public BitmapJobDrawable() {
    }

    public void setMediaItem(MediaItem item) {
        if (mLoader != null) {
            mLoader.cancelLoad();
        }
        mItem = item;
        if (mBitmap != null) {
            GalleryBitmapPool.getInstance().put(mBitmap);
            mBitmap = null;
        }
        // TODO: Figure out why ThumbnailLoader doesn't like to be re-used
        mLoader = new ThumbnailLoader(this);
        mLoader.startLoad();
        invalidateSelf();
    }

    @Override
    public void run() {
        Bitmap bitmap = mLoader.getBitmap();
        if (bitmap != null) {
            mBitmap = bitmap;
            updateDrawMatrix();
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        updateDrawMatrix();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (mBitmap != null) {
            canvas.save();
            canvas.clipRect(bounds);
            canvas.concat(mDrawMatrix);
            canvas.drawBitmap(mBitmap, 0, 0, mPaint);
            canvas.restore();
        } else {
            mPaint.setColor(0xFFCCCCCC);
            canvas.drawRect(bounds, mPaint);
        }
    }

    private void updateDrawMatrix() {
        Rect bounds = getBounds();
        if (mBitmap == null || bounds.isEmpty()) {
            mDrawMatrix.reset();
            return;
        }

        float scale;
        float dx = 0, dy = 0;

        int dwidth = mBitmap.getWidth();
        int dheight = mBitmap.getHeight();
        int vwidth = bounds.width();
        int vheight = bounds.height();

        // Calculates a matrix similar to ScaleType.CENTER_CROP
        if (dwidth * vheight > vwidth * dheight) {
            scale = (float) vheight / (float) dheight;
            dx = (vwidth - dwidth * scale) * 0.5f;
        } else {
            scale = (float) vwidth / (float) dwidth;
            dy = (vheight - dheight * scale) * 0.5f;
        }

        mDrawMatrix.setScale(scale, scale);
        mDrawMatrix.postTranslate((int) (dx + 0.5f), (int) (dy + 0.5f));
        invalidateSelf();
    }

    @Override
    public int getIntrinsicWidth() {
        return MediaItem.getTargetSize(MediaItem.TYPE_MICROTHUMBNAIL);
    }

    @Override
    public int getIntrinsicHeight() {
        return MediaItem.getTargetSize(MediaItem.TYPE_MICROTHUMBNAIL);
    }

    @Override
    public int getOpacity() {
        Bitmap bm = mBitmap;
        return (bm == null || bm.hasAlpha() || mPaint.getAlpha() < 255) ?
                PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE;
    }

    @Override
    public void setAlpha(int alpha) {
        int oldAlpha = mPaint.getAlpha();
        if (alpha != oldAlpha) {
            mPaint.setAlpha(alpha);
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mPaint.setColorFilter(cf);
        invalidateSelf();
    }

    private static class ThumbnailLoader extends BitmapLoader {
        private static final ThreadPool sThreadPool = new ThreadPool(0, 2);
        private BitmapJobDrawable mParent;

        public ThumbnailLoader(BitmapJobDrawable parent) {
            mParent = parent;
        }

        @Override
        protected Future<Bitmap> submitBitmapTask(FutureListener<Bitmap> l) {
            return sThreadPool.submit(
                    mParent.mItem.requestImage(MediaItem.TYPE_MICROTHUMBNAIL), this);
        }

        @Override
        protected void onLoadComplete(Bitmap bitmap) {
            mParent.scheduleSelf(mParent, 0);
        }
    }

}
