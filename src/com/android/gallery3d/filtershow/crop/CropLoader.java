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
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.exif.ExifInterface;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.text.SimpleDateFormat;

/**
 * This class contains static methods for loading a bitmap and
 * maintains no instance state.
 */
public abstract class CropLoader {
    public static final String LOGTAG = "CropLoader";
    public static final String JPEG_MIME_TYPE = "image/jpeg";

    private static final String TIME_STAMP_NAME = "'IMG'_yyyyMMdd_HHmmss";
    public static final String DEFAULT_SAVE_DIRECTORY = "EditedOnlinePhotos";

    /**
     * Returns the orientation of image at the given URI as one of 0, 90, 180,
     * 270.
     *
     * @param uri URI of image to open.
     * @param context context whose ContentResolver to use.
     * @return the orientation of the image. Defaults to 0.
     */
    public static int getMetadataRotation(Uri uri, Context context) {
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
                    new String[] { MediaStore.Images.ImageColumns.ORIENTATION },
                    null, null, null);
            if (cursor.moveToNext()) {
                int ori = cursor.getInt(0);
                return (ori < 0) ? 0 : ori;
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
            options.inSampleSize = 1;
            if (imageSide > maxSideLength) {
                int shifts = 1 + Integer.numberOfLeadingZeros(maxSideLength)
                        - Integer.numberOfLeadingZeros(imageSide);
                options.inSampleSize <<= shifts;
            }

            // Make sure sample size is reasonable
            if (options.inSampleSize <= 0 ||
                    0 >= (int) (Math.min(w, h) / options.inSampleSize)) {
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

    // TODO: Super gnarly (copied from SaveCopyTask.java), do cleanup.

    public static File getFinalSaveDirectory(Context context, Uri sourceUri) {
        File saveDirectory = getSaveDirectory(context, sourceUri);
        if ((saveDirectory == null) || !saveDirectory.canWrite()) {
            saveDirectory = new File(Environment.getExternalStorageDirectory(),
                    DEFAULT_SAVE_DIRECTORY);
        }
        // Create the directory if it doesn't exist
        if (!saveDirectory.exists())
            saveDirectory.mkdirs();
        return saveDirectory;
    }



    public static String getNewFileName(long time) {
        return new SimpleDateFormat(TIME_STAMP_NAME).format(new Date(time));
    }

    public static File getNewFile(Context context, Uri sourceUri, String filename) {
        File saveDirectory = getFinalSaveDirectory(context, sourceUri);
        return new File(saveDirectory, filename  + ".JPG");
    }

    private interface ContentResolverQueryCallback {

        void onCursorResult(Cursor cursor);
    }

    private static void querySource(Context context, Uri sourceUri, String[] projection,
            ContentResolverQueryCallback callback) {
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(sourceUri, projection, null, null,
                    null);
            if ((cursor != null) && cursor.moveToNext()) {
                callback.onCursorResult(cursor);
            }
        } catch (Exception e) {
            // Ignore error for lacking the data column from the source.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private static File getSaveDirectory(Context context, Uri sourceUri) {
        final File[] dir = new File[1];
        querySource(context, sourceUri, new String[] {
                ImageColumns.DATA }, new ContentResolverQueryCallback() {
                    @Override
                    public void onCursorResult(Cursor cursor) {
                        dir[0] = new File(cursor.getString(0)).getParentFile();
                    }
                });
        return dir[0];
    }

    public static Uri insertContent(Context context, Uri sourceUri, File file, String saveFileName,
            long time) {
        time /= 1000;

        final ContentValues values = new ContentValues();
        values.put(Images.Media.TITLE, saveFileName);
        values.put(Images.Media.DISPLAY_NAME, file.getName());
        values.put(Images.Media.MIME_TYPE, "image/jpeg");
        values.put(Images.Media.DATE_TAKEN, time);
        values.put(Images.Media.DATE_MODIFIED, time);
        values.put(Images.Media.DATE_ADDED, time);
        values.put(Images.Media.ORIENTATION, 0);
        values.put(Images.Media.DATA, file.getAbsolutePath());
        values.put(Images.Media.SIZE, file.length());

        final String[] projection = new String[] {
                ImageColumns.DATE_TAKEN,
                ImageColumns.LATITUDE, ImageColumns.LONGITUDE,
        };
        querySource(context, sourceUri, projection,
                new ContentResolverQueryCallback() {

                    @Override
                    public void onCursorResult(Cursor cursor) {
                        values.put(Images.Media.DATE_TAKEN, cursor.getLong(0));

                        double latitude = cursor.getDouble(1);
                        double longitude = cursor.getDouble(2);
                        // TODO: Change || to && after the default location
                        // issue is fixed.
                        if ((latitude != 0f) || (longitude != 0f)) {
                            values.put(Images.Media.LATITUDE, latitude);
                            values.put(Images.Media.LONGITUDE, longitude);
                        }
                    }
                });

        return context.getContentResolver().insert(
                Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    public static Uri makeAndInsertUri(Context context, Uri sourceUri) {
        long time = System.currentTimeMillis();
        String filename = getNewFileName(time);
        File file = getNewFile(context, sourceUri, filename);
        return insertContent(context, sourceUri, file, filename, time);
    }
}
