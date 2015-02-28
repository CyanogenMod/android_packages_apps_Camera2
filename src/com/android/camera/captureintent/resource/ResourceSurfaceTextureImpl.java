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

import com.android.camera.async.MainThread;
import com.android.camera.async.RefCountBase;
import com.android.camera.captureintent.CaptureIntentModuleUI;
import com.android.camera.captureintent.PreviewTransformCalculator;
import com.android.camera.debug.Log;
import com.android.camera.util.Size;

import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.view.Surface;

import javax.annotation.Nonnull;

public final class ResourceSurfaceTextureImpl implements ResourceSurfaceTexture {
    private static final Log.Tag TAG = new Log.Tag("ResSurfaceTexture");

    /** The surface texture. */
    private final SurfaceTexture mSurfaceTexture;

    /** The preview stream size. */
    private Size mPreviewSize;

    /** The preview layout size. */
    private Size mPreviewLayoutSize;

    private final PreviewTransformCalculator mPreviewTransformCalculator;

    private final CaptureIntentModuleUI mModuleUI;

    /**
     * Creates a reference counted {@link ResourceSurfaceTextureImpl} object.
     */
    public static RefCountBase<ResourceSurfaceTexture> create(
            SurfaceTexture surfaceTexture,
            PreviewTransformCalculator previewTransformCalculator,
            CaptureIntentModuleUI moduleUI) {
        ResourceSurfaceTexture resourceSurfaceTexture = new ResourceSurfaceTextureImpl(
                surfaceTexture, previewTransformCalculator, moduleUI);
        return new RefCountBase<>(resourceSurfaceTexture);
    }

    private ResourceSurfaceTextureImpl(
            SurfaceTexture surfaceTexture,
            PreviewTransformCalculator previewTransformCalculator,
            CaptureIntentModuleUI moduleUI) {
        mSurfaceTexture = surfaceTexture;
        mPreviewTransformCalculator = previewTransformCalculator;
        mPreviewSize = new Size(0, 0);
        mPreviewLayoutSize = new Size(0, 0);
        mModuleUI = moduleUI;
    }

    @Override
    public Surface createPreviewSurface() {
        return new Surface(mSurfaceTexture);
    }

    @Override
    public Size getPreviewSize() {
        return mPreviewSize;
    }

    @Override
    public Size getPreviewLayoutSize() {
        return mPreviewLayoutSize;
    }

    @Override
    public void setPreviewSize(@Nonnull Size previewSize) {
        // Update preview transform when preview stream size is changed.
        mPreviewSize = previewSize;

        // Update surface texture's default buffer size. See b/17286155.
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.width(), mPreviewSize.height());
    }

    @Override
    public void setPreviewLayoutSize(@Nonnull Size previewLayoutSize) {
        MainThread.checkMainThread();

        // Update preview transform when preview layout size is changed.
        if (!mPreviewLayoutSize.equals(previewLayoutSize)) {
            mPreviewLayoutSize = previewLayoutSize;
            updatePreviewTransform();
        }
    }

    @Override
    public void updatePreviewTransform() {
        MainThread.checkMainThread();
        if (mPreviewSize.getWidth() == 0 || mPreviewSize.getHeight() == 0 ||
                mPreviewLayoutSize.getWidth() == 0 || mPreviewLayoutSize.getHeight() == 0) {
            return;
        }
        Matrix transformMatrix = mPreviewTransformCalculator.toTransformMatrix(
                mPreviewLayoutSize, mPreviewSize);
        mModuleUI.updatePreviewTransform(transformMatrix);
    }

    @Override
    public void close() {
    }
}
