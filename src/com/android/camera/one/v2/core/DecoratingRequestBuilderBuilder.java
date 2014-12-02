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

import com.android.camera.async.ConstantPollable;
import com.android.camera.async.Pollable;

/**
 * A {@link RequestBuilder.Factory} which allows modifying each
 * {@link RequestBuilder} that is created. <br>
 * TODO Write Tests
 */
public class DecoratingRequestBuilderBuilder implements RequestBuilder.Factory {
    private static class Parameter<T> {
        private final CaptureRequest.Key<T> key;
        private final Pollable<T> value;

        private Parameter(CaptureRequest.Key<T> key, Pollable<T> value) {
            this.key = key;
            this.value = value;
        }

        public void addToBuilder(RequestBuilder builder) {
            try {
                builder.setParam(key, value.get());
            } catch (Pollable.NoValueSetException e) {
                // If there is no value to set, do nothing.
            }
        }
    }

    private final RequestBuilder.Factory mRequestBuilderFactory;
    private final Set<ResponseListener> mResponseListeners;
    private final List<Parameter<?>> mParameters;
    private final List<CaptureStream> mCaptureStreams;

    public DecoratingRequestBuilderBuilder(RequestBuilder.Factory requestBuilderFactory) {
        mRequestBuilderFactory = requestBuilderFactory;
        mResponseListeners = new HashSet<>();
        mParameters = new ArrayList<>();
        mCaptureStreams = new ArrayList<>();
    }

    public <T> DecoratingRequestBuilderBuilder withParam(CaptureRequest.Key<T> key, T value) {
        return withParam(key, new ConstantPollable<T>(value));
    }

    public <T> DecoratingRequestBuilderBuilder withParam(CaptureRequest.Key<T> key,
            Pollable<T> value) {
        mParameters.add(new Parameter<T>(key, value));
        return this;
    }

    public DecoratingRequestBuilderBuilder withResponseListener(ResponseListener listener) {
        mResponseListeners.add(listener);
        return this;
    }

    public DecoratingRequestBuilderBuilder withStream(CaptureStream stream) {
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
