/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.filtershow.cache;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.exif.ExifTag;
import com.android.gallery3d.exif.ExifInterface;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.HistoryAdapter;
import com.android.gallery3d.filtershow.filters.FiltersManager;
import com.android.gallery3d.filtershow.imageshow.ImageShow;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.presets.ImagePreset;
import com.android.gallery3d.filtershow.tools.BitmapTask;
import com.android.gallery3d.filtershow.tools.SaveCopyTask;
import com.android.gallery3d.util.InterruptableOutputStream;
import com.android.gallery3d.util.XmpUtilHelper;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;


// TODO: this class has waaaay to much bitmap copying.  Cleanup.
public class ImageLoader {

    private static final String LOGTAG = "ImageLoader";
    private final Vector<ImageShow> mListeners = new Vector<ImageShow>();
    private Bitmap mOriginalBitmapSmall = null;
    private Bitmap mOriginalBitmapLarge = null;
    private Bitmap mOriginalBitmapHighres = null;
    private Bitmap mBackgroundBitmap = null;

    private final ZoomCache mZoomCache = new ZoomCache();

    private int mOrientation = 0;
    private HistoryAdapter mAdapter = null;

    private FilterShowActivity mActivity = null;

    public static final String JPEG_MIME_TYPE = "image/jpeg";

    public static final String DEFAULT_SAVE_DIRECTORY = "EditedOnlinePhotos";
    public static final int DEFAULT_COMPRESS_QUALITY = 95;

    public static final int ORI_NORMAL = ExifInterface.Orientation.TOP_LEFT;
    public static final int ORI_ROTATE_90 = ExifInterface.Orientation.RIGHT_TOP;
    public static final int ORI_ROTATE_180 = ExifInterface.Orientation.BOTTOM_LEFT;
    public static final int ORI_ROTATE_270 = ExifInterface.Orientation.RIGHT_BOTTOM;
    public static final int ORI_FLIP_HOR = ExifInterface.Orientation.TOP_RIGHT;
    public static final int ORI_FLIP_VERT = ExifInterface.Orientation.BOTTOM_RIGHT;
    public static final int ORI_TRANSPOSE = ExifInterface.Orientation.LEFT_TOP;
    public static final int ORI_TRANSVERSE = ExifInterface.Orientation.LEFT_BOTTOM;

    private static final int BITMAP_LOAD_BACKOUT_ATTEMPTS = 5;
    private Context mContext = null;
    private Uri mUri = null;

    private Rect mOriginalBounds = null;
    private static int mZoomOrientation = ORI_NORMAL;

    static final int MAX_BITMAP_DIM = 900;

    private ReentrantLock mLoadingLock = new ReentrantLock();

    public ImageLoader(FilterShowActivity activity, Context context) {
        mActivity = activity;
        mContext = context;
    }

    public static int getZoomOrientation() {
        return mZoomOrientation;
    }

    public FilterShowActivity getActivity() {
        return mActivity;
    }

    public boolean loadBitmap(Uri uri, int size) {
        mLoadingLock.lock();
        mUri = uri;
        mOrientation = getOrientation(mContext, uri);
        mOriginalBitmapSmall = loadScaledBitmap(uri, 160);
        if (mOriginalBitmapSmall == null) {
            // Couldn't read the bitmap, let's exit
            mLoadingLock.unlock();
            return false;
        }
        mOriginalBitmapLarge = loadScaledBitmap(uri, size);
        if (mOriginalBitmapLarge == null) {
            mLoadingLock.unlock();
            return false;
        }
        if (MasterImage.getImage().supportsHighRes()) {
            int highresPreviewSize = mOriginalBitmapLarge.getWidth() * 2;
            if (highresPreviewSize > mOriginalBounds.width()) {
                highresPreviewSize = mOriginalBounds.width();
            }
            mOriginalBitmapHighres = loadScaledBitmap(uri, highresPreviewSize, false);
        }
        updateBitmaps();
        mLoadingLock.unlock();
        return true;
    }

