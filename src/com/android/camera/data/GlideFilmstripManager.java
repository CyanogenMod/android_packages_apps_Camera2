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

    // GL max texture size: keep bitmaps below this value.
    public static final int MAXIMUM_TEXTURE_SIZE = 2048;
    public static final int MAXIMUM_SMOOTH_PIXELS = 1024 * 1024;

    public static final int MEDIASTORE_THUMB_WIDTH = 512;
    public static final int MEDIASTORE_THUMB_HEIGHT = 384;

    public static final int TINY_THUMBNAIL_SIZE = 256;

    private static final int JPEG_COMPRESS_QUALITY = 90;

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
              .dontAnimate();

        mLargeImageBuilder = request
              .fromMediaStore()
              .encoder(drawableEncoder)
              .fitCenter()
              .dontAnimate();
    }

    /**
     * Create a full size drawable request for a given width and height that is
     * as large as we can reasonably load into a view without causing massive
     * jank problems.
     */
    public final DrawableRequestBuilder<Uri> loadFull(Uri uri, Key key, int width,
          int height) {
        // compute a ratio such that viewWidth and viewHeight are less than
        // MAXIMUM_SMOOTH_TEXTURE_SIZE but maintain their aspect ratio.
        float downscaleRatio = downscaleRatioToFit(width, height,
              (double) MAXIMUM_TEXTURE_SIZE * MAXIMUM_TEXTURE_SIZE);

        return mLargeImageBuilder
              .clone()
              .load(uri)
              .signature(key)
              .override(
                    Math.round(width * downscaleRatio),
                    Math.round(height * downscaleRatio));
    }

    /**
     * Create a full size drawable request for a given width and height that is
     * smaller than loadFull, but is intended be large enough to fill the screen
     * pixels.
     */
    public DrawableRequestBuilder<Uri> loadScreen(Uri uri, Key key, int width,
          int height) {
        // compute a ratio such that viewWidth and viewHeight are less than
        // MAXIMUM_SMOOTH_TEXTURE_SIZE but maintain their aspect ratio.
        float downscaleRatio = downscaleRatioToFit(width, height, (double) MAXIMUM_SMOOTH_PIXELS);

        return mLargeImageBuilder
              .clone()
              .load(uri)
              .signature(key)
              .override(
                    Math.round(width * downscaleRatio),
                    Math.round(height * downscaleRatio));
    }

    /**
     * Create a small thumbnail sized image that has the same bounds as the
     * media store thumbnail images.
     *
     * If the Uri points at an animated gif, the gif will not play.
     */
    public GenericRequestBuilder<Uri, ?, ?, GlideDrawable> loadMediaStoreThumb(Uri uri, Key key) {
        return mTinyImageBuilder
              .clone()
              .load(uri)
              .signature(key)
              .placeholder(DEFAULT_PLACEHOLDER_RESOURCE)
              // This attempts to ensure we load the cached media store version.
              .override(MEDIASTORE_THUMB_WIDTH, MEDIASTORE_THUMB_HEIGHT);
    }

    /**
     * Create very tiny thumbnail request that should complete as fast
     * as possible.
     *
     * If the Uri points at an animated gif, the gif will not play.
     */
    public GenericRequestBuilder<Uri, ?, ?, GlideDrawable> loadTinyThumb(Uri uri, Key key) {
        return mTinyImageBuilder
              .clone()
              .load(uri)
              .signature(key)
              .placeholder(DEFAULT_PLACEHOLDER_RESOURCE)
              .override(TINY_THUMBNAIL_SIZE, TINY_THUMBNAIL_SIZE);
    }

    private float downscaleRatioToFit(int width, int height, double area) {
        // Compute a ratio that will keep the area of the image within the fit size parameter.

        float ratio = (float) Math.sqrt(area / (height * width));
        return Math.min(ratio, 1.0f);
    }
}
