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

package com.android.camera;

import java.io.File;
import java.io.FileOutputStream;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;

import com.android.camera.data.LocalData;
import com.android.camera.exif.ExifInterface;
import com.android.camera.util.ApiHelper;

public class Storage {
    private static final String TAG = "CameraStorage";

    public static final String JPEG_POSTFIX = ".jpg";

    public static final long UNAVAILABLE = -1L;
    public static final long PREPARING = -2L;
    public static final long UNKNOWN_SIZE = -3L;
    public static final long LOW_STORAGE_THRESHOLD_BYTES = 50000000;

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private static void setImageSize(ContentValues values, int width, int height) {
        // The two fields are available since ICS but got published in JB
        if (ApiHelper.HAS_MEDIA_COLUMNS_WIDTH_AND_HEIGHT) {
            values.put(MediaColumns.WIDTH, width);
            values.put(MediaColumns.HEIGHT, height);
        }
    }

    private String mRoot = Environment.getExternalStorageDirectory().toString();
    private static Storage sStorage;

    private Storage() { }

    public static Storage getInstance() {
        if (sStorage == null) {
            sStorage = new Storage();
        }
        return sStorage;
    }

    public void setRoot(String root) {
        mRoot = root;
    }

    public int writeFile(String path, byte[] jpeg, ExifInterface exif,
            String mimeType) {
        if (exif != null && (mimeType == null ||
            mimeType.equalsIgnoreCase("jpeg"))) {
            try {
                return exif.writeExif(jpeg, path);
            } catch (Exception e) {
                Log.e(TAG, "Failed to write data", e);
            }
        } else if (jpeg != null) {
            if (!(mimeType.equalsIgnoreCase("jpeg") || mimeType == null)) {
                 File dir = new File(generateRawDirectory());
                 dir.mkdirs();
            }
            return jpeg.length;
        }
        return 0;
    }

