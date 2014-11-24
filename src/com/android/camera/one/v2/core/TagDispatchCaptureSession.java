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

package com.android.camera.one.v2.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Handler;

import com.android.camera.one.v2.camera2proxy.CaptureRequestBuilderProxy;

/**
 * Like {@link android.hardware.camera2.CameraCaptureSession}, but takes
 * {@link Request}s and dispatches to the appropriate {@link ResponseListener}
 * on a per-request basis, instead of for every {@link CaptureRequest} submitted
 * at the same time.
 */
public class TagDispatchCaptureSession {
    private static class CaptureCallback extends CameraCaptureSession.CaptureCallback {
        private final Map<Object, ResponseListener> mListeners;

        /**
         * @param listeners A map from tag objects to the listener to be invoked
         *            for events related to the request with that tag.
         */
        public CaptureCallback(Map<Object, ResponseListener> listeners) {
            mListeners = new HashMap<>(listeners);
        }

        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                long timestamp, long frameNumber) {
            Object tag = request.getTag();
            mListeners.get(tag).onStarted(timestamp);
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                CaptureResult partialResult) {
            Object tag = request.getTag();
            long timestamp = partialResult.get(CaptureResult.SENSOR_TIMESTAMP);
            mListeners.get(tag).onProgressed(timestamp, partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                TotalCaptureResult result) {
            Object tag = request.getTag();
            long timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
            mListeners.get(tag).onCompleted(timestamp, result);
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                CaptureFailure failure) {
            Object tag = request.getTag();
            mListeners.get(tag).onFailed(failure);
        }

        @Override
        public void onCaptureSequenceAborted(CameraCaptureSession session, int sequenceId) {
            // Ignored
        }

        @Override
        public void onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId,
                long frameNumber) {
            // Ignored
        }
    }

    private final CameraCaptureSession mCaptureSession;
    private final Handler mCameraHandler;
    private long mTagCounter;

    public TagDispatchCaptureSession(CameraCaptureSession captureSession, Handler cameraHandler) {
        mCaptureSession = captureSession;
        mCameraHandler = cameraHandler;
        mTagCounter = 0;
    }

    private Object generateTag() {
        Object tag = Long.valueOf(mTagCounter);
        mTagCounter++;
        return tag;
    }

    /**
     * Submits the given burst request to the underlying
     * {@link CameraCaptureSession}.
     * <p/>
     * Note that the Tag associated with the {@link CaptureRequest} from each
     * {@link Request} will be overwritten.
     *
     * @param burstRequests The list of {@link Request}s to send.
     * @param repeating Whether the request should be sent as a repeating
     *            request.
     * @throws CameraAccessException See
     *             {@link android.hardware.camera2.CameraCaptureSession#captureBurst}
     *             and
     *             {@link android.hardware.camera2.CameraCaptureSession#setRepeatingBurst}
     * @throws InterruptedException if interrupted while waiting to allocate
     *             resources necessary for each {@link Request}.
     */
    public void submitRequest(List<Request> burstRequests, boolean repeating) throws
            CameraAccessException, InterruptedException {
        try {
            Map<Object, ResponseListener> tagListenerMap = new HashMap<Object, ResponseListener>();
            List<CaptureRequest> captureRequests = new ArrayList<>(burstRequests.size());

            for (Request request : burstRequests) {
                Object tag = generateTag();

                tagListenerMap.put(tag, request.getResponseListener());

                CaptureRequestBuilderProxy builder = request.allocateCaptureRequest();
                builder.setTag(tag);
                captureRequests.add(builder.build());
            }

            if (repeating) {
                mCaptureSession.setRepeatingBurst(captureRequests, new
                        CaptureCallback(tagListenerMap), mCameraHandler);
            } else {
                mCaptureSession.captureBurst(captureRequests, new
                        CaptureCallback(tagListenerMap), mCameraHandler);
            }
        } catch (Exception e) {
            for (Request r : burstRequests) {
                r.abort();
            }
            throw e;
        }
    }
}
