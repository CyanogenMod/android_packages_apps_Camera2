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
import com.android.camera.debug.Log;
import com.android.camera.util.Size;
import com.android.camera2.R;
import com.bumptech.glide.Glide;
import com.google.common.base.Optional;

import java.util.Date;

import javax.annotation.Nonnull;

/**
 * This is used to represent a local data item that is in progress and not
 * yet in the media store.
 */
public class SessionItem implements FilmstripItem {
    protected final Metadata mMetaData;
    private FilmstripItemData mData;
    private final FilmstripItemAttributes mAttributes;
    private final Context mContext;
    private final Uri mUri;

    /**
     * Creates a new session from the given URI.
     * @param context valid android application context.
     * @param uri the URI of the session.
     * @return If the session was found, a new SessionItem is returned.
     */
    public static Optional<SessionItem> create(Context context, Uri uri) {
        if (!Storage.containsPlaceholderSize(uri)) {
            return Optional.absent();
        }
        Size dimension = getSessionSize(uri);
        if (dimension == null) {
            return Optional.absent();
        }
        return Optional.of(new SessionItem(context, uri, dimension));
    }

    protected SessionItem(Context context, Uri uri, Size dimension) {
        mContext = context;
        mUri = uri;

        mMetaData = new Metadata();
        mMetaData.setLoaded(true);

        Date creationDate = new Date();
        mData = new FilmstripItemData.Builder(uri)
              .withCreationDate(creationDate)
              .withLastModifiedDate(creationDate)
              .withDimensions(dimension)
              .build();

        mAttributes = new FilmstripItemAttributes.Builder()
                .with(FilmstripItemAttributes.Attributes.IS_RENDERING)
                .build();
    }

    private static Size getSessionSize(Uri uri) {
        Point size = Storage.getSizeForSession(uri);
        if (size == null) {
            return null;
        }
        return new Size(size);
    }

    @Override
    public View getView(Optional<View> optionalView, LocalFilmstripDataAdapter adapter,
          boolean isInProgress, VideoClickedCallback videoClickedCallback) {
        ImageView imageView;

        if (optionalView.isPresent()) {
            imageView = (ImageView) optionalView.get();
        } else {
            imageView = new ImageView(mContext);
            imageView.setTag(R.id.mediadata_tag_viewtype, getItemViewType().ordinal());
        }

        Optional<Bitmap> placeholder = Storage.getPlaceholderForSession(mData.getUri());
        if (placeholder.isPresent()) {
            imageView.setImageBitmap(placeholder.get());
        } else {
            imageView.setImageResource(GlideFilmstripManager.DEFAULT_PLACEHOLDER_RESOURCE);
        }
        imageView.setContentDescription(mContext.getResources().getString(
                R.string.media_processing_content_description));
        return imageView;
    }

    @Override
    public FilmstripItemType getItemViewType() {
        return FilmstripItemType.SESSION;
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
        if (dimension == null) {
            Log.w(TAG, "Cannot refresh item, session does not exist.");
            return this;
        }

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
        return Storage.getPlaceholderForSession(mUri);
    }

    @Override
    public void recycle(@Nonnull View view) {
        Glide.clear(view);
    }

    @Override
    public Size getDimensions() {
        return mData.getDimensions();
    }

    @Override
    public int getOrientation() {
        return mData.getOrientation();
    }
}
