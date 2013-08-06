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
package com.android.photos.data;

import android.test.InstrumentationTestRunner;
import android.test.InstrumentationTestSuite;

import com.android.photos.data.TestHelper.TestInitialization;

import junit.framework.TestCase;
import junit.framework.TestSuite;

public class DataTestRunner extends InstrumentationTestRunner {
    @Override
    public TestSuite getAllTests() {
        TestSuite suite = new InstrumentationTestSuite(this);
        suite.addTestSuite(PhotoDatabaseTest.class);
        suite.addTestSuite(PhotoProviderTest.class);
        TestHelper.addTests(MediaCacheTest.class, suite, new TestInitialization() {
            @Override
            public void initialize(TestCase testCase) {
                MediaCacheTest test = (MediaCacheTest) testCase;
                test.setLocalContext(getContext());
            }
        });
        return suite;
    }

    @Override
    public ClassLoader getLoader() {
        return DataTestRunner.class.getClassLoader();
    }
}
