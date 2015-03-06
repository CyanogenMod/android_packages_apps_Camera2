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

import android.hardware.camera2.TotalCaptureResult;

import com.android.camera.async.Updatable;
import com.android.camera.one.v2.camera2proxy.AndroidTotalCaptureResultProxy;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.android.camera.one.v2.core.ResponseListener;
import com.android.camera.one.v2.core.ResponseListeners;

/**
 * A {@link ResponseListener} which provides a stream of
 * {@link TotalCaptureResult}s.
 *
 * @deprecated Use {@link ResponseListeners#forFinalMetadata}
 */
@Deprecated
public class TotalCaptureResultResponseListener extends ResponseListener {
    private final Updatable<TotalCaptureResultProxy> mResults;

    public TotalCaptureResultResponseListener(Updatable<TotalCaptureResultProxy> results) {
        mResults = results;
    }

    @Override
    public void onCompleted(TotalCaptureResult result) {
        mResults.update(new AndroidTotalCaptureResultProxy(result));
    }
}
