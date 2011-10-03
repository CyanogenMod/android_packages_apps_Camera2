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

package com.android.gallery3d.photoeditor;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.view.Gravity;
import android.widget.Toast;

import com.android.gallery3d.R;

import java.io.File;
import java.sql.Date;
import java.text.SimpleDateFormat;

/**
 * Asynchronous task for saving edited photo as a new copy.
 */
public class SaveCopyTask extends AsyncTask<Bitmap, Void, Uri> {

    /**
     * Callback for the completed asynchronous task.
     */
    public interface Callback {

        void onComplete(Uri result);
    }

    private static final String TIME_STAMP_NAME = "'IMG'_yyyyMMdd_HHmmss";
    private static final int INDEX_DATE_TAKEN = 0;
    private static final int INDEX_LATITUDE = 1;
    private static final int INDEX_LONGITUDE = 2;

    private static final String[] IMAGE_PROJECTION = new String[] {
        ImageColumns.DATE_TAKEN,
        ImageColumns.LATITUDE,
        ImageColumns.LONGITUDE,
    };

    private final Context context;
    private final Uri sourceUri;
    private final Callback callback;
    private final String albumName;
    private final String saveFileName;

    public SaveCopyTask(Context context, Uri sourceUri, Callback callback) {
        this.context = context;
        this.sourceUri = sourceUri;
        this.callback = callback;

        albumName = context.getString(R.string.edited_photo_bucket_name);
        saveFileName = new SimpleDateFormat(TIME_STAMP_NAME).format(
                new Date(System.currentTimeMillis()));
    }

    /**
     * The task should be executed with one given bitmap to be saved.
     */
    @Override
    protected Uri doInBackground(Bitmap... params) {
        // TODO: Support larger dimensions for photo saving.
        if (params[0] == null) {
            return null;
        }
        Bitmap bitmap = params[0];
        File file = save(bitmap);
        Uri uri = (file != null) ? insertContent(file) : null;
        bitmap.recycle();
        return uri;
    }

    @Override
    protected void onPostExecute(Uri result) {
        String message = (result == null) ? context.getString(R.string.saving_failure)
                : context.getString(R.string.photo_saved, albumName);
        Toast toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();

        callback.onComplete(result);
    }

    private File save(Bitmap bitmap) {
        String directory = Environment.getExternalStorageDirectory().toString() + "/" + albumName;
        return new BitmapUtils(context).saveBitmap(
                bitmap, directory, saveFileName, Bitmap.CompressFormat.JPEG);
    }

    /**
     * Insert the content (saved file) with proper source photo properties.
     */
    private Uri insertContent(File file) {
        long now = System.currentTimeMillis() / 1000;
        long dateTaken = now;
        double latitude = 0f;
        double longitude = 0f;

        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(sourceUri, IMAGE_PROJECTION, null, null, null);
            if ((cursor != null) && cursor.moveToNext()) {
                dateTaken = cursor.getLong(INDEX_DATE_TAKEN);
                latitude = cursor.getDouble(INDEX_LATITUDE);
                longitude = cursor.getDouble(INDEX_LONGITUDE);
            }
        } catch (Exception e) {
            // Ignore error for lacking property columns from the source.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        ContentValues values = new ContentValues();
        values.put(Images.Media.TITLE, saveFileName);
        values.put(Images.Media.DISPLAY_NAME, saveFileName);
        values.put(Images.Media.MIME_TYPE, "image/jpeg");
        values.put(Images.Media.DATE_TAKEN, dateTaken);
        values.put(Images.Media.DATE_MODIFIED, now);
        values.put(Images.Media.DATE_ADDED, now);
        values.put(Images.Media.ORIENTATION, 0);
        values.put(Images.Media.DATA, file.getAbsolutePath());
        values.put(Images.Media.SIZE, file.length());

        // TODO: Change || to && after the default location issue is fixed.
        if ((latitude != 0f) || (longitude != 0f)) {
            values.put(Images.Media.LATITUDE, latitude);
            values.put(Images.Media.LONGITUDE, longitude);
        }
        return contentResolver.insert(Images.Media.EXTERNAL_CONTENT_URI, values);
    }
}
