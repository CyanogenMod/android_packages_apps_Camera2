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

import android.content.res.XmlResourceParser;
import android.test.InstrumentationTestCase;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public class ExifReaderTest extends InstrumentationTestCase {
    private static final String TAG = "ExifReaderTest";

    private final int mImageResourceId;
    private final int mXmlResourceId;

    private final HashMap<Short, String> mIfd0Value = new HashMap<Short, String>();
    private final HashMap<Short, String> mIfd1Value = new HashMap<Short, String>();
    private final HashMap<Short, String> mExifIfdValue = new HashMap<Short, String>();
    private final HashMap<Short, String> mInteroperabilityIfdValue = new HashMap<Short, String>();

    private InputStream mImageInputStream;

    public ExifReaderTest(int imageResourceId, int xmlResourceId) {
        mImageResourceId = imageResourceId;
        mXmlResourceId = xmlResourceId;
    }

    @Override
    public void setUp() throws Exception {
        mImageInputStream = getInstrumentation()
                .getContext().getResources().openRawResource(mImageResourceId);

        XmlResourceParser parser =
                getInstrumentation().getContext().getResources().getXml(mXmlResourceId);

        ExifXmlReader.readXml(parser, mIfd0Value, mIfd1Value, mExifIfdValue
                , mInteroperabilityIfdValue);
        parser.close();
    }

    public void testRead() throws ExifInvalidFormatException, IOException {
        ExifReader reader = new ExifReader();
        ExifData exifData = reader.getExifData(mImageInputStream);
        checkIfd(exifData.getIfdData(IfdId.TYPE_IFD_0), mIfd0Value);
        checkIfd(exifData.getIfdData(IfdId.TYPE_IFD_1), mIfd1Value);
        checkIfd(exifData.getIfdData(IfdId.TYPE_IFD_EXIF), mExifIfdValue);
        checkIfd(exifData.getIfdData(IfdId.TYPE_IFD_INTEROPERABILITY),
                mInteroperabilityIfdValue);
    }

    private void checkIfd(IfdData ifd, HashMap<Short, String> ifdValue) {
        if (ifd == null) {
            assertEquals(0 ,ifdValue.size());
            return;
        }
        ExifTag[] tags = ifd.getAllTags(new ExifTag[0]);
        for (ExifTag tag : tags) {
            assertEquals(ifdValue.get(tag.getTagId()), tag.valueToString());
        }
        assertEquals(ifdValue.size(), tags.length);
    }

    @Override
    public void tearDown() throws Exception {
        mImageInputStream.close();
    }
}
