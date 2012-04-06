// Copyright 2012 Google Inc. All Rights Reserved.

package com.android.gallery3d.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataSourceType;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.util.ThreadPool.JobContext;

public class AlbumLabelMaker {
    private static final int FONT_COLOR_TITLE = Color.WHITE;
    private static final int FONT_COLOR_COUNT = 0x80FFFFFF;  // 50% white

    // We keep a border around the album label to prevent aliasing
    private static final int BORDER_SIZE = 1;
    private static final int BACKGROUND_COLOR = 0x60000000; // 36% Dark

    private final AlbumSetSlotRenderer.LabelSpec mSpec;
    private final TextPaint mTitlePaint;
    private final TextPaint mCountPaint;
    private final Context mContext;

    private int mLabelWidth;
    private BitmapPool mBitmapPool;

    private final LazyLoadedBitmap mLocalSetIcon;
    private final LazyLoadedBitmap mPicasaIcon;
    private final LazyLoadedBitmap mCameraIcon;
    private final LazyLoadedBitmap mMtpIcon;

    public AlbumLabelMaker(Context context, AlbumSetSlotRenderer.LabelSpec spec) {
        mContext = context;
        mSpec = spec;
        mTitlePaint = getTextPaint(spec.titleFontSize, FONT_COLOR_TITLE, false);
        mCountPaint = getTextPaint(spec.countFontSize, FONT_COLOR_COUNT, true);

        mLocalSetIcon = new LazyLoadedBitmap(R.drawable.frame_overlay_gallery_folder);
        mPicasaIcon = new LazyLoadedBitmap(R.drawable.frame_overlay_gallery_picasa);
        mCameraIcon = new LazyLoadedBitmap(R.drawable.frame_overlay_gallery_camera);
        mMtpIcon = new LazyLoadedBitmap(R.drawable.frame_overlay_gallery_ptp);
    }

    public static int getBorderSize() {
        return BORDER_SIZE;
    }

    private Bitmap getOverlayAlbumIcon(int sourceType) {
        switch (sourceType) {
            case DataSourceType.TYPE_CAMERA:
                return mCameraIcon.get();
            case DataSourceType.TYPE_LOCAL:
                return mLocalSetIcon.get();
            case DataSourceType.TYPE_MTP:
                return mMtpIcon.get();
            case DataSourceType.TYPE_PICASA:
                return mPicasaIcon.get();
        }
        return null;
    }

    private static TextPaint getTextPaint(int textSize, int color, boolean isBold) {
        TextPaint paint = new TextPaint();
        paint.setTextSize(textSize);
        paint.setAntiAlias(true);
        paint.setColor(color);
        paint.setShadowLayer(2f, 0f, 0f, Color.BLACK);
        if (isBold) {
            paint.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        }
        return paint;
    }

    private class LazyLoadedBitmap {
        private Bitmap mBitmap;
        private int mResId;

        public LazyLoadedBitmap(int resId) {
            mResId = resId;
        }

        public synchronized Bitmap get() {
            if (mBitmap == null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                mBitmap = BitmapFactory.decodeResource(
                        mContext.getResources(), mResId, options);
            }
            return mBitmap;
        }
    }

    public synchronized void setLabelWidth(int width) {
        if (mLabelWidth == width) return;
        mLabelWidth = width;
        int borders = 2 * BORDER_SIZE;
        mBitmapPool = new BitmapPool(
                width + borders, mSpec.labelBackgroundHeight + borders);
    }

    public ThreadPool.Job<Bitmap> requestLabel(
            String title, String count, int sourceType) {
        return new AlbumLabelJob(null, title, count, sourceType);
    }

    public ThreadPool.Job<Bitmap> requestLabel(
            MediaSet album, int sourceType) {
        return new AlbumLabelJob(album, null, null, sourceType);
    }

    private static void drawText(Canvas canvas,
            int x, int y, String text, int lengthLimit, TextPaint p) {
        // The TextPaint cannot be used concurrently
        synchronized (p) {
            text = TextUtils.ellipsize(
                    text, p, lengthLimit, TextUtils.TruncateAt.END).toString();
            canvas.drawText(text, x, y - p.getFontMetricsInt().ascent, p);
        }
    }

    private class AlbumLabelJob implements ThreadPool.Job<Bitmap> {
        private final MediaSet mAlbum;
        private final String mTitle;
        private final String mCount;
        private final int mSourceType;

        public AlbumLabelJob(MediaSet album,
                String title, String count, int sourceType) {
            mAlbum = album;
            mTitle = title;
            mCount = count;
            mSourceType = sourceType;
        }

        @Override
        public Bitmap run(JobContext jc) {
            MediaSet album = mAlbum;
            AlbumSetSlotRenderer.LabelSpec s = mSpec;

            String title = Utils.ensureNotNull(
                    (album == null) ? mTitle : album.getName());
            String count = album == null
                    ? Utils.ensureNotNull(mCount)
                    : String.valueOf(album.getTotalMediaItemCount());
            Bitmap icon = getOverlayAlbumIcon(mSourceType);

            Bitmap bitmap;
            int labelWidth;

            synchronized (this) {
                labelWidth = mLabelWidth;
                bitmap = mBitmapPool.getBitmap();
            }

            if (bitmap == null) {
                int borders = 2 * BORDER_SIZE;
                bitmap = Bitmap.createBitmap(labelWidth + borders,
                        s.labelBackgroundHeight + borders, Config.ARGB_8888);
            }

            Canvas canvas = new Canvas(bitmap);
            canvas.clipRect(BORDER_SIZE, BORDER_SIZE,
                    bitmap.getWidth() - BORDER_SIZE,
                    bitmap.getHeight() - BORDER_SIZE);
            canvas.drawColor(BACKGROUND_COLOR, PorterDuff.Mode.SRC);

            canvas.translate(BORDER_SIZE, BORDER_SIZE);

            // draw title
            if (jc.isCancelled()) return null;
            int x = s.leftMargin;
            int y = s.titleOffset;
            drawText(canvas, x, y, title, labelWidth - s.leftMargin, mTitlePaint);

            // draw the count
            if (jc.isCancelled()) return null;
            if (icon != null) x = s.iconSize;
            y += s.titleFontSize + s.countOffset;
            drawText(canvas, x, y, count,
                    labelWidth - s.leftMargin - s.iconSize, mCountPaint);

            // draw the icon
            if (icon != null) {
                if (jc.isCancelled()) return null;
                float scale = (float) s.iconSize / icon.getWidth();
                canvas.translate(0, bitmap.getHeight()
                        - Math.round(scale * icon.getHeight()));
                canvas.scale(scale, scale);
                canvas.drawBitmap(icon, 0, 0, null);
            }

            return bitmap;
        }
    }

    public void reycleLabel(Bitmap label) {
        mBitmapPool.recycle(label);
    }

    public void clearRecycledLabels() {
        mBitmapPool.clear();
    }
}
