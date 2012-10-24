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

import java.io.Closeable;

class Util {
    public static boolean equals(Object a, Object b) {
        return (a == b) || (a == null ? false : a.equals(b));
    }

    public static void closeSilently(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Throwable t) {
            // do nothing
        }
    }

    /**
     * Tags that are not defined in the spec.
     */
    static final short TAG_XP_TITLE = (short) 0x9c9b;
    static final short TAG_XP_COMMENT = (short) 0x9c9c;
    static final short TAG_XP_AUTHOR = (short) 0x9c9d;
    static final short TAG_XP_KEYWORDS = (short) 0x9c9e;
    static final short TAG_XP_SUBJECT = (short) 0x9c9f;

    private static String tagUndefinedTypeValueToString(ExifTag tag) {
        StringBuilder sbuilder = new StringBuilder();
        byte[] buf = new byte[tag.getComponentCount()];
        tag.getBytes(buf);
        switch (tag.getTagId()) {
            case ExifTag.TAG_COMPONENTS_CONFIGURATION:
                for(int i = 0, n = tag.getComponentCount(); i < n; i++) {
                    if(i != 0) sbuilder.append(" ");
                    sbuilder.append(buf[i]);
                }
                break;
            default:
                if (buf.length == 1) {
                    sbuilder.append(buf[0]);
                } else {
                    for (int i = 0, n = buf.length; i < n; i++) {
                        byte code = buf[i];
                        if (code == 0) continue;
                        if (code > 31 && code < 127) {
                            sbuilder.append((char) code);
                        } else {
                            sbuilder.append('.');
                        }
                    }
                }
        }
        return sbuilder.toString();
    }

    /**
     * Returns a string representation of the value of this tag.
     */
    public static String tagValueToString(ExifTag tag) {
        StringBuilder sbuilder = new StringBuilder();
        short id = tag.getTagId();
        switch (tag.getDataType()) {
            case ExifTag.TYPE_UNDEFINED:
                sbuilder.append(tagUndefinedTypeValueToString(tag));
                break;
            case ExifTag.TYPE_UNSIGNED_BYTE:
                if (id == ExifTag.TAG_MAKER_NOTE || id == TAG_XP_TITLE ||
                        id == TAG_XP_COMMENT || id == TAG_XP_AUTHOR ||
                        id == TAG_XP_KEYWORDS || id == TAG_XP_SUBJECT) {
                    sbuilder.append(tagUndefinedTypeValueToString(tag));
                } else {
                    byte[] buf = new byte[tag.getComponentCount()];
                    tag.getBytes(buf);
                    for(int i = 0, n = tag.getComponentCount(); i < n; i++) {
                        if(i != 0) sbuilder.append(" ");
                        sbuilder.append(buf[i]);
                    }
                }
                break;
            case ExifTag.TYPE_ASCII:
                byte[] buf = tag.getStringByte();
                for (int i = 0, n = buf.length; i < n; i++) {
                    byte code = buf[i];
                    if (code == 0) {
                        // Treat some tag as undefined type data.
                        if (id == ExifTag.TAG_COPYRIGHT || id == ExifTag.TAG_GPS_DATE_STAMP) {
                            continue;
                        } else {
                            break;
                        }
                    }
                    if (code > 31 && code < 127) {
                        sbuilder.append((char) code);
                    } else {
                        sbuilder.append('.');
                    }
                }
                break;
            case ExifTag.TYPE_UNSIGNED_LONG:
                for(int i = 0, n = tag.getComponentCount(); i < n; i++) {
                    if(i != 0) sbuilder.append(" ");
                    sbuilder.append(tag.getUnsignedLong(i));
                }
                break;
            case ExifTag.TYPE_RATIONAL:
            case ExifTag.TYPE_UNSIGNED_RATIONAL:
                for(int i = 0, n = tag.getComponentCount(); i < n; i++) {
                    Rational r = tag.getRational(i);
                    if(i != 0) sbuilder.append(" ");
                    sbuilder.append(r.getNominator()).append("/").append(r.getDenominator());
                }
                break;
            case ExifTag.TYPE_UNSIGNED_SHORT:
                for(int i = 0, n = tag.getComponentCount(); i < n; i++) {
                    if(i != 0) sbuilder.append(" ");
                    sbuilder.append(tag.getUnsignedShort(i));
                }
                break;
            case ExifTag.TYPE_LONG:
                for(int i = 0, n = tag.getComponentCount(); i < n; i++) {
                    if(i != 0) sbuilder.append(" ");
                    sbuilder.append(tag.getLong(i));
                }
                break;
        }
        return sbuilder.toString();
    }
}