    public Uri getUri() {
        return mUri;
    }

    public Rect getOriginalBounds() {
        return mOriginalBounds;
    }

    public static int getOrientation(Context context, Uri uri) {
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            String mimeType = context.getContentResolver().getType(uri);
            if (mimeType != ImageLoader.JPEG_MIME_TYPE) {
                return -1;
            }
            String path = uri.getPath();
            int orientation = -1;
            InputStream is = null;
            ExifInterface exif = new ExifInterface();
            try {
                exif.readExif(path);
                orientation = ExifInterface.getRotationForOrientationValue(
                        exif.getTagIntValue(ExifInterface.TAG_ORIENTATION).shortValue());
            } catch (IOException e) {
                Log.w(LOGTAG, "Failed to read EXIF orientation", e);
            }
            return orientation;
        }
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[] {
                        MediaStore.Images.ImageColumns.ORIENTATION
                    },
                    null, null, null);
            if (cursor.moveToNext()) {
                int ori = cursor.getInt(0);

                switch (ori) {
                    case 0:
                        return ORI_NORMAL;
                    case 90:
                        return ORI_ROTATE_90;
                    case 270:
                        return ORI_ROTATE_270;
                    case 180:
                        return ORI_ROTATE_180;
                    default:
                        return -1;
                }
            } else {
                return -1;
            }
        } catch (SQLiteException e) {
            return -1;
        } catch (IllegalArgumentException e) {
            return -1;
        } finally {
            Utils.closeSilently(cursor);
        }
    }

    private void updateBitmaps() {
        if (mOrientation > 1) {
            mOriginalBitmapSmall = rotateToPortrait(mOriginalBitmapSmall, mOrientation);
            mOriginalBitmapLarge = rotateToPortrait(mOriginalBitmapLarge, mOrientation);
            if (mOriginalBitmapHighres != null) {
                mOriginalBitmapHighres = rotateToPortrait(mOriginalBitmapHighres, mOrientation);
            }
        }
        mZoomOrientation = mOrientation;
        warnListeners();
    }

    public Bitmap decodeImage(int id, BitmapFactory.Options options) {
        return BitmapFactory.decodeResource(mContext.getResources(), id, options);
    }

    public static Bitmap rotateToPortrait(Bitmap bitmap, int ori) {
        Matrix matrix = new Matrix();
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (ori == ORI_ROTATE_90 ||
                ori == ORI_ROTATE_270 ||
                ori == ORI_TRANSPOSE ||
                ori == ORI_TRANSVERSE) {
            int tmp = w;
            w = h;
            h = tmp;
        }
        switch (ori) {
            case ORI_ROTATE_90:
                matrix.setRotate(90, w / 2f, h / 2f);
                break;
            case ORI_ROTATE_180:
                matrix.setRotate(180, w / 2f, h / 2f);
                break;
            case ORI_ROTATE_270:
                matrix.setRotate(270, w / 2f, h / 2f);
                break;
            case ORI_FLIP_HOR:
                matrix.preScale(-1, 1);
                break;
            case ORI_FLIP_VERT:
                matrix.preScale(1, -1);
                break;
            case ORI_TRANSPOSE:
                matrix.setRotate(90, w / 2f, h / 2f);
                matrix.preScale(1, -1);
                break;
            case ORI_TRANSVERSE:
                matrix.setRotate(270, w / 2f, h / 2f);
                matrix.preScale(1, -1);
                break;
            case ORI_NORMAL:
            default:
                return bitmap;
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                bitmap.getHeight(), matrix, true);
    }

    private Bitmap loadRegionBitmap(Uri uri, BitmapFactory.Options options, Rect bounds) {
        InputStream is = null;
        try {
            is = mContext.getContentResolver().openInputStream(uri);
            BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is, false);
            Rect r = new Rect(0, 0, decoder.getWidth(), decoder.getHeight());
            // return null if bounds are not entirely within the bitmap
            if (!r.contains(bounds)) {
                return null;
            }
            return decoder.decodeRegion(bounds, options);
        } catch (FileNotFoundException e) {
            Log.e(LOGTAG, "FileNotFoundException: " + uri);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Utils.closeSilently(is);
        }
        return null;
    }

    private Bitmap loadScaledBitmap(Uri uri, int size) {
        return loadScaledBitmap(uri, size, true);
    }

    private Bitmap loadScaledBitmap(Uri uri, int size, boolean enforceSize) {
        InputStream is = null;
        try {
            is = mContext.getContentResolver().openInputStream(uri);
            Log.v(LOGTAG, "loading uri " + uri.getPath() + " input stream: "
                    + is);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, o);

            int width_tmp = o.outWidth;
            int height_tmp = o.outHeight;

            mOriginalBounds = new Rect(0, 0, width_tmp, height_tmp);

            int scale = 1;
            while (true) {
                if (width_tmp <= 2 || height_tmp <= 2) {
                    break;
                }
                if (!enforceSize
                        || (width_tmp <= MAX_BITMAP_DIM
                        && height_tmp <= MAX_BITMAP_DIM)) {
                    if (width_tmp / 2 < size || height_tmp / 2 < size) {
                        break;
                    }
                }
                width_tmp /= 2;
                height_tmp /= 2;
                scale *= 2;
            }

            // decode with inSampleSize
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = scale;
            o2.inMutable = true;

            Utils.closeSilently(is);
            is = mContext.getContentResolver().openInputStream(uri);
            return BitmapFactory.decodeStream(is, null, o2);
        } catch (FileNotFoundException e) {
            Log.e(LOGTAG, "FileNotFoundException: " + uri);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Utils.closeSilently(is);
        }
        return null;
    }

    public Bitmap getBackgroundBitmap(Resources resources) {
        if (mBackgroundBitmap == null) {
            mBackgroundBitmap = BitmapFactory.decodeResource(resources,
                    R.drawable.filtershow_background);
        }
        return mBackgroundBitmap;

    }

    public Bitmap getOriginalBitmapSmall() {
        return mOriginalBitmapSmall;
    }

    public Bitmap getOriginalBitmapLarge() {
        return mOriginalBitmapLarge;
    }

    public Bitmap getOriginalBitmapHighres() {
        return mOriginalBitmapHighres;
    }

    public void addListener(ImageShow imageShow) {
        mLoadingLock.lock();
        if (!mListeners.contains(imageShow)) {
            mListeners.add(imageShow);
        }
        mLoadingLock.unlock();
    }

    private void warnListeners() {
        mActivity.runOnUiThread(mWarnListenersRunnable);
    }

    private Runnable mWarnListenersRunnable = new Runnable() {

        @Override
        public void run() {
            for (int i = 0; i < mListeners.size(); i++) {
                ImageShow imageShow = mListeners.elementAt(i);
                imageShow.imageLoaded();
            }
        }
    };

    public Bitmap getScaleOneImageForPreset(Rect bounds, Rect destination) {
        mLoadingLock.lock();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        if (destination != null) {
            if (bounds.width() > destination.width()) {
                int sampleSize = 1;
                int w = bounds.width();
                while (w > destination.width()) {
                    sampleSize *= 2;
                    w /= sampleSize;
                }
                options.inSampleSize = sampleSize;
            }
        }
        Bitmap bmp = loadRegionBitmap(mUri, options, bounds);
        mLoadingLock.unlock();
        return bmp;
    }

    public void saveImage(ImagePreset preset, final FilterShowActivity filterShowActivity,
            File destination) {
        new SaveCopyTask(mContext, mUri, destination, new SaveCopyTask.Callback() {

            @Override
            public void onComplete(Uri result) {
                filterShowActivity.completeSaveImage(result);
            }

        }).execute(preset);
    }

    public static Bitmap loadMutableBitmap(Context context, Uri sourceUri) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        return loadMutableBitmap(context, sourceUri, options);
    }

    public static Bitmap loadMutableBitmap(Context context, Uri sourceUri,
            BitmapFactory.Options options) {
        // TODO: on <3.x we need a copy of the bitmap (inMutable doesn't
        // exist)
        options.inMutable = true;

        Bitmap bitmap = decodeUriWithBackouts(context, sourceUri, options);
        if (bitmap == null) {
            return null;
        }
        int orientation = ImageLoader.getOrientation(context, sourceUri);
        bitmap = ImageLoader.rotateToPortrait(bitmap, orientation);
        return bitmap;
    }

    public static Bitmap decodeUriWithBackouts(Context context, Uri sourceUri,
            BitmapFactory.Options options) {
        boolean noBitmap = true;
        int num_tries = 0;
        InputStream is = getInputStream(context, sourceUri);

        if (options.inSampleSize < 1) {
            options.inSampleSize = 1;
        }
        // Stopgap fix for low-memory devices.
        Bitmap bmap = null;
        while (noBitmap) {
            if (is == null) {
                return null;
            }
            try {
                // Try to decode, downsample if low-memory.
                bmap = BitmapFactory.decodeStream(is, null, options);
                noBitmap = false;
            } catch (java.lang.OutOfMemoryError e) {
                // Try 5 times before failing for good.
                if (++num_tries >= BITMAP_LOAD_BACKOUT_ATTEMPTS) {
                    throw e;
                }
                is = null;
                bmap = null;
                System.gc();
                is = getInputStream(context, sourceUri);
                options.inSampleSize *= 2;
            }
        }
        Utils.closeSilently(is);
        return bmap;
    }

    private static InputStream getInputStream(Context context, Uri sourceUri) {
        InputStream is = null;
        try {
            is = context.getContentResolver().openInputStream(sourceUri);
        } catch (FileNotFoundException e) {
            Log.w(LOGTAG, "could not load bitmap ", e);
            Utils.closeSilently(is);
            is = null;
        }
        return is;
    }

    public static Bitmap decodeResourceWithBackouts(Resources res, BitmapFactory.Options options,
            int id) {
        boolean noBitmap = true;
        int num_tries = 0;
        if (options.inSampleSize < 1) {
            options.inSampleSize = 1;
        }
        // Stopgap fix for low-memory devices.
        Bitmap bmap = null;
        while (noBitmap) {
            try {
                // Try to decode, downsample if low-memory.
                bmap = BitmapFactory.decodeResource(
                        res, id, options);
                noBitmap = false;
            } catch (java.lang.OutOfMemoryError e) {
                // Try 5 times before failing for good.
                if (++num_tries >= BITMAP_LOAD_BACKOUT_ATTEMPTS) {
                    throw e;
                }
                bmap = null;
                System.gc();
                options.inSampleSize *= 2;
            }
        }
        return bmap;
    }

    public void returnFilteredResult(ImagePreset preset,
            final FilterShowActivity filterShowActivity) {
        BitmapTask.Callbacks<ImagePreset> cb = new BitmapTask.Callbacks<ImagePreset>() {

            @Override
            public void onComplete(Bitmap result) {
                filterShowActivity.onFilteredResult(result);
            }

            @Override
            public void onCancel() {
            }

            @Override
            public Bitmap onExecute(ImagePreset param) {
                if (param == null || mUri == null) {
                    return null;
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                boolean noBitmap = true;
                int num_tries = 0;
                if (options.inSampleSize < 1) {
                    options.inSampleSize = 1;
                }
                Bitmap bitmap = null;
                // Stopgap fix for low-memory devices.
                while (noBitmap) {
                    try {
                        // Try to do bitmap operations, downsample if low-memory
                        bitmap = loadMutableBitmap(mContext, mUri, options);
                        if (bitmap == null) {
                            Log.w(LOGTAG, "Failed to save image!");
                            return null;
                        }
                        CachingPipeline pipeline = new CachingPipeline(
                                FiltersManager.getManager(), "Saving");
                        bitmap = pipeline.renderFinalImage(bitmap, param);
                        noBitmap = false;
                    } catch (java.lang.OutOfMemoryError e) {
                        // Try 5 times before failing for good.
                        if (++num_tries >= 5) {
                            throw e;
                        }
                        bitmap = null;
                        System.gc();
                        options.inSampleSize *= 2;
                    }
                }
                return bitmap;
            }
        };

        (new BitmapTask<ImagePreset>(cb)).execute(preset);
    }

    private String getFileExtension(String requestFormat) {
        String outputFormat = (requestFormat == null)
                ? "jpg"
                : requestFormat;
        outputFormat = outputFormat.toLowerCase();
        return (outputFormat.equals("png") || outputFormat.equals("gif"))
                ? "png" // We don't support gif compression.
                : "jpg";
    }

    private CompressFormat convertExtensionToCompressFormat(String extension) {
        return extension.equals("png") ? CompressFormat.PNG : CompressFormat.JPEG;
    }

    public void saveToUri(Bitmap bmap, Uri uri, final String outputFormat,
            final FilterShowActivity filterShowActivity) {

        OutputStream out = null;
        try {
            out = filterShowActivity.getContentResolver().openOutputStream(uri);
        } catch (FileNotFoundException e) {
            Log.w(LOGTAG, "cannot write output", e);
            out = null;
        } finally {
            if (bmap == null || out == null) {
                return;
            }
        }

        final InterruptableOutputStream ios = new InterruptableOutputStream(out);

        BitmapTask.Callbacks<Bitmap> cb = new BitmapTask.Callbacks<Bitmap>() {

            @Override
            public void onComplete(Bitmap result) {
                filterShowActivity.done();
            }

            @Override
            public void onCancel() {
                ios.interrupt();
            }

            @Override
            public Bitmap onExecute(Bitmap param) {
                CompressFormat cf = convertExtensionToCompressFormat(getFileExtension(outputFormat));
                param.compress(cf, DEFAULT_COMPRESS_QUALITY, ios);
                Utils.closeSilently(ios);
                return null;
            }
        };

        (new BitmapTask<Bitmap>(cb)).execute(bmap);
    }

    public void setAdapter(HistoryAdapter adapter) {
        mAdapter = adapter;
    }

    public HistoryAdapter getHistory() {
        return mAdapter;
    }

    public XMPMeta getXmpObject() {
        try {
            InputStream is = mContext.getContentResolver().openInputStream(getUri());
            return XmpUtilHelper.extractXMPMeta(is);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    /**
     * Determine if this is a light cycle 360 image
     *
     * @return true if it is a light Cycle image that is full 360
     */
    public boolean queryLightCycle360() {
        InputStream is = null;
        try {
            is = mContext.getContentResolver().openInputStream(getUri());
            XMPMeta meta = XmpUtilHelper.extractXMPMeta(is);
            if (meta == null) {
                return false;
            }
            String name = meta.getPacketHeader();
            String namespace = "http://ns.google.com/photos/1.0/panorama/";
            String cropWidthName = "GPano:CroppedAreaImageWidthPixels";
            String fullWidthName = "GPano:FullPanoWidthPixels";

            if (!meta.doesPropertyExist(namespace, cropWidthName)) {
                return false;
            }
            if (!meta.doesPropertyExist(namespace, fullWidthName)) {
                return false;
            }

            Integer cropValue = meta.getPropertyInteger(namespace, cropWidthName);
            Integer fullValue = meta.getPropertyInteger(namespace, fullWidthName);

            // Definition of a 360:
            // GFullPanoWidthPixels == CroppedAreaImageWidthPixels
            if (cropValue != null && fullValue != null) {
                return cropValue.equals(fullValue);
            }

            return false;
        } catch (FileNotFoundException e) {
            return false;
        } catch (XMPException e) {
            return false;
        } finally {
            Utils.closeSilently(is);
        }
    }
}
