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

import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;

import com.android.camera.debug.Log;
import com.android.camera2.R;
import com.google.common.base.Optional;

/**
 * An abstract interface that represents the Local filmstrip items.
 */
public interface FilmstripItem {
    static final Log.Tag TAG = new Log.Tag("FilmstripItem");

    public static final int MEDIASTORE_THUMB_WIDTH = 512;
    public static final int MEDIASTORE_THUMB_HEIGHT = 384;

    // GL max texture size: keep bitmaps below this value.
    public static final int MAXIMUM_TEXTURE_SIZE = 2048;
    public static final int MAXIMUM_SMOOTH_TEXTURE_SIZE = 1024;

    /** Default placeholder to display while images load */
    static final int DEFAULT_PLACEHOLDER_RESOURCE = R.color.photo_placeholder;


    /**
     * An action callback to be used for actions on the filmstrip items.
     */
    public static interface VideoClickedCallback {

        /**
         * Plays the video with the given URI and title.
         */
        public void playVideo(Uri uri, String title);
    }

    /**
     * Returns the backing data for this filmstrip item.
     */
    public FilmstripItemData getData();

    /**
     * Returns the UI attributes of this filmstrip item.
     */
    public FilmstripItemAttributes getAttributes();

    /**
     * Returns the generic filmstrip item type.
     */
    public FilmstripItemType getItemViewType();

    /**
     * @return The media details (such as EXIF) for the data. {@code null} if not
     * available for the data.
     */
    public Optional<MediaDetails> getMediaDetails();

    /**
     * @return the metadata.
     */
    public Metadata getMetadata();

    /**
     * Gives the data a hint when its view is going to be removed from the view
     * hierarchy. {@code FilmStripView} should always call this function after its
     * corresponding view is removed from the view hierarchy.
     */
    public void recycle(View view);

    /**
     * Create or recycle an existing view (if provided) to render this item.
     *
     * @param viewWidthPx Width in pixels of the suggested zoomed out view/image size.
     * @param viewHeightPx Height in pixels of the suggested zoomed out view/image size.
     * @param adapter Data adapter for this data item.
     */
    public View getView(Optional<View> view, int viewWidthPx, int viewHeightPx,
          LocalFilmstripDataAdapter adapter, boolean isInProgress,
          VideoClickedCallback videoClickedCallback);

    /**
     * Request resize of View created by getView().
     *
     * @param thumbWidth Width in pixels of the suggested zoomed out view/image size.
     * @param thumbHeight Height in pixels of the suggested zoomed out view/image size.
     * @param view View created by getView();
     */
    public void loadFullImage(int thumbWidth, int thumbHeight, View view);

    /**
     * Removes the data from the storage if possible.
     */
    public boolean delete();

    /**
     * Refresh the data content.
     *
     * @return A new LocalData object if success, null otherwise.
     */
    public FilmstripItem refresh();

    /**
     * @return a bitmap thumbnail for this item.
     */
    public Optional<Bitmap> generateThumbnail(int boundingWidthPx, int boundingHeightPx);
}
