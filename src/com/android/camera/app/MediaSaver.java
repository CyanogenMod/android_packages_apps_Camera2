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

package com.android.camera.app;

import android.content.ContentValues;
import android.location.Location;
import android.net.Uri;

import com.android.camera.exif.ExifInterface;

/**
 * An interface defining the media saver which saves media files in the
 * background.
 */
public interface MediaSaver {

    /**
     * An interface defining the callback for task queue status changes.
     */
    public interface QueueListener {
        /**
         * The callback when the queue status changes. Every time a new
         * {@link com.android.camera.app.MediaSaver.QueueListener} is set by
         * {@link #setQueueListener(com.android.camera.app.MediaSaver.QueueListener)}
         * this callback will be invoked to notify the current status of the
         * queue.
         *
         * @param full Whether the queue is full.
         */
        public void onQueueStatus(boolean full);
    }

    /**
     * An interface defining the callback when a media is saved.
     */
    public interface OnMediaSavedListener {
        /**
         * The callback when the saving is done in the background.
         * @param uri The final content Uri of the saved media.
         */
        public void onMediaSaved(Uri uri);
    }

    /**
     * Checks whether the queue is full.
     */
    boolean isQueueFull();

    /**
     * Adds an image into {@link android.content.ContentResolver} and also
     * saves the file to the storage in the background.
     * <p/>
     * Equivalent to calling
     * {@link #addImage(byte[], String, long, Location, int, int, int,
     * ExifInterface, OnMediaSavedListener, String)}
     * with <code>image/jpeg</code> as <code>mimeType</code>.
     *
     * @param data The JPEG image data.
     * @param title The title of the image.
     * @param date The date when the image is created.
     * @param loc The location where the image is created. Can be {@code null}.
     * @param width The width of the image data before the orientation is
     *              applied.
     * @param height The height of the image data before the orientation is
     *               applied.
     * @param orientation The orientation of the image. The value should be a
     *                    degree of rotation in clockwise. Valid values are
     *                    0, 90, 180 and 270.
     * @param exif The EXIF data of this image.
     * @param l A callback object used when the saving is done.
     */
    void addImage(byte[] data, String title, long date, Location loc, int width, int height,
            int orientation, ExifInterface exif, OnMediaSavedListener l);

    /**
     * Adds an image into {@link android.content.ContentResolver} and also
     * saves the file to the storage in the background.
     *
     * @param data The image data.
     * @param title The title of the image.
     * @param date The date when the image is created.
     * @param loc The location where the image is created. Can be {@code null}.
     * @param width The width of the image data before the orientation is
     *              applied.
     * @param height The height of the image data before the orientation is
     *               applied.
     * @param orientation The orientation of the image. The value should be a
     *                    degree of rotation in clockwise. Valid values are
     *                    0, 90, 180 and 270.
     * @param exif The EXIF data of this image.
     * @param l A callback object used when the saving is done.
     * @param mimeType The mimeType of the image.
     */
    void addImage(byte[] data, String title, long date, Location loc, int width, int height,
            int orientation, ExifInterface exif, OnMediaSavedListener l, String mimeType);

    /**
     * Adds an image into {@link android.content.ContentResolver} and also
     * saves the file to the storage in the background. The width and height
     * will be obtained directly from the image data.
     *
     * @param data The JPEG image data.
     * @param title The title of the image.
     * @param date The date when the image is created.
     * @param loc The location where the image is created. Can be {@code null}.
     * @param orientation The orientation of the image. The value should be a
     *                    degree of rotation in clockwise. Valid values are
     *                    0, 90, 180 and 270.
     * @param exif The EXIF data of this image.
     * @param l A callback object used when the saving is done.
     */
    void addImage(byte[] data, String title, long date, Location loc, int orientation,
            ExifInterface exif, OnMediaSavedListener l);

    /**
     * Adds an image into {@link android.content.ContentResolver} and also
     * saves the file to the storage in the background. The time will be set by
     * {@link System#currentTimeMillis()}.
     * will be obtained directly from the image data.
     *
     * @param data The JPEG image data.
     * @param title The title of the image.
     * @param loc The location where the image is created. Can be {@code null}.
     * @param width The width of the image data before the orientation is
     *              applied.
     * @param height The height of the image data before the orientation is
     *               applied.
     * @param orientation
     * @param exif The EXIF data of this image.
     * @param l A callback object used when the saving is done.
     */
    void addImage(byte[] data, String title, Location loc, int width, int height, int orientation,
            ExifInterface exif, OnMediaSavedListener l);

    /**
     * Adds the video data into the {@link android.content.ContentResolver} in
     * the background. Only the database is updated here. The file should
     * already be created by {@link android.media.MediaRecorder}.
     * @param path The path of the video file on the storage.
     * @param values The values to be stored in the database.
     * @param l A callback object used when the saving is done.
     */
    void addVideo(String path, ContentValues values, OnMediaSavedListener l);

    /**
     * Sets the queue listener.
     */
    void setQueueListener(QueueListener l);
}
