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

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Map;

public class IfdData {

    private final int mIfdType;
    private final Map<Short, ExifTag> mExifTags = new HashMap<Short, ExifTag>();
    private final Map<Short, Object> mValues = new HashMap<Short, Object>();

    public IfdData(int ifdType) {
        mIfdType = ifdType;
    }

    public ExifTag[] getAllTags(ExifTag[] outTag) {
        return mExifTags.values().toArray(outTag);
    }

    public int getIfdType() {
        return mIfdType;
    }

    public ExifTag getTag(short tagId) {
        return mExifTags.get(tagId);
    }

    public short getShort(short tagId, int index) {
        return (Short) Array.get(mValues.get(tagId), index);
    }

    public short getShort(short tagId) {
        return (Short) Array.get(mValues.get(tagId), 0);
    }

    public int getUnsignedShort(short tagId, int index) {
        return (Integer) Array.get(mValues.get(tagId), index);
    }

    public int getUnsignedShort(short tagId) {
        return (Integer) Array.get(mValues.get(tagId), 0);
    }

    public int getInt(short tagId, int index) {
        return (Integer) Array.get(mValues.get(tagId), index);
    }

    public int getInt(short tagId) {
        return (Integer) Array.get(mValues.get(tagId), 0);
    }

    public long getUnsignedInt(short tagId, int index) {
        return (Long) Array.get(mValues.get(tagId), index);
    }

    public long getUnsignedInt(short tagId) {
        return (Long) Array.get(mValues.get(tagId), 0);
    }

    public String getString(short tagId) {
        return (String) mValues.get(tagId);
    }

    public Rational getRational(short tagId, int index) {
        return ((Rational[]) mValues.get(tagId))[index];
    }

    public Rational getRational(short tagId) {
        return ((Rational[]) mValues.get(tagId))[0];
    }

    public int getBytes(short tagId, byte[] buf) {
        return getBytes(tagId, buf, 0, buf.length);
    }

    public int getBytes(short tagId, byte[] buf, int offset, int length) {
        Object data = mValues.get(tagId);
        if (Array.getLength(data) < length + offset) {
            System.arraycopy(data, offset, buf, 0, Array.getLength(data) - offset);
            return Array.getLength(data) - offset;
        } else {
            System.arraycopy(data, offset, buf, 0, length);
            return length;
        }
    }

    public void addTag(ExifTag tag, Object object) {
        mExifTags.put(tag.getTagId(),  tag);
        if (object.getClass().isArray() || object.getClass() == String.class) {
            mValues.put(tag.getTagId(), object);
        } else {
            Object array = Array.newInstance(object.getClass(), 1);
            Array.set(array, 0, object);
            mValues.put(tag.getTagId(), array);
        }
    }
}
