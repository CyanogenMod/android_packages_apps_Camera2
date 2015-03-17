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

package com.android.camera.one.v2.camera2proxy;

import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Wraps {@link TotalCaptureResult}
 */
@ParametersAreNonnullByDefault
public final class AndroidTotalCaptureResultProxy extends AndroidCaptureResultProxy implements
        TotalCaptureResultProxy {
    final TotalCaptureResult mTotalCaptureResult;

    public AndroidTotalCaptureResultProxy(TotalCaptureResult totalCaptureResult) {
        super(totalCaptureResult);
        mTotalCaptureResult = totalCaptureResult;
    }

    @Nonnull
    public List<CaptureResultProxy> getPartialResults() {
        List<CaptureResult> partialResults = mTotalCaptureResult.getPartialResults();
        ArrayList<CaptureResultProxy> proxies = new ArrayList<>(partialResults.size());
        for (CaptureResult result : partialResults) {
            proxies.add(new AndroidCaptureResultProxy(result));
        }
        return proxies;
    }
}
