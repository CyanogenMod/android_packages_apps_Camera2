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

import android.test.InstrumentationTestRunner;
import android.test.InstrumentationTestSuite;

import com.android.gallery3d.tests.R;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.lang.reflect.Method;

public class ExifTestRunner extends InstrumentationTestRunner {
    private static final int[] IMG_RESOURCE = {
        R.raw.galaxy_nexus
    };
    private static final int[] EXIF_DATA_RESOURCE = {
        R.xml.galaxy_nexus
    };

    @Override
    public TestSuite getAllTests() {
        TestSuite suite = new InstrumentationTestSuite(this);
        for (Method method : ExifParserTest.class.getDeclaredMethods()) {
            if (method.getName().startsWith("test") && method.getParameterTypes().length == 0) {
                for (int i = 0; i < IMG_RESOURCE.length; i++) {
                    TestCase test = new ExifParserTest(IMG_RESOURCE[i], EXIF_DATA_RESOURCE[i]);
                    test.setName(method.getName());
                    suite.addTest(test);
                }
            }
        }
        return suite;
    }

    @Override
    public ClassLoader getLoader() {
        return ExifTestRunner.class.getClassLoader();
    }
}
