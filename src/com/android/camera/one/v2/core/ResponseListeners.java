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

package com.android.camera.one.v2.core;

import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;

import com.android.camera.async.Updatable;
import com.android.camera.one.v2.camera2proxy.AndroidCaptureResultProxy;
import com.android.camera.one.v2.camera2proxy.AndroidTotalCaptureResultProxy;
import com.android.camera.one.v2.camera2proxy.CaptureResultProxy;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;

import java.util.Collection;

/**
 * Static factories for simple {@link ResponseListener}s.
 */
public final class ResponseListeners {
    /**
     * Base class for {@link ResponseListener}s which wrap another callback.
     * <p>
     * Forwards hashCode and equals (and no other methods) to the callback. This
     * enables us to maintain sets of listeners without duplicates. Subclasses
     * should override the appropriate {@link ResponseListener} methods to
     * forward to mDelegate as needed.
     */
    private static abstract class ResponseListenerBase<T> extends ResponseListener {
        private final Updatable<T> mDelegate;

        private ResponseListenerBase(Updatable<T> delegate) {
            mDelegate = delegate;
        }

        @Override
        public int hashCode() {
            return mDelegate.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return mDelegate.equals(other);
        }
    }

    private ResponseListeners() {
    }

    /**
     * @param callback A thread-safe callback to receive the final metadata for
     *            each frame. Metadata will always be received in-order.
     */
    public static ResponseListener forFinalMetadata(
            final Updatable<TotalCaptureResultProxy> callback) {
        return new ResponseListenerBase<TotalCaptureResultProxy>(callback) {
            @Override
            public void onCompleted(TotalCaptureResult result) {
                callback.update(new AndroidTotalCaptureResultProxy(result));
            }
        };
    }

    /**
     * @param callback A thread-safe callback to receive partial and final
     *            metadata for each frame. Metadata may be received
     *            out-of-order.
     */
    public static ResponseListener forPartialMetadata(final Updatable<CaptureResultProxy> callback) {
        return new ResponseListenerBase<CaptureResultProxy>(callback) {
            @Override
            public void onProgressed(CaptureResult partialResult) {
                callback.update(new AndroidCaptureResultProxy(partialResult));
            }

            @Override
            public void onCompleted(TotalCaptureResult result) {
                callback.update(new AndroidTotalCaptureResultProxy(result));
            }
        };
    }

    /**
     * @param callback A thread-safe callback to receive the timestamp of the
     *            expose time for each frame. Timestamps will be received
     *            in-order.
     */
    public static ResponseListener forTimestamps(final Updatable<Long> callback) {
        return new ResponseListenerBase<Long>(callback) {
            @Override
            public void onStarted(long timestamp) {
                callback.update(timestamp);
            }
        };
    }

    /**
     * @param callback A thread-safe callback to be invoked as soon as each
     *            frame is exposed by the device.
     */
    public static ResponseListener forFrameExposure(final Updatable<Void> callback) {
        return new ResponseListenerBase<Void>(callback) {
            @Override
            @SuppressWarnings("ConstantConditions")
            public void onStarted(long timestamp) {
                callback.update(null);
            }
        };
    }

    /**
     * Combines multiple {@link ResponseListener}s.
     */
    public static ResponseListener forListeners(ResponseListener... listeners) {
        return new ResponseListenerBroadcaster(listeners);
    }

    /**
     * Combines multiple {@link ResponseListener}s.
     */
    public static ResponseListener forListeners(Collection<ResponseListener> listeners) {
        return new ResponseListenerBroadcaster(listeners);
    }
}
