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

import android.net.Uri;

import com.android.camera.util.Size;

import java.util.Date;

/**
 * Represents an immutable set of backing data. No object or value
 * returned from this object should be mutable.
 */
public class FilmstripItemData {
    // TODO Make these enum values.
    public static final String MIME_TYPE_JPEG = "image/jpeg";
    public static final String MIME_TYPE_GIF = "image/gif";
    public static final String MIME_TYPE_PHOTOSPHERE = "application/vnd.google.panorama360+jpg";
    public static final String MIME_TYPE_MP4 = "video/mp4";

    private final long mContentId;
    private final String mTitle;
    private final String mMimeType;
    private final Date mCreationDate;
    private final Date mLastModifiedDate;
    private final String mFilePath;
    private final Uri mUri;
    private final Size mDimensions;
    private final long mSizeInBytes;
    private final int mOrientation;
    private final Location mLocation;

    public FilmstripItemData(
          long contentId,
          String title,
          String mimeType,
          Date creationDate,
          Date lastModifiedDate,
          String filePath,
          Uri uri,
          Size dimensions,
          long sizeInBytes,
          int orientation,
          Location location) {
        mContentId = contentId;
        mTitle = title;
        mMimeType = mimeType;
        mCreationDate = creationDate;
        mLastModifiedDate = lastModifiedDate;
        mFilePath = filePath;
        mUri = uri;
        mDimensions = dimensions;
        mSizeInBytes = sizeInBytes;
        mOrientation = orientation;
        mLocation = location;
    }

    public long getContentId() {
        return mContentId;
    }

    /**
     * Gets the string title of this item. May be used for sorting.
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * @return The mimetype of this data item, or null, if this item has no
     *         mimetype associated with it.
     */
    public String getMimeType() {
        return mMimeType;
    }

    /**
     * Gets the date when this item was created. The returned date may be used
     * for sorting.
     */
    public Date getCreationDate() {
        return mCreationDate;
    }

    /**
     * Gets the date when this item was last modified. The returned date may
     * be used for sorting.
     */
    public Date getLastModifiedDate() {
        return mLastModifiedDate;
    }

    /**
     * Returns the path to the data on the storage.
     */
    public String getFilePath() {
        return mFilePath;
    }

    /**
     * @return The URI of this data. Must be a unique one and not null.
     */
    public Uri getUri() {
        return mUri;
    }

    /**
     * Dimensions of this item.
     *
     * @return physical width and height in pixels.
     */
    /* package */ Size getDimensions() {
        return mDimensions;
    }

    /**
     * @return total number of bytes that represent this item.
     */
    public long getSizeInBytes() {
        return mSizeInBytes;
    }

    /**
     * Returns the rotation of the image in degrees clockwise. The valid values
     * are 0, 90, 180, and 270.
     */
    /* package */ int getOrientation() {
        return mOrientation;
    }

    public Location getLocation() {
        return mLocation;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FilmstripItemData {");
        sb.append("id:");
        sb.append(mContentId);
        sb.append(",title:");
        sb.append(mTitle);
        sb.append(",mimeType:");
        sb.append(mMimeType);
        sb.append(",creationDate:");
        sb.append(mCreationDate);
        sb.append(",lastModifiedDate:");
        sb.append(mLastModifiedDate);
        sb.append(",filePath:");
        sb.append(mFilePath);
        sb.append(",uri:");
        sb.append(mUri);
        sb.append(",dimensions:");
        sb.append(mDimensions);
        sb.append(",sizeInBytes:");
        sb.append(mSizeInBytes);
        sb.append(",orientation:");
        sb.append(mOrientation);
        sb.append(",location:");
        sb.append(mLocation);
        sb.append("}");
        return sb.toString();
    }

    public static class Builder {
        public static final Date EMPTY = new Date(0);
        public static final Size ZERO = new Size(0, 0);

        private long mContentId = -1;
        private String mTitle = "";
        private String mMimeType = "";
        private Date mCreationDate = EMPTY;
        private Date mLastModifiedDate = EMPTY;
        private String mFilePath = "";
        private final Uri mUri;
        private Size mDimensions = ZERO;
        private long mSizeInBytes = 0;
        private int mOrientation = 0;
        private Location mLocation = Location.UNKNOWN;

        public Builder(Uri uri) {
            mUri = uri;
        }

        public FilmstripItemData build() {
            return new FilmstripItemData(
                  mContentId,
                  mTitle,
                  mMimeType,
                  mCreationDate,
                  mLastModifiedDate,
                  mFilePath,
                  mUri,
                  mDimensions,
                  mSizeInBytes,
                  mOrientation,
                  mLocation
            );
        }

        public static Builder from(FilmstripItemData data) {
            Builder builder = new Builder(data.getUri());
            builder.mContentId = data.getContentId();
            builder.mTitle = data.getTitle();
            builder.mMimeType = data.getMimeType();
            builder.mCreationDate = data.getCreationDate();
            builder.mLastModifiedDate = data.getLastModifiedDate();
            builder.mFilePath = data.getFilePath();
            builder.mDimensions = data.getDimensions();
            builder.mSizeInBytes = data.getSizeInBytes();
            builder.mOrientation = data.getOrientation();
            builder.mLocation = data.getLocation();
            return builder;
        }

        public Builder withContentId(long contentId) {
            mContentId = contentId;
            return this;
        }

        public Builder withTitle(String title) {
            mTitle = title;
            return this;
        }

        public Builder withMimeType(String mimeType) {
            mMimeType = mimeType;
            return this;
        }

        public Builder withCreationDate(Date creationDate) {
            mCreationDate = creationDate;
            return this;
        }

        public Builder withLastModifiedDate(Date lastModifiedDate) {
            mLastModifiedDate = lastModifiedDate;
            return this;
        }

        public Builder withFilePath(String filePath) {
            mFilePath = filePath;
            return this;
        }

        public Builder withDimensions(Size dimensions) {
            mDimensions = dimensions;
            return this;
        }

        public Builder withSizeInBytes(long sizeInBytes) {
            mSizeInBytes = sizeInBytes;
            return this;
        }

        public Builder withOrientation(int orientation) {
            mOrientation = orientation;
            return this;
        }

        public Builder withLocation(Location location) {
            mLocation = location;
            return this;
        }
    }
}
