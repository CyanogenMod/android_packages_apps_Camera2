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

package com.android.photos.data;

import android.net.Uri;

import java.io.File;

public interface MediaRetriever {
    public enum MediaSize {
        TemporaryThumbnail(5), Thumbnail(10), TemporaryPreview(15), Preview(20), Original(30);

        private final int mValue;

        private MediaSize(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }

        static MediaSize fromInteger(int value) {
            switch (value) {
                case 10:
                    return MediaSize.Thumbnail;
                case 20:
                    return MediaSize.Preview;
                case 30:
                    return MediaSize.Original;
                default:
                    throw new IllegalArgumentException();
            }
        }

        public boolean isBetterThan(MediaSize that) {
            return mValue > that.mValue;
        }

        public boolean isTemporary() {
            return this == TemporaryThumbnail || this == TemporaryPreview;
        }
    }

    /**
     * Returns the local File for the given Uri. If the image is not stored
     * locally, null should be returned. The image should not be retrieved if it
     * isn't already available.
     *
     * @param contentUri The media URI to search for.
     * @return The local File of the image if it is available or null if it
     *         isn't.
     */
    File getLocalFile(Uri contentUri);

    /**
     * Returns the fast access image type for a given image size, if supported.
     * This image should be smaller than size and should be quick to retrieve.
     * It does not have to obey the expected aspect ratio.
     *
     * @param contentUri The original media Uri.
     * @param size The target size to search for a fast-access image.
     * @return The fast image type supported for the given image size or null of
     *         no fast image is supported.
     */
    MediaSize getFastImageSize(Uri contentUri, MediaSize size);

    /**
     * Returns a byte array containing the contents of the fast temporary image
     * for a given image size. For example, a thumbnail may be smaller or of a
     * different aspect ratio than the generated thumbnail.
     *
     * @param contentUri The original media Uri.
     * @param temporarySize The target media size. Guaranteed to be a MediaSize
     *            for which isTemporary() returns true.
     * @return A byte array of contents for for the given contentUri and
     *         fastImageType. null can be retrieved if the quick retrieval
     *         fails.
     */
    byte[] getTemporaryImage(Uri contentUri, MediaSize temporarySize);

    /**
     * Retrieves an image and saves it to a file.
     *
     * @param contentUri The original media Uri.
     * @param size The target media size.
     * @param tempFile The file to write the bitmap to.
     * @return <code>true</code> on success.
     */
    boolean getMedia(Uri contentUri, MediaSize imageSize, File tempFile);

    /**
     * Normalizes a URI that may have additional parameters. It is fine to
     * return contentUri. This is executed on the calling thread, so it must be
     * a fast access operation and cannot depend, for example, on I/O.
     *
     * @param contentUri The URI to normalize
     * @param size The size of the image being requested
     * @return The normalized URI representation of contentUri.
     */
    Uri normalizeUri(Uri contentUri, MediaSize size);

    /**
     * Normalize the MediaSize for a given URI. Typically the size returned
     * would be the passed-in size. Some URIs may only have one size used and
     * should be treaded as Thumbnails, for example. This is executed on the
     * calling thread, so it must be a fast access operation and cannot depend,
     * for example, on I/O.
     *
     * @param contentUri The URI for the size being normalized.
     * @param size The size to be normalized.
     * @return The normalized size of the given URI.
     */
    MediaSize normalizeMediaSize(Uri contentUri, MediaSize size);
}
