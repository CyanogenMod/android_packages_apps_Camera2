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
import java.util.Comparator;
import java.util.TreeSet;

public class IfdParser {

    // special sub IDF tags
    private static final short EXIF_IDF = (short) 0x8769;
    private static final short GPS_IDF = (short) 0x8825;
    private static final short INTEROPERABILITY_IDF = (short) 0xA005;

    private static final int TAG_SIZE = 12;

    private final TiffInputStream mTiffStream;
    private final int mEndOfTagOffset;
    private final int mNumOfTag;
    private int mNextOffset;
    private int mOffsetToNextIfd = 0;

    private TreeSet<ExifTag> mCorrespondingTag = new TreeSet<ExifTag>(
            new Comparator<ExifTag>() {
        @Override
        public int compare(ExifTag lhs, ExifTag rhs) {
            return lhs.getOffset() - rhs.getOffset();
        }
    });
    private ExifTag mCurrTag;

    public static final int TYPE_NEW_TAG = 0;
    public static final int TYPE_VALUE_OF_PREV_TAG = 1;
    public static final int TYPE_NEXT_IFD = 2;
    public static final int TYPE_END = 3;
    public static final int TYPE_SUB_IFD = 4;

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
            mCurrTag = mCorrespondingTag.pollFirst();
            skipTo(mCurrTag.getOffset());
            if (isSubIfdTag(mCurrTag.getTagId())) {
                return TYPE_SUB_IFD;
            } else {
                return TYPE_VALUE_OF_PREV_TAG;
            }
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

        if (ExifTag.getElementSize(dataFormat) * numOfComp > 4
                || isSubIfdTag(tagId)) {
            int offset = mTiffStream.readInt();
            return new ExifTag(tagId, dataFormat, (int) numOfComp, offset);
        } else {
            return new ExifTag(tagId, dataFormat, (int) numOfComp);
        }
    }

    public ExifTag getCorrespodingExifTag() {
        return mCurrTag.getOffset() != mTiffStream.getReadByteCount() ? null : mCurrTag;
    }

    public void waitValueOfTag(ExifTag tag) {
        mCorrespondingTag.add(tag);
    }

    public void skipTo(int offset) throws IOException {
        mTiffStream.skipTo(offset);
        while (!mCorrespondingTag.isEmpty() && mCorrespondingTag.first().getOffset() < offset) {
            mCorrespondingTag.pollFirst();
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
        byte[] buf = new byte[n];
        mTiffStream.readOrThrow(buf);
        return new String(buf, 0, n - 1, "UTF8");
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

    private static boolean isSubIfdTag(short tagId) {
        return tagId == EXIF_IDF || tagId == GPS_IDF || tagId == INTEROPERABILITY_IDF;
    }
}