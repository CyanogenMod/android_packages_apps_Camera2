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

import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.android.gallery3d.common.BitmapUtils;

import java.io.File;
import java.io.IOException;

public class FileRetriever implements MediaRetriever {
    private static final String TAG = FileRetriever.class.getSimpleName();

    @Override
    public File getLocalFile(Uri contentUri) {
        return new File(contentUri.getPath());
    }

    @Override
    public MediaSize getFastImageSize(Uri contentUri, MediaSize size) {
        if (isVideo(contentUri)) {
            return null;
        }
        return MediaSize.TemporaryThumbnail;
    }

    @Override
    public byte[] getTemporaryImage(Uri contentUri, MediaSize fastImageSize) {

        try {
            ExifInterface exif = new ExifInterface(contentUri.getPath());
            if (exif.hasThumbnail()) {
                return exif.getThumbnail();
            }
        } catch (IOException e) {
            Log.w(TAG, "Unable to load exif for " + contentUri);
        }
        return null;
    }

    @Override
    public boolean getMedia(Uri contentUri, MediaSize imageSize, File tempFile) {
        if (imageSize == MediaSize.Original) {
            return false; // getLocalFile should always return the original.
        }
        if (imageSize == MediaSize.Thumbnail) {
            File preview = MediaCache.getInstance().getCachedFile(contentUri, MediaSize.Preview);
            if (preview != null) {
                // Just downsample the preview, it is faster.
                return MediaCacheUtils.downsample(preview, imageSize, tempFile);
            }
        }
        File highRes = new File(contentUri.getPath());
        boolean success;
        if (!isVideo(contentUri)) {
            success = MediaCacheUtils.downsample(highRes, imageSize, tempFile);
        } else {
            // Video needs to extract the bitmap.
            Bitmap bitmap = BitmapUtils.createVideoThumbnail(highRes.getPath());
            if (bitmap == null) {
                return false;
            } else if (imageSize == MediaSize.Thumbnail
                    && !MediaCacheUtils.needsDownsample(bitmap, MediaSize.Preview)
                    && MediaCacheUtils.writeToFile(bitmap, tempFile)) {
                // Opportunistically save preview
                MediaCache mediaCache = MediaCache.getInstance();
                mediaCache.insertIntoCache(contentUri, MediaSize.Preview, tempFile);
            }
            // Now scale the image
            success = MediaCacheUtils.downsample(bitmap, imageSize, tempFile);
        }
        return success;
    }

    @Override
    public Uri normalizeUri(Uri contentUri, MediaSize size) {
        return contentUri;
    }

    @Override
    public MediaSize normalizeMediaSize(Uri contentUri, MediaSize size) {
        return size;
    }

    private static boolean isVideo(Uri uri) {
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        String mimeType = mimeTypeMap.getMimeTypeFromExtension(extension);
        return (mimeType != null && mimeType.startsWith("video/"));
    }
}
