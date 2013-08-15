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

package com.android.camera.data;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;

import com.android.camera.ui.FilmStripView;

import java.util.Comparator;

/**
 * An abstract interface that represents the local media data. Also implements
 * Comparable interface so we can sort in DataAdapter.
 */
public interface LocalData extends FilmStripView.ImageData {
    static final String TAG = "CAM_LocalData";

    public static final int ACTION_NONE = 0;
    public static final int ACTION_PLAY = 1;
    public static final int ACTION_DELETE = (1 << 1);

    // Local data types. Returned by getLocalDataType().
    // Camera preview.
    public static final int LOCAL_CAMERA_PREVIEW = 1;
    // A data for showing an arbitrary view.
    public static final int LOCAL_VIEW           = 2;
    // A still image.
    public static final int LOCAL_IMAGE          = 3;
    // A video.
    public static final int LOCAL_VIDEO          = 4;
    // A still image but with valid PhotoSphere metadata.
    public static final int LOCAL_PHOTO_SPHERE   = 5;

    View getView(Context c, int width, int height, Drawable placeHolder);

    /**
     * Gets the date when this data is created. The returned date is also used
     * for sorting data.
     *
     * @return The date when this data is created.
     * @see {@link NewestFirstComparator}
     */
    long getDateTaken();

    /**
     * Gets the date when this data is modified. The returned date is also used
     * for sorting data.
     *
     * @return The date when this data is modified.
     * @see {@link NewestFirstComparator}
     */
    long getDateModified();

    /** Gets the title of this data */
    String getTitle();

    /**
     * Checks if the data actions (delete/play ...) can be applied on this data.
     *
     * @param actions The actions to check.
     * @return Whether all the actions are supported.
     */
    boolean isDataActionSupported(int actions);

    boolean delete(Context c);

    void onFullScreen(boolean fullScreen);

    /** Returns {@code true} if it allows swipe to filmstrip in full screen. */
    boolean canSwipeInFullScreen();

    /**
     * Returns the path to the data on the storage.
     *
     * @return Empty path if there's none.
     */
    String getPath();

    /**
     * Returns the content URI of this data item.
     *
     * @return {@code Uri.EMPTY} if not valid.
     */
    Uri getContentUri();

    /**
     * Return media data (such as EXIF) for the item.
     */
    MediaDetails getMediaDetails(Context context);

    /**
     * Returns the type of the local data defined by {@link LocalData}.
     *
     * @return The local data type. Could be one of the following:
     * {@code LOCAL_CAMERA_PREVIEW}, {@code LOCAL_VIEW}, {@code LOCAL_IMAGE},
     * {@code LOCAL_VIDEO}, and {@code LOCAL_PHOTO_SPHERE},
     */
    int getLocalDataType();

    /**
     * Refresh the data content.
     *
     * @param resolver {@link ContentResolver} to refresh the data.
     * @return {@code true} if success, {@code false} otherwise.
     */
    boolean refresh(ContentResolver resolver);

    static class NewestFirstComparator implements Comparator<LocalData> {

        /** Compare taken/modified date of LocalData in descent order to make
         newer data in the front.
         The negative numbers here are always considered "bigger" than
         positive ones. Thus, if any one of the numbers is negative, the logic
         is reversed. */
        private static int compareDate(long v1, long v2) {
            if (v1 >= 0 && v2 >= 0) {
                return ((v1 < v2) ? 1 : ((v1 > v2) ? -1 : 0));
            }
            return ((v2 < v1) ? 1 : ((v2 > v1) ? -1 : 0));
        }

        @Override
        public int compare(LocalData d1, LocalData d2) {
            int cmp = compareDate(d1.getDateTaken(), d2.getDateTaken());
            if (cmp == 0) {
                cmp = compareDate(d1.getDateModified(), d2.getDateModified());
            }
            if (cmp == 0) {
                cmp = d1.getTitle().compareTo(d2.getTitle());
            }
            return cmp;
        }
    }
}

