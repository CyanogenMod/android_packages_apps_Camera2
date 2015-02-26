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

package com.android.camera.captureintent.state;

import com.android.camera.app.AppController;
import com.android.camera.async.MainThread;
import com.android.camera.async.RefCountBase;
import com.android.camera.async.SafeCloseable;
import com.android.camera.captureintent.PreviewTransformCalculator;
import com.android.camera.debug.Log;
import com.android.camera.one.OneCamera;
import com.android.camera.util.Size;

import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.view.Surface;

import javax.annotation.Nonnull;

public final class ResourceSurfaceTexture implements SafeCloseable {
    private static final Log.Tag TAG = new Log.Tag("StateSurfaceTextureAvailable");

    /** The surface texture. */
    private final SurfaceTexture mSurfaceTexture;

    /** The preview stream size. */
    private Size mPreviewSize;

    /** The preview layout size. */
    private Size mPreviewLayoutSize;

    private final PreviewTransformCalculator mPreviewTransformCalculator;

    // TODO: Hope one day we could get rid of AppController.
    private final AppController mAppController;

    /**
     * Creates a reference counted {@link ResourceSurfaceTexture} object.
     */
    public static RefCountBase<ResourceSurfaceTexture> create(
            SurfaceTexture surfaceTexture,
            PreviewTransformCalculator previewTransformCalculator,
            AppController appController) {
        ResourceSurfaceTexture resourceSurfaceTexture = new ResourceSurfaceTexture(
                surfaceTexture, previewTransformCalculator, appController);
        return new RefCountBase<>(resourceSurfaceTexture);
    }

    private ResourceSurfaceTexture(
            SurfaceTexture surfaceTexture,
            PreviewTransformCalculator previewTransformCalculator,
            AppController appController) {
        mSurfaceTexture = surfaceTexture;
        mPreviewTransformCalculator = previewTransformCalculator;
        mPreviewSize = new Size(0, 0);
        mPreviewLayoutSize = new Size(0, 0);
        mAppController = appController;
    }

    public Surface createPreviewSurface() {
        return new Surface(mSurfaceTexture);
    }

    public Size getPreviewSize() {
        return mPreviewSize;
    }

    public Size getPreviewLayoutSize() {
        return mPreviewLayoutSize;
    }

    public void setPreviewSize(@Nonnull Size previewSize) {
        MainThread.checkMainThread();
        // Update preview transform when preview stream size is changed.
        mPreviewSize = previewSize;
        updatePreviewTransform();

        // Update surface texture's default buffer size. See b/17286155.
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.width(), mPreviewSize.height());
    }

    public void setPreviewLayoutSize(@Nonnull Size previewLayoutSize) {
        MainThread.checkMainThread();

        // Update preview transform when preview layout size is changed.
        mPreviewLayoutSize = previewLayoutSize;
        updatePreviewTransform();
    }

    private void updatePreviewTransform() {
        MainThread.checkMainThread();
        if (mPreviewLayoutSize.getWidth() == 0 || mPreviewLayoutSize.getHeight() == 0) {
            return;
        }

        Matrix transformMatrix = mPreviewTransformCalculator.toTransformMatrix(
                mPreviewLayoutSize, mPreviewSize);
        mAppController.updatePreviewTransform(transformMatrix);
    }

    @Override
    public void close() {
    }
}
