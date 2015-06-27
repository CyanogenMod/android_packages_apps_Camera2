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
import com.android.camera.captureintent.PreviewTransformCalculator;
import com.android.camera.debug.Log;
import com.android.camera.util.Size;

import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.view.Surface;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class ResourceSurfaceTextureImpl implements ResourceSurfaceTexture {
    private static final Log.Tag TAG = new Log.Tag("ResSurfaceTexture");

    private final RefCountBase<ResourceConstructed> mResourceConstructed;

    /** The surface texture. */
    private final SurfaceTexture mSurfaceTexture;

    /** The preview stream size. */
    private Size mPreviewSize;

    /** The preview layout size. */
    private Size mPreviewLayoutSize;

    /** The default buffer size in SurfaceTexture. */
    private Size mSurfaceTextureDefaultBufferSize;

    private final PreviewTransformCalculator mPreviewTransformCalculator;

    /**
     * Creates a reference counted {@link ResourceSurfaceTextureImpl} object.
     */
    public static RefCountBase<ResourceSurfaceTexture> create(
            RefCountBase<ResourceConstructed> resourceConstructed,
            SurfaceTexture surfaceTexture) {
        ResourceSurfaceTexture resourceSurfaceTexture = new ResourceSurfaceTextureImpl(
                resourceConstructed,
                surfaceTexture,
                new PreviewTransformCalculator(resourceConstructed.get().getOrientationManager()));
        return new RefCountBase<>(resourceSurfaceTexture);
    }

    protected ResourceSurfaceTextureImpl(
            RefCountBase<ResourceConstructed> resourceConstructed,
            SurfaceTexture surfaceTexture,
            PreviewTransformCalculator previewTransformCalculator) {
        mResourceConstructed = resourceConstructed;
        mResourceConstructed.addRef();
        mSurfaceTexture = surfaceTexture;
        mPreviewTransformCalculator = previewTransformCalculator;
        mPreviewSize = new Size(0, 0);
        mPreviewLayoutSize = new Size(0, 0);
        mSurfaceTextureDefaultBufferSize = new Size(0, 0);
    }

    public RefCountBase<ResourceConstructed> getResourceConstructed() {
        return mResourceConstructed;
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
    public void setPreviewSize(Size previewSize) {
        // Update preview transform when preview stream size is changed.
        if (!mPreviewSize.equals(previewSize)) {
            mPreviewSize = previewSize;

            /**
             * Update transform here since preview size might change when
             * switching between back and front camera.
             */
            mResourceConstructed.get().getMainThread().execute(new Runnable() {
                @Override
                public void run() {
                    updatePreviewTransform();
                }
            });
        }

        // Update surface texture's default buffer size. See b/17286155.
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.width(), mPreviewSize.height());
    }

    @Override
    public void setPreviewLayoutSize(Size previewLayoutSize) {
        MainThread.checkMainThread();

        // Update preview transform when preview layout size is changed.
        if (!mPreviewLayoutSize.equals(previewLayoutSize)) {
            mPreviewLayoutSize = previewLayoutSize;
            updatePreviewTransform();
        }
    }

    /**
     * Updates the default buffer size in SurfaceTexture with the configured
     * preview stream size.
     */
    protected void updateSurfaceTextureDefaultBufferSize(Size defaultBufferSize) {
        mSurfaceTexture.setDefaultBufferSize(defaultBufferSize.width(), defaultBufferSize.height());
    }

    @Override
    public void updatePreviewTransform() {
        MainThread.checkMainThread();
        if (mPreviewSize.getWidth() == 0 || mPreviewSize.getHeight() == 0) {
            Log.w(TAG, "Do nothing since mPreviewSize is 0");
            return;
        }
        if (mPreviewLayoutSize.getWidth() == 0 || mPreviewLayoutSize.getHeight() == 0) {
            Log.w(TAG, "Do nothing since mPreviewLayoutSize is 0");
            return;
        }

        Matrix transformMatrix = mPreviewTransformCalculator.toTransformMatrix(
                mPreviewLayoutSize, mPreviewSize);
        mResourceConstructed.get().getModuleUI().updatePreviewTransform(transformMatrix);
    }

    @Override
    public void close() {
        mResourceConstructed.close();
    }
}
