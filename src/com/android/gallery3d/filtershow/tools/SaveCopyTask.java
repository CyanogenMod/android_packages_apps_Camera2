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
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.exif.ExifInterface;
import com.android.gallery3d.filtershow.cache.CachingPipeline;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.filters.FiltersManager;
import com.android.gallery3d.filtershow.presets.ImagePreset;
import com.android.gallery3d.util.XmpUtilHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * Asynchronous task for saving edited photo as a new copy.
 */
public class SaveCopyTask extends AsyncTask<ImagePreset, Void, Uri> {

    private static final String LOGTAG = "SaveCopyTask";

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
        if (!saveDirectory.exists())
            saveDirectory.mkdirs();
        return saveDirectory;
    }

    public static File getNewFile(Context context, Uri sourceUri) {
        File saveDirectory = getFinalSaveDirectory(context, sourceUri);
        String filename = new SimpleDateFormat(TIME_STAMP_NAME).format(new Date(
                System.currentTimeMillis()));
        return new File(saveDirectory, filename + ".JPG");
    }

    public Object getPanoramaXMPData(Uri source, ImagePreset preset) {
        Object xmp = null;
        if (preset.isPanoramaSafe()) {
            InputStream is = null;
            try {
                is = context.getContentResolver().openInputStream(source);
                xmp = XmpUtilHelper.extractXMPMeta(is);
            } catch (FileNotFoundException e) {
                Log.w(LOGTAG, "Failed to get XMP data from image: ", e);
            } finally {
                Utils.closeSilently(is);
            }
        }
        return xmp;
    }

    public boolean putPanoramaXMPData(File file, Object xmp) {
        if (xmp != null) {
            return XmpUtilHelper.writeXMPMeta(file.getAbsolutePath(), xmp);
        }
        return false;
    }

    public ExifInterface getExifData(Uri source) {
        ExifInterface exif = new ExifInterface();
        String mimeType = context.getContentResolver().getType(sourceUri);
        if (mimeType.equals(ImageLoader.JPEG_MIME_TYPE)) {
            InputStream inStream = null;
            try {
                inStream = context.getContentResolver().openInputStream(source);
                exif.readExif(inStream);
            } catch (FileNotFoundException e) {
                Log.w(LOGTAG, "Cannot find file: " + source, e);
            } catch (IOException e) {
                Log.w(LOGTAG, "Cannot read exif for: " + source, e);
            } finally {
                Utils.closeSilently(inStream);
            }
        }
        return exif;
    }

    public boolean putExifData(File file, ExifInterface exif, Bitmap image) {
        boolean ret = false;
        try {
            exif.writeExif(image, file.getAbsolutePath());
            ret = true;
        } catch (FileNotFoundException e) {
            Log.w(LOGTAG, "File not found: " + file.getAbsolutePath(), e);
        } catch (IOException e) {
            Log.w(LOGTAG, "Could not write exif: ", e);
        }
        return ret;
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
        BitmapFactory.Options options = new BitmapFactory.Options();
        Uri uri = null;
        boolean noBitmap = true;
        int num_tries = 0;
        // Stopgap fix for low-memory devices.
        while (noBitmap) {
            try {
                // Try to do bitmap operations, downsample if low-memory
                Bitmap bitmap = ImageLoader.loadMutableBitmap(context, sourceUri, options);
                if (bitmap == null) {
                    return null;
                }
                CachingPipeline pipeline = new CachingPipeline(FiltersManager.getManager(), "Saving");
                bitmap = pipeline.renderFinalImage(bitmap, preset);

                Object xmp = getPanoramaXMPData(sourceUri, preset);
                ExifInterface exif = getExifData(sourceUri);

                // Set tags
                long time = System.currentTimeMillis();
                exif.addDateTimeStampTag(ExifInterface.TAG_DATE_TIME, time,
                        TimeZone.getDefault());
                exif.setTag(exif.buildTag(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.Orientation.TOP_LEFT));

                // Remove old thumbnail
                exif.removeCompressedThumbnail();

                // If we succeed in writing the bitmap as a jpeg, return a uri.
                if (putExifData(this.destinationFile, exif, bitmap)) {
                    putPanoramaXMPData(this.destinationFile, xmp);
                    uri = insertContent(context, sourceUri, this.destinationFile, saveFileName,
                            time);
                }
                noBitmap = false;
            } catch (java.lang.OutOfMemoryError e) {
                // Try 5 times before failing for good.
                if (++num_tries >= 5) {
                    throw e;
                }
                System.gc();
                options.inSampleSize *= 2;
            }
        }
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

}
