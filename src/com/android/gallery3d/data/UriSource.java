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

package com.android.gallery3d.data;

import com.android.gallery3d.app.GalleryApp;

import android.net.Uri;

import java.net.URLDecoder;
import java.net.URLEncoder;

class UriSource extends MediaSource {
    @SuppressWarnings("unused")
    private static final String TAG = "UriSource";

    private GalleryApp mApplication;

    public UriSource(GalleryApp context) {
        super("uri");
        mApplication = context;
    }

    @Override
    public MediaObject createMediaObject(Path path) {
        String segment[] = path.split();
        if (segment.length != 2) {
            throw new RuntimeException("bad path: " + path);
        }

        String decoded = URLDecoder.decode(segment[1]);
        return new UriImage(mApplication, path, Uri.parse(decoded));
    }

    @Override
    public Path findPathByUri(Uri uri) {
        String type = mApplication.getContentResolver().getType(uri);
        // Assume the type is image if the type cannot be resolved
        // This could happen for "http" URI.
        if (type == null || type.startsWith("image/")) {
            return Path.fromString("/uri/" + URLEncoder.encode(uri.toString()));
        }
        return null;
    }
}
