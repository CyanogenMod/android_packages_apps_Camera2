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

public class ExifTag {

    public static final short TYPE_BYTE = 1;
    public static final short TYPE_ASCII = 2;
    public static final short TYPE_SHORT = 3;
    public static final short TYPE_INT = 4;
    public static final short TYPE_RATIONAL = 5;
    public static final short TYPE_UNDEFINED = 7;
    public static final short TYPE_SINT = 9;
    public static final short TYPE_SRATIONAL = 10;

    private static final int TYPE_TO_SIZE_MAP[] = new int[11];
    static {
        TYPE_TO_SIZE_MAP[TYPE_BYTE] = 1;
        TYPE_TO_SIZE_MAP[TYPE_ASCII] = 1;
        TYPE_TO_SIZE_MAP[TYPE_SHORT] = 2;
        TYPE_TO_SIZE_MAP[TYPE_INT] = 4;
        TYPE_TO_SIZE_MAP[TYPE_RATIONAL] = 8;
        TYPE_TO_SIZE_MAP[TYPE_UNDEFINED] = 1;
        TYPE_TO_SIZE_MAP[TYPE_SINT] = 4;
        TYPE_TO_SIZE_MAP[TYPE_SRATIONAL] = 8;
    }

    public static int getElementSize(short type) {
        return TYPE_TO_SIZE_MAP[type];
    }

    private final short mTagId;
    private final short mDataType;
    private final int mDataCount;
    private final int mOffset;

    ExifTag(short tagId, short type, int dataCount) {
        mTagId = tagId;
        mDataType = type;
        mDataCount = dataCount;
        mOffset = -1;
    }

    ExifTag(short tagId, short type, int dataCount, int offset) {
        mTagId = tagId;
        mDataType = type;
        mDataCount = dataCount;
        mOffset = offset;
    }

    public int getOffset() {
        return mOffset;
    }

    public short getTagId() {
        return mTagId;
    }

    public short getDataType() {
        return mDataType;
    }

    public int getComponentCount() {
        return mDataCount;
    }
}