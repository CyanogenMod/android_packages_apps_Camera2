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
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.Uri;
import android.provider.MediaStore;

import com.android.camera.Storage;
import com.android.camera.debug.Log;
import com.android.camera.exif.ExifInterface;
import com.android.camera.util.CameraUtil;

/**
 * Handles placeholders in filmstrip that show up temporarily while a final
 * output media item is being produced.
 */
public class PlaceholderManager {
    private static final Log.Tag TAG = new Log.Tag("PlaceholderMgr");

    private final Context mContext;

    public static class Session {
        final String outputTitle;
        final Uri outputUri;
        final long time;

        Session(String title, Uri uri, long timestamp) {
            outputTitle = title;
            outputUri = uri;
            time = timestamp;
        }
    }

    public PlaceholderManager(Context context) {
        mContext = context;
    }

    public Session insertPlaceholder(String title, byte[] placeholder, long timestamp) {
        if (title == null || placeholder == null) {
            throw new IllegalArgumentException("Null argument passed to insertPlaceholder");
        }

        // Decode bounds
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(placeholder, 0, placeholder.length, options);
        int width = options.outWidth;
        int height = options.outHeight;

        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image had bad height/width");
        }

        Uri uri =
                Storage.addPlaceholder(placeholder, width, height);
        if (uri == null) {
            return null;
        }
        return new Session(title, uri, timestamp);
    }

    /**
     * Converts an existing item into a placeholder for re-processing.
     *
     * @param uri the URI of an existing media item.
     * @return A session that can be used to update the progress of the new
     *         session.
     */
    public Session convertToPlaceholder(Uri uri) {
        return createSessionFromUri(uri);
    }

    /**
     * This converts the placeholder in to a real media item
     *
     * @param session the session that is being finished.
     * @param location the location of the image
     * @param orientation the orientation of the image
     * @param exif the exif of the image
     * @param jpeg the bytes of the image
     * @param width the width of the image
     * @param height the height of the image
     * @param mimeType the mime type of the image
     * @return The content URI of the new media item.
     */
    public Uri finishPlaceholder(Session session, Location location, int orientation,
                                 ExifInterface exif, byte[] jpeg, int width, int height, String mimeType) {

        Uri resultUri = Storage.updateImage(session.outputUri, mContext.getContentResolver(), session.outputTitle,
                session.time, location, orientation, exif, jpeg, width, height, mimeType);
        CameraUtil.broadcastNewPicture(mContext, resultUri);
        return resultUri;
    }

    /**
     * This changes the temporary placeholder jpeg without writing it to the media store
     *
     * @param session the session to update
     * @param jpeg the new placeholder bytes
     * @param width the width of the image
     * @param height the height of the image
     */
    public void replacePlaceholder(Session session,
                                   byte[] jpeg, int width, int height) {

        Storage.replacePlaceholder(session.outputUri,
                jpeg, width, height);
        CameraUtil.broadcastNewPicture(mContext, session.outputUri);
    }

    /**
     * Create a new session instance from the given URI by querying the media
     * store.
     * <p>
     * TODO: Make sure this works with types other than images when needed.
     */
    private Session createSessionFromUri(Uri uri) {
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

        return new Session(name, uri, date);
    }
}
