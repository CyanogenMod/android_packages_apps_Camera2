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

package com.android.camera.one.v2.components;

import java.util.Arrays;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;

import com.android.camera.one.v2.async.BufferQueue;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.RequestBuilder;
import com.android.camera.one.v2.sharedimagereader.SharedImageReader;

/**
 * Captures single images.
 */
public class StaticPictureCommand implements CameraCommand {
    private final FrameServer mFrameServer;
    private final RequestBuilder.Factory mBuilderFactory;
    private final SharedImageReader mImageReader;
    private final ImageSaver mImageSaver;

    public StaticPictureCommand(FrameServer frameServer, RequestBuilder.Factory builder,
            SharedImageReader imageReader, ImageSaver imageSaver) {
        mFrameServer = frameServer;
        mBuilderFactory = builder;
        mImageReader = imageReader;
        mImageSaver = imageSaver;
    }

    /**
     * Sends a request to take a picture and blocks until it completes.
     */
    public void run() throws
            InterruptedException, CameraAccessException {
        FrameServer.Session session = mFrameServer.createSession();
        try {
            RequestBuilder photoRequest = mBuilderFactory.create(CameraDevice
                    .TEMPLATE_STILL_CAPTURE);

            try (SharedImageReader.ImageCaptureBufferQueue imageStream = mImageReader
                    .createStream(1)) {
                photoRequest.addStream(imageStream);
                // TODO Add a {@link ResponseListener} to notify the caller of
                // when the frame is exposed.
                session.submitRequest(Arrays.asList(photoRequest.build()), false);

                ImageProxy image = imageStream.getNext();
                mImageSaver.saveAndCloseImage(image);
            } catch (BufferQueue.BufferQueueClosedException e) {
                // If we get here, the request was submitted, but the image
                // never arrived.
                // TODO Log failure and notify the caller
            }
        } finally {
            session.close();
        }
    }
}
