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

import com.android.camera.data.LocalData;
import com.android.camera.debug.Log;
import com.android.camera.exif.ExifInterface;
import com.android.camera.util.ApiHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
    private static final Log.Tag TAG = new Log.Tag("Storage");
    private static final String GOOGLE_COM = "google.com";
    private static HashMap<Uri, Uri> sSessionsToContentUris = new HashMap<Uri, Uri>();
    private static HashMap<Uri, Uri> sContentUrisToSessions = new HashMap<Uri, Uri>();
    private static HashMap<Uri, byte[]> sSessionsToPlaceholderBytes = new HashMap<Uri, byte[]>();
    private static HashMap<Uri, Point> sSessionsToSizes = new HashMap<Uri, Point>();
    private static HashMap<Uri, Integer> sSessionsToPlaceholderVersions =
        new HashMap<Uri, Integer>();

    /**
     * Save the image with default JPEG MIME type and add it to the MediaStore.
     *
     * @param resolver The The content resolver to use.
     * @param title The title of the media file.
     * @param date The date fo the media file.
     * @param location The location of the media file.
     * @param orientation The orientation of the media file.
     * @param exif The EXIF info. Can be {@code null}.
     * @param jpeg The JPEG data.
     * @param width The width of the media file after the orientation is
     *              applied.
     * @param height The height of the media file after the orientation is
     *               applied.
     */
    public static Uri addImage(ContentResolver resolver, String title, long date,
            Location location, int orientation, ExifInterface exif, byte[] jpeg, int width,
            int height) {

        return addImage(resolver, title, date, location, orientation, exif, jpeg, width, height,
                LocalData.MIME_TYPE_JPEG);
    }

    /**
     * Saves the media with a given MIME type and adds it to the MediaStore.
     * <p>
     * The path will be automatically generated according to the title.
     * </p>
     *
     * @param resolver The The content resolver to use.
     * @param title The title of the media file.
     * @param data The data to save.
     * @param date The date fo the media file.
     * @param location The location of the media file.
     * @param orientation The orientation of the media file.
     * @param exif The EXIF info. Can be {@code null}.
     * @param width The width of the media file after the orientation is
     *            applied.
     * @param height The height of the media file after the orientation is
     *            applied.
     * @param mimeType The MIME type of the data.
     * @return The URI of the added image, or null if the image could not be
     *         added.
     */
    private static Uri addImage(ContentResolver resolver, String title, long date,
            Location location, int orientation, ExifInterface exif, byte[] data, int width,
            int height, String mimeType) {

        String path = generateFilepath(title);
        long fileLength = writeFile(path, data, exif);
        if (fileLength >= 0) {
            return addImageToMediaStore(resolver, title, date, location, orientation, fileLength,
                    path, width, height, mimeType);
        }
        return null;
    }

    /**
     * Add the entry for the media file to media store.
     *
     * @param resolver The The content resolver to use.
     * @param title The title of the media file.
     * @param date The date fo the media file.
     * @param location The location of the media file.
     * @param orientation The orientation of the media file.
     * @param width The width of the media file after the orientation is
     *            applied.
     * @param height The height of the media file after the orientation is
     *            applied.
     * @param mimeType The MIME type of the data.
     * @return The content URI of the inserted media file or null, if the image
     *         could not be added.
     */
    private static Uri addImageToMediaStore(ContentResolver resolver, String title, long date,
            Location location, int orientation, long jpegLength, String path, int width, int height,
            String mimeType) {
        // Insert into MediaStore.
        ContentValues values =
                getContentValuesForData(title, date, location, orientation, jpegLength, path, width,
                        height, mimeType);

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

    // Get a ContentValues object for the given photo data
    public static ContentValues getContentValuesForData(String title,
            long date, Location location, int orientation, long jpegLength,
            String path, int width, int height, String mimeType) {

        File file = new File(path);
        long dateModifiedSeconds = TimeUnit.MILLISECONDS.toSeconds(file.lastModified());

        ContentValues values = new ContentValues(11);
        values.put(ImageColumns.TITLE, title);
        values.put(ImageColumns.DISPLAY_NAME, title + JPEG_POSTFIX);
        values.put(ImageColumns.DATE_TAKEN, date);
        values.put(ImageColumns.MIME_TYPE, mimeType);
        values.put(ImageColumns.DATE_MODIFIED, dateModifiedSeconds);
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
        Integer currentVersion = sSessionsToPlaceholderVersions.get(uri);
        sSessionsToPlaceholderVersions.put(uri, currentVersion == null ? 0 : currentVersion + 1);
    }

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

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private static void setImageSize(ContentValues values, int width, int height) {
        // The two fields are available since ICS but got published in JB
        if (ApiHelper.HAS_MEDIA_COLUMNS_WIDTH_AND_HEIGHT) {
            values.put(MediaColumns.WIDTH, width);
            values.put(MediaColumns.HEIGHT, height);
        }
    }

    /**
     * Writes the JPEG data to a file. If there's EXIF info, the EXIF header
     * will be added.
     *
     * @param path The path to the target file.
     * @param jpeg The JPEG data.
     * @param exif The EXIF info. Can be {@code null}.
     *
     * @return The size of the file. -1 if failed.
     */
    private static long writeFile(String path, byte[] jpeg, ExifInterface exif) {
        if (exif != null) {
            try {
                exif.writeExif(jpeg, path);
                File f = new File(path);
                return f.length();
            } catch (Exception e) {
                Log.e(TAG, "Failed to write data", e);
            }
        } else {
            return writeFile(path, jpeg);
        }
        return -1;
    }

    /**
     * Writes the data to a file.
     *
     * @param path The path to the target file.
     * @param data The data to save.
     *
     * @return The size of the file. -1 if failed.
     */
    private static long writeFile(String path, byte[] data) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(path);
            out.write(data);
            return data.length;
        } catch (Exception e) {
            Log.e(TAG, "Failed to write data", e);
        } finally {
            try {
                out.close();
            } catch (Exception e) {
                Log.e(TAG, "Failed to close file after write", e);
            }
        }
        return -1;
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
            resultUri = addImageToMediaStore(resolver, title, date, location, orientation,
                    jpegLength, path, width, height, mimeType);
            sSessionsToContentUris.put(imageUri, resultUri);
            sContentUrisToSessions.put(resultUri, imageUri);
        } else {
            // Update the MediaStore
            resolver.update(imageUri, values, null, null);
        }
        return resultUri;
    }

    private static String generateFilepath(String title) {
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
     * Returns the current version of a placeholder for a session. The version will increment
     * with each call to replacePlaceholder.
     *
     * @param uri the session uri to look up.
     * @return the current version int.
     */
    public static int getJpegVersionForSession(Uri uri) {
        return sSessionsToPlaceholderVersions.get(uri);
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
     * Takes a content URI and returns the original Session Uri if any
     *
     * @param contentUri the uri of the media store content
     * @return The session uri of the original session, if it exists, or null.
     */
    public static Uri getSessionUriFromContentUri(Uri contentUri) {
        return sContentUrisToSessions.get(contentUri);
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
