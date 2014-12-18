/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;

import com.android.camera.Storage;
import com.android.camera.util.Size;
import com.android.camera2.R;
import com.bumptech.glide.Glide;
import com.google.common.base.Optional;

import java.util.Date;

/**
 * This is used to represent a local data item that is in progress and not
 * yet in the media store.
 */
public class SessionItem implements FilmstripItem {
    protected final Metadata mMetaData;
    private FilmstripItemData mData;
    private final FilmstripItemAttributes mAttributes;
    private final Context mContext;

    public SessionItem(Context context, Uri uri) {
        mContext = context;
        mMetaData = new Metadata();
        mMetaData.setLoaded(true);

        Date creationDate = new Date();
        Size dimension = getSessionSize(uri);

        mData = new FilmstripItemData.Builder(uri)
              .withCreationDate(creationDate)
              .withLastModifiedDate(creationDate)
              .withDimensions(dimension)
              .build();

        mAttributes = FilmstripItemAttributes.DEFAULT;
    }

    private Size getSessionSize(Uri uri) {
        Point size = Storage.getSizeForSession(uri);
        return new Size(size.x, size.y);
    }

    @Override
    public View getView(Optional<View> optionalView, int viewWidthPx, int viewHeightPx,
          int placeHolderResourceId, LocalFilmstripDataAdapter adapter, boolean isInProgress,
          VideoClickedCallback videoClickedCallback) {
        ImageView imageView;

        if (optionalView.isPresent()) {
            imageView = (ImageView) optionalView.get();
        } else {
            imageView = new ImageView(mContext);
            imageView.setTag(R.id.mediadata_tag_viewtype, getItemViewType().ordinal());
        }

        Bitmap placeholder = Storage.getPlacerHolderForSession(mData.getUri());
        imageView.setImageBitmap(placeholder);
        imageView.setContentDescription(mContext.getResources().getString(
                R.string.media_processing_content_description));
        return imageView;
    }

    @Override
    public FilmstripItemType getItemViewType() {
        return FilmstripItemType.SESSION;
    }

    @Override
    public void loadFullImage(int width, int height, View view) {
    }

    @Override
    public boolean delete() {
        return false;
    }

    @Override
    public Optional<MediaDetails> getMediaDetails() {
        return Optional.absent();
    }

    @Override
    public FilmstripItem refresh() {
        Size dimension = getSessionSize(mData.getUri());

        mData = FilmstripItemData.Builder.from(mData)
              .withDimensions(dimension)
              .build();

        return this;
    }

    @Override
    public Metadata getMetadata() {
        return mMetaData;
    }

    @Override
    public FilmstripItemData getData() {
        return mData;
    }

    @Override
    public FilmstripItemAttributes getAttributes() {
        return mAttributes;
    }

    @Override
    public Optional<Bitmap> generateThumbnail(int boundingWidthPx, int boundingHeightPx) {
        return Optional.absent();
    }

    @Override
    public void recycle(View view) {
        Glide.clear(view);
    }

}
