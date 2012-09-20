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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public class ExifParserTest extends ExifXmlDataTestCase {
    private static final String TAG = "ExifParserTest";

    private HashMap<Short, String> mIfd0Value = new HashMap<Short, String>();
    private HashMap<Short, String> mIfd1Value = new HashMap<Short, String>();
    private HashMap<Short, String> mExifIfdValue = new HashMap<Short, String>();
    private HashMap<Short, String> mInteroperabilityIfdValue = new HashMap<Short, String>();

    private InputStream mImageInputStream;

    public ExifParserTest(int imageResourceId, int xmlResourceId) {
        super(imageResourceId, xmlResourceId);
    }

    @Override
    protected void setUp() throws Exception {
        mImageInputStream = getInstrumentation()
                .getContext().getResources().openRawResource(mImageResourceId);

        XmlResourceParser parser =
                getInstrumentation().getContext().getResources().getXml(mXmlResourceId);

        ExifXmlReader.readXml(parser, mIfd0Value, mIfd1Value, mExifIfdValue
                , mInteroperabilityIfdValue);
        parser.close();
    }

    public void testParse() throws IOException, ExifInvalidFormatException {
        ExifParser parser = ExifParser.parse(mImageInputStream);
        int event = parser.next();
        while (event != ExifParser.EVENT_END) {
            switch (event) {
                case ExifParser.EVENT_START_OF_IFD:
                    break;
                case ExifParser.EVENT_NEW_TAG:
                    ExifTag tag = parser.getTag();
                    if (!tag.hasValue()) {
                        parser.registerForTagValue(tag);
                    } else {
                        checkTag(tag);
                    }
                    break;
                case ExifParser.EVENT_VALUE_OF_REGISTERED_TAG:
                    tag = parser.getTag();
                    if (tag.getDataType() == ExifTag.TYPE_UNDEFINED) {
                        byte[] buf = new byte[tag.getComponentCount()];
                        parser.read(buf);
                        tag.setValue(buf);
                    }
                    checkTag(tag);
                    break;
            }
            event = parser.next();
        }
    }

    private void checkTag(ExifTag tag) {
        HashMap<Short, String> truth = null;
        switch (tag.getIfd()) {
            case IfdId.TYPE_IFD_0:
                truth = mIfd0Value;
                break;
            case IfdId.TYPE_IFD_1:
                truth = mIfd1Value;
                break;
            case IfdId.TYPE_IFD_EXIF:
                truth = mExifIfdValue;
                break;
            case IfdId.TYPE_IFD_INTEROPERABILITY:
                truth = mInteroperabilityIfdValue;
                break;
        }

        String truthString = truth.get(tag.getTagId());
        String dataString = tag.valueToString().trim();
        if (truthString == null) {
            fail(String.format("Unknown Tag %02x", tag.getTagId()));
        }
        assertEquals(String.format("Tag %02x", tag.getTagId()), truthString, dataString);
    }

    private void parseOneIfd(int ifd, int options, HashMap<Short, String> expectedResult)
            throws IOException, ExifInvalidFormatException {
        int numOfTag = 0;
        ExifParser parser = ExifParser.parse(mImageInputStream, options);
        int event = parser.next();
        while(event != ExifParser.EVENT_END) {
            switch (event) {
                case ExifParser.EVENT_START_OF_IFD:
                    assertEquals(ifd, parser.getCurrentIfd());
                    break;
                case ExifParser.EVENT_NEW_TAG:
                    numOfTag++;
                    ExifTag tag = parser.getTag();
                    if (tag.hasValue()) {
                        checkTag(tag);
                    } else {
                        parser.registerForTagValue(tag);
                    }
                    break;
                case ExifParser.EVENT_VALUE_OF_REGISTERED_TAG:
                    tag = parser.getTag();
                    if (tag.getDataType() == ExifTag.TYPE_UNDEFINED) {
                        byte[] buf = new byte[tag.getComponentCount()];
                        parser.read(buf);
                        tag.setValue(buf);
                    }
                    checkTag(tag);
                    break;
                case ExifParser.EVENT_COMPRESSED_IMAGE:
                case ExifParser.EVENT_UNCOMPRESSED_STRIP:
                    fail("Invalid Event type: " + event);
                    break;
            }
            event = parser.next();
        }
        assertEquals(expectedResult.size(), numOfTag);
    }

    public void testOnlyExifIfd() throws IOException, ExifInvalidFormatException {
        parseOneIfd(IfdId.TYPE_IFD_EXIF, ExifParser.OPTION_IFD_EXIF, mExifIfdValue);
    }

    public void testOnlyIfd0() throws IOException, ExifInvalidFormatException {
        parseOneIfd(IfdId.TYPE_IFD_0, ExifParser.OPTION_IFD_0, mIfd0Value);
    }

    public void testOnlyIfd1() throws IOException, ExifInvalidFormatException {
        parseOneIfd(IfdId.TYPE_IFD_1, ExifParser.OPTION_IFD_1, mIfd1Value);
    }

    public void testOnlyInteroperabilityIfd() throws IOException, ExifInvalidFormatException {
        parseOneIfd(IfdId.TYPE_IFD_INTEROPERABILITY, ExifParser.OPTION_IFD_INTEROPERABILITY
                , mInteroperabilityIfdValue);
    }

    public void testOnlyReadSomeTag() throws IOException, ExifInvalidFormatException {
        ExifParser parser = ExifParser.parse(mImageInputStream, ExifParser.OPTION_IFD_0);
        int event = parser.next();
        boolean isTagFound = false;
        while (event != ExifParser.EVENT_END) {
            switch (event) {
                case ExifParser.EVENT_START_OF_IFD:
                    assertEquals(IfdId.TYPE_IFD_0, parser.getCurrentIfd());
                    break;
                case ExifParser.EVENT_NEW_TAG:
                    ExifTag tag = parser.getTag();
                    if (tag.getTagId() == ExifTag.TAG_MODEL) {
                        if (tag.hasValue()) {
                            isTagFound = true;
                            checkTag(tag);
                        } else {
                            parser.registerForTagValue(tag);
                        }
                        parser.skipRemainingTagsInCurrentIfd();
                    }
                    break;
                case ExifParser.EVENT_VALUE_OF_REGISTERED_TAG:
                    tag = parser.getTag();
                    assertEquals(ExifTag.TAG_MODEL, tag.getTagId());
                    checkTag(tag);
                    isTagFound = true;
                    break;
            }
            event = parser.next();
        }
        assertTrue(isTagFound);
    }

    public void testReadThumbnail() throws ExifInvalidFormatException, IOException {
        ExifParser parser = ExifParser.parse(mImageInputStream,
                ExifParser.OPTION_IFD_1 | ExifParser.OPTION_THUMBNAIL);

        int event = parser.next();
        Bitmap bmp = null;
        boolean mIsContainCompressedImage = false;
        while (event != ExifParser.EVENT_END) {
            switch (event) {
                case ExifParser.EVENT_NEW_TAG:
                    ExifTag tag = parser.getTag();
                    if (tag.getTagId() == ExifTag.TAG_COMPRESSION) {
                        if (tag.getUnsignedShort(0) == ExifTag.Compression.JPEG) {
                            mIsContainCompressedImage = true;
                        }
                    }
                    break;
                case ExifParser.EVENT_COMPRESSED_IMAGE:
                    int imageSize = parser.getCompressedImageSize();
                    byte buf[] = new byte[imageSize];
                    parser.read(buf);
                    bmp = BitmapFactory.decodeByteArray(buf, 0, imageSize);
                    break;
            }
            event = parser.next();
        }
        if (mIsContainCompressedImage) {
            assertNotNull(bmp);
        }
    }

    @Override
    protected void tearDown() throws IOException {
        mImageInputStream.close();
        mIfd0Value.clear();
        mIfd1Value.clear();
        mExifIfdValue.clear();
    }
}