/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.camera.captureintent.resource;

import com.android.camera.async.SafeCloseable;
import com.android.camera.util.Size;

import android.view.Surface;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Defines an interface that any implementation of this is responsible for
 * retaining and releasing a {@link android.graphics.SurfaceTexture}.
 */
@ParametersAreNonnullByDefault
public interface ResourceSurfaceTexture extends SafeCloseable {
    /**
     * Creates a surface from this surface texture for preview.
     *
     * @return A {@link android.view.Surface} object.
     */
    public Surface createPreviewSurface();

    /**
     * Updates the transform matrix in {@link com.android.camera.TextureViewHelper}.
     */
    public void updatePreviewTransform();

    /**
     * Obtains the chosen preview stream size.
     *
     * @return A {@link com.android.camera.util.Size} object.
     */
    public Size getPreviewSize();

    /**
     * Changes the preview stream size.
     *
     * @param previewSize The new preview stream size.
     */
    public void setPreviewSize(Size previewSize);

    /**
     * Obtains the current view layout size for the preview.
     *
     * @return A {@link com.android.camera.util.Size} object.
     */
    public Size getPreviewLayoutSize();

    /**
     * Changes the current view layout size.
     *
     * @param previewLayoutSize The new preview view layout size.
     */
    public void setPreviewLayoutSize(Size previewLayoutSize);
}
