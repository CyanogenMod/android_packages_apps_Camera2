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

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Point;
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
import com.android.camera.util.ImageLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.UUID;

public class Storage {
    public static final String DCIM =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();
    public static final String DIRECTORY = DCIM + "/Camera";
    public static final String JPEG_POSTFIX = ".jpg";
    // Match the code in MediaProvider.computeBucketValues().
    public static final String BUCKET_ID =
            String.valueOf(DIRECTORY.toLowerCase().hashCode());
    public static final long UNAVAILABLE = -1L;
    public static final long PREPARING = -2L;
    public static final long UNKNOWN_SIZE = -3L;
    public static final long LOW_STORAGE_THRESHOLD_BYTES = 50000000;
    public static final String CAMERA_SESSION_SCHEME = "camera_session";
    private static final String TAG = "Storage";
    private static final String GOOGLE_COM = "google.com";
    private static HashMap<Uri, Uri> sSessionsToContentUris = new HashMap<Uri, Uri>();
    private static HashMap<Uri, byte[]> sSessionsToPlaceholderBytes = new HashMap<Uri, byte[]>();
    private static HashMap<Uri, Point> sSessionsToSizes= new HashMap<Uri, Point>();

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private static void setImageSize(ContentValues values, int width, int height) {
        // The two fields are available since ICS but got published in JB
        if (ApiHelper.HAS_MEDIA_COLUMNS_WIDTH_AND_HEIGHT) {
            values.put(MediaColumns.WIDTH, width);
            values.put(MediaColumns.HEIGHT, height);
        }
    }

    public static void writeFile(String path, byte[] jpeg, ExifInterface exif) {
        if (exif != null) {
            try {
                exif.writeExif(jpeg, path);
            } catch (Exception e) {
                Log.e(TAG, "Failed to write data", e);
            }
        } else {
            writeFile(path, jpeg);
        }
    }

