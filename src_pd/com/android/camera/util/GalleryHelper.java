/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.camera.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import com.android.camera2.R;

/**
 * A helper class to provide Gallery related info.
 */
public class GalleryHelper {
    private static final String GALLERY_PACKAGE_NAME = "com.android.gallery3d";
    private static final String GALLERY_ACTIVITY_CLASS =
        "com.android.gallery3d.app.GalleryActivity";
    private static final int GALLERY_APP_NAME_ID = R.string.gallery_app_name;

    public static void setGalleryIntentClassName(Intent intent) {
        intent.setClassName(GALLERY_PACKAGE_NAME, GALLERY_ACTIVITY_CLASS);
    }

    public static Drawable getGalleryIcon(Context context, Intent galleryIntent) {
        if (galleryIntent != null) {
            try {
                return context.getPackageManager().getActivityIcon(galleryIntent);
            } catch (PackageManager.NameNotFoundException e) {
                // Do nothing.
            }
        }
        return null;
    }

    public static CharSequence getGalleryAppName(Context context, Intent galleryIntent) {
        ComponentName componentName = galleryIntent.getComponent();
        if (componentName != null
                && GALLERY_PACKAGE_NAME.equals(componentName.getPackageName())
                && GALLERY_ACTIVITY_CLASS.equals(componentName.getClassName())) {
            return context.getResources().getString(GALLERY_APP_NAME_ID);
        } else {
            return null;
        }
    }

    public static void setContentUri(Intent intent, Uri uri) {
        // Do nothing.
    }
}
