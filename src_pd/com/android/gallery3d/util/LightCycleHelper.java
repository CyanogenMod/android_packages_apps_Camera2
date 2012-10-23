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

package com.android.gallery3d.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.android.camera.CameraModule;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.app.StitchingProgressManager;

public class LightCycleHelper {
    public static class PanoramaMetadata {
        // Whether a panorama viewer should be used
        public final boolean mUsePanoramaViewer;
        // Whether a panorama is 360 degrees
        public final boolean mIsPanorama360;

        public PanoramaMetadata(boolean usePanoramaViewer, boolean isPanorama360) {
            mUsePanoramaViewer = usePanoramaViewer;
            mIsPanorama360 = isPanorama360;
        }
    }

    public static class PanoramaViewHelper {

        public PanoramaViewHelper(Activity activity) {
            /* Do nothing */
        }

        public void onStart() {
            /* Do nothing */
        }

        public void onCreate() {
            /* Do nothing */
        }

        public void onStop() {
            /* Do nothing */
        }

        public void showPanorama(Uri uri) {
            /* Do nothing */
        }
    }

    public static final PanoramaMetadata NOT_PANORAMA = new PanoramaMetadata(false, false);

    public static void setupCaptureIntent(Context context, Intent it, String outputDir) {
        /* Do nothing */
    }

    public static boolean hasLightCycleCapture(Context context) {
        return false;
    }

    public static PanoramaMetadata getPanoramaMetadata(Context context, Uri uri) {
        return NOT_PANORAMA;
    }

    public static CameraModule createPanoramaModule() {
        return null;
    }

    public static StitchingProgressManager createStitchingManagerInstance(GalleryApp app) {
        return null;
    }
}
