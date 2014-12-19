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

package com.android.camera.one.v2.initialization;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.android.camera.one.v2.autofocus.ManualAutoFocus;

/**
 * A {@link ManualAutoFocus} on which {@link #triggerFocusAndMeterAtPoint} may
 * be called even if the underlying camera is not yet ready.
 */
class DeferredManualAutoFocus implements ManualAutoFocus {
    private final Future<ManualAutoFocus> mManualAutoFocusFuture;

    public DeferredManualAutoFocus(Future<ManualAutoFocus> manualAutoFocusFuture) {
        mManualAutoFocusFuture = manualAutoFocusFuture;
    }

    @Override
    public void triggerFocusAndMeterAtPoint(float nx, float ny) {
        if (mManualAutoFocusFuture.isDone()) {
            try {
                ManualAutoFocus af = mManualAutoFocusFuture.get();
                af.triggerFocusAndMeterAtPoint(nx, ny);
            } catch (InterruptedException | ExecutionException | CancellationException e) {
                // If the {@link Future} is not ready, do nothing.
                return;
            }
        }
    }
}
