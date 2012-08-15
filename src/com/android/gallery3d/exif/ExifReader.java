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
import java.io.InputStream;

public class ExifReader {

    public ExifData getExifData(InputStream inputStream) throws ExifInvalidFormatException,
            IOException {
        ExifParser parser = new ExifParser();
        IfdParser ifdParser = parser.parse(inputStream);
        ExifData exifData = new ExifData();
        IfdData ifdData = new IfdData(ExifData.TYPE_IFD_0);
        parseIfd(ifdParser, ifdData, exifData);
        exifData.addIfdData(ifdData);
        return exifData;
    }

    public void parseIfd(IfdParser ifdParser, IfdData ifdData, ExifData exifData)
            throws IOException, ExifInvalidFormatException {
        int type = ifdParser.next();
        while (type != IfdParser.TYPE_END) {
            switch (type) {
                case IfdParser.TYPE_NEW_TAG:
                    ExifTag tag = ifdParser.readTag();
                    if (tag.getDataSize() > 4) {
                        long offset = ifdParser.readUnsignedInt();
                        ifdParser.waitValueOfTag(tag, offset);
                    } else if (tag.getTagId() == ExifTag.TIFF_TAG.TAG_EXIF_IFD
                            || tag.getTagId() == ExifTag.TIFF_TAG.TAG_GPS_IFD
                            || tag.getTagId() == ExifTag.EXIF_TAG.TAG_INTEROPERABILITY_IFD) {
                        long offset = ifdParser.readUnsignedInt();
                        ifdParser.waitValueOfTag(tag, offset);
                        ifdData.addTag(tag, offset);
                    } else {
                        readAndSaveTag(tag, ifdParser, ifdData);
                    }
                    break;
                case IfdParser.TYPE_NEXT_IFD:
                    IfdData ifd1 = new IfdData(ExifData.TYPE_IFD_1);
                    parseIfd(ifdParser.parseIfdBlock(), ifd1, exifData);
                    exifData.addIfdData(ifd1);
                    break;
                case IfdParser.TYPE_VALUE_OF_PREV_TAG:
                    tag = ifdParser.getCorrespodingExifTag();
                    if(tag.getTagId() == ExifTag.TIFF_TAG.TAG_EXIF_IFD) {
                        IfdData ifd = new IfdData(ExifData.TYPE_IFD_EXIF);
                        parseIfd(ifdParser.parseIfdBlock(), ifd, exifData);
                        exifData.addIfdData(ifd);
                    } else if(tag.getTagId() == ExifTag.TIFF_TAG.TAG_GPS_IFD) {
                        IfdData ifd = new IfdData(ExifData.TYPE_IFD_GPS);
                        parseIfd(ifdParser.parseIfdBlock(), ifd, exifData);
                        exifData.addIfdData(ifd);
                    } else if(tag.getTagId() == ExifTag.EXIF_TAG.TAG_INTEROPERABILITY_IFD) {
                        IfdData ifd = new IfdData(ExifData.TYPE_IFD_INTEROPERABILITY);
                        parseIfd(ifdParser.parseIfdBlock(), ifd, exifData);
                        exifData.addIfdData(ifd);
                    } else {
                        readAndSaveTag(tag, ifdParser, ifdData);
                    }
                    break;
            }
            type = ifdParser.next();
        }
    }

    public void readAndSaveTag(ExifTag tag, IfdParser parser, IfdData ifdData)
            throws IOException {
        switch(tag.getDataType()) {
            case ExifTag.TYPE_BYTE:
            {
                byte buf[] = new byte[tag.getComponentCount()];
                parser.read(buf);
                ifdData.addTag(tag, buf);
                break;
            }
            case ExifTag.TYPE_ASCII:
                ifdData.addTag(tag, parser.readString(tag.getComponentCount()));
                break;
            case ExifTag.TYPE_INT:
            {
                long value[] = new long[tag.getComponentCount()];
                for (int i = 0, n = value.length; i < n; i++) {
                    value[i] = parser.readUnsignedInt();
                }
                ifdData.addTag(tag, value);
                break;
            }
            case ExifTag.TYPE_RATIONAL:
            {
                Rational value[] = new Rational[tag.getComponentCount()];
                for (int i = 0, n = value.length; i < n; i++) {
                    value[i] = parser.readUnsignedRational();
                }
                ifdData.addTag(tag, value);
                break;
            }
            case ExifTag.TYPE_SHORT:
            {
                int value[] = new int[tag.getComponentCount()];
                for (int i = 0, n = value.length; i < n; i++) {
                    value[i] = parser.readUnsignedShort();
                }
                ifdData.addTag(tag, value);
                break;
            }
            case ExifTag.TYPE_SINT:
            {
                int value[] = new int[tag.getComponentCount()];
                for (int i = 0, n = value.length; i < n; i++) {
                    value[i] = parser.readInt();
                }
                ifdData.addTag(tag, value);
                break;
            }
            case ExifTag.TYPE_SRATIONAL:
            {
                Rational value[] = new Rational[tag.getComponentCount()];
                for (int i = 0, n = value.length; i < n; i++) {
                    value[i] = parser.readRational();
                }
                ifdData.addTag(tag, value);
                break;
            }
            case ExifTag.TYPE_UNDEFINED:
            {
                byte buf[] = new byte[tag.getComponentCount()];
                parser.read(buf);
                ifdData.addTag(tag, buf);
                break;
            }
        }
    }
}
