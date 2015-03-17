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
import android.hardware.camera2.TotalCaptureResult;

import com.android.camera.async.Updatable;
import com.android.camera.debug.Log;
import com.android.camera.debug.Logger;
import com.android.camera.one.v2.camera2proxy.CaptureRequestProxy;
import com.android.camera.one.v2.camera2proxy.CaptureResultProxy;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Like {@link AcceptableZslImageFilter}, but determines whether or not to
 * filter ZSL images by AE convergence based on the most-recent converged AE
 * result. This enables an optimization in which pictures can be taken with zero
 * shutter lag when flash is AUTO even if AE is searching.
 * <p>
 * For example, when flash is AUTO, we can only capture images via the ZSL
 * buffer if we know that flash is not required. We know that flash is not
 * required when CONTROL_AE_STATE == CONTROL_AE_STATE_CONVERGED. However, when
 * the AE system is scanning, we do not know if flash is required. So, instead
 * of waiting until it converges, we can cache the most recent result and allow
 * capturing an image instantly.
 * <p>
 * Note that this optimization presents a trade-off between speed and
 * quality/accuracy. For example, if a user moves the camera from a bright scene
 * to a dark scene and tries to immediately take a picture before AE has
 * converged, then the flash may not fire. However, it enables faster capture if
 * the user moves the camera from a bright scene to another bright scene with a
 * different level of illumination, which would not otherwise require flash.
 */
@ParametersAreNonnullByDefault
public final class AutoFlashZslImageFilter implements Predicate<TotalCaptureResultProxy>,
        Updatable<CaptureResultProxy> {
    private final Logger mLog;
    private final AcceptableZslImageFilter mDefaultFilter;

    private AtomicBoolean mRequireAEConvergence;
    private long mLastFrameNumber;

    private AutoFlashZslImageFilter(Logger.Factory logFactory,
            AcceptableZslImageFilter defaultFilter) {
        mDefaultFilter = defaultFilter;
        mLog = logFactory.create(new Log.Tag("AutoFlashZslImgFltr"));
        mRequireAEConvergence = new AtomicBoolean(true);
        mLastFrameNumber = -1;
    }

    /**
     * Wraps a TotalCaptureResult, converting
     * CaptureResult.CONTROL_AE_STATE_SEARCHING into
     * CaptureResult.CONTROL_AE_STATE_CONVERGED.
     */
    private static class AEConvergedTotalCaptureResult implements TotalCaptureResultProxy {
        private final TotalCaptureResultProxy mDelegate;

        public AEConvergedTotalCaptureResult(TotalCaptureResultProxy delegate) {
            mDelegate = delegate;
        }

        @Nullable
        @Override
        public <T> T get(CaptureResult.Key<T> key) {
            if (key == TotalCaptureResult.CONTROL_AE_STATE) {
                Integer aeState = (Integer) mDelegate.get(key);
                if (Objects.equal(aeState, CaptureResult.CONTROL_AE_STATE_SEARCHING)) {
                    return (T) ((Integer) CaptureResult.CONTROL_AE_STATE_CONVERGED);
                }
            }
            return mDelegate.get(key);
        }

        @Nonnull
        @Override
        public List<CaptureResult.Key<?>> getKeys() {
            return mDelegate.getKeys();
        }

        @Nonnull
        @Override
        public CaptureRequestProxy getRequest() {
            return mDelegate.getRequest();
        }

        @Override
        public long getFrameNumber() {
            return mDelegate.getFrameNumber();
        }

        @Override
        public int getSequenceId() {
            return mDelegate.getSequenceId();
        }

        @Nonnull
        @Override
        public List<CaptureResultProxy> getPartialResults() {
            return mDelegate.getPartialResults();
        }
    }

    public static AutoFlashZslImageFilter create(Logger.Factory logFactory,
            boolean requireAFConvergence) {
        return new AutoFlashZslImageFilter(
                logFactory,
                new AcceptableZslImageFilter(requireAFConvergence, /* aeConvergence */true));
    }

    @Override
    public boolean apply(TotalCaptureResultProxy totalCaptureResultProxy) {
        if (!mRequireAEConvergence.get()) {
            // If AE was previously converged, wrap the metadata to appear as if AE is currently
            // converged.
            totalCaptureResultProxy = new AEConvergedTotalCaptureResult(totalCaptureResultProxy);
        }
        return mDefaultFilter.apply(totalCaptureResultProxy);
    }

    @Override
    public void update(@Nonnull CaptureResultProxy captureResult) {
        if (captureResult.getFrameNumber() > mLastFrameNumber) {
            Integer aeState = captureResult.get(CaptureResult.CONTROL_AE_STATE);
            if (aeState != null) {
                if (aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED) {
                    boolean previousValue = mRequireAEConvergence.getAndSet(true);
                    if (previousValue != true) {
                        // Only log changes
                        mLog.i("Flash required");
                    }
                } else if (aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                    boolean previousValue = mRequireAEConvergence.getAndSet(false);
                    if (previousValue != false) {
                        // Only log changes
                        mLog.i("Flash not required");
                    }
                }
                mLastFrameNumber = captureResult.getFrameNumber();
            }
        }
    }
}