    public void writeFile(String path, byte[] data) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(path);
            out.write(data);
        } catch (Exception e) {
            Log.e(TAG, "Failed to write data", e);
        } finally {
            try {
                out.close();
            } catch (Exception e) {
                Log.e(TAG, "Failed to close file after write", e);
            }
        }
    }

    // Save the image with a given mimeType and add it the MediaStore.
    public Uri addImage(ContentResolver resolver, String title, long date,
            Location location, int orientation, ExifInterface exif, byte[] jpeg, int width,
            int height, String mimeType) {

        String path = generateFilepath(title, mimeType);
        int size = writeFile(path, jpeg, exif, mimeType);
        return addImage(resolver, title, date, location, orientation,
                size, path, width, height, mimeType);
    }

    // Get a ContentValues object for the given photo data
    public ContentValues getContentValuesForData(String title,
            long date, Location location, int orientation, int jpegLength,
            String path, int width, int height, String mimeType) {
        // Insert into MediaStore.
        ContentValues values = new ContentValues(9);
        values.put(ImageColumns.TITLE, title);
        if (mimeType.equalsIgnoreCase("jpeg") ||
            mimeType.equalsIgnoreCase("image/jpeg") ||
            mimeType == null) {
            values.put(ImageColumns.DISPLAY_NAME, title + ".jpg");
        } else {
            values.put(ImageColumns.DISPLAY_NAME, title + ".raw");
        }
        values.put(ImageColumns.DATE_TAKEN, date);
        values.put(ImageColumns.MIME_TYPE, "image/jpeg");
        // Clockwise rotation in degrees. 0, 90, 180, or 270.
        values.put(ImageColumns.ORIENTATION, orientation);
        values.put(ImageColumns.DATA, path);
        values.put(ImageColumns.SIZE, jpegLength);

        setImageSize(values, width, height);

        if (location != null) {
            values.put(ImageColumns.LATITUDE, location.getLatitude());
            values.put(ImageColumns.LONGITUDE, location.getLongitude());
        }
        return values;
    }

    // Add the image to media store.
    public Uri addImage(ContentResolver resolver, String title,
            long date, Location location, int orientation, int jpegLength,
            String path, int width, int height, String mimeType) {
        // Insert into MediaStore.
        ContentValues values =
                getContentValuesForData(title, date, location, orientation, jpegLength, path,
                        width, height, mimeType);

         return insertImage(resolver, values);
    }

    // Overwrites the file and updates the MediaStore, or inserts the image if
    // one does not already exist.
    public void updateImage(Uri imageUri, ContentResolver resolver, String title, long date,
            Location location, int orientation, ExifInterface exif, byte[] jpeg, int width,
            int height, String mimeType) {
        String path = generateFilepath(title, mimeType);
        writeFile(path, jpeg, exif, mimeType);
        updateImage(imageUri, resolver, title, date, location, orientation, jpeg.length, path,
                width, height, mimeType);
    }

    // Updates the image values in MediaStore, or inserts the image if one does
    // not already exist.
    public void updateImage(Uri imageUri, ContentResolver resolver, String title,
            long date, Location location, int orientation, int jpegLength,
            String path, int width, int height, String mimeType) {

        ContentValues values =
                getContentValuesForData(title, date, location, orientation, jpegLength, path,
                        width, height, mimeType);

        // Update the MediaStore
        int rowsModified = resolver.update(imageUri, values, null, null);

        if (rowsModified == 0) {
            // If no prior row existed, insert a new one.
            Log.w(TAG, "updateImage called with no prior image at uri: " + imageUri);
            insertImage(resolver, values);
        } else if (rowsModified != 1) {
            // This should never happen
            throw new IllegalStateException("Bad number of rows (" + rowsModified
                    + ") updated for uri: " + imageUri);
        }
    }

    public void deleteImage(ContentResolver resolver, Uri uri) {
        try {
            resolver.delete(uri, null, null);
        } catch (Throwable th) {
            Log.e(TAG, "Failed to delete image: " + uri);
        }
    }

    public String generateFilepath(String title, String pictureFormat) {
        if (pictureFormat.equalsIgnoreCase("jpeg") || pictureFormat == null) {
            return generateDirectory() + '/' + title + ".jpg";
        } else {
            return generateRawDirectory() + '/' + title + ".raw";
        }
    }

    private String generateDCIM() {
        return new File(mRoot, Environment.DIRECTORY_DCIM).toString();
    }

    public String generateDirectory() {
        return generateDCIM() + "/Camera";
    }

    public String generateRawDirectory() {
        return generateDirectory() + "/raw";
    }

    public String generateBucketId() {
        return String.valueOf(generateBucketIdInt());
    }

    public int generateBucketIdInt() {
        return generateDirectory().toLowerCase().hashCode();
    }

    public long getAvailableSpace() {
        File dir = new File(generateDirectory());
        String state = Environment.getStorageState(dir);
        Log.d(TAG, "External storage state=" + state);
        if (Environment.MEDIA_CHECKING.equals(state)) {
            return PREPARING;
        }
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            return UNAVAILABLE;
        }

        dir.mkdirs();
        if (!dir.isDirectory() || !dir.canWrite()) {
            return UNAVAILABLE;
        }

        try {
            StatFs stat = new StatFs(generateDirectory());
            return stat.getAvailableBlocks() * (long) stat.getBlockSize();
        } catch (Exception e) {
            Log.i(TAG, "Fail to access external storage", e);
        }
        return UNKNOWN_SIZE;
    }

    /**
     * OSX requires plugged-in USB storage to have path /DCIM/NNNAAAAA to be
     * imported. This is a temporary fix for bug#1655552.
     */
    public void ensureOSXCompatible() {
        File nnnAAAAA = new File(generateDCIM(), "100ANDRO");
        if (!(nnnAAAAA.exists() || nnnAAAAA.mkdirs())) {
            Log.e(TAG, "Failed to create " + nnnAAAAA.getPath());
        }
    }

    private static Uri insertImage(ContentResolver resolver, ContentValues values) {
        Uri uri = null;
        try {
            uri = resolver.insert(Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (Throwable th)  {
            // This can happen when the external volume is already mounted, but
            // MediaScanner has not notify MediaProvider to add that volume.
            // The picture is still safe and MediaScanner will find it and
            // insert it into MediaProvider. The only problem is that the user
            // cannot click the thumbnail to review the picture.
            Log.e(TAG, "Failed to write MediaStore" + th);
        }
        return uri;
    }
}
