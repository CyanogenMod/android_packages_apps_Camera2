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

package com.android.gallery3d.filtershow.crop;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.exif.ExifInterface;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * This class contains static methods for loading a bitmap and
 * mantains no instance state.
 */
public abstract class CropLoader {
    public static final String LOGTAG = "CropLoader";
    public static final String JPEG_MIME_TYPE = "image/jpeg";
    public static final int ORI_NORMAL = ExifInterface.Orientation.TOP_LEFT;
    public static final int ORI_ROTATE_90 = ExifInterface.Orientation.RIGHT_TOP;
    public static final int ORI_ROTATE_180 = ExifInterface.Orientation.BOTTOM_LEFT;
    public static final int ORI_ROTATE_270 = ExifInterface.Orientation.RIGHT_BOTTOM;

    /**
     * Returns the orientation of image at the given URI as one of 0, 90, 180,
     * 270.
     *
     * @param uri URI of image to open.
     * @param context context whose ContentResolver to use.
     * @return the orientation of the image. Defaults to 0.
     */
    public static int getMetadataOrientation(Uri uri, Context context) {
        if (uri == null || context == null) {
            throw new IllegalArgumentException("bad argument to getScaledBitmap");
        }
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            String mimeType = context.getContentResolver().getType(uri);
            if (mimeType != JPEG_MIME_TYPE) {
                return 0;
            }
            String path = uri.getPath();
            int orientation = 0;
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
                        return 0;
                }
            }
        } catch (SQLiteException e) {
            return 0;
        } catch (IllegalArgumentException e) {
            return 0;
        } finally {
            Utils.closeSilently(cursor);
        }
        return 0;
    }

    /**
     * Gets a bitmap at a given URI that is downsampled so that both sides are
     * smaller than maxSideLength. The Bitmap's original dimensions are stored
     * in the rect originalBounds.
     *
     * @param uri URI of image to open.
     * @param context context whose ContentResolver to use.
     * @param maxSideLength max side length of returned bitmap.
     * @param originalBounds set to the actual bounds of the stored bitmap.
     * @return downsampled bitmap or null if this operation failed.
     */
    public static Bitmap getConstrainedBitmap(Uri uri, Context context, int maxSideLength,
            Rect originalBounds) {
        if (maxSideLength <= 0 || originalBounds == null || uri == null || context == null) {
            throw new IllegalArgumentException("bad argument to getScaledBitmap");
        }
        InputStream is = null;
        try {
            // Get width and height of stored bitmap
            is = context.getContentResolver().openInputStream(uri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            int w = options.outWidth;
            int h = options.outHeight;
            originalBounds.set(0, 0, w, h);

            // If bitmap cannot be decoded, return null
            if (w <= 0 || h <= 0) {
                return null;
            }

            options = new BitmapFactory.Options();

            // Find best downsampling size
            int imageSide = Math.max(w, h);
            if (imageSide > maxSideLength) {
                int shifts = 1 + Integer.numberOfLeadingZeros(maxSideLength)
                        - Integer.numberOfLeadingZeros(imageSide);
                options.inSampleSize = 1 << shifts;
            }

            // Make sure sample size is reasonable
            if (0 >= (int) (Math.min(w, h) / options.inSampleSize)) {
                return null;
            }

            // Decode actual bitmap.
            options.inMutable = true;
            is.close();
            is = context.getContentResolver().openInputStream(uri);
            return BitmapFactory.decodeStream(is, null, options);
        } catch (FileNotFoundException e) {
            Log.e(LOGTAG, "FileNotFoundException: " + uri, e);
        } catch (IOException e) {
            Log.e(LOGTAG, "IOException: " + uri, e);
        } finally {
            Utils.closeSilently(is);
        }
        return null;
    }

    /**
     * Gets a bitmap that has been downsampled using sampleSize.
     *
     * @param uri URI of image to open.
     * @param context context whose ContentResolver to use.
     * @param sampleSize downsampling amount.
     * @return downsampled bitmap.
     */
    public static Bitmap getBitmap(Uri uri, Context context, int sampleSize) {
        if (uri == null || context == null) {
            throw new IllegalArgumentException("bad argument to getScaledBitmap");
        }
        InputStream is = null;
        try {
            is = context.getContentResolver().openInputStream(uri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true;
            options.inSampleSize = sampleSize;
            return BitmapFactory.decodeStream(is, null, options);
        } catch (FileNotFoundException e) {
            Log.e(LOGTAG, "FileNotFoundException: " + uri, e);
        } finally {
            Utils.closeSilently(is);
        }
        return null;
    }

}
