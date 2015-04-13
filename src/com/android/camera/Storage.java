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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.location.Location;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.MediaColumns;
import android.util.LruCache;

import com.android.camera.data.FilmstripItemData;
import com.android.camera.debug.Log;
import com.android.camera.exif.ExifInterface;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.Size;
import com.google.common.base.Optional;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

public class Storage {
    public static final String DCIM =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();
    public static final String DIRECTORY = DCIM + "/Camera";
    public static final File DIRECTORY_FILE = new File(DIRECTORY);
    public static final String JPEG_POSTFIX = ".jpg";
    public static final String GIF_POSTFIX = ".gif";
    public static final long UNAVAILABLE = -1L;
    public static final long PREPARING = -2L;
    public static final long UNKNOWN_SIZE = -3L;
    public static final long ACCESS_FAILURE = -4L;
    public static final long LOW_STORAGE_THRESHOLD_BYTES = 50000000;
    public static final String CAMERA_SESSION_SCHEME = "camera_session";
    private static final Log.Tag TAG = new Log.Tag("Storage");
    private static final String GOOGLE_COM = "google.com";
    private static HashMap<Uri, Uri> sSessionsToContentUris = new HashMap<>();
    private static HashMap<Uri, Uri> sContentUrisToSessions = new HashMap<>();
    private static LruCache<Uri, Bitmap> sSessionsToPlaceholderBitmap =
            // 20MB cache as an upper bound for session bitmap storage
            new LruCache<Uri, Bitmap>(20 * 1024 * 1024) {
                @Override
                protected int sizeOf(Uri key, Bitmap value) {
                    return value.getByteCount();
                }
            };
    private static HashMap<Uri, Point> sSessionsToSizes = new HashMap<>();
    private static HashMap<Uri, Integer> sSessionsToPlaceholderVersions = new HashMap<>();

    /**
     * Save the image with default JPEG MIME type and add it to the MediaStore.
     *
     * @param resolver The The content resolver to use.
     * @param title The title of the media file.
     * @param date The date for the media file.
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
            int height) throws IOException {

        return addImage(resolver, title, date, location, orientation, exif, jpeg, width, height,
              FilmstripItemData.MIME_TYPE_JPEG);
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
     * @param date The date for the media file.
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
    public static Uri addImage(ContentResolver resolver, String title, long date,
            Location location, int orientation, ExifInterface exif, byte[] data, int width,
            int height, String mimeType) throws IOException {

        String path = generateFilepath(title, mimeType);
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
     * @param date The date for the media file.
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
    public static Uri addImageToMediaStore(ContentResolver resolver, String title, long date,
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
     *
     * @param placeholder the placeholder image
     * @return A new URI used to reference this placeholder
     */
    public static Uri addPlaceholder(Bitmap placeholder) {
        Uri uri = generateUniquePlaceholderUri();
        replacePlaceholder(uri, placeholder);
        return uri;
    }

    /**
     * Remove a placeholder from in memory storage.
     */
    public static void removePlaceholder(Uri uri) {
        sSessionsToSizes.remove(uri);
        sSessionsToPlaceholderBitmap.remove(uri);
        sSessionsToPlaceholderVersions.remove(uri);
    }

    /**
     * Add or replace placeholder for a new image that does not exist yet.
     *
     * @param uri the uri of the placeholder to replace, or null if this is a
     *            new one
     * @param placeholder the placeholder image
     * @return A URI used to reference this placeholder
     */
    public static void replacePlaceholder(Uri uri, Bitmap placeholder) {
        Log.v(TAG, "session bitmap cache size: " + sSessionsToPlaceholderBitmap.size());
        Point size = new Point(placeholder.getWidth(), placeholder.getHeight());
        sSessionsToSizes.put(uri, size);
        sSessionsToPlaceholderBitmap.put(uri, placeholder);
        Integer currentVersion = sSessionsToPlaceholderVersions.get(uri);
        sSessionsToPlaceholderVersions.put(uri, currentVersion == null ? 0 : currentVersion + 1);
    }

