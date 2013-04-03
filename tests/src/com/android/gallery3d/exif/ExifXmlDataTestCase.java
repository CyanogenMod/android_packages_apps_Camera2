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

import android.content.res.Resources;
import android.test.InstrumentationTestCase;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ExifXmlDataTestCase extends InstrumentationTestCase {

    private static final String RES_ID_TITLE = "Resource ID: %x";

    private InputStream mImageInputStream;
    private InputStream mXmlInputStream;
    private XmlPullParser mXmlParser;
    private final String mImagePath;
    private final String mXmlPath;
    private final int mImageResourceId;
    private final int mXmlResourceId;

    public ExifXmlDataTestCase(int imageRes, int xmlRes) {
        mImagePath = null;
        mXmlPath = null;
        mImageResourceId = imageRes;
        mXmlResourceId = xmlRes;
    }

    public ExifXmlDataTestCase(String imagePath, String xmlPath) {
        mImagePath = imagePath;
        mXmlPath = xmlPath;
        mImageResourceId = 0;
        mXmlResourceId = 0;
    }

    protected InputStream getImageInputStream() {
        return mImageInputStream;
    }

    protected XmlPullParser getXmlParser() {
        return mXmlParser;
    }

    @Override
    public void setUp() throws Exception {
        try {
            if (mImagePath != null) {
                mImageInputStream = new FileInputStream(mImagePath);
                mXmlInputStream = new FileInputStream(mXmlPath);
                mXmlParser = Xml.newPullParser();
                mXmlParser.setInput(new InputStreamReader(mXmlInputStream));
            } else {
                Resources res = getInstrumentation().getContext().getResources();
                mImageInputStream = res.openRawResource(mImageResourceId);
                mXmlParser = res.getXml(mXmlResourceId);
            }
        } catch (Exception e) {
            throw new Exception(getImageTitle(), e);
        }
    }

    @Override
    public void tearDown() throws Exception {
        Util.closeSilently(mImageInputStream);
        Util.closeSilently(mXmlInputStream);
        mXmlParser = null;
    }

    protected String getImageTitle() {
        if (mImagePath != null) {
            return mImagePath;
        } else {
            return String.format(RES_ID_TITLE, mImageResourceId);
        }
    }

    protected InputStream reopenFileStream() throws Exception {
        try {
            if (mImagePath != null) {
                return new FileInputStream(mImagePath);
            } else {
                Resources res = getInstrumentation().getContext().getResources();
                return res.openRawResource(mImageResourceId);
            }
        } catch (Exception e) {
            throw new Exception(getImageTitle(), e);
        }
    }
}
