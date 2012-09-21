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

import java.nio.ByteOrder;

public class ExifDataTest extends TestCase {
    public void testAddTag() {
        ExifData exifData = new ExifData(ByteOrder.BIG_ENDIAN);
        // IFD0 tag
        exifData.addTag(ExifTag.TAG_MAKE).setValue("test");
        exifData.addTag(ExifTag.TAG_IMAGE_WIDTH).setValue(1000);

        // EXIF tag
        exifData.addTag(ExifTag.TAG_ISO_SPEED_RATINGS).setValue(1);

        // GPS tag
        exifData.addTag(ExifTag.TAG_GPS_ALTITUDE).setValue(new Rational(10, 100));

        // Interoperability tag
        exifData.addInteroperabilityTag(ExifTag.TAG_INTEROPERABILITY_INDEX).setValue("inter_test");

        // IFD1 tag
        exifData.addThumbnailTag(ExifTag.TAG_MAKE).setValue("test_thumb");
        exifData.addThumbnailTag(ExifTag.TAG_IMAGE_WIDTH).setValue(100);

        // check data
        assertEquals("test", exifData.getTag(ExifTag.TAG_MAKE).getString());
        assertEquals(1000, exifData.getTag(ExifTag.TAG_IMAGE_WIDTH).getUnsignedLong(0));
        assertEquals(1, exifData.getTag(ExifTag.TAG_ISO_SPEED_RATINGS).getUnsignedShort(0));
        assertEquals(new Rational(10, 100),
                exifData.getTag(ExifTag.TAG_GPS_ALTITUDE).getRational(0));
        assertEquals("inter_test",
                exifData.getInteroperabilityTag(ExifTag.TAG_INTEROPERABILITY_INDEX).getString());
        assertEquals("test_thumb", exifData.getThumbnailTag(ExifTag.TAG_MAKE).getString());
        assertEquals(100, exifData.getThumbnailTag(ExifTag.TAG_IMAGE_WIDTH).getUnsignedLong(0));
    }
}
