/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.data;

import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.util.ThreadPool.CancelListener;
import com.android.gallery3d.util.ThreadPool.JobContext;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.InputStream;

public class DecodeUtils {
    private static final String TAG = "DecodeService";

    private static class DecodeCanceller implements CancelListener {
        Options mOptions;
        public DecodeCanceller(Options options) {
            mOptions = options;
        }
        public void onCancel() {
            mOptions.requestCancelDecode();
        }
    }

    public static Bitmap requestDecode(JobContext jc, final String filePath,
            Options options) {
        if (options == null) options = new Options();
        jc.setCancelListener(new DecodeCanceller(options));
        return ensureGLCompatibleBitmap(
                BitmapFactory.decodeFile(filePath, options));
    }

    public static Bitmap requestDecode(JobContext jc, FileDescriptor fd, Options options) {
        if (options == null) options = new Options();
        jc.setCancelListener(new DecodeCanceller(options));
        return ensureGLCompatibleBitmap(
                BitmapFactory.decodeFileDescriptor(fd, null, options));
    }

    public static Bitmap requestDecode(JobContext jc, byte[] bytes,
            Options options) {
        return requestDecode(jc, bytes, 0, bytes.length, options);
    }

    public static Bitmap requestDecode(JobContext jc, byte[] bytes, int offset,
            int length, Options options) {
        if (options == null) options = new Options();
        jc.setCancelListener(new DecodeCanceller(options));
        return ensureGLCompatibleBitmap(
                BitmapFactory.decodeByteArray(bytes, offset, length, options));
    }

    public static Bitmap requestDecode(JobContext jc, final String filePath,
            Options options, int targetSize) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(filePath);
            FileDescriptor fd = fis.getFD();
            return requestDecode(jc, fd, options, targetSize);
        } catch (Exception ex) {
            Log.w(TAG, ex);
            return null;
        } finally {
            Utils.closeSilently(fis);
        }
    }

    public static Bitmap requestDecode(JobContext jc, FileDescriptor fd,
            Options options, int targetSize) {
        if (options == null) options = new Options();
        jc.setCancelListener(new DecodeCanceller(options));

        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fd, null, options);
        if (jc.isCancelled()) return null;

        options.inSampleSize = BitmapUtils.computeSampleSizeLarger(
                options.outWidth, options.outHeight, targetSize);
        options.inJustDecodeBounds = false;

        Bitmap result = BitmapFactory.decodeFileDescriptor(fd, null, options);
        // We need to resize down if the decoder does not support inSampleSize.
        // (For example, GIF images.)
        result = BitmapUtils.resizeDownIfTooBig(result, targetSize, true);
        return ensureGLCompatibleBitmap(result);
    }

    /**
     * Decodes the bitmap from the given byte array if the image size is larger than the given
     * requirement.
     *
     * Note: The returned image may be resized down. However, both width and height must be
     * larger than the <code>targetSize</code>.
     */
    public static Bitmap requestDecodeIfBigEnough(JobContext jc, byte[] data,
            Options options, int targetSize) {
        if (options == null) options = new Options();
        jc.setCancelListener(new DecodeCanceller(options));

        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (jc.isCancelled()) return null;
        if (options.outWidth < targetSize || options.outHeight < targetSize) {
            return null;
        }
        options.inSampleSize = BitmapUtils.computeSampleSizeLarger(
                options.outWidth, options.outHeight, targetSize);
        options.inJustDecodeBounds = false;
        return ensureGLCompatibleBitmap(
                BitmapFactory.decodeByteArray(data, 0, data.length, options));
    }

    public static Bitmap requestDecode(JobContext jc,
            FileDescriptor fileDescriptor, Rect paddings, Options options) {
        if (options == null) options = new Options();
        jc.setCancelListener(new DecodeCanceller(options));
        return ensureGLCompatibleBitmap(BitmapFactory.decodeFileDescriptor
                (fileDescriptor, paddings, options));
    }

    // TODO: This function should not be called directly from
    // DecodeUtils.requestDecode(...), since we don't have the knowledge
    // if the bitmap will be uploaded to GL.
    public static Bitmap ensureGLCompatibleBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.getConfig() != null) return bitmap;
        Bitmap newBitmap = bitmap.copy(Config.ARGB_8888, false);
        bitmap.recycle();
        return newBitmap;
    }

    public static BitmapRegionDecoder requestCreateBitmapRegionDecoder(
            JobContext jc, byte[] bytes, int offset, int length,
            boolean shareable) {
        if (offset < 0 || length <= 0 || offset + length > bytes.length) {
            throw new IllegalArgumentException(String.format(
                    "offset = %s, length = %s, bytes = %s",
                    offset, length, bytes.length));
        }

        try {
            return BitmapRegionDecoder.newInstance(
                    bytes, offset, length, shareable);
        } catch (Throwable t)  {
            Log.w(TAG, t);
            return null;
        }
    }

    public static BitmapRegionDecoder requestCreateBitmapRegionDecoder(
            JobContext jc, String filePath, boolean shareable) {
        try {
            return BitmapRegionDecoder.newInstance(filePath, shareable);
        } catch (Throwable t)  {
            Log.w(TAG, t);
            return null;
        }
    }

    public static BitmapRegionDecoder requestCreateBitmapRegionDecoder(
            JobContext jc, FileDescriptor fd, boolean shareable) {
        try {
            return BitmapRegionDecoder.newInstance(fd, shareable);
        } catch (Throwable t)  {
            Log.w(TAG, t);
            return null;
        }
    }

    public static BitmapRegionDecoder requestCreateBitmapRegionDecoder(
            JobContext jc, InputStream is, boolean shareable) {
        try {
            return BitmapRegionDecoder.newInstance(is, shareable);
        } catch (Throwable t)  {
            // We often cancel the creating of bitmap region decoder,
            // so just log one line.
            Log.w(TAG, "requestCreateBitmapRegionDecoder: " + t);
            return null;
        }
    }

    public static BitmapRegionDecoder requestCreateBitmapRegionDecoder(
            JobContext jc, Uri uri, ContentResolver resolver,
            boolean shareable) {
        ParcelFileDescriptor pfd = null;
        try {
            pfd = resolver.openFileDescriptor(uri, "r");
            return BitmapRegionDecoder.newInstance(
                    pfd.getFileDescriptor(), shareable);
        } catch (Throwable t) {
            Log.w(TAG, t);
            return null;
        } finally {
            Utils.closeSilently(pfd);
        }
    }
}
