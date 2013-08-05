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

package com.android.gallery3d.data;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

public class PathTest extends AndroidTestCase {
    @SuppressWarnings("unused")
    private static final String TAG = "PathTest";

    @SmallTest
    public void testToString() {
        Path p = Path.fromString("/hello/world");
        assertEquals("/hello/world", p.toString());

        p = Path.fromString("/a");
        assertEquals("/a", p.toString());

        p = Path.fromString("");
        assertEquals("", p.toString());
    }

    @SmallTest
    public void testSplit() {
        Path p = Path.fromString("/hello/world");
        String[] s = p.split();
        assertEquals(2, s.length);
        assertEquals("hello", s[0]);
        assertEquals("world", s[1]);

        p = Path.fromString("");
        assertEquals(0, p.split().length);
    }

    @SmallTest
    public void testPrefix() {
        Path p = Path.fromString("/hello/world");
        assertEquals("hello", p.getPrefix());

        p = Path.fromString("");
        assertEquals("", p.getPrefix());
    }

    @SmallTest
    public void testGetChild() {
        Path p = Path.fromString("/hello");
        Path q = Path.fromString("/hello/world");
        assertSame(q, p.getChild("world"));
        Path r = q.getChild(17);
        assertEquals("/hello/world/17", r.toString());
    }

    @SmallTest
    public void testSplitSequence() {
        String[] s = Path.splitSequence("{a,bb,ccc}");
        assertEquals(3, s.length);
        assertEquals("a", s[0]);
        assertEquals("bb", s[1]);
        assertEquals("ccc", s[2]);

        s = Path.splitSequence("{a,{bb,ccc},d}");
        assertEquals(3, s.length);
        assertEquals("a", s[0]);
        assertEquals("{bb,ccc}", s[1]);
        assertEquals("d", s[2]);
    }
}
