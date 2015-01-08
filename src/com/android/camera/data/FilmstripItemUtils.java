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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.media.MediaMetadataRetriever;

import com.android.camera.debug.Log;

import java.io.InputStream;

import javax.microedition.khronos.opengles.GL11;

/**
 * An utility class for data in content provider.
 */
public class FilmstripItemUtils {

    private static final Log.Tag TAG = new Log.Tag("LocalDataUtil");

    /**
     * @param mimeType The MIME type to check.
     * @return Whether the MIME is a video type.
     */
    public static boolean isMimeTypeVideo(String mimeType) {
        return mimeType != null && mimeType.startsWith("video/");
    }

    /**
     * Checks whether the MIME type represents an image media item.
     *
     * @param mimeType The MIME type to check.
     * @return Whether the MIME is a image type.
     */
    public static boolean isMimeTypeImage(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }


    /**
     * Decodes the dimension of a bitmap.
     *
     * @param is An input stream with the data of the bitmap.
     * @return The decoded width/height is stored in Point.x/Point.y
     *         respectively.
     */
    public static Point decodeBitmapDimension(InputStream is) {
        Point size = null;
        BitmapFactory.Options justBoundsOpts = new BitmapFactory.Options();
        justBoundsOpts.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, justBoundsOpts);
        if (justBoundsOpts.outWidth > 0 && justBoundsOpts.outHeight > 0) {
            size = new Point(justBoundsOpts.outWidth, justBoundsOpts.outHeight);
        } else {
            Log.e(TAG, "Bitmap dimension decoding failed");
        }
        return size;
    }

    /**
     * Load the thumbnail of an image from an {@link java.io.InputStream}.
     *
     * @param stream The input stream of the image.
     * @param imageWidth Image width.
     * @param imageHeight Image height.
     * @param widthBound The bound of the width of the decoded image.
     * @param heightBound The bound of the height of the decoded image.
     * @param orientation The orientation of the image. The image will be rotated
     *                    clockwise in degrees.
     * @param maximumPixels The bound for the number of pixels of the decoded image.
     * @return {@code null} if the decoding failed.
     */
    public static Bitmap loadImageThumbnailFromStream(InputStream stream, int imageWidth,
            int imageHeight, int widthBound, int heightBound, int orientation,
            int maximumPixels) {

        /** 32K buffer. */
        byte[] decodeBuffer = new byte[32 * 1024];

        if (orientation % 180 != 0) {
            int dummy = imageHeight;
            imageHeight = imageWidth;
            imageWidth = dummy;
        }

        // Generate Bitmap of maximum size that fits into widthBound x heightBound.
        // Algorithm: start with full size and step down in powers of 2.
        int targetWidth = imageWidth;
        int targetHeight = imageHeight;
        int sampleSize = 1;
        while (targetHeight > heightBound || targetWidth > widthBound ||
                targetHeight > GL11.GL_MAX_TEXTURE_SIZE || targetWidth > GL11.GL_MAX_TEXTURE_SIZE ||
                targetHeight * targetWidth > maximumPixels) {
            sampleSize <<= 1;
            targetWidth = imageWidth / sampleSize;
            targetHeight = imageWidth / sampleSize;
        }

        // For large (> MAXIMUM_TEXTURE_SIZE) high aspect ratio (panorama)
        // Bitmap requests:
        //   Step 1: ask for double size.
        //   Step 2: scale maximum edge down to MAXIMUM_TEXTURE_SIZE.
        //
        // Here's the step 1: double size.
        if ((heightBound > GL11.GL_MAX_TEXTURE_SIZE || widthBound > GL11.GL_MAX_TEXTURE_SIZE) &&
                targetWidth * targetHeight < maximumPixels / 4 && sampleSize > 1) {
            sampleSize >>= 2;
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSize;
        opts.inTempStorage = decodeBuffer;
        Bitmap b = BitmapFactory.decodeStream(stream, null, opts);

        if (b == null) {
            return null;
        }

        // Step 2: scale maximum edge down to maximum texture size.
        // If Bitmap maximum edge > MAXIMUM_TEXTURE_SIZE, which can happen for panoramas,
        // scale to fit in MAXIMUM_TEXTURE_SIZE.
        if (b.getWidth() > GL11.GL_MAX_TEXTURE_SIZE || b.getHeight() >
                GL11.GL_MAX_TEXTURE_SIZE) {
            int maxEdge = Math.max(b.getWidth(), b.getHeight());
            b = Bitmap.createScaledBitmap(b, b.getWidth() * GL11.GL_MAX_TEXTURE_SIZE / maxEdge,
                    b.getHeight() * GL11.GL_MAX_TEXTURE_SIZE / maxEdge, false);
        }

        // Not called often because most modes save image data non-rotated.
        if (orientation != 0 && b != null) {
            Matrix m = new Matrix();
            m.setRotate(orientation);
            b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, false);
        }

        return b;
    }

    /**
     * Loads the thumbnail of a video.
     *
     * @param path The path to the video file.
     * @return {@code null} if the loading failed.
     */
    public static Bitmap loadVideoThumbnail(String path) {
        Bitmap bitmap = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            byte[] data = retriever.getEmbeddedPicture();
            if (data != null) {
                bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            }
            if (bitmap == null) {
                bitmap = retriever.getFrameAtTime();
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "MediaMetadataRetriever.setDataSource() fail:" + e.getMessage());
        }
        retriever.release();
        return bitmap;
    }
}
