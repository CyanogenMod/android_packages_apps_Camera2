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

import com.android.camera.async.RefCountBase;
import com.android.camera.captureintent.PreviewTransformCalculator;
import com.android.camera.util.AspectRatio;
import com.android.camera.util.Size;

import android.graphics.SurfaceTexture;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Workaround for TextureView/HAL issues in API1 / API2 Legacy Mode
 * (b/19271661) for 16:9 preview streams on Nexus 4.
 *
 * This workaround for 16:9 consists of:
 * 1) For any 16x9 resolution, the largest 4:3 preview size will be chosen for
 *    SurfaceTexture default buffer. Noted that though the surface is 4:3, the
 *    surface content (the preview) provided by HAL is 16:9.
 * 2) Enable auto transform in TextureViewHelper rather than using
 *    PreviewTransformCalculator. Since the preview content is still 16:9, we
 *    still need to call {@link com.android.camera.TextureViewHelper#updateAspectRatio}
 *    with 16:9 aspect ratio to get correct transform matrix.
 */
@ParametersAreNonnullByDefault
public final class ResourceSurfaceTextureNexus4Impl extends ResourceSurfaceTextureImpl {
    private static final Size LARGEST_4x3_PREVIEW_SIZE_NEXUS4 = new Size(1280, 960);

    /**
     * Creates a reference counted {@link ResourceSurfaceTextureNexus4Impl}
     * object.
     */
    public static RefCountBase<ResourceSurfaceTexture> create(
            RefCountBase<ResourceConstructed> resourceConstructed,
            SurfaceTexture surfaceTexture) {
        ResourceSurfaceTexture resourceSurfaceTexture = new ResourceSurfaceTextureNexus4Impl(
                resourceConstructed,
                surfaceTexture,
                new PreviewTransformCalculator(resourceConstructed.get().getOrientationManager()));
        return new RefCountBase<>(resourceSurfaceTexture);
    }

    private ResourceSurfaceTextureNexus4Impl(
            RefCountBase<ResourceConstructed> resourceConstructed,
            SurfaceTexture surfaceTexture,
            PreviewTransformCalculator previewTransformCalculator) {
        super(resourceConstructed, surfaceTexture, previewTransformCalculator);
    }

    @Override
    public void setPreviewSize(Size previewSize) {
        super.setPreviewSize(previewSize);

        final AspectRatio previewAspectRatio = AspectRatio.of(previewSize);
        getResourceConstructed().get().getMainThread().execute(new Runnable() {
            @Override
            public void run() {
                getResourceConstructed().get().getModuleUI()
                        .updatePreviewAspectRatio(previewAspectRatio.toFloat());
            }
        });

        // Override the preview selection logic to the largest N4 4:3
        // preview size but pass in 16:9 aspect ratio in
        // updatePreviewTransform() later.
        if (previewAspectRatio.equals(AspectRatio.of16x9())) {
            updateSurfaceTextureDefaultBufferSize(LARGEST_4x3_PREVIEW_SIZE_NEXUS4);
        }
    }

    @Override
    public void updatePreviewTransform() {
        // Override and let it be no-op since TextureViewHelper auto transform
        // is enabled!
    }
}
