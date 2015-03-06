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

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.util.Range;
import android.view.Surface;

import com.android.camera.async.BufferQueue;
import com.android.camera.async.BufferQueue.BufferQueueClosedException;
import com.android.camera.async.Lifetime;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.commands.CameraCommand;
import com.android.camera.one.v2.core.CaptureStream;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.Request;
import com.android.camera.one.v2.core.RequestBuilder;
import com.android.camera.one.v2.core.RequestTemplate;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;
import com.android.camera.one.v2.core.ResponseListener;
import com.android.camera.one.v2.imagesaver.MetadataImage;
import com.android.camera.one.v2.photo.MetadataFuture;
import com.android.camera.one.v2.sharedimagereader.ManagedImageReader;
import com.android.camera.one.v2.sharedimagereader.imagedistributor.ImageStream;
import com.android.camera.util.ApiHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class BurstCaptureCommand implements CameraCommand {
    /**
     * Template to use for the burst capture.
     */
    private static final int BURST_TEMPLATE_TYPE = CameraDevice.TEMPLATE_VIDEO_SNAPSHOT;

    private final FrameServer mFrameServer;
    private final RequestBuilder.Factory mBuilderFactory;
    private final ManagedImageReader mManagedImageReader;
    private final Surface mBurstInputSurface;
    private final EvictionHandler mBurstEvictionHandler;
    private final BurstController mBurstController;
    private final Runnable mRestorePreviewCommand;
    /**
     *  The max images supported by the {@link ImageStream}.
     */
    private final int mMaxImageCount;
    private final Lifetime mBurstLifetime;

    /**
     * Initializes a new burst capture command.
     *
     * @param frameServer the {@link FrameServer} instance for creating session
     * @param builder factory to use for creating the {@link Request} for burst
     *            capture
     * @param managedImageReader the factory to use for creating a stream of
     *            images
     * @param burstInputSurface the input surface to use for streaming preview
     *            frames to burst
     * @param lifetime the lifetime of the burst, the burst stops capturing
     *            images once the lifetime is closed
     * @param burstEvictionHandler the eviction handler to use for
     *            {@link RingBuffer}
     * @param burstController the burst controller
     * @param restorePreviewCommand the command to run to restore the preview,
     *            once burst capture is complete
     * @param maxImageCount the maximum number of images supported by the image
     *            reader
     */
    public BurstCaptureCommand(FrameServer frameServer, RequestBuilder.Factory builder,
            ManagedImageReader managedImageReader, Surface burstInputSurface,
            Lifetime lifetime,
            EvictionHandler burstEvictionHandler,
            BurstController burstController,
            Runnable restorePreviewCommand,
            int maxImageCount) {
        mFrameServer = frameServer;
        mBuilderFactory = new RequestTemplate(builder);
        mManagedImageReader = managedImageReader;
        mBurstInputSurface = burstInputSurface;
        mBurstLifetime = lifetime;
        mBurstEvictionHandler = burstEvictionHandler;
        mBurstController = burstController;
        mRestorePreviewCommand = restorePreviewCommand;
        mMaxImageCount = maxImageCount;
    }

    @Override
    public void run() throws InterruptedException, CameraAccessException,
            CameraCaptureSessionClosedException, ResourceAcquisitionFailedException {
        List<MetadataImage> capturedImages = new ArrayList<>();
        try (FrameServer.Session session = mFrameServer.createExclusiveSession()) {
            // Create a ring buffer and with the passed burst eviction
            // handler and insert images in it from the image stream.
            // The ring buffer size is one less than the image count.
            int ringBufferSize = mMaxImageCount - 1;
            try (RingBuffer<MetadataImage> ringBuffer =
                    new RingBuffer<MetadataImage>(ringBufferSize, mBurstEvictionHandler)) {
                try (ImageStream imageStream =
                        mManagedImageReader.createStream(mMaxImageCount)) {
                    mBurstLifetime.add(imageStream);

                    // Use the video snapshot template for the burst.
                    RequestBuilder photoRequest =
                            mBuilderFactory.create(BURST_TEMPLATE_TYPE);
                    photoRequest.setParam(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                    checkAndApplyNexus5FrameRateWorkaround(photoRequest);

                    photoRequest.addStream(imageStream);
                    // Hook up the camera stream to burst input surface.
                    photoRequest.addStream(new CaptureStream() {
                            @Override
                        public Surface bind(BufferQueue<Long> timestamps)
                                throws InterruptedException,
                                ResourceAcquisitionFailedException {
                            return mBurstInputSurface;
                        }
                    });

                    // Hook the response listener to invoke eviction handler
                    // frame capture result.
                    photoRequest.addResponseListener(new ResponseListener() {
                        @Override
                        public void onCompleted(TotalCaptureResult result) {
                            final long timestamp = result.get(TotalCaptureResult.SENSOR_TIMESTAMP);
                            mBurstEvictionHandler.onFrameCaptureResultAvailable(timestamp, result);
                        }
                    });
                    session.submitRequest(Arrays.asList(photoRequest.build()),
                            FrameServer.RequestType.REPEATING);

                    try {
                        while (!imageStream.isClosed()) {
                            // metadata
                            MetadataFuture metadataFuture = new MetadataFuture();
                            photoRequest.addResponseListener(metadataFuture);

                            ringBuffer.insertImage(new MetadataImage(imageStream.getNext(),
                                    metadataFuture.getMetadata()));
                        }
                    } catch (BufferQueueClosedException e) {
                        // This is normal. the image stream was closed.
                    }
                } finally {
                    // Burst was completed call remove the images from the ring
                    // buffer.
                    capturedImages = ringBuffer.getAndRemoveAllImages();
                }
            }
        } finally {
            try {
                // Note: BurstController will release images after use
                mBurstController.processBurstResults(capturedImages);
            } finally {
                // Switch back to the old request.
                mRestorePreviewCommand.run();
            }
        }
    }

    /**
     * On Nexus 5 limit frame rate to 24 fps. See b/18950682.
     */
    private static void checkAndApplyNexus5FrameRateWorkaround(RequestBuilder request) {
        if (ApiHelper.IS_NEXUS_5) {
            // For burst limit the frame rate to 24 fps.
            Range<Integer> frameRateBackOff = new Range<>(7, 24);
            request.setParam(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, frameRateBackOff);
        }
    }
}
