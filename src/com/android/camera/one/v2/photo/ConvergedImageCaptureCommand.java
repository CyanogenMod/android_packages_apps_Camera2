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

package com.android.camera.one.v2.photo;

import static com.android.camera.one.v2.core.ResponseListeners.forFrameExposure;
import static com.android.camera.one.v2.core.ResponseListeners.forPartialMetadata;

import android.annotation.TargetApi;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;

import com.android.camera.async.BufferQueue;
import com.android.camera.async.Updatable;
import com.android.camera.one.v2.autofocus.AETriggerResult;
import com.android.camera.one.v2.autofocus.AFTriggerResult;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.Request;
import com.android.camera.one.v2.core.RequestBuilder;
import com.android.camera.one.v2.core.RequestTemplate;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;
import com.android.camera.one.v2.imagesaver.ImageSaver;
import com.android.camera.one.v2.sharedimagereader.ManagedImageReader;
import com.android.camera.one.v2.sharedimagereader.imagedistributor.ImageStream;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Captures a burst after waiting for AF and AE convergence.
 */
@ParametersAreNonnullByDefault
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class ConvergedImageCaptureCommand implements ImageCaptureCommand {
    private final ManagedImageReader mImageReader;
    private final FrameServer mFrameServer;
    private final RequestBuilder.Factory mScanRequestTemplate;
    private final RequestBuilder.Factory mRepeatingRequestBuilder;
    private final int mRepeatingRequestTemplate;
    private final int mStillCaptureRequestTemplate;
    private final List<RequestBuilder.Factory> mBurst;

    private final boolean mWaitForAEConvergence;
    private final boolean mWaitForAFConvergence;

    /**
     * Transforms a request template by resetting focus and exposure modes.
     */
    private static RequestBuilder.Factory resetFocusExposureModes(RequestBuilder.Factory template) {
        RequestTemplate result = new RequestTemplate(template);
        result.setParam(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        result.setParam(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        result.setParam(CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
        result.setParam(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
        return result;
    }

    /**
     * @param imageReader Creates the {@link ImageStream} used for capturing
     *            images to be saved.
     * @param frameServer Used for interacting with the camera device.
     * @param repeatingRequestBuilder Creates request builders to use for
     *            repeating requests sent during the scanning phase and after
     *            capture is complete.
     * @param repeatingRequestTemplate The template type to use for repeating
     *            requests.
     * @param burst Creates request builders to use for each image captured from
     * @param waitForAEConvergence
     * @param waitForAFConvergence
     */
    public ConvergedImageCaptureCommand(ManagedImageReader imageReader, FrameServer frameServer,
            RequestBuilder.Factory repeatingRequestBuilder,
            int repeatingRequestTemplate, int stillCaptureRequestTemplate,
            List<RequestBuilder.Factory> burst, boolean waitForAEConvergence,
            boolean waitForAFConvergence) {
        mImageReader = imageReader;
        mFrameServer = frameServer;
        mRepeatingRequestBuilder = repeatingRequestBuilder;
        mRepeatingRequestTemplate = repeatingRequestTemplate;
        mStillCaptureRequestTemplate = stillCaptureRequestTemplate;
        mBurst = burst;
        mWaitForAEConvergence = waitForAEConvergence;
        mWaitForAFConvergence = waitForAFConvergence;

        mScanRequestTemplate = resetFocusExposureModes(repeatingRequestBuilder);
    }

    /**
     * Sends a request to take a picture and blocks until it completes.
     */
    @Override
    public void run(Updatable<Void> imageExposureUpdatable, ImageSaver imageSaver) throws
            InterruptedException, CameraAccessException, CameraCaptureSessionClosedException,
            ResourceAcquisitionFailedException {
        try (FrameServer.Session session = mFrameServer.createExclusiveSession()) {
            try (ImageStream imageStream = mImageReader.createPreallocatedStream(mBurst.size())) {
                if (mWaitForAFConvergence) {
                    waitForAFConvergence(session);
                }
                if (mWaitForAEConvergence) {
                    waitForAEConvergence(session);
                }
                captureBurst(session, imageStream, imageExposureUpdatable, imageSaver);
            } finally {
                // Always reset the repeating stream to ensure AF/AE are not
                // locked when this exits.
                // Note that this may still throw if the camera or session is
                // closed.
                resetRepeating(session);
            }
        } finally {
            imageSaver.close();
        }
    }

    private void waitForAFConvergence(FrameServer.Session session) throws CameraAccessException,
            InterruptedException, ResourceAcquisitionFailedException,
            CameraCaptureSessionClosedException {
        AFTriggerResult afStateMachine = new AFTriggerResult();

        RequestBuilder triggerBuilder = mScanRequestTemplate.create(mRepeatingRequestTemplate);
        triggerBuilder.setParam(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest
                .CONTROL_AF_TRIGGER_START);
        triggerBuilder.addResponseListener(forPartialMetadata(afStateMachine));

        RequestBuilder idleBuilder = mScanRequestTemplate.create(mRepeatingRequestTemplate);
        idleBuilder.addResponseListener(forPartialMetadata(afStateMachine));

        session.submitRequest(Arrays.asList(idleBuilder.build()),
                FrameServer.RequestType.REPEATING);

        session.submitRequest(Arrays.asList(triggerBuilder.build()),
                FrameServer.RequestType.NON_REPEATING);

        // Block until the AF trigger is complete
        afStateMachine.get();
    }

    private void waitForAEConvergence(FrameServer.Session session) throws CameraAccessException,
            InterruptedException, ResourceAcquisitionFailedException,
            CameraCaptureSessionClosedException {
        AETriggerResult aeStateMachine = new AETriggerResult();

        RequestBuilder triggerBuilder = mScanRequestTemplate.create(mRepeatingRequestTemplate);
        triggerBuilder.setParam(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        triggerBuilder.addResponseListener(forPartialMetadata(aeStateMachine));

        RequestBuilder idleBuilder = mScanRequestTemplate.create(mRepeatingRequestTemplate);
        idleBuilder.addResponseListener(forPartialMetadata(aeStateMachine));

        session.submitRequest(Arrays.asList(idleBuilder.build()),
                FrameServer.RequestType.REPEATING);

        session.submitRequest(Arrays.asList(triggerBuilder.build()),
                FrameServer.RequestType.NON_REPEATING);

        // Wait until the ae state converges to a result.
        aeStateMachine.get();
    }

    private void captureBurst(FrameServer.Session session, ImageStream imageStream, Updatable<Void>
            imageExposureUpdatable, ImageSaver imageSaver) throws CameraAccessException,
            InterruptedException, ResourceAcquisitionFailedException,
            CameraCaptureSessionClosedException {
        List<Request> burstRequest = new ArrayList<>(mBurst.size());
        List<ListenableFuture<TotalCaptureResultProxy>> metadata = new ArrayList<>(mBurst.size());
        boolean first = true;
        for (RequestBuilder.Factory builderTemplate : mBurst) {
            RequestBuilder builder = builderTemplate.create(mStillCaptureRequestTemplate);

            builder.setParam(CaptureRequest.CONTROL_AF_MODE, CaptureRequest
                    .CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            builder.setParam(CaptureRequest.CONTROL_CAPTURE_INTENT,
                    CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);

            if (first) {
                first = false;
                builder.addResponseListener(forFrameExposure(imageExposureUpdatable));
            }

            MetadataFuture metadataFuture = new MetadataFuture();
            builder.addResponseListener(metadataFuture);
            metadata.add(metadataFuture.getMetadata());

            builder.addStream(imageStream);

            burstRequest.add(builder.build());
        }

        session.submitRequest(burstRequest, FrameServer.RequestType.NON_REPEATING);

        for (int i = 0; i < mBurst.size(); i++) {
            try {
                ImageProxy image = imageStream.getNext();
                imageSaver.addFullSizeImage(image, metadata.get(i));
            } catch (BufferQueue.BufferQueueClosedException e) {
                // No more images will be available, so just quit.
                return;
            }
        }
    }

    private void resetRepeating(FrameServer.Session session) throws InterruptedException,
            CameraCaptureSessionClosedException, CameraAccessException,
            ResourceAcquisitionFailedException {
        RequestBuilder repeatingBuilder = mRepeatingRequestBuilder.create
                (mRepeatingRequestTemplate);
        session.submitRequest(Arrays.asList(repeatingBuilder.build()),
                FrameServer.RequestType.REPEATING);

        RequestBuilder triggerCancelBuilder = mRepeatingRequestBuilder
                .create(mRepeatingRequestTemplate);
        triggerCancelBuilder.setParam(CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
        session.submitRequest(Arrays.asList(triggerCancelBuilder.build()),
                FrameServer.RequestType.NON_REPEATING);

        // Some devices (e.g. N6) implicitly lock AE after sending an
        // AE_PRECAPTURE trigger. (see bug: 19265647)
        // The implicit lock is released when a request with
        // INTENT_STILL_CAPTURE is taken.

        // However, if we never get to that point (because the command was
        // interrupted before the request for a photo was sent), then we must be
        // sure to cancel this implicit AE lock to resume normal AE behavior.
        // Sending a request for an explicit AE lock (followed, implicitly, by a
        // request from the current repeating request, which has AE lock off)
        // fixes the issue and results in normal AE behavior.
        RequestBuilder hackAETriggerCancelBuilder = mRepeatingRequestBuilder.create
                (mRepeatingRequestTemplate);
        hackAETriggerCancelBuilder.setParam(CaptureRequest.CONTROL_AE_LOCK, true);

        session.submitRequest(Arrays.asList(hackAETriggerCancelBuilder.build()),
                FrameServer.RequestType.NON_REPEATING);
    }
}
