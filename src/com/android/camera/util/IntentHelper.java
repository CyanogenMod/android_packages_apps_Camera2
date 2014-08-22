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
package com.android.camera.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import com.android.camera.debug.Log;

import java.util.List;

public class IntentHelper {
    private static final Log.Tag TAG = new Log.Tag("IntentHelper");

    public static Intent getGalleryIntent(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        GalleryHelper.setGalleryIntentClassName(intent);

        // check if intent can launch gallery
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfos =
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfos.size() == 0) {
            return null;
        } else {
            return intent;
        }
    }

    public static Drawable getGalleryIcon(Context context, Intent galleryIntent) {
        return GalleryHelper.getGalleryIcon(context, galleryIntent);
    }

    public static CharSequence getGalleryAppName(Context context, Intent galleryIntent) {
        return GalleryHelper.getGalleryAppName(context, galleryIntent);
    }

    public static Intent getVideoPlayerIntent(Uri uri) {
        return new Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "video/*");
    }
}
