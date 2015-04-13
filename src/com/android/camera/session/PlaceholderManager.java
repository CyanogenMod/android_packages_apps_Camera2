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

package com.android.camera.session;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.Uri;
import android.provider.MediaStore;

import com.android.camera.Storage;
import com.android.camera.debug.Log;
import com.android.camera.exif.ExifInterface;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.Size;
import com.google.common.base.Optional;

import java.io.IOException;

/**
 * Handles placeholders in filmstrip that show up temporarily while a final
 * output media item is being produced.
 */
public class PlaceholderManager {
    private static final Log.Tag TAG = new Log.Tag("PlaceholderMgr");

    private final Context mContext;

    public static class Placeholder {
        final String outputTitle;
        final Uri outputUri;
        final long time;

        Placeholder(String title, Uri uri, long timestamp) {
            outputTitle = title;
            outputUri = uri;
            time = timestamp;
        }
    }

    public PlaceholderManager(Context context) {
        mContext = context;
    }

    /**
     * Adds an empty placeholder.
     *
     * @param title the title of the item
     * @param size the size of the placeholder in pixels.
     * @param timestamp the timestamp of the placeholder (used for ordering
     *            within the filmstrip). Millis since epoch.
     * @return A session instance representing the new placeholder.
     */
    public Placeholder insertEmptyPlaceholder(String title, Size size, long timestamp) {
        Uri uri =  Storage.addEmptyPlaceholder(size);
        return new Placeholder(title, uri, timestamp);
    }

    /**
     * Inserts a new placeholder into the filmstrip.
     *
     * @param title the title of the item
     * @param placeholder the initial thumbnail to show for this placeholder
     * @param timestamp the timestamp of the placeholder (used for ordering
     *            within the filmstrip). Millis since epoch.
     * @return A session instance representing the new placeholder.
     */
    public Placeholder insertPlaceholder(String title, Bitmap placeholder, long timestamp) {
        if (title == null || placeholder == null) {
            throw new IllegalArgumentException("Null argument passed to insertPlaceholder");
        }

        if (placeholder.getWidth() <= 0 || placeholder.getHeight() <= 0) {
            throw new IllegalArgumentException("Image had bad height/width");
        }

        Uri uri =  Storage.addPlaceholder(placeholder);
        if (uri == null) {
            return null;
        }
        return new Placeholder(title, uri, timestamp);
    }

    public Placeholder insertPlaceholder(String title, byte[] placeholder, long timestamp) {
        if (title == null || placeholder == null) {
            throw new IllegalArgumentException("Null argument passed to insertPlaceholder");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        Bitmap bitmap = BitmapFactory.decodeByteArray(placeholder, 0, placeholder.length, options);
        return insertPlaceholder(title, bitmap, timestamp);
    }

    /**
     * Converts an existing item into a placeholder for re-processing.
     *
     * @param uri the URI of an existing media item.
     * @return A session that can be used to update the progress of the new
     *         session.
     */
    public Placeholder convertToPlaceholder(Uri uri) {
        return createSessionFromUri(uri);
    }

    /**
     * This converts the placeholder in to a real media item
     *
     * @param placeholder the session that is being finished.
     * @param location the location of the image
     * @param orientation the orientation of the image
     * @param exif the exif of the image
     * @param jpeg the bytes of the image
     * @param width the width of the image
     * @param height the height of the image
     * @param mimeType the mime type of the image
     * @return The content URI of the new media item.
     */
    public Uri finishPlaceholder(Placeholder placeholder, Location location, int orientation,
            ExifInterface exif, byte[] jpeg, int width, int height, String mimeType) throws IOException {
        Uri resultUri = Storage.updateImage(placeholder.outputUri, mContext.getContentResolver(),
                placeholder.outputTitle, placeholder.time, location, orientation, exif, jpeg, width,
                height, mimeType);
        CameraUtil.broadcastNewPicture(mContext, resultUri);
        return resultUri;
    }

    /**
     * This changes the temporary placeholder jpeg without writing it to the media store
     *
     * @param session the session to update
     * @param placeholder the placeholder bitmap
     */
    public void replacePlaceholder(Placeholder session, Bitmap placeholder) {
        Storage.replacePlaceholder(session.outputUri, placeholder);
        CameraUtil.broadcastNewPicture(mContext, session.outputUri);
    }

    /**
     * Retrieve the placeholder for a given session.
     *
     * @param placeholder the session for which to retrieve bitmap placeholder
     */
    public Optional<Bitmap> getPlaceholder(Placeholder placeholder) {
        return Storage.getPlaceholderForSession(placeholder.outputUri);
    }


    /**
     * Remove the placeholder for a given session.
     *
     * @param placeholder the session for which to remove the bitmap placeholder.
     */
    public void removePlaceholder(Placeholder placeholder) {
        Storage.removePlaceholder(placeholder.outputUri);
    }

    /**
     * Create a new session instance from the given URI by querying the media
     * store.
     * <p>
     * TODO: Make sure this works with types other than images when needed.
     */
    private Placeholder createSessionFromUri(Uri uri) {
        ContentResolver resolver = mContext.getContentResolver();

        Cursor cursor = resolver.query(uri,
                new String[] {
                        MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DISPLAY_NAME,
                }, null, null, null);
        // The count could be 0 if the original media item was deleted before
        // the session was created.
        if (cursor == null || cursor.getCount() == 0) {
            return null;
        }
        int dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN);
        int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);

        cursor.moveToFirst();
        long date = cursor.getLong(dateIndex);
        String name = cursor.getString(nameIndex);

        if (name.toLowerCase().endsWith(Storage.JPEG_POSTFIX)) {
            name = name.substring(0, name.length() - Storage.JPEG_POSTFIX.length());
        }

        return new Placeholder(name, uri, date);
    }
}
