/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.filtershow.tools;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.exif.ExifData;
import com.android.gallery3d.exif.ExifInvalidFormatException;
import com.android.gallery3d.exif.ExifOutputStream;
import com.android.gallery3d.exif.ExifReader;
import com.android.gallery3d.exif.ExifTag;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.presets.ImagePreset;
import com.android.gallery3d.util.XmpUtilHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * Asynchronous task for saving edited photo as a new copy.
 */
public class SaveCopyTask extends AsyncTask<ImagePreset, Void, Uri> {


    private static final String LOGTAG = "SaveCopyTask";
    /**
     * Saves the bitmap in the final destination
     */
    public static void saveBitmap(Bitmap bitmap, File destination, Object xmp) {
        saveBitmap(bitmap, destination, xmp, null);
    }

    private static void saveBitmap(Bitmap bitmap, File destination, Object xmp, ExifData exif) {
        OutputStream os = null;
        try {
            os = new FileOutputStream(destination);
            if (exif != null) {
                ExifOutputStream eos = new ExifOutputStream(os);
                eos.setExifData(exif);
                bitmap.compress(CompressFormat.JPEG, ImageLoader.DEFAULT_COMPRESS_QUALITY, eos);
            } else {
                bitmap.compress(CompressFormat.JPEG, ImageLoader.DEFAULT_COMPRESS_QUALITY, os);
            }
        } catch (FileNotFoundException e) {
            Log.v(LOGTAG,"Error in writing "+destination.getAbsolutePath());
        } finally {
            Utils.closeSilently(os);;
        }
        if (xmp != null) {
            XmpUtilHelper.writeXMPMeta(destination.getAbsolutePath(), xmp);
        }
    }

    /**
     * Callback for the completed asynchronous task.
     */
    public interface Callback {

        void onComplete(Uri result);
    }

    private interface ContentResolverQueryCallback {

        void onCursorResult(Cursor cursor);
    }

    private static final String TIME_STAMP_NAME = "'IMG'_yyyyMMdd_HHmmss";

    private final Context context;
    private final Uri sourceUri;
    private final Callback callback;
    private final String saveFileName;
    private final File destinationFile;

    public SaveCopyTask(Context context, Uri sourceUri, File destination, Callback callback) {
        this.context = context;
        this.sourceUri = sourceUri;
        this.callback = callback;

        if (destination == null) {
            this.destinationFile = getNewFile(context, sourceUri);
        } else {
            this.destinationFile = destination;
        }

        saveFileName = new SimpleDateFormat(TIME_STAMP_NAME).format(new Date(
                System.currentTimeMillis()));
    }

    public static File getFinalSaveDirectory(Context context, Uri sourceUri) {
        File saveDirectory = getSaveDirectory(context, sourceUri);
        if ((saveDirectory == null) || !saveDirectory.canWrite()) {
            saveDirectory = new File(Environment.getExternalStorageDirectory(),
                    ImageLoader.DEFAULT_SAVE_DIRECTORY);
        }
        // Create the directory if it doesn't exist
        if (!saveDirectory.exists()) saveDirectory.mkdirs();
        return saveDirectory;
    }

    public static File getNewFile(Context context, Uri sourceUri) {
        File saveDirectory = getFinalSaveDirectory(context, sourceUri);
        String filename = new SimpleDateFormat(TIME_STAMP_NAME).format(new Date(
                System.currentTimeMillis()));
        return new File(saveDirectory, filename + ".JPG");
    }

    private ExifData getExifData(Uri sourceUri) {
        String mimeType = context.getContentResolver().getType(sourceUri);
        if (mimeType != ImageLoader.JPEG_MIME_TYPE) {
            return null;
        }
        InputStream is = null;
        try {
            is = context.getContentResolver().openInputStream(sourceUri);
            ExifReader reader = new ExifReader();
            return reader.read(is);
        } catch (FileNotFoundException e) {
            Log.w(LOGTAG, "Failed to find file", e);
            return null;
        } catch (ExifInvalidFormatException e) {
            Log.w(LOGTAG, "Invalid EXIF data", e);
            return null;
        } catch (IOException e) {
            Log.w(LOGTAG, "Failed to read original file", e);
            return null;
        } finally {
            Utils.closeSilently(is);
        }
    }

