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

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;

public class ExifTagTest extends TestCase {

    private static long MAX_UNSIGNED_LONG = (1L << 32) - 1;
    private static int MAX_LONG = Integer.MAX_VALUE;
    private static int MIN_LONG = Integer.MIN_VALUE;

    Map<Integer, ExifTag> mTestTags;
    ExifInterface mInterface;
    private ExifTag mVersionTag;
    private ExifTag mGpsVersionTag;
    private ExifTag mModelTag;
    private ExifTag mDateTimeTag;
    private ExifTag mCompressionTag;
    private ExifTag mThumbnailFormatTag;
    private ExifTag mLongitudeTag;
    private ExifTag mShutterTag;
    private ExifTag mInteropIndex;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mInterface = new ExifInterface();

        // TYPE_UNDEFINED with 4 components
        mVersionTag = mInterface.buildTag(ExifInterface.TAG_EXIF_VERSION, new byte[] {
                5, 4, 3, 2
        });
        // TYPE_UNSIGNED_BYTE with 4 components
        mGpsVersionTag = mInterface.buildTag(ExifInterface.TAG_GPS_VERSION_ID, new byte[] {
                6, 7, 8, 9
        });
        // TYPE ASCII with arbitrary length
        mModelTag = mInterface.buildTag(ExifInterface.TAG_MODEL, "helloworld");
        // TYPE_ASCII with 20 components
        mDateTimeTag = mInterface.buildTag(ExifInterface.TAG_DATE_TIME, "2013:02:11 20:20:20");
        // TYPE_UNSIGNED_SHORT with 1 components
        mCompressionTag = mInterface.buildTag(ExifInterface.TAG_COMPRESSION, 100);
        // TYPE_UNSIGNED_LONG with 1 components
        mThumbnailFormatTag =
                mInterface.buildTag(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT, 100);
        // TYPE_UNSIGNED_RATIONAL with 3 components
        mLongitudeTag = mInterface.buildTag(ExifInterface.TAG_GPS_LONGITUDE, new Rational[] {
                new Rational(2, 2), new Rational(11, 11),
                new Rational(102, 102)
        });
        // TYPE_RATIONAL with 1 components
        mShutterTag = mInterface
                .buildTag(ExifInterface.TAG_SHUTTER_SPEED_VALUE, new Rational(4, 6));
        // TYPE_ASCII with arbitrary length
        mInteropIndex = mInterface.buildTag(ExifInterface.TAG_INTEROPERABILITY_INDEX, "foo");

        mTestTags = new HashMap<Integer, ExifTag>();

