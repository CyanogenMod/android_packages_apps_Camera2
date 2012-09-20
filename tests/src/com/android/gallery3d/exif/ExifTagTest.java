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

public class ExifTagTest extends TestCase {

    private static long MAX_UNSIGNED_LONG = (1L << 32) - 1;
    private static int MAX_LONG = Integer.MAX_VALUE;
    private static int MIN_LONG = Integer.MIN_VALUE;

    private static final ExifTag sTestTags[] = {
        ExifTag.buildTag(ExifTag.TAG_EXIF_VERSION), // TYPE_UNDEFINED with 4 components
        ExifTag.buildTag(ExifTag.TAG_GPS_VERSION_ID), // TYPE_UNSIGNED_BYTE with 4 components
        ExifTag.buildTag(ExifTag.TAG_DATE_TIME), // TYPE_ASCII with 20 components
        ExifTag.buildTag(ExifTag.TAG_COMPRESSION), // TYPE_UNSIGNED_SHORT with 1 components
        // TYPE_UNSIGNED_LONG with 1 components
        ExifTag.buildTag(ExifTag.TAG_JPEG_INTERCHANGE_FORMAT),
        ExifTag.buildTag(ExifTag.TAG_GPS_LONGITUDE), // TYPE_UNSIGNED_RATIONAL with 3 components
        ExifTag.buildTag(ExifTag.TAG_SHUTTER_SPEED_VALUE), // TYPE_RATIONAL with 1 components
        // There is no tag defined with TYPE_LONG. Create a dummy one for testing.
        new ExifTag((short) 0, ExifTag.TYPE_LONG, 1, 0)
    };

    public void testValueType() {
        for (ExifTag tag: sTestTags) {
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
                if (i != 0) sb.append("*");
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
        boolean excepThrow = false;
        short type = tag.getDataType();
        try {
            tag.setValue(buf);
        } catch (IllegalArgumentException e) {
            excepThrow = true;
        }
        assertTrue("Tag ID: " + tag.getTagId(),
                (type == ExifTag.TYPE_UNDEFINED || type == ExifTag.TYPE_UNSIGNED_BYTE)
                ^ excepThrow);
    }

    private void checkTypeAscii(ExifTag tag, String str) {
        boolean excepThrow = false;
        try {
            tag.setValue(str);
        } catch (IllegalArgumentException e) {
            excepThrow = true;
        }
        assertTrue("Tag ID: " + tag.getTagId(),
                tag.getDataType() == ExifTag.TYPE_ASCII ^ excepThrow);
    }

    private void checkTypeUnsignedShort(ExifTag tag, int[] intBuf) {
        boolean excepThrow = false;
        short type = tag.getDataType();
        try {
            tag.setValue(intBuf);
        } catch (IllegalArgumentException e) {
            excepThrow = true;
        }
        assertTrue("Tag ID: " + tag.getTagId(),
                (type == ExifTag.TYPE_UNSIGNED_SHORT
                || type == ExifTag.TYPE_UNSIGNED_LONG || type == ExifTag.TYPE_LONG) ^ excepThrow);
    }

    private void checkTypeUnsignedLong(ExifTag tag, int[] intBuf, long[] longBuf) {

        // Test value only for unsigned long.
        boolean excepThrow = false;
        int count = intBuf.length;
        try {
            intBuf[count - 1] = MAX_LONG;
            tag.setValue(intBuf);
            longBuf[count - 1] = MAX_UNSIGNED_LONG;
            tag.setValue(longBuf);
        } catch (IllegalArgumentException e) {
            excepThrow = true;
        }
        intBuf[count - 1] = 0;
        assertTrue("Tag ID: " + tag.getTagId(),
                tag.getDataType() == ExifTag.TYPE_UNSIGNED_LONG ^ excepThrow);


        // Test invalid value for all type.
        try {
            longBuf[count - 1] = MAX_UNSIGNED_LONG + 1;
            tag.setValue(longBuf);
            fail();
        } catch (IllegalArgumentException expected) {}
        longBuf[count - 1] = 0;
    }

    private void checkTypeLong(ExifTag tag, int[] intBuf) {
        boolean excepThrow = false;
        int count = intBuf.length;
        try {
            intBuf[count - 1] = MAX_LONG;
            tag.setValue(intBuf);
            intBuf[count - 1] = MIN_LONG;
            tag.setValue(intBuf);
        } catch (IllegalArgumentException e) {
            excepThrow = true;
        }
        intBuf[count - 1] = 0;
        assertTrue("Tag ID: " + tag.getTagId(),
                tag.getDataType() == ExifTag.TYPE_LONG ^ excepThrow);
    }

    private void checkTypeRational(ExifTag tag, Rational rationalBuf[]) {
        boolean excepThrow = false;
        int count = rationalBuf.length;
        Rational r = rationalBuf[count - 1];
        try {
            rationalBuf[count - 1] = new Rational(MAX_LONG, MIN_LONG);
            tag.setValue(rationalBuf);
        } catch (IllegalArgumentException e) {
            excepThrow = true;
        }
        assertTrue("Tag ID: " + tag.getTagId(),
                tag.getDataType() == ExifTag.TYPE_RATIONAL ^ excepThrow);

        if(tag.getDataType() == ExifTag.TYPE_RATIONAL) {
            // check overflow
            try {
                rationalBuf[count - 1] = new Rational(MAX_LONG + 1L, MIN_LONG);
                tag.setValue(rationalBuf);
                fail();
            } catch (IllegalArgumentException expected) {}

            try {
                rationalBuf[count - 1] = new Rational(MAX_LONG, MIN_LONG - 1L);
                tag.setValue(rationalBuf);
                fail();
            } catch (IllegalArgumentException expected) {}
        }
        rationalBuf[count - 1] = r;
    }

    private void checkTypeUnsignedRational(ExifTag tag, Rational rationalBuf[]) {
        boolean excepThrow = false;
        int count = rationalBuf.length;
        Rational r = rationalBuf[count - 1];
        try {
            rationalBuf[count - 1] = new Rational(MAX_UNSIGNED_LONG, MAX_UNSIGNED_LONG);
            tag.setValue(rationalBuf);
        } catch (IllegalArgumentException e) {
            excepThrow = true;
        }
        assertTrue("Tag ID: " + tag.getTagId(),
                tag.getDataType() == ExifTag.TYPE_UNSIGNED_RATIONAL ^ excepThrow);

        if(tag.getDataType() == ExifTag.TYPE_UNSIGNED_RATIONAL) {
            // check overflow
            try {
                rationalBuf[count - 1] = new Rational(MAX_UNSIGNED_LONG + 1, 0);
                tag.setValue(rationalBuf);
                fail();
            } catch (IllegalArgumentException expected) {}

            try {
                rationalBuf[count - 1] = new Rational(MAX_UNSIGNED_LONG, -1);
                tag.setValue(rationalBuf);
                fail();
            } catch (IllegalArgumentException expected) {}
        }
        rationalBuf[count - 1] = r;
    }
}
