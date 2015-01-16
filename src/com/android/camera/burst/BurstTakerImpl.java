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

package com.android.camera.burst;

import android.view.Surface;

import com.android.camera.async.Lifetime;
import com.android.camera.async.MainThread;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.Request;
import com.android.camera.one.v2.core.RequestBuilder;
import com.android.camera.one.v2.sharedimagereader.ManagedImageReader;
import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class BurstTakerImpl implements BurstTaker {

    private final CameraCommandExecutor mCameraCommandExecutor;
    private final FrameServer mFrameServer;
    private final ManagedImageReader mImageFactory;
    private final RequestBuilder.Factory mRequestBuilder;
    private final Surface mBurstInputSurface;
    private final Runnable mRestorePreviewCommand;
    private final int mMaxImageCount;
    /**
     * The lifetime of the burst, the burst stops capturing images once the
     * lifetime is closed
     */
    @Nullable
    private Lifetime mBurstLifetime;

    /**
     * Creates an instance for burst taker.
     * <p/>
     *
     * @param cameraCommandExecutor the executor to use for executing
     *            {@link BurstCaptureCommand}
     * @param frameServer the {@link FrameServer} instance for creating session
     * @param builder factory to use for creating the {@link Request} for burst
     *            capture
     * @param imageFactory the factory to use for creating a stream of images
     * @param burstInputSurface the input surface to use for streaming preview
     *            frames to burst
     * @param restorePreviewCommand the command to run to restore the preview,
     *            once burst capture is complete
     * @param maxImageCount the maximum number of images supported by the image
     *            reader
     */
    public BurstTakerImpl(CameraCommandExecutor cameraCommandExecutor,
            FrameServer frameServer, RequestBuilder.Factory builder,
            ManagedImageReader imageFactory, Surface burstInputSurface,
            Runnable restorePreviewCommand,
            int maxImageCount) {
        mCameraCommandExecutor = cameraCommandExecutor;
        mFrameServer = frameServer;
        mRequestBuilder = builder;
        mImageFactory = imageFactory;
        mBurstInputSurface = burstInputSurface;
        mRestorePreviewCommand = restorePreviewCommand;
        mMaxImageCount = maxImageCount;
    }

    @Override
    public void startBurst(EvictionHandler evictionHandler,
            BurstController burstController) {
        MainThread.checkMainThread();
        Preconditions.checkState(mBurstLifetime == null,
                "Burst cannot be started, while another is running.");
        mBurstLifetime = new Lifetime();
        BurstCaptureCommand burstCommand = new BurstCaptureCommand(
                mFrameServer, mRequestBuilder,
                mImageFactory, mBurstInputSurface,
                mBurstLifetime, evictionHandler, burstController, mRestorePreviewCommand,
                mMaxImageCount);

        mCameraCommandExecutor.execute(burstCommand);
    }

    @Override
    public synchronized void stopBurst() {
        MainThread.checkMainThread();
        if (mBurstLifetime != null) {
            mBurstLifetime.close();
            mBurstLifetime = null;
        }
    }
}