    /**
     * Creates an empty placeholder.
     *
     * @param size the size of the placeholder in pixels.
     * @return A new URI used to reference this placeholder
     */
    @Nonnull
    public static Uri addEmptyPlaceholder(@Nonnull Size size) {
        Uri uri = generateUniquePlaceholderUri();
        sSessionsToSizes.put(uri, new Point(size.getWidth(), size.getHeight()));
        sSessionsToPlaceholderBitmap.remove(uri);
        Integer currentVersion = sSessionsToPlaceholderVersions.get(uri);
        sSessionsToPlaceholderVersions.put(uri, currentVersion == null ? 0 : currentVersion + 1);
        return uri;
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
           byte[] jpeg, int width, int height, String mimeType) throws IOException {
        String path = generateFilepath(title, mimeType);
        writeFile(path, jpeg, exif);
        return updateImage(imageUri, resolver, title, date, location, orientation, jpeg.length, path,
                width, height, mimeType);
    }

    private static Uri generateUniquePlaceholderUri() {
        Uri.Builder builder = new Uri.Builder();
        String uuid = UUID.randomUUID().toString();
        builder.scheme(CAMERA_SESSION_SCHEME).authority(GOOGLE_COM).appendPath(uuid);
        return builder.build();
    }

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
    public static long writeFile(String path, byte[] jpeg, ExifInterface exif) throws IOException {
        if (!createDirectoryIfNeeded(path)) {
            Log.e(TAG, "Failed to create parent directory for file: " + path);
            return -1;
        }
        if (exif != null) {
                exif.writeExif(jpeg, path);
                File f = new File(path);
                return f.length();
        } else {
            return writeFile(path, jpeg);
        }
//        return -1;
    }

    /**
     * Renames a file.
     *
     * <p/>
     * Can only be used for regular files, not directories.
     *
     * @param inputPath the original path of the file
     * @param newFilePath the new path of the file
     * @return false if rename was not successful
     */
    public static boolean renameFile(File inputPath, File newFilePath) {
        if (newFilePath.exists()) {
            Log.e(TAG, "File path already exists: " + newFilePath.getAbsolutePath());
            return false;
        }
        if (inputPath.isDirectory()) {
            Log.e(TAG, "Input path is directory: " + inputPath.getAbsolutePath());
            return false;
        }
        if (!createDirectoryIfNeeded(newFilePath.getAbsolutePath())) {
            Log.e(TAG, "Failed to create parent directory for file: " +
                    newFilePath.getAbsolutePath());
            return false;
        }
        return inputPath.renameTo(newFilePath);
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

    /**
     * Given a file path, makes sure the directory it's in exists, and if not
     * that it is created.
     *
     * @param filePath the absolute path of a file, e.g. '/foo/bar/file.jpg'.
     * @return Whether the directory exists. If 'false' is returned, this file
     *         cannot be written to since the parent directory could not be
     *         created.
     */
    private static boolean createDirectoryIfNeeded(String filePath) {
        File parentFile = new File(filePath).getParentFile();

        // If the parent exists, return 'true' if it is a directory. If it's a
        // file, return 'false'.
        if (parentFile.exists()) {
            return parentFile.isDirectory();
        }

        // If the parent does not exists, attempt to create it and return
        // whether creating it succeeded.
        return parentFile.mkdirs();
    }

    /** Updates the image values in MediaStore. */
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

    private static String generateFilepath(String title, String mimeType) {
        return generateFilepath(DIRECTORY, title, mimeType);
    }

    public static String generateFilepath(String directory, String title, String mimeType) {
        String extension = null;
        if (FilmstripItemData.MIME_TYPE_JPEG.equals(mimeType)) {
            extension = JPEG_POSTFIX;
        } else if (FilmstripItemData.MIME_TYPE_GIF.equals(mimeType)) {
            extension = GIF_POSTFIX;
        } else {
            throw new IllegalArgumentException("Invalid mimeType: " + mimeType);
        }
        return (new File(directory, title + extension)).getAbsolutePath();
    }

    /**
     * Returns the jpeg bytes for a placeholder session
     *
     * @param uri the session uri to look up
     * @return The bitmap or null
     */
    public static Optional<Bitmap> getPlaceholderForSession(Uri uri) {
        return Optional.fromNullable(sSessionsToPlaceholderBitmap.get(uri));
    }

    /**
     * @return Whether a placeholder size for the session with the given URI
     *         exists.
     */
    public static boolean containsPlaceholderSize(Uri uri) {
        return sSessionsToSizes.containsKey(uri);
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
