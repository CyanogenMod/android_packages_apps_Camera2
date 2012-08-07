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
import android.test.InstrumentationTestCase;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public class ExifParserTest extends InstrumentationTestCase {
    private static final String TAG = "ExifParserTest";

    private final int mImageResourceId;
    private final int mXmlResourceId;

    private HashMap<Short, String> mIfd0Value = new HashMap<Short, String>();
    private HashMap<Short, String> mIfd1Value = new HashMap<Short, String>();
    private HashMap<Short, String> mExifIfdValue = new HashMap<Short, String>();

    private InputStream mImageInputStream;

    private static final String XML_EXIF_TAG = "exif";
    private static final String XML_IFD_TAG = "ifd";
    private static final String XML_IFD_NAME = "name";
    private static final String XML_TAG = "tag";
    private static final String XML_IFD0 = "ifd0";
    private static final String XML_IFD1 = "ifd1";
    private static final String XML_EXIF_IFD = "exif-ifd";
    private static final String XML_TAG_ID = "id";

    public ExifParserTest(int imageResourceId, int xmlResourceId) {
        mImageResourceId = imageResourceId;
        mXmlResourceId = xmlResourceId;
    }

    @Override
    protected void setUp() throws Exception {
        mImageInputStream = getInstrumentation()
                .getContext().getResources().openRawResource(mImageResourceId);

        XmlResourceParser parser =
                getInstrumentation().getContext().getResources().getXml(mXmlResourceId);

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                assert(parser.getName().equals(XML_EXIF_TAG));
                readXml(parser);
                break;
            }
        }
        parser.close();
    }

    private void readXml(XmlPullParser parser) throws XmlPullParserException,
            IOException {
        parser.require(XmlPullParser.START_TAG, null, XML_EXIF_TAG);
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                readXmlIfd(parser);
            }
        }
        parser.require(XmlPullParser.END_TAG, null, XML_EXIF_TAG);
    }

    private void readXmlIfd(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, XML_IFD_TAG);
        String name = parser.getAttributeValue(null, XML_IFD_NAME);
        HashMap<Short, String> ifdData = null;
        if (XML_IFD0.equals(name)) {
            ifdData = mIfd0Value;
        } else if (XML_IFD1.equals(name)) {
            ifdData = mIfd1Value;
        } else if (XML_EXIF_IFD.equals(name)) {
            ifdData = mExifIfdValue;
        } else {
            throw new RuntimeException("Unknown IFD name in xml file: " + name);
        }
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                readXmlTag(parser, ifdData);
            }
        }
        parser.require(XmlPullParser.END_TAG, null, XML_IFD_TAG);
    }

    private void readXmlTag(XmlPullParser parser, HashMap<Short, String> data)
            throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, XML_TAG);
        short id = Integer.decode(parser.getAttributeValue(null, XML_TAG_ID)).shortValue();
        String value = "";
        if (parser.next() == XmlPullParser.TEXT) {
            value = parser.getText();
            parser.next();
        }
        data.put(id, value);
        parser.require(XmlPullParser.END_TAG, null, XML_TAG);
    }

    public void testParse() throws IOException, ExifInvalidFormatException {
        ExifParser parser = new ExifParser();
        parseIfd0(parser.parse(mImageInputStream));
    }

    private void parseIfd0(IfdParser ifdParser) throws IOException,
            ExifInvalidFormatException {
        int type = ifdParser.next();
        int tagNumber=0;
        while (type != IfdParser.TYPE_END) {
            switch (type) {
                case IfdParser.TYPE_NEW_TAG:
                    ExifTag tag = ifdParser.readTag();
                    if (tag.getDataSize() > 4 || tag.getTagId() == ExifTag.TIFF_TAG.TAG_EXIF_IFD) {
                        long offset = ifdParser.readUnsignedInt();
                        assertTrue(offset <= Integer.MAX_VALUE);
                        ifdParser.waitValueOfTag(tag, offset);
                    } else {
                        checkTag(tag, ifdParser, mIfd0Value);
                        tagNumber++;
                    }
                    break;
                case IfdParser.TYPE_NEXT_IFD:
                    parseIfd1(ifdParser.parseIfdBlock());
                    break;
                case IfdParser.TYPE_VALUE_OF_PREV_TAG:
                    tag = ifdParser.getCorrespodingExifTag();
                    if(tag.getTagId() == ExifTag.TIFF_TAG.TAG_EXIF_IFD) {
                        parseExifIfd(ifdParser.parseIfdBlock());
                    } else {
                        checkTag(ifdParser.getCorrespodingExifTag(), ifdParser, mIfd0Value);
                        tagNumber++;
                    }
                    break;
            }
            type = ifdParser.next();
        }
        assertEquals(mIfd0Value.size(), tagNumber);
    }

    private void parseIfd1(IfdParser ifdParser) throws IOException,
            ExifInvalidFormatException {
        int type = ifdParser.next();
        int tagNumber = 0;
        while (type != IfdParser.TYPE_END) {
            switch (type) {
                case IfdParser.TYPE_NEW_TAG:
                    ExifTag tag = ifdParser.readTag();
                    if (tag.getDataSize() > 4) {
                        long offset = ifdParser.readUnsignedInt();
                        assertTrue(offset <= Integer.MAX_VALUE);
                        ifdParser.waitValueOfTag(tag, offset);
                    } else {
                        checkTag(tag, ifdParser, mIfd1Value);
                        tagNumber++;
                    }
                    break;
                case IfdParser.TYPE_NEXT_IFD:
                    fail("Find a ifd after ifd1");
                    break;
                case IfdParser.TYPE_VALUE_OF_PREV_TAG:
                    checkTag(ifdParser.getCorrespodingExifTag(), ifdParser, mIfd1Value);
                    tagNumber++;
                    break;
            }
            type = ifdParser.next();
        }
        assertEquals(mIfd1Value.size(), tagNumber);
    }

    private void parseExifIfd(IfdParser ifdParser) throws IOException,
            ExifInvalidFormatException {
        int type = ifdParser.next();
        int tagNumber = 0;
        while (type != IfdParser.TYPE_END) {
            switch (type) {
                case IfdParser.TYPE_NEW_TAG:
                    ExifTag tag = ifdParser.readTag();
                    if (tag.getDataSize() > 4) {
                        long offset = ifdParser.readUnsignedInt();
                        assertTrue(offset <= Integer.MAX_VALUE);
                        ifdParser.waitValueOfTag(tag, offset);
                    } else {
                        checkTag(tag, ifdParser, mExifIfdValue);
                        tagNumber++;
                    }
                    break;
                case IfdParser.TYPE_NEXT_IFD:
                    fail("Find a ifd after exif ifd");
                    break;
                case IfdParser.TYPE_VALUE_OF_PREV_TAG:
                    checkTag(ifdParser.getCorrespodingExifTag(), ifdParser, mExifIfdValue);
                    tagNumber++;
                    break;
            }
            type = ifdParser.next();
        }
        assertEquals(mExifIfdValue.size(), tagNumber);
    }

    private void checkTag(ExifTag tag, IfdParser ifdParser, HashMap<Short, String> truth)
            throws IOException {
        assertEquals(truth.get(tag.getTagId()), readValueToString(tag, ifdParser));
    }

    private String readValueToString(ExifTag tag, IfdParser parser) throws IOException {
        StringBuilder sbuilder = new StringBuilder();
        switch(tag.getDataType()) {
            case ExifTag.TYPE_BYTE:
                byte buf[] = new byte[tag.getComponentCount()];
                parser.read(buf);
                for(int i = 0; i < tag.getComponentCount(); i++) {
                    if(i != 0) sbuilder.append(" ");
                    sbuilder.append(String.format("%02x", buf[i]));
                }
                break;
            case ExifTag.TYPE_ASCII:
                buf = new byte[tag.getComponentCount()];
                parser.read(buf);
                int length = 0;
                while (buf[length] != 0 && length < buf.length) length++;
                // trim the string to fit the answer from xml
                sbuilder.append(new String(buf, 0, length).trim());
                break;
            case ExifTag.TYPE_INT:
                for(int i = 0; i < tag.getComponentCount(); i++) {
                    if(i != 0) sbuilder.append(" ");
                    sbuilder.append(parser.readUnsignedInt());
                }
                break;
            case ExifTag.TYPE_RATIONAL:
                for(int i = 0; i < tag.getComponentCount(); i++) {
                    Rational r = parser.readUnsignedRational();
                    if(i != 0) sbuilder.append(" ");
                    sbuilder.append(r.getNominator()).append("/").append(r.getDenominator());
                }
                break;
            case ExifTag.TYPE_SHORT:
                for(int i = 0; i < tag.getComponentCount(); i++) {
                    if(i != 0) sbuilder.append(" ");
                    sbuilder.append(parser.readUnsignedShort());
                }
                break;
            case ExifTag.TYPE_SINT:
                for(int i = 0; i < tag.getComponentCount(); i++) {
                    if(i != 0) sbuilder.append(" ");
                    sbuilder.append(parser.readInt());
                }
                break;
            case ExifTag.TYPE_SRATIONAL:
                for(int i = 0; i < tag.getComponentCount(); i++) {
                    Rational r = parser.readRational();
                    if(i != 0) sbuilder.append(" ");
                    sbuilder.append(r.getNominator()).append("/").append(r.getDenominator());
                }
                break;
            case ExifTag.TYPE_UNDEFINED:
                byte buffer[] = new byte[tag.getComponentCount()];
                parser.read(buffer);
                for(int i = 0; i < tag.getComponentCount(); i++) {
                    if(i != 0) sbuilder.append(" ");
                    sbuilder.append(String.format("%02x", buffer[i]));
                }
                break;
        }
        return sbuilder.toString();
    }

    public void testSkipToNextIfd() throws ExifInvalidFormatException, IOException {
        ExifParser exifParser = new ExifParser();
        IfdParser ifdParser = exifParser.parse(mImageInputStream);
        int type = ifdParser.next();
        while (type != IfdParser.TYPE_END) {
            switch (type) {
                case IfdParser.TYPE_NEW_TAG:
                    // Do nothing, we don't care
                    break;
                case IfdParser.TYPE_NEXT_IFD:
                    parseIfd1(ifdParser.parseIfdBlock());
                    break;
                case IfdParser.TYPE_VALUE_OF_PREV_TAG:
                    // We won't get this since to skip everything
                    fail("Get value of previous tag but we've skip everything");
                    break;
            }
            type = ifdParser.next();
        }
    }

    public void testOnlySaveSomeValue() throws ExifInvalidFormatException, IOException {
        ExifParser exifParser = new ExifParser();
        IfdParser ifdParser = exifParser.parse(mImageInputStream);
        int type = ifdParser.next();
        while (type != IfdParser.TYPE_END) {
            switch (type) {
                case IfdParser.TYPE_NEW_TAG:
                    ExifTag tag = ifdParser.readTag();
                    // only interested in these two tags
                    if (tag.getDataSize() > 4 || tag.getTagId() == ExifTag.TIFF_TAG.TAG_EXIF_IFD) {
                        if(tag.getTagId() == ExifTag.TIFF_TAG.TAG_MODEL
                                || tag.getTagId() == ExifTag.TIFF_TAG.TAG_EXIF_IFD) {
                            long offset = ifdParser.readUnsignedInt();
                            assertTrue(offset <= Integer.MAX_VALUE);
                            ifdParser.waitValueOfTag(tag, offset);
                        }
                    }
                    break;
                case IfdParser.TYPE_NEXT_IFD:
                    parseIfd1(ifdParser.parseIfdBlock());
                    break;
                case IfdParser.TYPE_VALUE_OF_PREV_TAG:
                    tag = ifdParser.getCorrespodingExifTag();
                    if(tag.getTagId() == ExifTag.TIFF_TAG.TAG_EXIF_IFD) {
                        parseExifIfd(ifdParser.parseIfdBlock());
                    } else {
                        checkTag(ifdParser.getCorrespodingExifTag(), ifdParser, mIfd0Value);
                    }
                    break;
            }
            type = ifdParser.next();
        }
    }

    public void testReadThumbnail() throws ExifInvalidFormatException, IOException {
        ExifParser exifParser = new ExifParser();
        IfdParser ifdParser = exifParser.parse(mImageInputStream);
        int type = ifdParser.next();
        while (type != IfdParser.TYPE_END && type != IfdParser.TYPE_NEXT_IFD) {
            type = ifdParser.next();
        }
        // We should meet next_ifd before end
        assertTrue(type != IfdParser.TYPE_END);

        IfdParser ifd1Parser = ifdParser.parseIfdBlock();
        int thumbOffset = 0;
        int thumbSize = 0;
        int width = 0;
        int height = 0;
        boolean isFinishRead = false;
        while (!isFinishRead) {
            switch (ifd1Parser.next()) {
                case IfdParser.TYPE_NEW_TAG:
                    ExifTag tag = ifd1Parser.readTag();
                    if (tag.getTagId() == ExifTag.TIFF_TAG.TAG_JPEG_INTERCHANGE_FORMAT) {
                        long unsignedInt = ifdParser.readUnsignedInt();
                        assertTrue(unsignedInt <= Integer.MAX_VALUE);
                        thumbOffset = (int) unsignedInt;
                    } else if (tag.getTagId() ==
                            ExifTag.TIFF_TAG.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH) {
                        long unsignedInt = ifdParser.readUnsignedInt();
                        assertTrue(unsignedInt <= Integer.MAX_VALUE);
                        thumbSize = (int) unsignedInt;
                    } else if (tag.getTagId() == ExifTag.TIFF_TAG.TAG_IMAGE_WIDTH) {
                        long unsigned = tag.getDataType() == ExifTag.TYPE_INT ?
                                ifd1Parser.readUnsignedInt() : ifd1Parser.readUnsignedShort();
                        assertTrue(unsigned <= (tag.getDataType() == ExifTag.TYPE_INT ?
                                Integer.MAX_VALUE: Short.MAX_VALUE));
                        width = (int) unsigned;
                    } else if (tag.getTagId() == ExifTag.TIFF_TAG.TAG_IMAGE_HEIGHT) {
                        long unsigned = tag.getDataType() == ExifTag.TYPE_INT ?
                                ifd1Parser.readUnsignedInt() : ifd1Parser.readUnsignedShort();
                        assertTrue(unsigned <= (tag.getDataType() == ExifTag.TYPE_INT ?
                                Integer.MAX_VALUE: Short.MAX_VALUE));
                        height = (int) unsigned;
                    }
                    isFinishRead = thumbOffset != 0 && thumbSize != 0 && width != 0 && height != 0;
                    break;
                case IfdParser.TYPE_END:
                    fail("No thumbnail information found");
                    break;
            }
        }

        byte buf[] = new byte[thumbSize];
        ifd1Parser.skipTo(thumbOffset);
        ifd1Parser.read(buf);
        Bitmap bmp = BitmapFactory.decodeByteArray(buf, 0, thumbSize);
        // Check correctly decoded
        assertTrue(bmp != null);
        assertEquals(width, bmp.getWidth());
        assertEquals(height, bmp.getHeight());
    }

    @Override
    protected void tearDown() throws IOException {
        mImageInputStream.close();
        mIfd0Value.clear();
        mIfd1Value.clear();
        mExifIfdValue.clear();
    }
}
