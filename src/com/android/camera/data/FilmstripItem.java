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
import com.android.camera.util.Size;
import com.google.common.base.Optional;

import javax.annotation.Nonnull;

/**
 * An abstract interface that represents the Local filmstrip items.
 */
public interface FilmstripItem {
    static final Log.Tag TAG = new Log.Tag("FilmstripItem");

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
    public void recycle(@Nonnull View view);

    /**
     * Create or recycle an existing view (if provided) to render this item.
     *
     * @param adapter Data adapter for this data item.
     */
    public View getView(Optional<View> view,
          LocalFilmstripDataAdapter adapter, boolean isInProgress,
          VideoClickedCallback videoClickedCallback);

    /**
     * Configure the suggested width and height in pixels for this view to render at.
     *
     * @param widthPx Suggested width in pixels.
     * @param heightPx Suggested height in pixels.
     */
    public void setSuggestedSize(int widthPx, int heightPx);

    /**
     * Request to load a tiny preview image into the view as fast as possible.
     *
     * @param view View created by getView();
     */
    public void renderTiny(@Nonnull View view);

    /**
     * Request to load screen sized version of the image into the view.
     *
     * @param view View created by getView();
     */
    public void renderThumbnail(@Nonnull View view);

    /**
     * Request to load the highest possible resolution image supported.
     *
     * @param view View created by getView();
     */
    public void renderFullRes(@Nonnull View view);

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

    /**
     * Dimensions of this item.
     *
     * @return physical width and height in pixels.
     */
    public Size getDimensions();

    /**
     * Returns the rotation of the image in degrees clockwise. The valid values
     * are 0, 90, 180, and 270.
     */
    public int getOrientation();
}
