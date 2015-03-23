/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.net.Uri;

import com.android.camera.util.Size;
import com.android.camera2.R;
import com.bumptech.glide.DrawableRequestBuilder;
import com.bumptech.glide.GenericRequestBuilder;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.resource.bitmap.BitmapEncoder;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.load.resource.gif.GifResourceEncoder;
import com.bumptech.glide.load.resource.gifbitmap.GifBitmapWrapperResourceEncoder;
import com.bumptech.glide.load.resource.transcode.BitmapToGlideDrawableTranscoder;

/**
 * Manage common glide image requests for the camera filmstrip.
 */
public final class GlideFilmstripManager {
    /** Default placeholder to display while images load */
    public static final int DEFAULT_PLACEHOLDER_RESOURCE = R.color.photo_placeholder;

    // GL max texture size: keep the longest edge of a bitmap below this value.
    // This is the default for android K and below.
    public static final Size MAX_GL_TEXTURE_SIZE = new Size(2048, 2048);
    public static final Size MEDIASTORE_THUMB_SIZE = new Size(512, 384);
    public static final Size TINY_THUMB_SIZE = new Size(256, 256);

    // Estimated number of pixels that can be used to generate a thumbnail
    // smoothly.
    public static final int MAXIMUM_SMOOTH_PIXELS = 1024 * 1024;
    public static final int MAXIMUM_FULL_RES_PIXELS = 2048 * 2048;
    public static final int JPEG_COMPRESS_QUALITY = 90;

    private final GenericRequestBuilder<Uri, ?, ?, GlideDrawable> mTinyImageBuilder;
    private final DrawableRequestBuilder<Uri> mLargeImageBuilder;

    public GlideFilmstripManager(Context context) {
        Glide glide = Glide.get(context);
        BitmapEncoder bitmapEncoder = new BitmapEncoder(Bitmap.CompressFormat.JPEG,
              JPEG_COMPRESS_QUALITY);
        GifBitmapWrapperResourceEncoder drawableEncoder = new GifBitmapWrapperResourceEncoder(
              bitmapEncoder,
              new GifResourceEncoder(glide.getBitmapPool()));
        RequestManager request = Glide.with(context);

        mTinyImageBuilder = request
              .fromMediaStore()
              .asBitmap() // This prevents gifs from animating at tiny sizes.
              .transcode(new BitmapToGlideDrawableTranscoder(context), GlideDrawable.class)
              .fitCenter()
              .placeholder(DEFAULT_PLACEHOLDER_RESOURCE)
              .dontAnimate();

        mLargeImageBuilder = request
              .fromMediaStore()
              .encoder(drawableEncoder)
              .fitCenter()
              .placeholder(DEFAULT_PLACEHOLDER_RESOURCE)
              .dontAnimate();
    }

    /**
     * Create a full size drawable request for a given width and height that is
     * as large as we can reasonably load into a view without causing massive
     * jank problems or blank frames due to overly large
     */
    public final DrawableRequestBuilder<Uri> loadFull(Uri uri, Key key, Size original) {
        Size size = clampSize(original, MAXIMUM_FULL_RES_PIXELS, MAX_GL_TEXTURE_SIZE);

        return mLargeImageBuilder
              .clone()
              .load(uri)
              .signature(key)
              .override(size.width(), size.height());
    }

    /**
     * Create a full size drawable request for a given width and height that is
     * smaller than loadFull, but is intended be large enough to fill the screen
     * pixels.
     */
    public DrawableRequestBuilder<Uri> loadScreen(Uri uri, Key key, Size original) {
        Size size = clampSize(original, MAXIMUM_SMOOTH_PIXELS, MAX_GL_TEXTURE_SIZE);
        return mLargeImageBuilder
              .clone()
              .load(uri)
              .signature(key)
              .override(size.width(), size.height());
    }

    /**
     * Create a small thumbnail sized image that has the same bounds as the
     * media store thumbnail images.
     *
     * If the Uri points at an animated gif, the gif will not play.
     */
    public GenericRequestBuilder<Uri, ?, ?, GlideDrawable> loadMediaStoreThumb(Uri uri, Key key) {
        Size size = clampSize(MEDIASTORE_THUMB_SIZE, MAXIMUM_SMOOTH_PIXELS, MAX_GL_TEXTURE_SIZE);
        return mTinyImageBuilder
              .clone()
              .load(uri)
              .signature(key)
              // This attempts to ensure we load the cached media store version.
              .override(size.width(), size.height());
    }

    /**
     * Create very tiny thumbnail request that should complete as fast
     * as possible.
     *
     * If the Uri points at an animated gif, the gif will not play.
     */
    public GenericRequestBuilder<Uri, ?, ?, GlideDrawable> loadTinyThumb(Uri uri, Key key) {
        Size size = clampSize(TINY_THUMB_SIZE, MAXIMUM_SMOOTH_PIXELS, MAX_GL_TEXTURE_SIZE);
        return mTinyImageBuilder
              .clone()
              .load(uri)
              .signature(key)
              .override(size.width(), size.height());
    }

    /**
     * Given a size, compute a value such that it will downscale the original size
     * to fit within the maxSize bounding box and be less than the provided area.
     *
     * This will never upscale sizes.
     */
    private Size clampSize(Size original, double maxArea, Size maxSize) {
        if (original.getWidth() * original.getHeight() < maxArea &&
              original.getWidth() < maxSize.getWidth() &&
              original.getHeight() < maxSize.getHeight()) {
            // In several cases, the size is smaller than the max, and the area is
            // smaller than the max area.
            return original;
        }

        // Compute a ratio that will keep the number of pixels in the image (hence,
        // the number of bytes that can be copied into memory) under the maxArea.
        double ratio = Math.min(Math.sqrt(maxArea / original.area()), 1.0f);
        int width = (int) Math.round(original.width() * ratio);
        int height = (int) Math.round(original.height() * ratio);

        // If that ratio results in an image where the edge length is still too large,
        // constrain based on max edge length instead.
        if (width > maxSize.width() || height > maxSize.height()) {
            return computeFitWithinSize(original, maxSize);
        }

        return new Size(width, height);
    }

    private Size computeFitWithinSize(Size original, Size maxSize) {
        double widthRatio = (double) maxSize.width() / original.width();
        double heightRatio = (double) maxSize.height() / original.height();

        double ratio = widthRatio > heightRatio ? heightRatio : widthRatio;

        // This rounds and ensures that (even with rounding and int conversion)
        // that the returned size is never larger than maxSize.
        return new Size(
              Math.min((int) Math.round(original.width() * ratio), maxSize.width()),
              Math.min((int) Math.round(original.height() * ratio), maxSize.height()));
    }
}
