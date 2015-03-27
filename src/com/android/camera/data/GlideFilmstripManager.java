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
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
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
    private static final Tag TAG = new Tag("GlideFlmMgr");

    /** Default placeholder to display while images load */
    public static final int DEFAULT_PLACEHOLDER_RESOURCE = R.color.photo_placeholder;

    // This is the default GL texture size for K and below, it may be bigger,
    // it should not be smaller than this.
    private static final int DEFAULT_MAX_IMAGE_DISPLAY_SIZE = 2048;

    // Some phones have massive GL_Texture sizes. Prevent images from doing
    // overly large allocations by capping the texture size.
    private static final int MAX_GL_TEXTURE_SIZE = 4096;
    private static Size MAX_IMAGE_DISPLAY_SIZE;
    public static Size getMaxImageDisplaySize() {
        if (MAX_IMAGE_DISPLAY_SIZE == null) {
            Integer size = computeEglMaxTextureSize();
            if (size == null) {
                // Fallback to the default 2048 if a size is not found.
                MAX_IMAGE_DISPLAY_SIZE = new Size(DEFAULT_MAX_IMAGE_DISPLAY_SIZE,
                      DEFAULT_MAX_IMAGE_DISPLAY_SIZE);
            } else if (size > MAX_GL_TEXTURE_SIZE) {
                // Cap the display size to prevent Out of memory problems during
                // pre-allocation of huge bitmaps.
                MAX_IMAGE_DISPLAY_SIZE = new Size(MAX_GL_TEXTURE_SIZE, MAX_GL_TEXTURE_SIZE);
            } else {
                MAX_IMAGE_DISPLAY_SIZE = new Size(size, size);
            }
        }

        return MAX_IMAGE_DISPLAY_SIZE;
    }

    public static final Size MEDIASTORE_THUMB_SIZE = new Size(512, 384);
    public static final Size TINY_THUMB_SIZE = new Size(256, 256);

    // Estimated memory bandwidth for N5 and N6 is about 500MB/s
    // 500MBs * 1000000(Bytes per MB) / 4 (RGBA pixel) / 1000 (milli per S)
    // Give a 20% margin for error and real conditions.
    private static final int EST_PIXELS_PER_MILLI = 100000;

    // Estimated number of bytes that can be used to usually display a thumbnail
    // in under a frame at 60fps (16ms).
    public static final int MAXIMUM_SMOOTH_PIXELS = EST_PIXELS_PER_MILLI * 10 /* millis */;

    // Estimated number of bytes that can be used to generate a large thumbnail in under
    // (about) 3 frames at 60fps (16ms).
    public static final int MAXIMUM_FULL_RES_PIXELS = EST_PIXELS_PER_MILLI * 45 /* millis */;
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
     * jank problems or blank frames due to overly large textures.
     */
    public final DrawableRequestBuilder<Uri> loadFull(Uri uri, Key key, Size original) {
        Size size = clampSize(original, MAXIMUM_FULL_RES_PIXELS, getMaxImageDisplaySize());

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
        Size size = clampSize(original, MAXIMUM_SMOOTH_PIXELS, getMaxImageDisplaySize());
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
        Size size = clampSize(MEDIASTORE_THUMB_SIZE, MAXIMUM_SMOOTH_PIXELS, getMaxImageDisplaySize());
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
        Size size = clampSize(TINY_THUMB_SIZE, MAXIMUM_SMOOTH_PIXELS,  getMaxImageDisplaySize());
        return mTinyImageBuilder
              .clone()
              .load(uri)
              .signature(key)
              .override(size.width(), size.height());
    }

    /**
     * Given a size, compute a value such that it will downscale the original size
     * to fit within the maxSize bounding box and to be less than the provided area.
     *
     * This will never upscale sizes.
     */
    private static Size clampSize(Size original, double maxArea, Size maxSize) {
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

    private static Size computeFitWithinSize(Size original, Size maxSize) {
        double widthRatio = (double) maxSize.width() / original.width();
        double heightRatio = (double) maxSize.height() / original.height();

        double ratio = widthRatio > heightRatio ? heightRatio : widthRatio;

        // This rounds and ensures that (even with rounding and int conversion)
        // that the returned size is never larger than maxSize.
        return new Size(
              Math.min((int) Math.round(original.width() * ratio), maxSize.width()),
              Math.min((int) Math.round(original.height() * ratio), maxSize.height()));
    }

    /**
     * Ridiculous way to read the devices maximum texture size because no other
     * way is provided.
     */
    private static Integer computeEglMaxTextureSize() {
        EGLDisplay eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] majorMinor = new int[2];
        EGL14.eglInitialize(eglDisplay, majorMinor, 0, majorMinor, 1);

        int[] configAttr = {
              EGL14.EGL_COLOR_BUFFER_TYPE, EGL14.EGL_RGB_BUFFER,
              EGL14.EGL_LEVEL, 0,
              EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
              EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
              EGL14.EGL_NONE
        };
        EGLConfig[] eglConfigs = new EGLConfig[1];
        int[] configCount = new int[1];
        EGL14.eglChooseConfig(eglDisplay, configAttr, 0,
              eglConfigs, 0, 1, configCount, 0);

        if (configCount[0] == 0) {
            Log.w(TAG, "No EGL configurations found!");
            return null;
        }
        EGLConfig eglConfig = eglConfigs[0];

        // Create a tiny surface
        int[] eglSurfaceAttributes = {
              EGL14.EGL_WIDTH, 64,
              EGL14.EGL_HEIGHT, 64,
              EGL14.EGL_NONE
        };
        //
        EGLSurface eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig,
              eglSurfaceAttributes, 0);

        int[] eglContextAttributes = {
              EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
              EGL14.EGL_NONE
        };

        // Create an EGL context.
        EGLContext eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT,
              eglContextAttributes, 0);
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);

        // Actually read the Gl_MAX_TEXTURE_SIZE into the array.
        int[] maxSize = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxSize, 0);
        int result = maxSize[0];

        // Tear down the surface, context, and display.
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
              EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(eglDisplay, eglSurface);
        EGL14.eglDestroyContext(eglDisplay, eglContext);
        EGL14.eglTerminate(eglDisplay);

        // Return the computed max size.
        return result;
    }
}
