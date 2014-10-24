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

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import com.android.camera.debug.Log;
import com.android.camera.filmstrip.ImageData;

import java.util.Comparator;

/**
 * An abstract interface that represents the local media data. Also implements
 * Comparable interface so we can sort in DataAdapter.
 * Note that all the sub-class of LocalData are designed to be immutable, i.e:
 * all the members need to be final, and there is no setter. In this way, we
 * can guarantee thread safety for LocalData.
 */
public interface LocalData extends ImageData {
    /**
     * An action callback to be used for actions on the local media data items.
     */
    public static interface ActionCallback {
        /** Plays the video with the given URI and title. */
        public void playVideo(Uri uri, String title);
    }

    static final Log.Tag TAG = new Log.Tag("LocalData");

    public static final String MIME_TYPE_JPEG = "image/jpeg";

    // Data actions.
    public static final int DATA_ACTION_NONE = 0;
    public static final int DATA_ACTION_PLAY = 1;
    public static final int DATA_ACTION_DELETE = (1 << 1);
    public static final int DATA_ACTION_EDIT = (1 << 2);
    public static final int DATA_ACTION_SHARE = (1 << 3);

    // Local data types. Returned by getLocalDataType().
    /**
     * Constant for denoting a camera preview.
     */
    public static final int LOCAL_CAMERA_PREVIEW   = 1;
    /**
     * Constant for denoting an arbitrary view.
     */
    public static final int LOCAL_VIEW             = 2;
    /**
     * Constant for denoting a still image.
     */
    public static final int LOCAL_IMAGE            = 3;
    /**
     * Constant for denoting a video.
     */
    public static final int LOCAL_VIDEO            = 4;
    /**
     * Constant for denoting an in-progress item which should not be touched
     * before the related task is done. Data of this type should not support
     * any actions like sharing, editing, etc.
     */
    public static final int LOCAL_IN_PROGRESS_DATA = 5;

    // TODO: Re-think how the in-progress logic works. We shouldn't need to pass
    // in the information about whether this session is in progress.

    /**
     * Creates View to represent media.
     *
     * @param context The {@link android.content.Context} to create the view.
     * @param thumbWidth Width in pixels of the suggested zoomed out view/image size.
     * @param thumbHeight Height in pixels of the suggested zoomed out view/image size.
     * @param adapter Data adapter for this data item.
     */
    View getView(Context context, View recycled, int thumbWidth, int thumbHeight,
            int placeHolderResourceId, LocalDataAdapter adapter, boolean isInProgress,
            ActionCallback actionCallback);

    /** Returns a unique identifier for the view created by this data so that the view
     * can be reused.
     *
     * @see android.widget.BaseAdapter#getItemViewType(int)
     */
    LocalDataViewType getItemViewType();

   /**
     * Request resize of View created by getView().
     *
     * @param context The {@link android.content.Context} to create the view.
     * @param thumbWidth Width in pixels of the suggested zoomed out view/image size.
     * @param thumbHeight Height in pixels of the suggested zoomed out view/image size.
     * @param view View created by getView();
     * @param adapter Data adapter for this data item.
     */
    public void loadFullImage(Context context, int thumbWidth, int thumbHeight, View view,
        LocalDataAdapter adapter);

    /**
     * Gets the date when this data is created. The returned date is also used
     * for sorting data. Value is epoch milliseconds.
     *
     * @return The date when this data is created.
     * @see {@link NewestFirstComparator}
     */
    long getDateTaken();

    /**
     * Gets the date when this data is modified. The returned date is also used
     * for sorting data. Value is epoch seconds.
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

    /** Removes the data from the storage if possible. */
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
     * @return The mimetype of this data item, or null, if this item has no
     *         mimetype associated with it.
     */
    String getMimeType();

    /**
     * @return The media details (such as EXIF) for the data. {@code null} if
     * not available for the data.
     */
    MediaDetails getMediaDetails(Context context);

    /**
     * Returns the type of the local data defined by {@link LocalData}.
     *
     * @return The local data type. Could be one of the following:
     * {@code LOCAL_CAMERA_PREVIEW}, {@code LOCAL_VIEW}, {@code LOCAL_IMAGE},
     * {@code LOCAL_VIDEO}, {@code LOCAL_PHOTO_SPHERE},
     * {@code LOCAL_360_PHOTO_SPHERE}, and {@code LOCAL_RGBZ}
     */
    int getLocalDataType();

    /**
     * @return The size of the data in bytes
     */
    long getSizeInBytes();

    /**
     * Refresh the data content.
     *
     * @param context The Android {@link android.content.Context}.
     * @return A new LocalData object if success, null otherwise.
     */
    LocalData refresh(Context context);

    /**
     * @return the {@link android.content.ContentResolver} Id of the data.
     */
    long getContentId();

    /**
     * @return the metadata. Should never be {@code null}.
     */
    Bundle getMetadata();

    /**
     * Any media store attribute that can potentially change the local data
     * should be included in this signature, primarily oriented at detecting
     * edits.
     *
     * @return A string identifying the set of changeable attributes.
     */
    String getSignature();

    /**
     * @return whether the metadata is updated.
     */
    public boolean isMetadataUpdated();

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
