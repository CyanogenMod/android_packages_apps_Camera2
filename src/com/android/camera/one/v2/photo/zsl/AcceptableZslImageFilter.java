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

package com.android.camera.one.v2.photo.zsl;

import android.hardware.camera2.CaptureResult;

import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.google.common.base.Predicate;

/**
 * {@link #apply} returns true for TotalCaptureResults of zsl images which may
 * be saved.
 */
public class AcceptableZslImageFilter implements Predicate<TotalCaptureResultProxy> {
    private final boolean requireAFConvergence;
    private final boolean requireAEConvergence;

    /**
     * @param requireAFConvergence Whether the filter should require AF convergence.
     * @param requireAEConvergence Whether the filter should require AE convergence.
     */
    public AcceptableZslImageFilter(boolean requireAFConvergence, boolean requireAEConvergence) {
        this.requireAFConvergence = requireAFConvergence;
        this.requireAEConvergence = requireAEConvergence;
    }

    @Override
    public boolean apply(TotalCaptureResultProxy metadata) {
        boolean result = true;
        result &= isLensStationary(metadata);
        if (requireAFConvergence) {
            result &= isAFAcceptable(metadata);
        }
        if (requireAEConvergence) {
            result &= isAEAcceptable(metadata);
        }
        return result;
    }

    private boolean isLensStationary(TotalCaptureResultProxy metadata) {
        Integer lensState = metadata.get(CaptureResult.LENS_STATE);
        if (lensState == null) {
            return true;
        } else {
            switch (lensState) {
                case CaptureResult.LENS_STATE_STATIONARY:
                    return true;
                default:
                    return false;
            }
        }
    }

    private boolean isAEAcceptable(TotalCaptureResultProxy metadata) {
        Integer aeState = metadata.get(CaptureResult.CONTROL_AE_STATE);
        if (aeState == null) {
            return true;
        } else {
            switch (aeState) {
                case CaptureResult.CONTROL_AE_STATE_INACTIVE:
                case CaptureResult.CONTROL_AE_STATE_LOCKED:
                case CaptureResult.CONTROL_AE_STATE_CONVERGED:
                    return true;
                default:
                    return false;
            }
        }
    }

    private boolean isAFAcceptable(TotalCaptureResultProxy metadata) {
        Integer afState = metadata.get(CaptureResult.CONTROL_AF_STATE);
        if (afState == null) {
            return true;
        } else {
            switch (afState) {
                case CaptureResult.CONTROL_AF_STATE_INACTIVE:
                case CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED:
                case CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED:
                case CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED:
                case CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED:
                    return true;
                default:
                    return false;
            }
        }
    }
}
