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

import android.test.suitebuilder.annotation.MediumTest;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Environment;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.tests.R;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class JpegStreamReaderTest extends JpegStreamTestCase {
    public static final String TAG = "JpegStreamReaderTest";
    private JPEGInputStream mStream;
    private Bitmap mBitmap;

    public JpegStreamReaderTest(int imageRes) {
        super(imageRes);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mBitmap = BitmapFactory.decodeStream(getImageInputStream());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        Utils.closeSilently(mStream);
        mStream = null;
        if (mBitmap != null) {
            mBitmap.recycle();
            mBitmap = null;
        }
    }

    @MediumTest
    public void testBasicReads() throws Exception {

        // Setup input stream.
        mStream = new JPEGInputStream(reopenFileStream(), JpegConfig.FORMAT_RGBA);
        Point dimens = mStream.getDimensions();

        // Read whole stream into array.
        byte[] bytes = new byte[dimens.x * StreamUtils.pixelSize(JpegConfig.FORMAT_RGBA) * dimens.y];
        assertTrue(mStream.read(bytes, 0, bytes.length) == bytes.length);

        // Set pixels in bitmap
        Bitmap test = Bitmap.createBitmap(dimens.x, dimens.y, Bitmap.Config.ARGB_8888);
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        test.copyPixelsFromBuffer(buf);
        assertTrue(test.getWidth() == mBitmap.getWidth() && test.getHeight() == mBitmap.getHeight());
        assertTrue(mStream.read(bytes, 0, bytes.length) == -1);
    }

    // TODO : more tests
}
