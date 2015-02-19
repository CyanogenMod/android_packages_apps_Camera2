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

import java.util.Date;
import java.util.UUID;

import javax.annotation.Nonnull;

/**
 * A LocalData that does nothing but only shows a view.
 */
public class PlaceholderItem implements FilmstripItem {
    private static final Log.Tag TAG = new Log.Tag("PlaceholderItem");
    private static final String SIMPLE_VIEW_URI_SCHEME = "simple_view_data";

    private static final FilmstripItemAttributes PLACEHOLDER_ITEM_ATTRIBUTES =
          new FilmstripItemAttributes.Builder()
                .build();

    private final View mView;
    private final Metadata mMetaData;
    private final FilmstripItemType mItemViewType;
    private final FilmstripItemData mItemData;
    private final FilmstripItemAttributes mAttributes;

    public PlaceholderItem(
          View v, FilmstripItemType viewType, int width, int height) {
        mView = v;
        mItemViewType = viewType;
        Size dimensions = new Size(width, height);
        Date creationDate = new Date(0);
        Date lastModifiedDate = new Date(0);
        mMetaData = new Metadata();
        mMetaData.setLoaded(true);
        Uri.Builder builder = new Uri.Builder();
        String uuid = UUID.randomUUID().toString();
        builder.scheme(SIMPLE_VIEW_URI_SCHEME).appendPath(uuid);
        Uri uri = builder.build();

        mItemData = new FilmstripItemData(
              -1,
              uuid,
              "",
              creationDate,
              lastModifiedDate,
              "" /* path */,
              uri,
              dimensions,
              0,
              0,
              Location.UNKNOWN);

        mAttributes = PLACEHOLDER_ITEM_ATTRIBUTES;
    }

    @Override
    public FilmstripItemData getData() {
        return mItemData;
    }

    @Override
    public FilmstripItemAttributes getAttributes() {
        return mAttributes;
    }

    @Override
    public FilmstripItemType getItemViewType() {
        return mItemViewType;
    }

    @Override
    public FilmstripItem refresh() {
        return this;
    }

    @Override
    public boolean delete() {
        return false;
    }

    @Override
    public View getView(Optional<View> optionalView,
          LocalFilmstripDataAdapter adapter, boolean isInProgress,
          VideoClickedCallback videoClickedCallback) {
        return mView;
    }

    @Override
    public void setSuggestedSize(int widthPx, int heightPx) { }

    @Override
    public void renderTiny(@Nonnull View view) { }

    @Override
    public void renderThumbnail(@Nonnull View view) { }

    @Override
    public void renderFullRes(@Nonnull View view) { }

    @Override
    public void recycle(@Nonnull View view) {
        // Do nothing.
    }

    @Override
    public Optional<MediaDetails> getMediaDetails() {
        return Optional.absent();
    }

    @Override
    public Metadata getMetadata() {
        return mMetaData;
    }

    @Override
    public Optional<Bitmap> generateThumbnail(int boundingWidthPx, int boundingHeightPx) {
        return Optional.absent();
    }

    @Override
    public Size getDimensions() {
        return mItemData.getDimensions();
    }

    @Override
    public int getOrientation() {
        return mItemData.getOrientation();
    }
}
