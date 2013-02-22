/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.gallery3d;

import com.android.gallery3d.stress.CameraLatency;
import com.android.gallery3d.stress.CameraStartUp;
import com.android.gallery3d.stress.ImageCapture;
import com.android.gallery3d.stress.SwitchPreview;

import junit.framework.Test;
import junit.framework.TestSuite;


/**
 * Instrumentation Test Runner for all Camera tests.
 *
 * Running all tests:
 *
 * adb shell am instrument \
 *    -e class com.android.gallery3d.StressTests \
 *    -w com.google.android.gallery3d.tests/com.android.gallery3d.stress.CameraStressTestRunner
 */

public class StressTests extends TestSuite {
    public static Test suite() {
        TestSuite result = new TestSuite();
        result.addTestSuite(CameraLatency.class);
        result.addTestSuite(CameraStartUp.class);
        result.addTestSuite(ImageCapture.class);
//      result.addTestSuite(SwitchPreview.class);
        return result;
    }
}
