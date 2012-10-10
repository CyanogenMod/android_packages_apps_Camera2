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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class ExifOutputStreamTest extends ExifXmlDataTestCase {
    public ExifOutputStreamTest(int imgRes, int xmlRes) {
        super(imgRes, xmlRes);
    }

    public ExifOutputStreamTest(String imgPath, String xmlPath) {
        super(imgPath, xmlPath);
    }

    public void testExifOutputStream() throws Exception {
        File file = File.createTempFile("exif_test", ".jpg");
        InputStream imageInputStream = null;
        InputStream exifInputStream = null;
        FileInputStream reDecodeInputStream = null;
        FileInputStream reParseInputStream = null;
        try {
            byte[] imgData = readToByteArray(getImageInputStream());
            imageInputStream = new ByteArrayInputStream(imgData);
            exifInputStream = new ByteArrayInputStream(imgData);

            // Read the image data
            Bitmap bmp = BitmapFactory.decodeStream(imageInputStream);
            // Read exif data
            ExifData exifData = new ExifReader().read(exifInputStream);

            // Encode the image with the exif data
            FileOutputStream outputStream = new FileOutputStream(file);
            ExifOutputStream exifOutputStream = new ExifOutputStream(outputStream);
            exifOutputStream.setExifData(exifData);
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, exifOutputStream);
            exifOutputStream.close();

            // Re-decode the temp file and check the data.
            reDecodeInputStream = new FileInputStream(file);
            Bitmap decodedBmp = BitmapFactory.decodeStream(reDecodeInputStream);
            assertNotNull(decodedBmp);

            // Re-parse the temp file the check EXIF tag
            reParseInputStream = new FileInputStream(file);
            ExifData reExifData = new ExifReader().read(reParseInputStream);
            assertEquals(exifData, reExifData);
        } finally {
            Util.closeSilently(imageInputStream);
            Util.closeSilently(exifInputStream);
            Util.closeSilently(reDecodeInputStream);
            Util.closeSilently(reParseInputStream);
        }
    }

    private byte[] readToByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int len;
        byte[] buf = new byte[1024];
        while ((len = is.read(buf)) > -1) {
            bos.write(buf, 0, len);
        }
        bos.flush();
        return bos.toByteArray();
    }
}
