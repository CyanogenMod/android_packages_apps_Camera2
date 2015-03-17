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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureRequest;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/**
 * A {@link RequestBuilder.Factory} which allows modifying each
 * {@link RequestBuilder} that is created.
 * <p>
 * This allows for easily factoring-out parameters, surfaces, and metadata logic
 * which applies to multiple different components.
 * <p>
 * For example, a RequestBuilder.Factory could be created which produces request
 * builders which already have the latest zoom settings, preview surface,
 * metering regions, auto-focus state listener, etc. applied.
 */
public class RequestTemplate implements RequestBuilder.Factory, ResponseManager {
    private static class Parameter<T> {
        private final CaptureRequest.Key<T> key;
        private final Supplier<T> value;

        private Parameter(CaptureRequest.Key<T> key, Supplier<T> value) {
            this.key = key;
            this.value = value;
        }

        public void addToBuilder(RequestBuilder builder) {
            builder.setParam(key, value.get());
        }
    }

    private final RequestBuilder.Factory mRequestBuilderFactory;
    private final Set<ResponseListener> mResponseListeners;
    private final List<Parameter<?>> mParameters;
    private final List<CaptureStream> mCaptureStreams;

    public RequestTemplate(RequestBuilder.Factory requestBuilderFactory) {
        mRequestBuilderFactory = requestBuilderFactory;
        mResponseListeners = new HashSet<>();
        mParameters = new ArrayList<>();
        mCaptureStreams = new ArrayList<>();
    }

    public <T> RequestTemplate setParam(CaptureRequest.Key<T> key, T value) {
        return setParam(key, Suppliers.ofInstance(value));
    }

    /**
     * Attaches the given value to all derived RequestBuilders. Note that the
     * value is polled when each new RequestBuilder is created.
     */
    public <T> RequestTemplate setParam(CaptureRequest.Key<T> key,
                                        Supplier<T> value) {
        mParameters.add(new Parameter<T>(key, value));
        return this;
    }

    /**
     * Attaches the given ResponseListener to all derived RequestBuilders.
     */
    @Override
    public void addResponseListener(ResponseListener listener) {
        mResponseListeners.add(listener);
    }

    /**
     * Attaches the given stream to all derived RequestBuilders.
     */
    public RequestTemplate addStream(CaptureStream stream) {
        mCaptureStreams.add(stream);
        return this;
    }

    @Override
    public RequestBuilder create(int templateType) throws CameraAccessException {
        RequestBuilder builder = mRequestBuilderFactory.create(templateType);
        for (Parameter param : mParameters) {
            param.addToBuilder(builder);
        }
        for (ResponseListener listener : mResponseListeners) {
            builder.addResponseListener(listener);
        }
        for (CaptureStream stream : mCaptureStreams) {
            builder.addStream(stream);
        }
        return builder;
    }
}
