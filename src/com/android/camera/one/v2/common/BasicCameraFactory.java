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

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.os.Build.VERSION_CODES;

import com.android.camera.async.Lifetime;
import com.android.camera.async.Observable;
import com.android.camera.async.SafeCloseable;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.one.OneCameraCharacteristics.FaceDetectMode;
import com.android.camera.one.Settings3A;
import com.android.camera.one.v2.autofocus.ManualAutoFocus;
import com.android.camera.one.v2.autofocus.ManualAutoFocusFactory;
import com.android.camera.one.v2.commands.CameraCommand;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.commands.ResettingRunnableCameraCommand;
import com.android.camera.one.v2.commands.PreviewCommandFactory;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.RequestBuilder;
import com.android.camera.one.v2.core.RequestTemplate;
import com.android.camera.one.v2.face.FaceDetect;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executors;

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
@TargetApi(VERSION_CODES.LOLLIPOP)
public class BasicCameraFactory {
    private final ManualAutoFocus mManualAutoFocus;
    private final RequestBuilder.Factory mMeteredZoomedRequestBuilder;
    private final Runnable mPreviewUpdater;

    /**
     * @param lifetime The lifetime of all created objects and their associated
     *            resources.
     * @param cameraCharacteristics
     * @param rootTemplate Provides preconfigured request builders to be used for
     *            all requests to mFrameServer.
     * @param cameraCommandExecutor The
     * @param templateType The template (e.g. CameraDevice.TEMPLATE_PREVIEW) to
     *            use for repeating requests.
     */
    public BasicCameraFactory(Lifetime lifetime,
            OneCameraCharacteristics cameraCharacteristics,
            FrameServer frameServer,
            RequestBuilder.Factory rootTemplate,
            CameraCommandExecutor cameraCommandExecutor,
            PreviewCommandFactory previewCommandFactory,
            Observable<OneCamera.PhotoCaptureParameters.Flash> flash,
            Observable<Integer> exposure,
            Observable<Float> zoom,
            Observable<Boolean> hdrSceneSetting,
            int templateType) {
        RequestTemplate requestTemplate = new RequestTemplate(rootTemplate);
        requestTemplate.setParam(
              CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        requestTemplate.setParam(
              CaptureRequest.CONTROL_AE_MODE, new FlashBasedAEMode(flash, hdrSceneSetting));
        requestTemplate.setParam(
              CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposure);

        Supplier<FaceDetectMode> faceDetectMode = Suppliers.ofInstance(
              FaceDetect.getHighestFaceDetectMode(cameraCharacteristics));

        requestTemplate.setParam(CaptureRequest.CONTROL_MODE,
              new ControlModeSelector(hdrSceneSetting,
                    faceDetectMode,
                    cameraCharacteristics.getSupportedHardwareLevel()));
        requestTemplate.setParam(
              CaptureRequest.CONTROL_SCENE_MODE, new ControlSceneModeSelector(
                    hdrSceneSetting,
                    faceDetectMode,
                    cameraCharacteristics.getSupportedHardwareLevel()));
        requestTemplate.setParam(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
              new StatisticsFaceDetectMode(faceDetectMode));

        Supplier<Rect> cropRegion = new ZoomedCropRegion(
                cameraCharacteristics.getSensorInfoActiveArraySize(), zoom);
        requestTemplate.setParam(CaptureRequest.SCALER_CROP_REGION, cropRegion);

        CameraCommand previewUpdaterCommand =
              previewCommandFactory.get(requestTemplate, templateType);

        // Use a resetting command to ensure that many rapid settings changes do
        // not result in many rapid (>30fps) requests to restart the preview.
        mPreviewUpdater = new ResettingRunnableCameraCommand(cameraCommandExecutor,
              previewUpdaterCommand);

        // Resend the repeating preview request when the zoom or flash state
        // changes to apply the new setting.
        // Also, de-register these callbacks when the camera is closed (to
        // not leak memory).
        SafeCloseable zoomCallback = zoom.addCallback(mPreviewUpdater, MoreExecutors
                .sameThreadExecutor());
        lifetime.add(zoomCallback);
        SafeCloseable flashCallback = flash.addCallback(mPreviewUpdater, MoreExecutors
                .sameThreadExecutor());
        lifetime.add(flashCallback);
        SafeCloseable exposureCallback = exposure.addCallback(mPreviewUpdater, MoreExecutors
                .sameThreadExecutor());
        lifetime.add(exposureCallback);
        SafeCloseable hdrCallback = hdrSceneSetting.addCallback(mPreviewUpdater, MoreExecutors
                .sameThreadExecutor());
        lifetime.add(hdrCallback);

        int sensorOrientation = cameraCharacteristics.getSensorOrientation();

        ManualAutoFocusFactory manualAutoFocusFactory = ManualAutoFocusFactory.create(new
                Lifetime(lifetime), frameServer, cameraCommandExecutor, cropRegion,
                sensorOrientation, mPreviewUpdater, requestTemplate,
                templateType, new Settings3A(), Executors.newScheduledThreadPool(1),
                3 /* afHoldSeconds */);
        mManualAutoFocus = manualAutoFocusFactory.provideManualAutoFocus();
        Supplier<MeteringRectangle[]> aeRegions =
                manualAutoFocusFactory.provideAEMeteringRegion();
        Supplier<MeteringRectangle[]> afRegions =
                manualAutoFocusFactory.provideAFMeteringRegion();

        requestTemplate.setParam(CaptureRequest.CONTROL_AE_REGIONS, aeRegions);
        requestTemplate.setParam(CaptureRequest.CONTROL_AF_REGIONS, afRegions);

        mMeteredZoomedRequestBuilder = requestTemplate;
    }

    public RequestBuilder.Factory provideMeteredZoomedRequestBuilder() {
        return mMeteredZoomedRequestBuilder;
    }

    public ManualAutoFocus provideManualAutoFocus() {
        return mManualAutoFocus;
    }

    public Runnable providePreviewUpdater() {
        return mPreviewUpdater;
    }
}
