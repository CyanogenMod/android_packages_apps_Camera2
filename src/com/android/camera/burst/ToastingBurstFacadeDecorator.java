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

package com.android.camera.burst;

import android.content.ContentResolver;
import android.content.Context;
import android.widget.Toast;

import com.android.camera.gl.FrameDistributor.FrameConsumer;
import com.android.camera.one.OneCamera;

/**
 * A simle decorator for a {@link BurstFacade} that shows toasts for when a
 * burst starts or stops.
 * <p>
 * This class can simply be removed once we have proper UI for this.
 */
public class ToastingBurstFacadeDecorator implements BurstFacade {

    /** Shows burst-related toasts to the user. */
    public static class BurstToaster {
        private final Context mContext;

        public BurstToaster(Context context) {
            mContext = context;
        }

        public void showToastBurstStarted() {
            Toast.makeText(mContext, MSG_BURST_STARTED, Toast.LENGTH_SHORT).show();
        }

        public void showToastBurstStopped() {
            Toast.makeText(mContext, MSG_BURST_STOPPED, Toast.LENGTH_LONG).show();
        }
    }

    private static final String MSG_BURST_STARTED =
            "Keep capture button pressed for duration of burst.";
    private static final String MSG_BURST_STOPPED =
            "Burst stopped. Please wait a few seconds for the results to appear.";

    private final BurstFacade mBurstFacade;
    private final BurstToaster mToaster;

    /**
     * Initialize the toasting burst facade decorator.
     *
     * @param facadeToDecorate the facade to decorate.
     * @param toaster the toaster to use to show toasts about the burst status.
     */
    public ToastingBurstFacadeDecorator(BurstFacade facadeToDecorate, BurstToaster toaster) {
        mBurstFacade = facadeToDecorate;
        mToaster = toaster;
    }

    @Override
    public void setContentResolver(ContentResolver contentResolver) {
        mBurstFacade.setContentResolver(contentResolver);
    }

    @Override
    public void onCameraAttached(OneCamera camera) {
        mBurstFacade.onCameraAttached(camera);
    }

    @Override
    public void onCameraDetached() {
        mBurstFacade.onCameraDetached();
    }

    @Override
    public FrameConsumer getPreviewFrameConsumer() {
        return mBurstFacade.getPreviewFrameConsumer();
    }

    @Override
    public void startBurst() {
        mToaster.showToastBurstStarted();
        mBurstFacade.startBurst();
    }

    @Override
    public boolean isReady() {
        return mBurstFacade.isReady();
    }

    @Override
    public boolean stopBurst() {
        boolean burstStopped = mBurstFacade.stopBurst();

        // Only show the toast if a burst was actually stopped.
        if (burstStopped) {
            mToaster.showToastBurstStopped();
        }
        return burstStopped;
    }
}
