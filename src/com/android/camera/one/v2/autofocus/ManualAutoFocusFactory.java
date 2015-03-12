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

import android.graphics.Rect;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;

import com.android.camera.async.ConcurrentState;
import com.android.camera.async.Lifetime;
import com.android.camera.async.ResettingDelayedExecutor;
import com.android.camera.one.Settings3A;
import com.android.camera.one.v2.commands.CameraCommand;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.commands.ResettingRunnableCameraCommand;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.RequestBuilder;
import com.android.camera.one.v2.core.RequestTemplate;
import com.google.common.base.Supplier;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Wires together "tap-to-focus" functionality, providing a
 * {@link ManualAutoFocus} instance to trigger auto-focus and metering. It also
 * provides a way of polling for the most up-to-date metering regions.
 */
public class ManualAutoFocusFactory {
    private final ManualAutoFocus mManualAutoFocus;
    private final Supplier<MeteringRectangle[]> mAEMeteringRegion;
    private final Supplier<MeteringRectangle[]> mAFMeteringRegion;

    private ManualAutoFocusFactory(ManualAutoFocus manualAutoFocus,
            Supplier<MeteringRectangle[]> aeMeteringRegion,
            Supplier<MeteringRectangle[]> afMeteringRegion) {
        mManualAutoFocus = manualAutoFocus;
        mAEMeteringRegion = aeMeteringRegion;
        mAFMeteringRegion = afMeteringRegion;
    }

    /**
     * @param lifetime The Lifetime for all created objects.
     * @param frameServer The FrameServer on which to perform manual AF scans.
     * @param commandExecutor The command executor on which to interact with the
     *            camera.
     * @param cropRegion The latest crop region.
     * @param sensorOrientation The sensor orientation.
     * @param previewRunner A runnable to restart the preview.
     * @param rootBuilder The root request builder to use for all requests sent
     * @param threadPool The executor on which to schedule delayed tasks.
     * @param afHoldSeconds The number of seconds to hold AF after a manual AF
     *            sweep is triggered.
     */
    public static ManualAutoFocusFactory create(Lifetime lifetime, FrameServer frameServer,
            CameraCommandExecutor commandExecutor, Supplier<Rect> cropRegion,
            int sensorOrientation,
            Runnable previewRunner, RequestBuilder.Factory rootBuilder,
            int templateType, Settings3A settings3A,
            ScheduledExecutorService threadPool,
            int afHoldSeconds) {
        ConcurrentState<MeteringParameters> currentMeteringParameters = new ConcurrentState<>(
                GlobalMeteringParameters.create());
        AEMeteringRegion aeMeteringRegion = new AEMeteringRegion(currentMeteringParameters,
                cropRegion);
        AFMeteringRegion afMeteringRegion = new AFMeteringRegion(currentMeteringParameters,
                cropRegion);

        RequestTemplate afScanRequestBuilder = new RequestTemplate(rootBuilder);
        afScanRequestBuilder.setParam(CaptureRequest.CONTROL_AE_REGIONS, aeMeteringRegion);
        afScanRequestBuilder.setParam(CaptureRequest.CONTROL_AF_REGIONS, afMeteringRegion);

        CameraCommand afScanCommand = new FullAFScanCommand(frameServer, afScanRequestBuilder,
                templateType);

        ResettingDelayedExecutor afHoldDelayedExecutor = new ResettingDelayedExecutor(
                threadPool, afHoldSeconds, TimeUnit.SECONDS);
        lifetime.add(afHoldDelayedExecutor);

        CameraCommand afScanHoldResetCommand = new AFScanHoldResetCommand(afScanCommand,
                afHoldDelayedExecutor, previewRunner, currentMeteringParameters);

        Runnable afRunner = new ResettingRunnableCameraCommand(commandExecutor,
                afScanHoldResetCommand);

        ManualAutoFocusImpl manualAutoFocus = new ManualAutoFocusImpl(currentMeteringParameters,
                afRunner, sensorOrientation, settings3A);

        return new ManualAutoFocusFactory(manualAutoFocus, aeMeteringRegion, afMeteringRegion);
    }

    public ManualAutoFocus provideManualAutoFocus() {
        return mManualAutoFocus;
    }

    public Supplier<MeteringRectangle[]> provideAEMeteringRegion() {
        return mAEMeteringRegion;
    }

    public Supplier<MeteringRectangle[]> provideAFMeteringRegion() {
        return mAFMeteringRegion;
    }
}
