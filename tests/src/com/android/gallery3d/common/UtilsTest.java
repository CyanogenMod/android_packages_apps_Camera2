/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.common;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

public class UtilsTest extends AndroidTestCase {
    private static final String TAG = "UtilsTest";

    private static final int [] testData = new int [] {
        /* outWidth, outHeight, minSideLength, maxNumOfPixels, sample size */
        1, 1, BitmapUtils.UNCONSTRAINED, BitmapUtils.UNCONSTRAINED, 1,
        1, 1, 1, 1, 1,
        100, 100, 100, 10000, 1,
        100, 100, 100, 2500, 2,
        99, 66, 33, 10000, 2,
        66, 99, 33, 10000, 2,
        99, 66, 34, 10000, 1,
        99, 66, 22, 10000, 4,
        99, 66, 16, 10000, 4,

        10000, 10000, 20000, 1000000, 16,

        100, 100, 100, 10000, 1, // 1
        100, 100, 50, 10000, 2,  // 2
        100, 100, 30, 10000, 4,  // 3->4
        100, 100, 22, 10000, 4,  // 4
        100, 100, 20, 10000, 8,  // 5->8
        100, 100, 11, 10000, 16, // 9->16
        100, 100, 5,  10000, 24, // 20->24
        100, 100, 2,  10000, 56, // 50->56

        100, 100, 100, 10000 - 1, 2,                  // a bit less than 1
        100, 100, 100, 10000 / (2 * 2) - 1, 4,        // a bit less than 2
        100, 100, 100, 10000 / (3 * 3) - 1, 4,        // a bit less than 3
        100, 100, 100, 10000 / (4 * 4) - 1, 8,        // a bit less than 4
        100, 100, 100, 10000 / (8 * 8) - 1, 16,       // a bit less than 8
        100, 100, 100, 10000 / (16 * 16) - 1, 24,     // a bit less than 16
        100, 100, 100, 10000 / (24 * 24) - 1, 32,     // a bit less than 24
        100, 100, 100, 10000 / (32 * 32) - 1, 40,     // a bit less than 32

        640, 480, 480, BitmapUtils.UNCONSTRAINED, 1,  // 1
        640, 480, 240, BitmapUtils.UNCONSTRAINED, 2,  // 2
        640, 480, 160, BitmapUtils.UNCONSTRAINED, 4,  // 3->4
        640, 480, 120, BitmapUtils.UNCONSTRAINED, 4,  // 4
        640, 480, 96, BitmapUtils.UNCONSTRAINED,  8,  // 5->8
        640, 480, 80, BitmapUtils.UNCONSTRAINED,  8,  // 6->8
        640, 480, 60, BitmapUtils.UNCONSTRAINED,  8,  // 8
        640, 480, 48, BitmapUtils.UNCONSTRAINED, 16,  // 10->16
        640, 480, 40, BitmapUtils.UNCONSTRAINED, 16,  // 12->16
        640, 480, 30, BitmapUtils.UNCONSTRAINED, 16,  // 16
        640, 480, 24, BitmapUtils.UNCONSTRAINED, 24,  // 20->24
        640, 480, 20, BitmapUtils.UNCONSTRAINED, 24,  // 24
        640, 480, 16, BitmapUtils.UNCONSTRAINED, 32,  // 30->32
        640, 480, 12, BitmapUtils.UNCONSTRAINED, 40,  // 40
        640, 480, 10, BitmapUtils.UNCONSTRAINED, 48,  // 48
        640, 480, 8, BitmapUtils.UNCONSTRAINED,  64,  // 60->64
        640, 480, 6, BitmapUtils.UNCONSTRAINED,  80,  // 80
        640, 480, 4, BitmapUtils.UNCONSTRAINED, 120,  // 120
        640, 480, 3, BitmapUtils.UNCONSTRAINED, 160,  // 160
        640, 480, 2, BitmapUtils.UNCONSTRAINED, 240,  // 240
        640, 480, 1, BitmapUtils.UNCONSTRAINED, 480,  // 480

        640, 480, BitmapUtils.UNCONSTRAINED, BitmapUtils.UNCONSTRAINED, 1,
        640, 480, BitmapUtils.UNCONSTRAINED, 640 * 480, 1,                  // 1
        640, 480, BitmapUtils.UNCONSTRAINED, 640 * 480 - 1, 2,              // a bit less than 1
        640, 480, BitmapUtils.UNCONSTRAINED, 640 * 480 / 4, 2,              // 2
        640, 480, BitmapUtils.UNCONSTRAINED, 640 * 480 / 4 - 1, 4,          // a bit less than 2
        640, 480, BitmapUtils.UNCONSTRAINED, 640 * 480 / 9, 4,              // 3
        640, 480, BitmapUtils.UNCONSTRAINED, 640 * 480 / 9 - 1, 4,          // a bit less than 3
        640, 480, BitmapUtils.UNCONSTRAINED, 640 * 480 / 16, 4,             // 4
        640, 480, BitmapUtils.UNCONSTRAINED, 640 * 480 / 16 - 1, 8,         // a bit less than 4
        640, 480, BitmapUtils.UNCONSTRAINED, 640 * 480 / 64, 8,             // 8
        640, 480, BitmapUtils.UNCONSTRAINED, 640 * 480 / 64 - 1, 16,        // a bit less than 8
        640, 480, BitmapUtils.UNCONSTRAINED, 640 * 480 / 256, 16,           // 16
        640, 480, BitmapUtils.UNCONSTRAINED, 640 * 480 / 256 - 1, 24,       // a bit less than 16
        640, 480, BitmapUtils.UNCONSTRAINED, 640 * 480 / (24 * 24) - 1, 32, // a bit less than 24
    };

