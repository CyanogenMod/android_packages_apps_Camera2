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

package com.android.camera.data;

import android.content.Context;

/**
 * A helper class to load the metadata of
 * {@link FilmstripItem}.
 */
public class MetadataLoader {

    /**
     * Adds information to the data's metadata bundle if any is available and returns
     * true if metadata was added and false otherwise. In either case, sets
     * a flag indicating that we've cached any available metadata and don't need to
     * load metadata again for this particular item.
     *
     * TODO: Replace with more explicit polymorphism.
     *
     * @param context A context.
     * @param data The data to update metadata for.
     * @return true if any metadata was added to the data, false otherwise.
     */
    public static boolean loadMetadata(final Context context, final FilmstripItem data) {
        boolean metadataAdded = false;
        if (data.getAttributes().isImage()) {
            metadataAdded |= PanoramaMetadataLoader.loadPanoramaMetadata(
                    context, data.getData().getUri(), data.getMetadata());
            metadataAdded |=  RgbzMetadataLoader.loadRgbzMetadata(
                    context, data.getData().getUri(), data.getMetadata());
        } else if (data.getAttributes().isVideo()) {
            metadataAdded = VideoRotationMetadataLoader.loadRotationMetadata(data);
        }
        data.getMetadata().setLoaded(true);
        return metadataAdded;
    }
}

