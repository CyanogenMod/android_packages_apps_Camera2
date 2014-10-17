/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.camera.burst;

import java.util.concurrent.Future;

/**
 * Accessor that can be used for retrieving high-res burst images from the
 * internal camera image buffer.
 * <p/>
 * On completion of a burst Camera hands over an instance of
 * {@link ResultsAccessor} to the burst module by calling the
 * {@link BurstControllerImpl#stopBurst(ResultsAccessor)} method. The burst
 * module can then retrieve images from the internal buffer by calling
 * {@link #extractImage(long)} with the timestamp of each image.
 * <p/>
 * When burst module has completed retrieving the high-res images from the
 * buffer it should call {@link #close()} to let camera free the internal buffer
 * and resources used by the burst.
 */
public interface ResultsAccessor extends AutoCloseable {
    /**
     * Extract image which has the given timestamp.
     * <p/>
     * Extracting an image is an expensive operation and is done asynchronously.
     * This method returns a <code>Future<BurstImage></code> which can be used
     * to retrieve the image. If the provided timestamp is not a result of burst
     * or internal buffer of Camera does not have the image, the
     * {@link Future#get()} will return null. Camera frees up resources related
     * to the timestamp after the first call. Calling this method with the same
     * timestamp will result in a null image.
     *
     * @param timestamp the timestamp of the image to be extracted
     * @return the future for BurstImage.
     */
    Future<BurstImage> extractImage(long timestamp);

    /**
     * Called when results extraction has been completed and the Camera can free
     * up resources related to results of burst.
     * <p/>
     * No further images can be extracted after calling this method. Any
     * computations for extracting images are cancelled.
     */
    @Override
    void close();
}
