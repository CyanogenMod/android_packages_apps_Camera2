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

package com.android.gallery3d.util;

import com.android.gallery3d.util.Profile;

import android.os.Environment;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import junit.framework.Assert;
import junit.framework.TestCase;

@SmallTest
public class ProfileTest extends TestCase {
    private static final String TAG = "ProfileTest";
    private static final String TEST_FILE =
            Environment.getExternalStorageDirectory().getPath() + "/test.dat";


    public void testProfile() throws IOException {
        ProfileData p = new ProfileData();
        ParsedProfile q;
        String[] A = {"A"};
        String[] B = {"B"};
        String[] AC = {"A", "C"};
        String[] AD = {"A", "D"};

        // Empty profile
        p.dumpToFile(TEST_FILE);
        q = new ParsedProfile(TEST_FILE);
        assertTrue(q.mEntries.isEmpty());
        assertTrue(q.mSymbols.isEmpty());

        // Only one sample
        p.addSample(A);
        p.dumpToFile(TEST_FILE);
        q = new ParsedProfile(TEST_FILE);
        assertEquals(1, q.mEntries.size());
        assertEquals(1, q.mSymbols.size());
        assertEquals(1, q.mEntries.get(0).sampleCount);

        // Two samples at the same place
        p.addSample(A);
        p.dumpToFile(TEST_FILE);
        q = new ParsedProfile(TEST_FILE);
        assertEquals(1, q.mEntries.size());
        assertEquals(1, q.mSymbols.size());
        assertEquals(2, q.mEntries.get(0).sampleCount);

        // Two samples at the different places
        p.reset();
        p.addSample(A);
        p.addSample(B);
        p.dumpToFile(TEST_FILE);
        q = new ParsedProfile(TEST_FILE);
        assertEquals(2, q.mEntries.size());
        assertEquals(2, q.mSymbols.size());
        assertEquals(1, q.mEntries.get(0).sampleCount);
        assertEquals(1, q.mEntries.get(1).sampleCount);

        // depth > 1
        p.reset();
        p.addSample(AC);
        p.dumpToFile(TEST_FILE);
        q = new ParsedProfile(TEST_FILE);
        assertEquals(1, q.mEntries.size());
        assertEquals(2, q.mSymbols.size());
        assertEquals(1, q.mEntries.get(0).sampleCount);

        // two samples (AC and AD)
        p.addSample(AD);
        p.dumpToFile(TEST_FILE);
        q = new ParsedProfile(TEST_FILE);
        assertEquals(2, q.mEntries.size());
        assertEquals(3, q.mSymbols.size());  // three symbols: A, C, D
        assertEquals(1, q.mEntries.get(0).sampleCount);
        assertEquals(1, q.mEntries.get(0).sampleCount);

        // Remove the test file
        new File(TEST_FILE).delete();
    }
}

class ParsedProfile {
    public class Entry {
        int sampleCount;
        int stackId[];
    }

    ArrayList<Entry> mEntries = new ArrayList<Entry>();
    HashMap<Integer, String> mSymbols = new HashMap<Integer, String>();
    private DataInputStream mIn;
    private byte[] mScratch = new byte[4];  // scratch buffer for readInt

    public ParsedProfile(String filename) throws IOException {
        mIn = new DataInputStream(new FileInputStream(filename));

        Entry entry = parseOneEntry();
        checkIsFirstEntry(entry);

        while (true) {
            entry = parseOneEntry();
            if (entry.sampleCount == 0) {
                checkIsLastEntry(entry);
                break;
            }
            mEntries.add(entry);
        }

        // Read symbol table
        while (true) {
            String line = mIn.readLine();
            if (line == null) break;
            String[] fields = line.split(" +");
            checkIsValidSymbolLine(fields);
            mSymbols.put(Integer.decode(fields[0]), fields[1]);
        }
    }

    private void checkIsFirstEntry(Entry entry) {
        Assert.assertEquals(0, entry.sampleCount);
        Assert.assertEquals(3, entry.stackId.length);
        Assert.assertEquals(1, entry.stackId[0]);
        Assert.assertTrue(entry.stackId[1] > 0);  // sampling period
        Assert.assertEquals(0, entry.stackId[2]);  // padding
    }

    private void checkIsLastEntry(Entry entry) {
        Assert.assertEquals(0, entry.sampleCount);
        Assert.assertEquals(1, entry.stackId.length);
        Assert.assertEquals(0, entry.stackId[0]);
    }

    private void checkIsValidSymbolLine(String[] fields) {
        Assert.assertEquals(2, fields.length);
        Assert.assertTrue(fields[0].startsWith("0x"));
    }

    private Entry parseOneEntry() throws IOException {
        int sampleCount = readInt();
        int depth = readInt();
        Entry e = new Entry();
        e.sampleCount = sampleCount;
        e.stackId = new int[depth];
        for (int i = 0; i < depth; i++) {
            e.stackId[i] = readInt();
        }
        return e;
    }

    private int readInt() throws IOException {
        mIn.read(mScratch, 0, 4);
        return (mScratch[0] & 0xff) |
                ((mScratch[1] & 0xff) << 8) |
                ((mScratch[2] & 0xff) << 16) |
                ((mScratch[3] & 0xff) << 24);
    }
}