    @SmallTest
    public void testComputeSampleSize() {

        for (int i = 0; i < testData.length; i += 5) {
            int w = testData[i];
            int h = testData[i + 1];
            int minSide = testData[i + 2];
            int maxPixels = testData[i + 3];
            int sampleSize = testData[i + 4];
            int result = BitmapUtils.computeSampleSize(w, h, minSide, maxPixels);
            if (result != sampleSize) {
                Log.v(TAG, w + "x" + h + ", minSide = " + minSide + ", maxPixels = "
                        + maxPixels + ", sampleSize = " + sampleSize + ", result = "
                        + result);
            }
            assertTrue(sampleSize == result);
        }
    }

    public void testAssert() {
        // This should not throw an exception.
        Utils.assertTrue(true);

        // This should throw an exception.
        try {
            Utils.assertTrue(false);
            fail();
        } catch (AssertionError ex) {
            // expected.
        }
    }

    public void testCheckNotNull() {
        // These should not throw an expection.
        Utils.checkNotNull(new Object());
        Utils.checkNotNull(0);
        Utils.checkNotNull("");

        // This should throw an expection.
        try {
            Utils.checkNotNull(null);
            fail();
        } catch (NullPointerException ex) {
            // expected.
        }
    }

    public void testEquals() {
        Object a = new Object();
        Object b = new Object();

        assertTrue(Utils.equals(null, null));
        assertTrue(Utils.equals(a, a));
        assertFalse(Utils.equals(null, a));
        assertFalse(Utils.equals(a, null));
        assertFalse(Utils.equals(a, b));
    }

    public void testNextPowerOf2() {
        int[] q = new int[] {1, 2, 3, 4, 5, 6, 10, 65535, (1 << 30) - 1, (1 << 30)};
        int[] a = new int[] {1, 2, 4, 4, 8, 8, 16, 65536, (1 << 30)    , (1 << 30)};

        for (int i = 0; i < q.length; i++) {
            assertEquals(a[i], Utils.nextPowerOf2(q[i]));
        }

        int[] e = new int[] {0, -1, -2, -4, -65536, (1 << 30) + 1, Integer.MAX_VALUE};

        for (int v : e) {
            try {
                Utils.nextPowerOf2(v);
                fail();
            } catch (IllegalArgumentException ex) {
                // expected.
            }
        }
    }

    public void testClamp() {
        assertEquals(1000, Utils.clamp(300, 1000, 2000));
        assertEquals(1300, Utils.clamp(1300, 1000, 2000));
        assertEquals(2000, Utils.clamp(2300, 1000, 2000));

        assertEquals(0.125f, Utils.clamp(0.1f, 0.125f, 0.5f));
        assertEquals(0.25f, Utils.clamp(0.25f, 0.125f, 0.5f));
        assertEquals(0.5f, Utils.clamp(0.9f, 0.125f, 0.5f));
    }

    public void testIsOpaque() {
        assertTrue(Utils.isOpaque(0xFF000000));
        assertTrue(Utils.isOpaque(0xFFFFFFFF));
        assertTrue(Utils.isOpaque(0xFF123456));

        assertFalse(Utils.isOpaque(0xFEFFFFFF));
        assertFalse(Utils.isOpaque(0x8FFFFFFF));
        assertFalse(Utils.isOpaque(0x00FF0000));
        assertFalse(Utils.isOpaque(0x5500FF00));
        assertFalse(Utils.isOpaque(0xAA0000FF));
    }

    public static void assertFloatEq(float expected, float actual) {
        if (Math.abs(actual - expected) > 1e-6) {
            Log.v(TAG, "expected: " + expected + ", actual: " + actual);
            fail();
        }
    }
}
