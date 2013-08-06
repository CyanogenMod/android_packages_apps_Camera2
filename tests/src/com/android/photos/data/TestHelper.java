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

import android.util.Log;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.lang.reflect.Method;

public class TestHelper {
    private static String TAG = TestHelper.class.getSimpleName();

    public interface TestInitialization {
        void initialize(TestCase testCase);
    }

    public static void addTests(Class<? extends TestCase> testClass, TestSuite suite,
            TestInitialization initialization) {
        for (Method method : testClass.getDeclaredMethods()) {
            if (method.getName().startsWith("test") && method.getParameterTypes().length == 0) {
                TestCase test;
                try {
                    test = testClass.newInstance();
                    test.setName(method.getName());
                    initialization.initialize(test);
                    suite.addTest(test);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Failed to create test case", e);
                } catch (InstantiationException e) {
                    Log.e(TAG, "Failed to create test case", e);
                } catch (IllegalAccessException e) {
                    Log.e(TAG, "Failed to create test case", e);
                }
            }
        }
    }

}
