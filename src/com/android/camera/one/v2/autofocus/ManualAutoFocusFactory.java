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

package com.android.camera.one.v2.autofocus;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.graphics.Rect;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;

import com.android.camera.app.OrientationManager;
import com.android.camera.async.ConcurrentState;
import com.android.camera.async.Lifetime;
import com.android.camera.async.Pollable;
import com.android.camera.async.ResettingDelayedExecutor;
import com.android.camera.one.v2.commands.CameraCommand;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.commands.RunnableCameraCommand;
import com.android.camera.one.v2.core.DecoratingRequestBuilderBuilder;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.RequestBuilder;

/**
 * Wires together "tap-to-focus" functionality, providing a
 * {@link ManualAutoFocus} instance to trigger auto-focus and metering. It also
 * provides a way of polling for the most up-to-date metering regions.
 */
public class ManualAutoFocusFactory {
    private static final int AF_HOLD_MILLIS = 3000;

    private final ManualAutoFocus mManualAutoFocus;
    private final Pollable<MeteringRectangle[]> mAEMeteringRegion;
    private final Pollable<MeteringRectangle[]> mAFMeteringRegion;

    /**
     * @param lifetime The Lifetime for all created objects.
     * @param frameServer The FrameServer on which to perform manual AF scans.
     * @param threadPool A dynamic (not fixed-size!) thread pool on which to run
     *            AF tasks.
     * @param cropRegion The latest crop region.
     * @param sensorOrientation The sensor orientation.
     * @param previewRunner A runnable to restart the preview.
     * @param rootBuilder The root request builder to use for all requests sent
     *            to the FrameServer.
     */
    public ManualAutoFocusFactory(Lifetime lifetime, FrameServer frameServer,
            ScheduledExecutorService threadPool, Pollable<Rect> cropRegion,
            OrientationManager.DeviceOrientation sensorOrientation,
            Runnable previewRunner, RequestBuilder.Factory rootBuilder,
            int templateType) {
        CameraCommandExecutor commandExecutor = new CameraCommandExecutor(threadPool);
        lifetime.add(commandExecutor);

        ConcurrentState<MeteringParameters> currentMeteringParameters = new ConcurrentState<>();
        mAEMeteringRegion = new AEMeteringRegion(currentMeteringParameters, cropRegion,
                sensorOrientation);
        mAFMeteringRegion = new AEMeteringRegion(currentMeteringParameters, cropRegion,
                sensorOrientation);

        DecoratingRequestBuilderBuilder afRequestBuilder =
                new DecoratingRequestBuilderBuilder(rootBuilder);
        afRequestBuilder.withParam(CaptureRequest.CONTROL_AE_REGIONS, mAEMeteringRegion);
        afRequestBuilder.withParam(CaptureRequest.CONTROL_AF_REGIONS, mAFMeteringRegion);

        CameraCommand afScanCommand = new FullAFScanCommand(frameServer, afRequestBuilder,
                templateType);

        ResettingDelayedExecutor afHoldDelayedExecutor = new ResettingDelayedExecutor(threadPool,
                AF_HOLD_MILLIS, TimeUnit.MILLISECONDS);
        lifetime.add(afHoldDelayedExecutor);

        CameraCommand afScanHoldResetCommand = new AFScanHoldResetCommand(afScanCommand,
                afHoldDelayedExecutor, previewRunner, currentMeteringParameters);

        Runnable afRunner = new RunnableCameraCommand(commandExecutor, afScanHoldResetCommand);

        mManualAutoFocus = new ManualAutoFocusImpl(currentMeteringParameters, afRunner);
    }

    public ManualAutoFocus provideManualAutoFocus() {
        return mManualAutoFocus;
    }

    public Pollable<MeteringRectangle[]> provideAEMeteringRegion() {
        return mAEMeteringRegion;
    }

    public Pollable<MeteringRectangle[]> provideAFMeteringRegion() {
        return mAFMeteringRegion;
    }
}
