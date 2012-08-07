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
import android.test.InstrumentationTestCase;

import com.android.gallery3d.tests.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public class ExifParserTest extends InstrumentationTestCase {
    private static final String TAG = "ExifParserTest";
    // The test image
    private static final int IMG_RESOURCE_ID = R.raw.test_galaxy_nexus;

    // IDF0 ground truth
    private static final HashMap<Short, String> IFD0_VALUE = new HashMap<Short, String>();
    static {
        IFD0_VALUE.put(ExifTag.TIFF_TAG.TAG_IMAGE_WIDTH, String.valueOf(2560));
        IFD0_VALUE.put(ExifTag.TIFF_TAG.TAG_IMAGE_HEIGHT, String.valueOf(1920));
        IFD0_VALUE.put(ExifTag.TIFF_TAG.TAG_MAKE, "google");
        IFD0_VALUE.put(ExifTag.TIFF_TAG.TAG_MODEL, "Nexus S");
        IFD0_VALUE.put(ExifTag.TIFF_TAG.TAG_ORIENTATION,
                String.valueOf(ExifTag.TIFF_TAG.ORIENTATION_TOP_LEFT));
        IFD0_VALUE.put(ExifTag.TIFF_TAG.TAG_SOFTWARE, "MASTER");
        IFD0_VALUE.put(ExifTag.TIFF_TAG.TAG_DATE_TIME, "2012:07:30 16:28:42");
        IFD0_VALUE.put(ExifTag.TIFF_TAG.TAG_Y_CB_CR_POSITIONING,
                String.valueOf(ExifTag.TIFF_TAG.Y_CB_CR_POSITIONING_CENTERED));
    }

    // IDF1 ground truth
    private static final HashMap<Short, String> IFD1_VALUE = new HashMap<Short, String>();
    static {
        IFD1_VALUE.put(ExifTag.TIFF_TAG.TAG_COMPRESSION,
                String.valueOf(ExifTag.TIFF_TAG.COMPRESSION_JPEG));
        IFD1_VALUE.put(ExifTag.TIFF_TAG.TAG_X_RESOLUTION, "72/1");
        IFD1_VALUE.put(ExifTag.TIFF_TAG.TAG_Y_RESOLUTION, "72/1");
        IFD1_VALUE.put(ExifTag.TIFF_TAG.TAG_RESOLUTION_UNIT,
                String.valueOf(ExifTag.TIFF_TAG.RESOLUTION_UNIT_INCHES));
        IFD1_VALUE.put(ExifTag.TIFF_TAG.TAG_IMAGE_WIDTH, String.valueOf(320));
        IFD1_VALUE.put(ExifTag.TIFF_TAG.TAG_IMAGE_HEIGHT, String.valueOf(240));
        IFD1_VALUE.put(ExifTag.TIFF_TAG.TAG_ORIENTATION,
                String.valueOf(ExifTag.TIFF_TAG.ORIENTATION_TOP_LEFT));
        IFD1_VALUE.put(ExifTag.TIFF_TAG.TAG_JPEG_INTERCHANGE_FORMAT, String.valueOf(690));
        IFD1_VALUE.put(ExifTag.TIFF_TAG.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH, String.valueOf(10447));
    }

    // Exif-idf ground truth
    private static final HashMap<Short, String> EXIF_IFD_VALUE = new HashMap<Short, String>();
    static {
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_EXPOSURE_TIME, "1/40");
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_F_NUMBER, "26/10");
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_EXPOSURE_PROGRAM,
                String.valueOf(ExifTag.EXIF_TAG.EXPOSURE_PROGRAM_APERTURE_PRIORITY));
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_ISO_SPEED_RATINGS, "100");
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_EXIF_VERSION, "48 50 50 48"); // 0220
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_DATE_TIME_ORIGINAL, "2012:07:30 16:28:42");
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_DATE_TIME_DIGITIZED, "2012:07:30 16:28:42");
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_SHUTTER_SPEED, "50/10");
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_APERTURE_VALUE, "30/10");
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_BRIGHTNESS_VALUE, "30/10");
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_EXPOSURE_BIAS_VALUE, "0/0");
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_MAX_APERTURE_VALUE, "30/10");
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_METERING_MODE,
                String.valueOf(ExifTag.EXIF_TAG.METERING_MODE_CENTER_WEIGHTED_AVERAGE));
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_FLASH,
                String.valueOf(ExifTag.EXIF_TAG.FLASH_DID_NOT_FIRED));
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_FOCAL_LENGTH, "343/100");
        // User command is strange in test_galaxy_nexus, so a binary representation is used here
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_USER_COMMENT,
                "0 0 0 73 73 67 83 65 85 115 101 114 32 99 111 109 109 101 110 116 115 0");
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_COLOR_SPACE,
                String.valueOf(ExifTag.EXIF_TAG.COLOR_SPACE_SRGB));
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_PIXEL_X_DIMENSION, "2560");
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_PIXEL_Y_DIMENSION, "1920");
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_EXPOSURE_MODE,
                String.valueOf(ExifTag.EXIF_TAG.EXPOSURE_MODE_AUTO_EXPOSURE));
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_WHITH_BALANCE,
                String.valueOf(ExifTag.EXIF_TAG.WHITE_BALACE_MODE_AUTO));
        EXIF_IFD_VALUE.put(ExifTag.EXIF_TAG.TAG_SCENE_CAPTURE_TYPE,
                String.valueOf(ExifTag.EXIF_TAG.SCENE_CAPTURE_TYPE_STANDARD));
    }

    private InputStream mImageInputStream;

    @Override
    protected void setUp() {
        mImageInputStream = getInstrumentation()
                .getContext().getResources().openRawResource(IMG_RESOURCE_ID);
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
                        checkTag(tag, ifdParser, IFD0_VALUE);
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
                        checkTag(ifdParser.getCorrespodingExifTag(), ifdParser, IFD0_VALUE);
                        tagNumber++;
                    }
                    break;
            }
            type = ifdParser.next();
        }
        assertEquals(IFD0_VALUE.size(), tagNumber);
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
                        checkTag(tag, ifdParser, IFD1_VALUE);
                        tagNumber++;
                    }
                    break;
                case IfdParser.TYPE_NEXT_IFD:
                    fail("Find a ifd after ifd1");
                    break;
                case IfdParser.TYPE_VALUE_OF_PREV_TAG:
                    checkTag(ifdParser.getCorrespodingExifTag(), ifdParser, IFD1_VALUE);
                    tagNumber++;
                    break;
            }
            type = ifdParser.next();
        }
        assertEquals(IFD1_VALUE.size(), tagNumber);
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
                        checkTag(tag, ifdParser, EXIF_IFD_VALUE);
                        tagNumber++;
                    }
                    break;
                case IfdParser.TYPE_NEXT_IFD:
                    fail("Find a ifd after exif ifd");
                    break;
                case IfdParser.TYPE_VALUE_OF_PREV_TAG:
                    checkTag(ifdParser.getCorrespodingExifTag(), ifdParser, EXIF_IFD_VALUE);
                    tagNumber++;
                    break;
            }
            type = ifdParser.next();
        }
        assertEquals(EXIF_IFD_VALUE.size(), tagNumber);
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
                    sbuilder.append(buf[i]);
                }
                break;
            case ExifTag.TYPE_ASCII:
                sbuilder.append(parser.readString(tag.getComponentCount()));
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
                    sbuilder.append(buffer[i]);
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
                        checkTag(ifdParser.getCorrespodingExifTag(), ifdParser, IFD0_VALUE);
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
    }
}
