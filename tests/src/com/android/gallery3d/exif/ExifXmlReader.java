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
import java.util.HashMap;

public class ExifXmlReader {

    private static final String XML_EXIF_TAG = "exif";
    private static final String XML_IFD_TAG = "ifd";
    private static final String XML_IFD_NAME = "name";
    private static final String XML_TAG = "tag";
    private static final String XML_IFD0 = "ifd0";
    private static final String XML_IFD1 = "ifd1";
    private static final String XML_EXIF_IFD = "exif-ifd";
    private static final String XML_INTEROPERABILITY_IFD = "interoperability-ifd";
    private static final String XML_TAG_ID = "id";

    public static void readXml(XmlPullParser parser, HashMap<Short, String> ifd0,
            HashMap<Short, String> ifd1, HashMap<Short, String> exifIfd,
            HashMap<Short, String> interoperabilityIfd) throws XmlPullParserException,
            IOException {

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                break;
            }
        }

        assert(parser.getName().equals(XML_EXIF_TAG));

        parser.require(XmlPullParser.START_TAG, null, XML_EXIF_TAG);
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                readXmlIfd(parser, ifd0, ifd1, exifIfd, interoperabilityIfd);
            }
        }
        parser.require(XmlPullParser.END_TAG, null, XML_EXIF_TAG);
    }

    private static void readXmlIfd(XmlPullParser parser, HashMap<Short, String> ifd0,
            HashMap<Short, String> ifd1, HashMap<Short, String> exifIfd,
            HashMap<Short, String> interoperabilityIfd) throws XmlPullParserException,
            IOException {
        parser.require(XmlPullParser.START_TAG, null, XML_IFD_TAG);
        String name = parser.getAttributeValue(null, XML_IFD_NAME);
        HashMap<Short, String> ifdData = null;
        if (XML_IFD0.equals(name)) {
            ifdData = ifd0;
        } else if (XML_IFD1.equals(name)) {
            ifdData = ifd1;
        } else if (XML_EXIF_IFD.equals(name)) {
            ifdData = exifIfd;
        } else if (XML_INTEROPERABILITY_IFD.equals(name)) {
            ifdData = interoperabilityIfd;
        } else {
            throw new RuntimeException("Unknown IFD name in xml file: " + name);
        }
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                readXmlTag(parser, ifdData);
            }
        }
        parser.require(XmlPullParser.END_TAG, null, XML_IFD_TAG);
    }

    private static void readXmlTag(XmlPullParser parser, HashMap<Short, String> data)
        throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, XML_TAG);
        short id = Integer.decode(parser.getAttributeValue(null, XML_TAG_ID)).shortValue();
        String value = "";
        if (parser.next() == XmlPullParser.TEXT) {
            value = parser.getText();
            parser.next();
        }
        data.put(id, value);
        parser.require(XmlPullParser.END_TAG, null, XML_TAG);
    }
}