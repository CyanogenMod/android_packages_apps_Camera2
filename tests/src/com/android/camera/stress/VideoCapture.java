/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.camera.stress;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.provider.MediaStore;
import android.test.ActivityInstrumentationTestCase2;
import android.view.KeyEvent;

import com.android.camera.CameraActivity;
import com.android.camera.stress.CameraStressTestRunner;
import com.android.camera.stress.TestUtil;

/**
 * Junit / Instrumentation test case for camera test
 * <p/>
 * Running the test suite:
 * <p/>
 * adb shell am instrument \
 * -e class com.android.camera.stress.VideoCapture \
 * -w com.google.android.camera.tests/android.test.InstrumentationTestRunner
 */

public class VideoCapture extends ActivityInstrumentationTestCase2<CameraActivity> {
    private static final long WAIT_FOR_PREVIEW = 4 * 1000; //4 seconds
    private static final long WAIT_FOR_SWITCH_CAMERA = 4 * 1000; //4 seconds

    // Private intent extras which control the camera facing.
    private final static String EXTRAS_CAMERA_FACING =
            "android.intent.extras.CAMERA_FACING";

    private TestUtil testUtil = new TestUtil();

    public VideoCapture() {
        super(CameraActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        testUtil.prepareOutputFile();
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        testUtil.closeOutputFile();
        super.tearDown();
    }

    public void captureVideos(String reportTag, Instrumentation inst) throws Exception {
        boolean memoryResult = false;
        int total_num_of_videos = CameraStressTestRunner.mVideoIterations;
        int video_duration = CameraStressTestRunner.mVideoDuration;
        testUtil.writeReportHeader(reportTag, total_num_of_videos);

        for (int i = 0; i < total_num_of_videos; i++) {
            Thread.sleep(WAIT_FOR_PREVIEW);
            // record a video
            inst.sendCharacterSync(KeyEvent.KEYCODE_CAMERA);
            Thread.sleep(video_duration);
            inst.sendCharacterSync(KeyEvent.KEYCODE_CAMERA);
            testUtil.writeResult(i);
        }
    }

    public void testBackVideoCapture() throws Exception {
        Instrumentation inst = getInstrumentation();
        Intent intent = new Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA);

        intent.setClass(getInstrumentation().getTargetContext(), CameraActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRAS_CAMERA_FACING,
                android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK);
        Activity act = inst.startActivitySync(intent);
        Thread.sleep(WAIT_FOR_SWITCH_CAMERA);
        captureVideos("Back Camera Video Capture\n", inst);
        act.finish();
        // Wait for a clean finish.
        Thread.sleep(2 * 1000); //sleep for 2 seconds

    }

    public void testFrontVideoCapture() throws Exception {
        Instrumentation inst = getInstrumentation();
        Intent intent = new Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA);

        intent.setClass(getInstrumentation().getTargetContext(), CameraActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRAS_CAMERA_FACING,
                android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT);
        Activity act = inst.startActivitySync(intent);
        Thread.sleep(WAIT_FOR_SWITCH_CAMERA);
        captureVideos("Front Camera Video Capture\n", inst);
        act.finish();
        // Wait for a clean finish.
        Thread.sleep(2 * 1000); //sleep for 2 seconds.

    }
}