    /**
     * The task should be executed with one given bitmap to be saved.
     */
    @Override
    protected Uri doInBackground(ImagePreset... params) {
        // TODO: Support larger dimensions for photo saving.
        if (params[0] == null || sourceUri == null) {
            return null;
        }
        ImagePreset preset = params[0];
        InputStream is = null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        boolean noBitmap = true;
        int num_tries = 0;
        // Stopgap fix for low-memory devices.
        while(noBitmap) {
            try {
                // Try to do bitmap operations, downsample if low-memory
                Bitmap bitmap = ImageLoader.loadMutableBitmap(context, sourceUri, options);
                if (bitmap == null) {
                    return null;
                }
                bitmap = preset.applyGeometry(bitmap);
                bitmap = preset.apply(bitmap);

                Object xmp = null;
                if (preset.isPanoramaSafe()) {
                    is = context.getContentResolver().openInputStream(sourceUri);
                    xmp =  XmpUtilHelper.extractXMPMeta(is);
                }
                ExifData exif = getExifData(sourceUri);
                if (exif != null) {
                    exif.addDateTimeStampTag(ExifTag.TAG_DATE_TIME, System.currentTimeMillis(),
                            TimeZone.getDefault());
                    // Since the image has been modified, set the orientation to normal.
                    exif.addTag(ExifTag.TAG_ORIENTATION).setValue(ExifTag.Orientation.TOP_LEFT);
                }
                saveBitmap(bitmap, this.destinationFile, xmp, exif);
                bitmap.recycle();
                noBitmap = false;
            } catch (FileNotFoundException ex) {
                Log.w(LOGTAG, "Failed to save image!", ex);
                return null;
            } catch (java.lang.OutOfMemoryError e) {
                // Try 5 times before failing for good.
                if (++num_tries >= 5) {
                    throw e;
                }
                System.gc();
                options.inSampleSize *= 2;
            } finally {
                Utils.closeSilently(is);
            }
        }
        Uri uri = insertContent(context, sourceUri, this.destinationFile, saveFileName);
        return uri;

    }

    @Override
    protected void onPostExecute(Uri result) {
        if (callback != null) {
            callback.onComplete(result);
        }
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
                ImageColumns.DATA
        },
                new ContentResolverQueryCallback() {

                    @Override
                    public void onCursorResult(Cursor cursor) {
                        dir[0] = new File(cursor.getString(0)).getParentFile();
                    }
                });
        return dir[0];
    }

    /**
     * Insert the content (saved file) with proper source photo properties.
     */
    public static Uri insertContent(Context context, Uri sourceUri, File file, String saveFileName) {
        long now = System.currentTimeMillis() / 1000;

        final ContentValues values = new ContentValues();
        values.put(Images.Media.TITLE, saveFileName);
        values.put(Images.Media.DISPLAY_NAME, file.getName());
        values.put(Images.Media.MIME_TYPE, "image/jpeg");
        values.put(Images.Media.DATE_TAKEN, now);
        values.put(Images.Media.DATE_MODIFIED, now);
        values.put(Images.Media.DATE_ADDED, now);
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
                // TODO: Change || to && after the default location issue is
                // fixed.
                if ((latitude != 0f) || (longitude != 0f)) {
                    values.put(Images.Media.LATITUDE, latitude);
                    values.put(Images.Media.LONGITUDE, longitude);
                }
            }
        });

        return context.getContentResolver().insert(
                Images.Media.EXTERNAL_CONTENT_URI, values);
    }

}
