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

import java.util.List;
import java.util.Map;

public class ExifParserTest extends ExifXmlDataTestCase {
    private static final String TAG = "ExifParserTest";

    private ExifInterface mInterface;

    public ExifParserTest(int imgRes, int xmlRes) {
        super(imgRes, xmlRes);
        mInterface = new ExifInterface();
    }

    public ExifParserTest(String imgPath, String xmlPath) {
        super(imgPath, xmlPath);
        mInterface = new ExifInterface();
    }

    private List<Map<Short, List<String>>> mGroundTruth;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mGroundTruth = ExifXmlReader.readXml(getXmlParser());
    }

    public void testParse() throws Exception {
        try {
            ExifParser parser = ExifParser.parse(getImageInputStream(), mInterface);
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
                            assertTrue(TAG, tag.setValue(buf));
                        }
                        checkTag(tag);
                        break;
                }
                event = parser.next();
            }
        } catch (Exception e) {
            throw new Exception(getImageTitle(), e);
        }
    }

    private void checkTag(ExifTag tag) {
        List<String> truth = mGroundTruth.get(tag.getIfd()).get(tag.getTagId());

        if (truth == null) {
            fail(String.format("Unknown Tag %02x", tag.getTagId()) + ", " + getImageTitle());
        }

        // No value from exiftool.
        if (truth.contains(null)) {
            return;
        }

        String dataString = Util.tagValueToString(tag).trim();
        assertTrue(String.format("Tag %02x", tag.getTagId()) + ", " + getImageTitle()
                + ": " + dataString,
                truth.contains(dataString));
    }

    private void parseOneIfd(int ifd, int options) throws Exception {
        try {
            Map<Short, List<String>> expectedResult = mGroundTruth.get(ifd);
            int numOfTag = 0;
            ExifParser parser = ExifParser.parse(getImageInputStream(), options, mInterface);
            int event = parser.next();
            while (event != ExifParser.EVENT_END) {
                switch (event) {
                    case ExifParser.EVENT_START_OF_IFD:
                        assertEquals(getImageTitle(), ifd, parser.getCurrentIfd());
                        break;
                    case ExifParser.EVENT_NEW_TAG:
                        ExifTag tag = parser.getTag();
                        numOfTag++;
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
                        fail("Invalid Event type: " + event + ", " + getImageTitle());
                        break;
                }
                event = parser.next();
            }
            assertEquals(getImageTitle(), ExifXmlReader.getTrueTagNumber(expectedResult), numOfTag);
        } catch (Exception e) {
            throw new Exception(getImageTitle(), e);
        }
    }

    public void testOnlyExifIfd() throws Exception {
        parseOneIfd(IfdId.TYPE_IFD_EXIF, ExifParser.OPTION_IFD_EXIF);
    }

    public void testOnlyIfd0() throws Exception {
        parseOneIfd(IfdId.TYPE_IFD_0, ExifParser.OPTION_IFD_0);
    }

    public void testOnlyIfd1() throws Exception {
        parseOneIfd(IfdId.TYPE_IFD_1, ExifParser.OPTION_IFD_1);
    }

    public void testOnlyInteroperabilityIfd() throws Exception {
        parseOneIfd(IfdId.TYPE_IFD_INTEROPERABILITY, ExifParser.OPTION_IFD_INTEROPERABILITY);
    }

    public void testOnlyReadSomeTag() throws Exception {
        // Do not do this test if there is no model tag.
        if (mGroundTruth.get(IfdId.TYPE_IFD_0).get(ExifInterface.TAG_MODEL) == null) {
            return;
        }

        try {
            ExifParser parser = ExifParser.parse(getImageInputStream(), ExifParser.OPTION_IFD_0,
                    mInterface);
            int event = parser.next();
            boolean isTagFound = false;
            while (event != ExifParser.EVENT_END) {
                switch (event) {
                    case ExifParser.EVENT_START_OF_IFD:
                        assertEquals(getImageTitle(), IfdId.TYPE_IFD_0, parser.getCurrentIfd());
                        break;
                    case ExifParser.EVENT_NEW_TAG:
                        ExifTag tag = parser.getTag();
                        if (tag.getTagId() == ExifInterface.TAG_MODEL) {
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
                        assertEquals(getImageTitle(), ExifInterface.TAG_MODEL, tag.getTagId());
                        checkTag(tag);
                        isTagFound = true;
                        break;
                }
                event = parser.next();
            }
            assertTrue(getImageTitle(), isTagFound);
        } catch (Exception e) {
            throw new Exception(getImageTitle(), e);
        }
    }

    public void testReadThumbnail() throws Exception {
        try {
            ExifParser parser = ExifParser.parse(getImageInputStream(),
                    ExifParser.OPTION_IFD_1 | ExifParser.OPTION_THUMBNAIL, mInterface);

            int event = parser.next();
            Bitmap bmp = null;
            boolean mIsContainCompressedImage = false;
            while (event != ExifParser.EVENT_END) {
                switch (event) {
                    case ExifParser.EVENT_NEW_TAG:
                        ExifTag tag = parser.getTag();
                        if (tag.getTagId() == ExifInterface.TAG_COMPRESSION) {
                            if (tag.getValueAt(0) == ExifInterface.Compression.JPEG) {
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
                assertNotNull(getImageTitle(), bmp);
            }
        } catch (Exception e) {
            throw new Exception(getImageTitle(), e);
        }
    }
}
