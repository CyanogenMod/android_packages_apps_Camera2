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

package com.android.camera.one.v2.common;

import android.graphics.Rect;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;

import com.android.camera.app.OrientationManager;
import com.android.camera.async.Lifetime;
import com.android.camera.async.Observable;
import com.android.camera.async.SafeCloseable;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.one.v2.autofocus.ManualAutoFocus;
import com.android.camera.one.v2.autofocus.ManualAutoFocusFactory;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.commands.PreviewCommand;
import com.android.camera.one.v2.commands.RunnableCameraCommand;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.RequestBuilder;
import com.android.camera.one.v2.core.RequestTemplate;
import com.google.common.base.Supplier;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Wires together functionality common to all cameras:
 * <ul>
 * <li>Tap-to-focus</li>
 * <li>Auto exposure, based on the current flash-setting</li>
 * <li>Metering regions</li>
 * <li>Zoom</li>
 * <li>TODO Logging of OS/driver-level errors</li>
 * </ul>
 * <p>
 * Note that this does not include functionality for taking pictures, since this
 * varies depending on hardware capability.
 * </p>
 */
public class BasicCameraFactory {
    private final ManualAutoFocus mManualAutoFocus;
    private final RequestBuilder.Factory mMeteredZoomedRequestBuilder;
    private final Runnable mPreviewStarter;
    private OrientationManager.DeviceOrientation mSensorOrientation;

    /**
     * @param lifetime The lifetime of all created objects and their associated
     *            resources.
     * @param cameraCharacteristics
     * @param rootBuilder Provides preconfigured request builders to be used for
 *            all requests to mFrameServer.
     * @param threadPool A dynamically-sized thread pool on which to interact
     * @param templateType The template (e.g. CameraDevice.TEMPLATE_PREVIEW) to use for repeating
     *                     requests.
     */
    public BasicCameraFactory(Lifetime lifetime,
                              OneCameraCharacteristics cameraCharacteristics,
                              FrameServer frameServer,
                              RequestBuilder.Factory rootBuilder,
                              ScheduledExecutorService threadPool,
                              Observable<OneCamera.PhotoCaptureParameters.Flash> flash,
                              Observable<Float> zoom, int templateType) {
        RequestTemplate previewBuilder = new RequestTemplate(rootBuilder);
        previewBuilder.setParam(
                CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        previewBuilder.setParam(
                CaptureRequest.CONTROL_AE_MODE, new FlashBasedAEMode(flash));

        Supplier<Rect> cropRegion = new ZoomedCropRegion(
                cameraCharacteristics.getSensorInfoActiveArraySize(), zoom);
        previewBuilder.setParam(CaptureRequest.SCALER_CROP_REGION, cropRegion);

        CameraCommandExecutor cameraCommandExecutor = new CameraCommandExecutor(threadPool);
        lifetime.add(cameraCommandExecutor);
        PreviewCommand previewCommand = new PreviewCommand(frameServer, previewBuilder,
                templateType);

        mPreviewStarter = new RunnableCameraCommand(cameraCommandExecutor,
                previewCommand);

        // Resend the repeating preview request when the zoom or flash state
        // changes to apply the new setting.
        // Also, de-register these callbacks when the camera is closed (to
        // not leak memory).
        SafeCloseable zoomCallback = zoom.addCallback(mPreviewStarter, threadPool);
        lifetime.add(zoomCallback);
        SafeCloseable flashCallback = flash.addCallback(mPreviewStarter, threadPool);
        lifetime.add(flashCallback);

        int sensorOrientation =
                cameraCharacteristics.getSensorOrientation();

        ManualAutoFocusFactory manualAutoFocusFactory = new ManualAutoFocusFactory(new
                Lifetime(lifetime), frameServer, threadPool, cropRegion,
                sensorOrientation, mPreviewStarter, previewBuilder,
                templateType);
        mManualAutoFocus = manualAutoFocusFactory.provideManualAutoFocus();
        Supplier<MeteringRectangle[]> aeRegions =
                manualAutoFocusFactory.provideAEMeteringRegion();
        Supplier<MeteringRectangle[]> afRegions =
                manualAutoFocusFactory.provideAFMeteringRegion();

        previewBuilder.setParam(CaptureRequest.CONTROL_AE_REGIONS, aeRegions);
        previewBuilder.setParam(CaptureRequest.CONTROL_AF_REGIONS, afRegions);

        mMeteredZoomedRequestBuilder = previewBuilder;
    }

    public RequestBuilder.Factory provideMeteredZoomedRequestBuilder() {
        return mMeteredZoomedRequestBuilder;
    }

    public ManualAutoFocus provideManualAutoFocus() {
        return mManualAutoFocus;
    }

    public Runnable providePreviewStarter() {
        return mPreviewStarter;
    }
}
