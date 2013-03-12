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

import android.content.Context;
import android.os.Environment;
import android.test.InstrumentationTestRunner;
import android.test.InstrumentationTestSuite;
import android.util.Log;

import com.android.gallery3d.tests.R;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ExifTestRunner extends InstrumentationTestRunner {
    private static final String TAG = "ExifTestRunner";

    private static final int[] IMG_RESOURCE = {
            R.raw.galaxy_nexus
    };

    private static final int[] EXIF_DATA_RESOURCE = {
            R.xml.galaxy_nexus
    };

    private static List<String> mTestImgPath = new ArrayList<String>();
    private static List<String> mTestXmlPath = new ArrayList<String>();

    @Override
    public TestSuite getAllTests() {
        getTestImagePath();
        TestSuite suite = new InstrumentationTestSuite(this);
        suite.addTestSuite(ExifDataTest.class);
        suite.addTestSuite(ExifTagTest.class);
        addAllTestsFromExifTestCase(ExifParserTest.class, suite);
        addAllTestsFromExifTestCase(ExifReaderTest.class, suite);
        addAllTestsFromExifTestCase(ExifOutputStreamTest.class, suite);
        addAllTestsFromExifTestCase(ExifModifierTest.class, suite);
        addAllTestsFromExifTestCase(ExifInterfaceTest.class, suite);
        return suite;
    }

    private void getTestImagePath() {
        Context context = getContext();
        File imgDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File xmlDir = new File(context.getExternalFilesDir(null).getPath(), "Xml");

        if (imgDir != null && xmlDir != null) {
            String[] imgs = imgDir.list();
            if (imgs == null) {
                return;
            }
            for (String imgName : imgs) {
                String xmlName = imgName.substring(0, imgName.lastIndexOf('.')) + ".xml";
                File xmlFile = new File(xmlDir, xmlName);
                if (xmlFile.exists()) {
                    mTestImgPath.add(new File(imgDir, imgName).getAbsolutePath());
                    mTestXmlPath.add(xmlFile.getAbsolutePath());
                }
            }
        }
    }

    private void addAllTestsFromExifTestCase(Class<? extends ExifXmlDataTestCase> testClass,
            TestSuite suite) {
        for (Method method : testClass.getDeclaredMethods()) {
            if (method.getName().startsWith("test") && method.getParameterTypes().length == 0) {
                for (int i = 0; i < IMG_RESOURCE.length; i++) {
                    TestCase test;
                    try {
                        test = testClass.getDeclaredConstructor(int.class, int.class).
                                newInstance(IMG_RESOURCE[i], EXIF_DATA_RESOURCE[i]);
                        test.setName(method.getName());
                        suite.addTest(test);
                    } catch (IllegalArgumentException e) {
                        Log.e(TAG, "Failed to create test case", e);
                    } catch (InstantiationException e) {
                        Log.e(TAG, "Failed to create test case", e);
                    } catch (IllegalAccessException e) {
                        Log.e(TAG, "Failed to create test case", e);
                    } catch (InvocationTargetException e) {
                        Log.e(TAG, "Failed to create test case", e);
                    } catch (NoSuchMethodException e) {
                        Log.e(TAG, "Failed to create test case", e);
                    }
                }
                for (int i = 0, n = mTestImgPath.size(); i < n; i++) {
                    TestCase test;
                    try {
                        test = testClass.getDeclaredConstructor(String.class, String.class).
                                newInstance(mTestImgPath.get(i), mTestXmlPath.get(i));
                        test.setName(method.getName());
                        suite.addTest(test);
                    } catch (IllegalArgumentException e) {
                        Log.e(TAG, "Failed to create test case", e);
                    } catch (InstantiationException e) {
                        Log.e(TAG, "Failed to create test case", e);
                    } catch (IllegalAccessException e) {
                        Log.e(TAG, "Failed to create test case", e);
                    } catch (InvocationTargetException e) {
                        Log.e(TAG, "Failed to create test case", e);
                    } catch (NoSuchMethodException e) {
                        Log.e(TAG, "Failed to create test case", e);
                    }
                }
            }
        }
    }

    @Override
    public ClassLoader getLoader() {
        return ExifTestRunner.class.getClassLoader();
    }
}
