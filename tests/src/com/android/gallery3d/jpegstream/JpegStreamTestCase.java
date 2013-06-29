/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.gallery3d.jpegstream;

import android.content.res.Resources;
import android.test.InstrumentationTestCase;
import android.util.Log;

import com.android.gallery3d.common.Utils;

import java.io.InputStream;

public class JpegStreamTestCase extends InstrumentationTestCase {
    public static final String TAG = "JpegStreamTestCase";

    private static final String RES_ID_TITLE = "Resource ID: %x";

    private InputStream mImageInputStream;
    private final int mImageResourceId;

    public JpegStreamTestCase(int imageRes) {
        mImageResourceId = imageRes;
    }

    protected InputStream getImageInputStream() {
        return mImageInputStream;
    }

    @Override
    public void setUp() throws Exception {
        Log.d(TAG, "doing setUp...");
        mImageInputStream = reopenFileStream();
    }

    @Override
    public void tearDown() throws Exception {
        Log.d(TAG, "doing tearDown...");
        Utils.closeSilently(mImageInputStream);
        mImageInputStream = null;
    }

    protected String getImageTitle() {
        return String.format(RES_ID_TITLE, mImageResourceId);
    }

    protected InputStream reopenFileStream() throws Exception {
        return openResource(mImageResourceId);
    }

    protected InputStream openResource(int resourceID) throws Exception {
        try {
            Resources res = getInstrumentation().getContext().getResources();
            return res.openRawResource(resourceID);
        } catch (Exception e) {
            throw new Exception(getImageTitle(), e);
        }
    }
}
