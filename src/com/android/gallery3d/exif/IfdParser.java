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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map.Entry;
import java.util.TreeMap;

public class IfdParser {

    private static final int TAG_SIZE = 12;

    private final TiffInputStream mTiffStream;
    private final int mEndOfTagOffset;
    private final int mNumOfTag;
    private int mNextOffset;
    private int mOffsetToNextIfd = 0;

    private TreeMap<Integer, ExifTag> mCorrespondingTag = new TreeMap<Integer, ExifTag>();

    private ExifTag mCurrTag;
    private int mCurrTagOffset;

    public static final int TYPE_NEW_TAG = 0;
    public static final int TYPE_VALUE_OF_PREV_TAG = 1;
    public static final int TYPE_NEXT_IFD = 2;
    public static final int TYPE_END = 3;

    IfdParser(TiffInputStream tiffStream, int offset) throws IOException {
        mTiffStream = tiffStream;
        mTiffStream.skipTo(offset);
        mNumOfTag = mTiffStream.readUnsignedShort();
        mEndOfTagOffset = offset + mNumOfTag * TAG_SIZE + 2;
        mNextOffset = offset + 2;
    }

    public int next() throws IOException {
        int offset = mTiffStream.getReadByteCount();
        if (offset < mEndOfTagOffset) {
            offset = mNextOffset;
            skipTo(mNextOffset);

            if(mNextOffset < mEndOfTagOffset) {
                mNextOffset += TAG_SIZE;
                return TYPE_NEW_TAG;
            }
        }

        if (offset == mEndOfTagOffset) {
            mOffsetToNextIfd = mTiffStream.readInt();
        }

        if (!mCorrespondingTag.isEmpty()) {
            Entry<Integer, ExifTag> entry = mCorrespondingTag.pollFirstEntry();
            mCurrTag = entry.getValue();
            mCurrTagOffset = entry.getKey();
            skipTo(entry.getKey());
            return TYPE_VALUE_OF_PREV_TAG;
        } else {
            if (offset <= mOffsetToNextIfd) {
                skipTo(mOffsetToNextIfd);
                // Reset mOffsetToNextIfd to 0 so next call to next() will point to the end
                mOffsetToNextIfd = 0;
                return TYPE_NEXT_IFD;
            } else {
                return TYPE_END;
            }
        }
    }

    public ExifTag readTag() throws IOException, ExifInvalidFormatException {
        short tagId = mTiffStream.readShort();
        short dataFormat = mTiffStream.readShort();
        long numOfComp = mTiffStream.readUnsignedInt();
        if (numOfComp > Integer.MAX_VALUE) {
            throw new ExifInvalidFormatException(
                    "Number of component is larger then Integer.MAX_VALUE");
        }
        return new ExifTag(tagId, dataFormat, (int) numOfComp);
    }

    public ExifTag getCorrespodingExifTag() {
        return mCurrTagOffset != mTiffStream.getReadByteCount() ? null : mCurrTag;
    }

    public void waitValueOfTag(ExifTag tag, long offset) {
        if (offset > Integer.MAX_VALUE || offset < 0) {
            throw new IllegalArgumentException(offset + " must be in 0 ~ " + Integer.MAX_VALUE);
        }
        mCorrespondingTag.put((int) offset, tag);
    }

    public void skipTo(int offset) throws IOException {
        mTiffStream.skipTo(offset);
        while (!mCorrespondingTag.isEmpty() && mCorrespondingTag.firstKey() < offset) {
            mCorrespondingTag.pollFirstEntry();
        }
    }

    public IfdParser parseIfdBlock() throws IOException {
        return new IfdParser(mTiffStream, mTiffStream.getReadByteCount());
    }

    public int read(byte[] buffer, int offset, int length) throws IOException {
        return mTiffStream.read(buffer, offset, length);
    }

    public int read(byte[] buffer) throws IOException {
        return mTiffStream.read(buffer);
    }

    public String readString(int n) throws IOException {
        if (n > 0) {
            byte[] buf = new byte[n];
            mTiffStream.readOrThrow(buf);
            return new String(buf, 0, n - 1, "UTF8");
        } else {
            return "";
        }
    }

    public String readString(int n, Charset charset) throws IOException {
        byte[] buf = new byte[n];
        mTiffStream.readOrThrow(buf);
        return new String(buf, 0, n - 1, charset);
    }

    public int readUnsignedShort() throws IOException {
        return readShort() & 0xffff;
    }

    public long readUnsignedInt() throws IOException {
        return readInt() & 0xffffffffL;
    }

    public Rational readUnsignedRational() throws IOException {
        long nomi = readUnsignedInt();
        long denomi = readUnsignedInt();
        return new Rational(nomi, denomi);
    }

    public int readInt() throws IOException {
        return mTiffStream.readInt();
    }

    public short readShort() throws IOException {
        return mTiffStream.readShort();
    }

    public Rational readRational() throws IOException {
        int nomi = readInt();
        int denomi = readInt();
        return new Rational(nomi, denomi);
    }
}