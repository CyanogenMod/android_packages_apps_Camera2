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

package com.android.gallery3d.common;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

public class LightCycleHelper {
    public static final String EXTRA_OUTPUT_DIR = "output_dir";
    private static final String PANORAMA_FILENAME_PREFIX = "panorama_";
    public static final String LIGHTCYCLE_PACKAGE =
            "com.google.android.apps.lightcycle";
    public static final String LIGHTCYCLE_CAPTURE_CLASS =
            "com.google.android.apps.lightcycle.PanoramaCaptureActivity";
    private static final String LIGHTCYCLE_VIEW_CLASS =
            "com.google.android.apps.lightcycle.PanoramaViewActivity";

    private static boolean sUpdated;
    private static boolean sHasViewActivity;
    private static boolean sHasCaptureActivity;

    private static boolean hasLightCycleActivity(PackageManager pm, String activityClass) {
        Intent it = new Intent();
        it.setClassName(LIGHTCYCLE_PACKAGE, activityClass);
        return (pm.resolveActivity(it, 0) != null);
    }

    private static void update(PackageManager pm) {
        sUpdated = true;
        sHasViewActivity = hasLightCycleActivity(pm, LIGHTCYCLE_VIEW_CLASS);
        sHasCaptureActivity = hasLightCycleActivity(pm, LIGHTCYCLE_CAPTURE_CLASS);
    }

    public static synchronized boolean hasLightCycleView(PackageManager pm) {
        if (!sUpdated) {
            update(pm);
        }
        return sHasViewActivity;
    }

    public static synchronized boolean hasLightCycleCapture(PackageManager pm) {
        if (!sUpdated) {
            update(pm);
        }
        return sHasCaptureActivity;
    }

    public static synchronized void onPackageAdded(Context context, String packageName) {
        if (LIGHTCYCLE_PACKAGE.equals(packageName)) {
            update(context.getPackageManager());
        }
    }

    public static synchronized void onPackageRemoved(Context context, String packageName) {
        if (LIGHTCYCLE_PACKAGE.equals(packageName)) {
            update(context.getPackageManager());
        }
    }

    public static synchronized void onPackageChanged(Context context, String packageName) {
        if (LIGHTCYCLE_PACKAGE.equals(packageName)) {
            update(context.getPackageManager());
        }
    }

    public static void viewPanorama(Activity activity, Uri uri, String type) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, type)
                    .setClassName(LIGHTCYCLE_PACKAGE, LIGHTCYCLE_VIEW_CLASS);
            activity.startActivity(intent);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isPanorama(String filename) {
        return filename.startsWith(PANORAMA_FILENAME_PREFIX);
    }
}