        mTestTags.put(ExifInterface.TAG_EXIF_VERSION, mVersionTag);
        mTestTags.put(ExifInterface.TAG_GPS_VERSION_ID, mGpsVersionTag);
        mTestTags.put(ExifInterface.TAG_MODEL, mModelTag);
        mTestTags.put(ExifInterface.TAG_DATE_TIME, mDateTimeTag);
        mTestTags.put(ExifInterface.TAG_COMPRESSION, mCompressionTag);
        mTestTags.put(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT, mThumbnailFormatTag);
        mTestTags.put(ExifInterface.TAG_GPS_LONGITUDE, mLongitudeTag);
        mTestTags.put(ExifInterface.TAG_SHUTTER_SPEED_VALUE, mShutterTag);
        mTestTags.put(ExifInterface.TAG_INTEROPERABILITY_INDEX, mInteropIndex);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mInterface = null;
        mTestTags = null;
    }

    public void testValueType() {
        for (ExifTag tag : mTestTags.values()) {
            assertTrue(tag != null);
            int count = tag.getComponentCount();
            int intBuf[] = new int[count];
            long longBuf[] = new long[count];
            byte byteBuf[] = new byte[count];
            Rational rationalBuf[] = new Rational[count];
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++) {
                intBuf[i] = 0;
                longBuf[i] = 0;
                byteBuf[i] = 0;
                rationalBuf[i] = new Rational(0, 0);
                // The string size should equal to component count - 1
                if (i != count - 1) {
                    sb.append("*");
                } else {
                    sb.append("\0");
                }
            }
            String strBuf = sb.toString();

            checkTypeByte(tag, byteBuf);
            checkTypeAscii(tag, strBuf);
            checkTypeUnsignedShort(tag, intBuf);
            checkTypeUnsignedLong(tag, intBuf, longBuf);
            checkTypeLong(tag, intBuf);
            checkTypeRational(tag, rationalBuf);
            checkTypeUnsignedRational(tag, rationalBuf);
        }
    }

    private void checkTypeByte(ExifTag tag, byte[] buf) {
        short type = tag.getDataType();
        assertFalse("\nTag: " + tag.toString(), tag.setValue(buf)
                ^ (type == ExifTag.TYPE_UNDEFINED || type == ExifTag.TYPE_UNSIGNED_BYTE));
    }

    private void checkTypeAscii(ExifTag tag, String str) {
        short type = tag.getDataType();
        assertFalse("\nTag: " + tag.toString(), tag.setValue(str)
                ^ (type == ExifTag.TYPE_ASCII || type == ExifTag.TYPE_UNDEFINED));
    }

    private void checkTypeUnsignedShort(ExifTag tag, int[] intBuf) {
        short type = tag.getDataType();
        assertFalse("\nTag: " + tag.toString(),
                tag.setValue(intBuf)
                        ^ (type == ExifTag.TYPE_UNSIGNED_SHORT
                                || type == ExifTag.TYPE_UNSIGNED_LONG
                                || type == ExifTag.TYPE_LONG));
    }

    private void checkTypeUnsignedLong(ExifTag tag, int[] intBuf, long[] longBuf) {

        // Test value only for unsigned long.
        int count = intBuf.length;
        intBuf[count - 1] = MAX_LONG;
        tag.setValue(intBuf);
        longBuf[count - 1] = MAX_UNSIGNED_LONG;

        assertFalse("\nTag: " + tag.toString(), tag.setValue(longBuf)
                ^ (tag.getDataType() == ExifTag.TYPE_UNSIGNED_LONG));

        intBuf[count - 1] = 0;
        // Test invalid value for all type.
        longBuf[count - 1] = MAX_UNSIGNED_LONG + 1;
        assertFalse(tag.setValue(longBuf));
        longBuf[count - 1] = 0;
    }

    private void checkTypeLong(ExifTag tag, int[] intBuf) {
        int count = intBuf.length;
        intBuf[count - 1] = MAX_LONG;
        tag.setValue(intBuf);
        intBuf[count - 1] = MIN_LONG;

        assertFalse("\nTag: " + tag.toString(), tag.setValue(intBuf)
                ^ (tag.getDataType() == ExifTag.TYPE_LONG));
        intBuf[count - 1] = 0;
    }

    private void checkTypeRational(ExifTag tag, Rational rationalBuf[]) {
        int count = rationalBuf.length;
        Rational r = rationalBuf[count - 1];
        rationalBuf[count - 1] = new Rational(MAX_LONG, MIN_LONG);

        assertFalse("\nTag: " + tag.toString(), tag.setValue(rationalBuf)
                ^ (tag.getDataType() == ExifTag.TYPE_RATIONAL));

        if (tag.getDataType() == ExifTag.TYPE_RATIONAL) {
            // check overflow

            rationalBuf[count - 1] = new Rational(MAX_LONG + 1L, MIN_LONG);
            assertFalse(tag.setValue(rationalBuf));

            rationalBuf[count - 1] = new Rational(MAX_LONG, MIN_LONG - 1L);
            assertFalse(tag.setValue(rationalBuf));
        }
        rationalBuf[count - 1] = r;
    }

    private void checkTypeUnsignedRational(ExifTag tag, Rational rationalBuf[]) {
        int count = rationalBuf.length;
        Rational r = rationalBuf[count - 1];
        rationalBuf[count - 1] = new Rational(MAX_UNSIGNED_LONG, MAX_UNSIGNED_LONG);

        assertFalse("\nTag: " + tag.toString(), tag.setValue(rationalBuf)
                ^ (tag.getDataType() == ExifTag.TYPE_UNSIGNED_RATIONAL));

        if (tag.getDataType() == ExifTag.TYPE_UNSIGNED_RATIONAL) {
            // check overflow
            rationalBuf[count - 1] = new Rational(MAX_UNSIGNED_LONG + 1, 0);
            assertFalse(tag.setValue(rationalBuf));

            rationalBuf[count - 1] = new Rational(MAX_UNSIGNED_LONG, -1);
            assertFalse(tag.setValue(rationalBuf));
        }
        rationalBuf[count - 1] = r;
    }
}
