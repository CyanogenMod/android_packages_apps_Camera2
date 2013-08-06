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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.Pools.SimplePool;
import android.util.Pools.SynchronizedPool;

import com.android.gallery3d.R;
import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DecodeUtils;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.util.ThreadPool.CancelListener;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.android.photos.data.MediaRetriever.MediaSize;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MediaCacheUtils {
    private static final String TAG = MediaCacheUtils.class.getSimpleName();
    private static int QUALITY = 80;
    private static final int BUFFER_SIZE = 4096;
    private static final SimplePool<byte[]> mBufferPool = new SynchronizedPool<byte[]>(5);

    private static final JobContext sJobStub = new JobContext() {

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void setCancelListener(CancelListener listener) {
        }

        @Override
        public boolean setMode(int mode) {
            return true;
        }
    };

    private static int mTargetThumbnailSize;
    private static int mTargetPreviewSize;

    public static void initialize(Context context) {
        Resources resources = context.getResources();
        mTargetThumbnailSize = resources.getDimensionPixelSize(R.dimen.size_thumbnail);
        mTargetPreviewSize = resources.getDimensionPixelSize(R.dimen.size_preview);
    }

    public static int getTargetSize(MediaSize size) {
        return (size == MediaSize.Thumbnail) ? mTargetThumbnailSize : mTargetPreviewSize;
    }

    public static boolean downsample(File inBitmap, MediaSize targetSize, File outBitmap) {
        if (MediaSize.Original == targetSize) {
            return false; // MediaCache should use the local path for this.
        }
        int size = getTargetSize(targetSize);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        // TODO: remove unnecessary job context from DecodeUtils.
        Bitmap bitmap = DecodeUtils.decodeThumbnail(sJobStub, inBitmap.getPath(), options, size,
                MediaItem.TYPE_THUMBNAIL);
        boolean success = (bitmap != null);
        if (success) {
            success = writeAndRecycle(bitmap, outBitmap);
        }
        return success;
    }

    public static boolean downsample(Bitmap inBitmap, MediaSize size, File outBitmap) {
        if (MediaSize.Original == size) {
            return false; // MediaCache should use the local path for this.
        }
        int targetSize = getTargetSize(size);
        boolean success;
        if (!needsDownsample(inBitmap, size)) {
            success = writeAndRecycle(inBitmap, outBitmap);
        } else {
            float maxDimension = Math.max(inBitmap.getWidth(), inBitmap.getHeight());
            float scale = targetSize / maxDimension;
            int targetWidth = Math.round(scale * inBitmap.getWidth());
            int targetHeight = Math.round(scale * inBitmap.getHeight());
            Bitmap scaled = Bitmap.createScaledBitmap(inBitmap, targetWidth, targetHeight, false);
            success = writeAndRecycle(scaled, outBitmap);
            inBitmap.recycle();
        }
        return success;
    }

    public static boolean extractImageFromVideo(File inVideo, File outBitmap) {
        Bitmap bitmap = BitmapUtils.createVideoThumbnail(inVideo.getPath());
        return writeAndRecycle(bitmap, outBitmap);
    }

    public static boolean needsDownsample(Bitmap bitmap, MediaSize size) {
        if (size == MediaSize.Original) {
            return false;
        }
        int targetSize = getTargetSize(size);
        int maxDimension = Math.max(bitmap.getWidth(), bitmap.getHeight());
        return maxDimension > (targetSize * 4 / 3);
    }

    public static boolean writeAndRecycle(Bitmap bitmap, File outBitmap) {
        boolean success = writeToFile(bitmap, outBitmap);
        bitmap.recycle();
        return success;
    }

    public static boolean writeToFile(Bitmap bitmap, File outBitmap) {
        boolean success = false;
        try {
            FileOutputStream out = new FileOutputStream(outBitmap);
            success = bitmap.compress(CompressFormat.JPEG, QUALITY, out);
            out.close();
        } catch (IOException e) {
            Log.w(TAG, "Couldn't write bitmap to cache", e);
            // success is already false
        }
        return success;
    }

    public static int copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = mBufferPool.acquire();
        if (buffer == null) {
            buffer = new byte[BUFFER_SIZE];
        }
        try {
            int totalWritten = 0;
            int bytesRead;
            while ((bytesRead = in.read(buffer)) >= 0) {
                out.write(buffer, 0, bytesRead);
                totalWritten += bytesRead;
            }
            return totalWritten;
        } finally {
            Utils.closeSilently(in);
            Utils.closeSilently(out);
            mBufferPool.release(buffer);
        }
    }
}
