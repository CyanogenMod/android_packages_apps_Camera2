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

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExifXmlReader {
    private static final String TAG_EXIF = "exif";
    private static final String TAG_TAG = "tag";

    private static final String IFD0 = "IFD0";
    private static final String EXIF_IFD = "ExifIFD";
    private static final String GPS_IFD = "GPS";
    private static final String IFD1 = "IFD1";
    private static final String INTEROP_IFD = "InteropIFD";

    private static final String ATTR_ID = "id";
    private static final String ATTR_IFD = "ifd";

    /**
     * This function read the ground truth XML.
     *
     * @throws XmlPullParserException
     * @throws IOException
     */
    static public List<Map<Short, Set<String>>> readXml(XmlPullParser parser)
            throws XmlPullParserException, IOException {

        List<Map<Short, Set<String>>> exifData =
                new ArrayList<Map<Short, Set<String>>>(IfdId.TYPE_IFD_COUNT);
        for (int i = 0; i < IfdId.TYPE_IFD_COUNT; i++) {
            exifData.add(new HashMap<Short, Set<String>>());
        }

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                break;
            }
        }
        parser.require(XmlPullParser.START_TAG, null, TAG_EXIF);

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }

            parser.require(XmlPullParser.START_TAG, null, TAG_TAG);

            int ifdId = getIfdIdFromString(parser.getAttributeValue(null, ATTR_IFD));
            short id = Integer.decode(parser.getAttributeValue(null, ATTR_ID)).shortValue();

            String value = "";
            if (parser.next() == XmlPullParser.TEXT) {
                value = parser.getText();
                parser.next();
            }

            if (ifdId < 0) {
                // TODO: the MarkerNote segment.
            } else {
                Set<String> tagData = exifData.get(ifdId).get(id);
                if (tagData == null) {
                    tagData = new HashSet<String>();
                    exifData.get(ifdId).put(id, tagData);
                }
                tagData.add(value.trim());
            }

            parser.require(XmlPullParser.END_TAG, null, null);
        }
        return exifData;
    }

    static private int getIfdIdFromString(String prefix) {
        if (IFD0.equals(prefix)) {
            return IfdId.TYPE_IFD_0;
        } else if (EXIF_IFD.equals(prefix)) {
            return IfdId.TYPE_IFD_EXIF;
        } else if (GPS_IFD.equals(prefix)) {
            return IfdId.TYPE_IFD_GPS;
        } else if (IFD1.equals(prefix)) {
            return IfdId.TYPE_IFD_1;
        } else if (INTEROP_IFD.equals(prefix)) {
            return IfdId.TYPE_IFD_INTEROPERABILITY;
        } else {
            assert(false);
            return -1;
        }
    }
}
