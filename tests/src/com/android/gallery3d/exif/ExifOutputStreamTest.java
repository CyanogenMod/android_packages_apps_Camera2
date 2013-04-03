/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.exif;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class ExifOutputStreamTest extends ExifXmlDataTestCase {

    private File mTmpFile;

    private ExifInterface mInterface;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mTmpFile = File.createTempFile("exif_test", ".jpg");
    }

    public ExifOutputStreamTest(int imgRes, int xmlRes) {
        super(imgRes, xmlRes);
        mInterface = new ExifInterface();
    }

    public ExifOutputStreamTest(String imgPath, String xmlPath) {
        super(imgPath, xmlPath);
        mInterface = new ExifInterface();
    }

    public void testExifOutputStream() throws Exception {
        InputStream imageInputStream = null;
        InputStream exifInputStream = null;
        FileInputStream reDecodeInputStream = null;
        FileInputStream reParseInputStream = null;

        InputStream dangerInputStream = null;
        OutputStream dangerOutputStream = null;
        try {
            try {
                byte[] imgData = Util.readToByteArray(getImageInputStream());
                imageInputStream = new ByteArrayInputStream(imgData);
                exifInputStream = new ByteArrayInputStream(imgData);

                // Read the image data
                Bitmap bmp = BitmapFactory.decodeStream(imageInputStream);
                // The image is invalid
                if (bmp == null) {
                    return;
                }

                // Read exif data
                ExifData exifData = new ExifReader(mInterface).read(exifInputStream);

                // Encode the image with the exif data
                FileOutputStream outputStream = new FileOutputStream(mTmpFile);
                ExifOutputStream exifOutputStream = new ExifOutputStream(outputStream, mInterface);
                exifOutputStream.setExifData(exifData);
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, exifOutputStream);
                exifOutputStream.close();
                exifOutputStream = null;

                // Re-decode the temp file and check the data.
                reDecodeInputStream = new FileInputStream(mTmpFile);
                Bitmap decodedBmp = BitmapFactory.decodeStream(reDecodeInputStream);
                assertNotNull(getImageTitle(), decodedBmp);
                reDecodeInputStream.close();

                // Re-parse the temp file the check EXIF tag
                reParseInputStream = new FileInputStream(mTmpFile);
                ExifData reExifData = new ExifReader(mInterface).read(reParseInputStream);
                assertEquals(getImageTitle(), exifData, reExifData);
                reParseInputStream.close();

                // Try writing exif to file with existing exif.
                dangerOutputStream = (OutputStream) new FileOutputStream(mTmpFile);
                exifOutputStream = new ExifOutputStream(dangerOutputStream, mInterface);
                exifOutputStream.setExifData(exifData);
                exifOutputStream.write(imgData);
                // exifOutputStream.write(strippedImgData);
                exifOutputStream.close();
                exifOutputStream = null;

                // Make sure it still can be parsed into a bitmap.
                dangerInputStream = (InputStream) new FileInputStream(mTmpFile);
                decodedBmp = null;
                decodedBmp = BitmapFactory.decodeStream(dangerInputStream);
                assertNotNull(getImageTitle(), decodedBmp);
                dangerInputStream.close();
                dangerInputStream = null;

                // Make sure exif is still well-formatted.
                dangerInputStream = (InputStream) new FileInputStream(mTmpFile);
                reExifData = null;
                reExifData = new ExifReader(mInterface).read(dangerInputStream);
                assertEquals(getImageTitle(), exifData, reExifData);
                dangerInputStream.close();
                dangerInputStream = null;

            } finally {
                Util.closeSilently(imageInputStream);
                Util.closeSilently(exifInputStream);
                Util.closeSilently(reDecodeInputStream);
                Util.closeSilently(reParseInputStream);

                Util.closeSilently(dangerInputStream);
                Util.closeSilently(dangerOutputStream);
            }
        } catch (Exception e) {
            throw new Exception(getImageTitle(), e);
        }
    }

    public void testOutputSpeed() throws Exception {
        final String LOGTAG = "testOutputSpeed";
        InputStream imageInputStream = null;
        OutputStream imageOutputStream = null;
        try {
            try {
                imageInputStream = getImageInputStream();
                // Read the image data
                Bitmap bmp = BitmapFactory.decodeStream(imageInputStream);
                // The image is invalid
                if (bmp == null) {
                    return;
                }
                imageInputStream.close();
                int nLoops = 20;
                long totalReadDuration = 0;
                long totalWriteDuration = 0;
                for (int i = 0; i < nLoops; i++) {
                    imageInputStream = reopenFileStream();
                    // Read exif data
                    long startTime = System.nanoTime();
                    ExifData exifData = new ExifReader(mInterface).read(imageInputStream);
                    long endTime = System.nanoTime();
                    long duration = endTime - startTime;
                    totalReadDuration += duration;
                    Log.v(LOGTAG, " read time: " + duration);
                    imageInputStream.close();

                    // Encode the image with the exif data
                    imageOutputStream = (OutputStream) new FileOutputStream(mTmpFile);
                    ExifOutputStream exifOutputStream = new ExifOutputStream(imageOutputStream,
                            mInterface);
                    exifOutputStream.setExifData(exifData);
                    startTime = System.nanoTime();
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, exifOutputStream);
                    endTime = System.nanoTime();
                    duration = endTime - startTime;
                    totalWriteDuration += duration;
                    Log.v(LOGTAG, " write time: " + duration);
                    exifOutputStream.close();
                }
                Log.v(LOGTAG, "======================= normal");
                Log.v(LOGTAG, "avg read time: " + totalReadDuration / nLoops);
                Log.v(LOGTAG, "avg write time: " + totalWriteDuration / nLoops);
                Log.v(LOGTAG, "=======================");
            } finally {
                Util.closeSilently(imageInputStream);
                Util.closeSilently(imageOutputStream);
            }
        } catch (Exception e) {
            throw new Exception(getImageTitle(), e);
        }
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mTmpFile.delete();
    }
}
