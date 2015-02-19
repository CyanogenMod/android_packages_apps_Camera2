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

package com.android.camera.one.v2.imagesaver;

import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Saves the last image in a burst.
 */
@ParametersAreNonnullByDefault
public class MostRecentImageSaver implements ImageSaver {
    private final SingleImageSaver mSingleImageSaver;
    private final Map<Long, ImageProxy> mThumbnails;
    private final Map<Long, MetadataImage> mFullSizeImages;

    public MostRecentImageSaver(SingleImageSaver singleImageSaver) {
        mSingleImageSaver = singleImageSaver;
        mThumbnails = new HashMap<>();
        mFullSizeImages = new HashMap<>();
    }

    @Override
    public void addThumbnail(ImageProxy imageProxy) {
        mThumbnails.put(imageProxy.getTimestamp(), imageProxy);
        closeOlderImages();
    }

    @Override
    public void addFullSizeImage(ImageProxy imageProxy,
            ListenableFuture<TotalCaptureResultProxy> metadata) {
        mFullSizeImages.put(imageProxy.getTimestamp(), new MetadataImage(imageProxy, metadata));
        closeOlderImages();
    }

    @Override
    public void close() {
        try {
            MetadataImage fullSize = getLastImage();
            if (fullSize != null) {
                // Pop the image out of the map so that closeAllImages() does
                // not close it.
                mFullSizeImages.remove(fullSize.getTimestamp());
            } else {
                return;
            }

            ImageProxy thumbnail = getThumbnail(fullSize.getTimestamp());
            if (thumbnail != null) {
                // Pop the image out of the map so that closeAllImages() does
                // not close it.
                mThumbnails.remove(thumbnail.getTimestamp());
            }

            mSingleImageSaver.saveAndCloseImage(fullSize, Optional.fromNullable(thumbnail),
                    fullSize.getMetadata());
        } finally {
            closeAllImages();
        }
    }

    private void closeAllImages() {
        for (ImageProxy image : mThumbnails.values()) {
            image.close();
        }

        for (ImageProxy image : mFullSizeImages.values()) {
            image.close();
        }
    }

    private void closeOlderImages(long threshold, Map<Long, ? extends ImageProxy> imageMap) {
        List<Long> toRemove = new ArrayList<>();
        for (long imageTimestamp : imageMap.keySet()) {
            if (imageTimestamp < threshold) {
                imageMap.get(imageTimestamp).close();
                toRemove.add(imageTimestamp);
            }
        }
        for (Long timestamp : toRemove) {
            imageMap.remove(timestamp);
        }
    }

    private void closeOlderImages() {
        Optional<Long> timestampThreshold = getMostRecentFullSizeImageTimestamp();
        if (timestampThreshold.isPresent()) {
            closeOlderImages(timestampThreshold.get(), mFullSizeImages);
            closeOlderImages(timestampThreshold.get(), mThumbnails);
        }
    }

    private Optional<Long> getMostRecentFullSizeImageTimestamp() {
        if (mFullSizeImages.isEmpty()) {
            return Optional.absent();
        }
        boolean pairFound = false;
        long oldestTimestamp = 0;
        for (ImageProxy image : mFullSizeImages.values()) {
            long timestamp = image.getTimestamp();
            if (!pairFound || timestamp > oldestTimestamp) {
                oldestTimestamp = timestamp;
                pairFound = true;
            }
        }
        if (!pairFound) {
            return Optional.absent();
        } else {
            return Optional.of(oldestTimestamp);
        }
    }

    @Nullable
    private MetadataImage getLastImage() {
        if (mFullSizeImages.isEmpty()) {
            return null;
        }
        MetadataImage lastImage = null;
        for (MetadataImage image : mFullSizeImages.values()) {
            if (lastImage == null || image.getTimestamp() > lastImage.getTimestamp()) {
                lastImage = image;
            }
        }
        return lastImage;
    }

    @Nullable
    private ImageProxy getThumbnail(long timestamp) {
        return mThumbnails.get(timestamp);
    }
}