    public static void writeFile(String path, byte[] data) {
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

    // Save the image and add it to the MediaStore.
    public static Uri addImage(ContentResolver resolver, String title, long date,
            Location location, int orientation, ExifInterface exif, byte[] jpeg, int width,
            int height) {

        return addImage(resolver, title, date, location, orientation, exif, jpeg, width, height,
                LocalData.MIME_TYPE_JPEG);
    }

    // Save the image with a given mimeType and add it the MediaStore.
    public static Uri addImage(ContentResolver resolver, String title, long date,
            Location location, int orientation, ExifInterface exif, byte[] jpeg, int width,
            int height, String mimeType) {

        String path = generateFilepath(title);
        writeFile(path, jpeg, exif);
        return addImage(resolver, title, date, location, orientation,
                jpeg.length, path, width, height, mimeType);
    }

    // Get a ContentValues object for the given photo data
    public static ContentValues getContentValuesForData(String title,
            long date, Location location, int orientation, int jpegLength,
            String path, int width, int height, String mimeType) {

        ContentValues values = new ContentValues(11);
        values.put(ImageColumns.TITLE, title);
        values.put(ImageColumns.DISPLAY_NAME, title + JPEG_POSTFIX);
        values.put(ImageColumns.DATE_TAKEN, date);
        values.put(ImageColumns.MIME_TYPE, mimeType);
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

    /**
     * Add a placeholder for a new image that does not exist yet.
     * @param jpeg the bytes of the placeholder image
     * @param width the image's width
     * @param height the image's height
     * @return A new URI used to reference this placeholder
     */
    public static Uri addPlaceholder(byte[] jpeg, int width, int height) {
        Uri uri;
        Uri.Builder builder = new Uri.Builder();
        String uuid = UUID.randomUUID().toString();
        builder.scheme(CAMERA_SESSION_SCHEME).authority(GOOGLE_COM).appendPath(uuid);
        uri = builder.build();

        replacePlaceholder(uri, jpeg, width, height);
        return uri;
    }

    /**
     * Add or replace placeholder for a new image that does not exist yet.
     * @param uri the uri of the placeholder to replace, or null if this is a new one
     * @param jpeg the bytes of the placeholder image
     * @param width the image's width
     * @param height the image's height
     * @return A URI used to reference this placeholder
     */
    public static void replacePlaceholder(Uri uri, byte[] jpeg, int width, int height) {
        Point size = new Point(width, height);
        sSessionsToSizes.put(uri, size);
        sSessionsToPlaceholderBytes.put(uri, jpeg);
    }

    // Add the image to media store.
    public static Uri addImage(ContentResolver resolver, String title,
            long date, Location location, int orientation, int jpegLength,
            String path, int width, int height, String mimeType) {
        // Insert into MediaStore.
        ContentValues values =
                getContentValuesForData(title, date, location, orientation, jpegLength, path,
                        width, height, mimeType);

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

    // Overwrites the file and updates the MediaStore

    /**
     * Take jpeg bytes and add them to the media store, either replacing an existing item
     * or a placeholder uri to replace
     * @param imageUri The content uri or session uri of the image being updated
     * @param resolver The content resolver to use
     * @param title of the image
     * @param date of the image
     * @param location of the image
     * @param orientation of the image
     * @param exif of the image
     * @param jpeg bytes of the image
     * @param width of the image
     * @param height of the image
     * @param mimeType of the image
     * @return The content uri of the newly inserted or replaced item.
     */
    public static Uri updateImage(Uri imageUri, ContentResolver resolver, String title, long date,
           Location location, int orientation, ExifInterface exif,
           byte[] jpeg, int width, int height, String mimeType) {
        String path = generateFilepath(title);
        writeFile(path, jpeg, exif);
        return updateImage(imageUri, resolver, title, date, location, orientation, jpeg.length, path,
                width, height, mimeType);
    }


    // Updates the image values in MediaStore
    private static Uri updateImage(Uri imageUri, ContentResolver resolver, String title,
            long date, Location location, int orientation, int jpegLength,
            String path, int width, int height, String mimeType) {

        ContentValues values =
                getContentValuesForData(title, date, location, orientation, jpegLength, path,
                        width, height, mimeType);


        Uri resultUri = imageUri;
        if (Storage.isSessionUri(imageUri)) {
            // If this is a session uri, then we need to add the image
            resultUri = addImage(resolver, title, date, location, orientation, jpegLength, path,
                    width, height, mimeType);
            sSessionsToContentUris.put(imageUri, resultUri);
        } else {
            // Update the MediaStore
            int rowsModified = resolver.update(imageUri, values, null, null);
            if (rowsModified != 1) {
                // This should never happen
                throw new IllegalStateException("Bad number of rows (" + rowsModified
                        + ") updated for uri: " + imageUri);
            }
        }
        return resultUri;
    }

    /**
     * Update the image from the file that has changed.
     * <p>
     * Note: This will update the DATE_TAKEN to right now. We could consider not
     * changing it to preserve the original timestamp.
     */
    public static void updateImageFromChangedFile(Uri mediaUri, Location location,
            ContentResolver resolver, String mimeType) {
        File mediaFile = new File(ImageLoader.getLocalPathFromUri(resolver, mediaUri));
        if (!mediaFile.exists()) {
            throw new IllegalArgumentException("Provided URI is not an existent file: "
                    + mediaUri.getPath());
        }

        ContentValues values = new ContentValues();
        // TODO: Read the date from file.
        values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis());
        values.put(Images.Media.MIME_TYPE, mimeType);
        values.put(Images.Media.SIZE, mediaFile.length());
        if (location != null) {
            values.put(ImageColumns.LATITUDE, location.getLatitude());
            values.put(ImageColumns.LONGITUDE, location.getLongitude());
        }

        resolver.update(mediaUri, values, null, null);
    }

    /**
     * Updates the item's mime type to the given one. This is useful e.g. when
     * switching an image to an in-progress type for re-processing.
     *
     * @param uri the URI of the item to change
     * @param mimeType the new mime type of the item
     */
    public static void updateItemMimeType(Uri uri, String mimeType, ContentResolver resolver) {
        ContentValues values = new ContentValues(1);
        values.put(ImageColumns.MIME_TYPE, mimeType);

        // Update the MediaStore
        int rowsModified = resolver.update(uri, values, null, null);
        if (rowsModified != 1) {
            // This should never happen
            throw new IllegalStateException("Bad number of rows (" + rowsModified
                    + ") updated for uri: " + uri);
        }
    }

    public static void deleteImage(ContentResolver resolver, Uri uri) {
        try {
            resolver.delete(uri, null, null);
        } catch (Throwable th) {
            Log.e(TAG, "Failed to delete image: " + uri);
        }
    }

    public static String generateFilepath(String title) {
        return DIRECTORY + '/' + title + ".jpg";
    }

    /**
     * Returns the jpeg bytes for a placeholder session
     *
     * @param uri the session uri to look up
     * @return The jpeg bytes or null
     */
    public static byte[] getJpegForSession(Uri uri) {
        return sSessionsToPlaceholderBytes.get(uri);
    }

    /**
     * Returns the dimensions of the placeholder image
     *
     * @param uri the session uri to look up
     * @return The size
     */
    public static Point getSizeForSession(Uri uri) {
        return sSessionsToSizes.get(uri);
    }

    /**
     * Takes a session URI and returns the finished image's content URI
     *
     * @param uri the uri of the session that was replaced
     * @return The uri of the new media item, if it exists, or null.
     */
    public static Uri getContentUriForSessionUri(Uri uri) {
        return sSessionsToContentUris.get(uri);
    }

    /**
     * Determines if a URI points to a camera session
     *
     * @param uri the uri to check
     * @return true if it is a session uri.
     */
    public static boolean isSessionUri(Uri uri) {
        return uri.getScheme().equals(CAMERA_SESSION_SCHEME);
    }

    public static long getAvailableSpace() {
        String state = Environment.getExternalStorageState();
        Log.d(TAG, "External storage state=" + state);
        if (Environment.MEDIA_CHECKING.equals(state)) {
            return PREPARING;
        }
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            return UNAVAILABLE;
        }

        File dir = new File(DIRECTORY);
        dir.mkdirs();
        if (!dir.isDirectory() || !dir.canWrite()) {
            return UNAVAILABLE;
        }

        try {
            StatFs stat = new StatFs(DIRECTORY);
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
    public static void ensureOSXCompatible() {
        File nnnAAAAA = new File(DCIM, "100ANDRO");
        if (!(nnnAAAAA.exists() || nnnAAAAA.mkdirs())) {
            Log.e(TAG, "Failed to create " + nnnAAAAA.getPath());
        }
    }

}
