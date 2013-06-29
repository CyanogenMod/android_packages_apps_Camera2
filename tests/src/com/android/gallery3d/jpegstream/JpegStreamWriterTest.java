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

package com.android.gallery3d.jpegstream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.tests.R;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class JpegStreamWriterTest extends JpegStreamTestCase {
    public static final String TAG = "JpegStreamWriterTest";
    private JPEGOutputStream mStream;
    private ByteArrayOutputStream mWrappedStream;
    private Bitmap mBitmap;
    private Bitmap mControl;

    // galaxy_nexus.jpg image compressed with q=20
    private static final int CONTROL_RID = R.raw.jpeg_control;

    public JpegStreamWriterTest(int imageRes) {
        super(imageRes);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mBitmap = BitmapFactory.decodeStream(getImageInputStream());
        mControl = BitmapFactory.decodeStream(openResource(CONTROL_RID));
        mWrappedStream = new ByteArrayOutputStream();

    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        Utils.closeSilently(mStream);
        Utils.closeSilently(mWrappedStream);
        mWrappedStream = null;
        mStream = null;
        if (mBitmap != null) {
            mBitmap.recycle();
            mBitmap = null;
        }
        if (mControl != null) {
            mControl.recycle();
            mControl = null;
        }
    }

    public void testBasicWrites() throws Exception {
        assertTrue(mBitmap != null);
        int width = mBitmap.getWidth();
        int height = mBitmap.getHeight();
        mStream = new JPEGOutputStream(mWrappedStream, width,
                height, 20, JpegConfig.FORMAT_RGBA);

        // Put bitmap pixels into a byte array (format is RGBA).
        int rowLength = width * StreamUtils.pixelSize(JpegConfig.FORMAT_RGBA);
        int size = height * rowLength;
        byte[] byteArray = new byte[size];
        ByteBuffer buf = ByteBuffer.wrap(byteArray);
        mBitmap.copyPixelsToBuffer(buf);

        // Write out whole array
        mStream.write(byteArray, 0, byteArray.length);
        mStream.close();

        // Get compressed jpeg output
        byte[] compressed = mWrappedStream.toByteArray();

        // Check jpeg
        ByteArrayInputStream inStream = new ByteArrayInputStream(compressed);
        Bitmap test = BitmapFactory.decodeStream(inStream);
        assertTrue(test != null);
        assertTrue(test.sameAs(mControl));
    }

    public void testStreamingWrites() throws Exception {
        assertTrue(mBitmap != null);
        int width = mBitmap.getWidth();
        int height = mBitmap.getHeight();
        mStream = new JPEGOutputStream(mWrappedStream, width,
                height, 20, JpegConfig.FORMAT_RGBA);

        // Put bitmap pixels into a byte array (format is RGBA).
        int rowLength = width * StreamUtils.pixelSize(JpegConfig.FORMAT_RGBA);
        int size = height * rowLength;
        byte[] byteArray = new byte[size];
        ByteBuffer buf = ByteBuffer.wrap(byteArray);
        mBitmap.copyPixelsToBuffer(buf);

        // Write array in chunks
        int chunkSize = rowLength / 3;
        int written = 0;
        while (written < size) {
            if (written + chunkSize > size) {
                chunkSize = size - written;
            }
            mStream.write(byteArray, written, chunkSize);
            written += chunkSize;
        }
        mStream.close();

        // Get compressed jpeg output
        byte[] compressed = mWrappedStream.toByteArray();

        // Check jpeg
        ByteArrayInputStream inStream = new ByteArrayInputStream(compressed);
        Bitmap test = BitmapFactory.decodeStream(inStream);
        assertTrue(test != null);
        assertTrue(test.sameAs(mControl));
    }

    // TODO : more tests
}
