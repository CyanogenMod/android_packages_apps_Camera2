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

import java.util.List;

import android.hardware.camera2.CameraAccessException;

/**
 * A {@link RequestBuilder.Factory} which allows modifying each
 * {@link RequestBuilder} that is created.
 */
public class DecoratingRequestBuilderFactory implements RequestBuilder.Factory {
    private final RequestBuilder.Factory mRequestBuilderFactory;
    private final List<RequestBuilderDecorator> mDecorators;

    public DecoratingRequestBuilderFactory(RequestBuilder.Factory requestBuilderFactory,
            List<RequestBuilderDecorator> decorators) {
        mRequestBuilderFactory = requestBuilderFactory;
        mDecorators = decorators;
    }

    @Override
    public RequestBuilder create(int templateType) throws CameraAccessException {
        RequestBuilder builder = mRequestBuilderFactory.create(templateType);
        for (RequestBuilderDecorator decorator : mDecorators) {
            decorator.decorateRequest(builder);
        }
        return builder;
    }

    public static interface RequestBuilderDecorator {
        public void decorateRequest(RequestBuilder builder);
    }
}
