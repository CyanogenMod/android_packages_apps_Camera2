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

import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.view.Surface;

import com.android.camera.async.Listenable;
import com.android.camera.async.SafeCloseable;
import com.android.camera.async.Updatable;
import com.android.camera.one.OneCamera;
import com.android.camera.one.v2.AutoFocusHelper;
import com.android.camera.session.CaptureSession;
import com.android.camera.util.Callback;
import com.android.camera.util.ScopedFactory;
import com.android.camera.util.Size;

/**
 * A generic, composable {@link OneCamera}.
 * <p>
 * Note: This implementation assumes that these four methods are invoked in
 * sequences matching the following regex:
 * <p>
 * startPreview (takePicture | triggerFocusAndMeterAtPoint)* close
 * <p>
 * All other methods may be called at any time.
 */
public class GenericOneCameraImpl implements OneCamera {
    public interface ManualAutoFocus {
        /**
         * @See {@link OneCamera#triggerFocusAndMeterAtPoint}
         */
        void triggerFocusAndMeterAtPoint(float nx, float ny);
    }

    public interface PictureTaker {
        /**
         * @See {@link OneCamera#takePicture}
         */
        public void takePicture(OneCamera.PhotoCaptureParameters params, CaptureSession session);
    }

    private final Set<SafeCloseable> mCloseListeners;
    private final PictureTaker mPictureTaker;
    private final ManualAutoFocus mManualAutoFocus;
    private final Listenable<Integer> mAFStateListenable;
    private final Listenable<Boolean> mReadyStateListenable;
    private final float mMaxZoom;
    private final Updatable<Float> mZoom;
    private final Size[] mSupportedPreviewSizes;
    private final float mFullSizeAspectRatio;
    private final Facing mDirection;
    private final PreviewSizeSelector mPreviewSizeSelector;
    private final Listenable<Boolean> mPreviewStartSuccessListenable;
    private final ScopedFactory<Surface, Runnable> mPreviewScopeEntrance;

    public GenericOneCameraImpl(Set<SafeCloseable> closeListeners, PictureTaker pictureTaker,
            ManualAutoFocus manualAutoFocus, Listenable<Integer> afStateProvider,
            Listenable<Boolean> readyStateListenable, float maxZoom, Updatable<Float> zoom,
            Size[] supportedPreviewSizes, float fullSizeAspectRatio, Facing direction,
            PreviewSizeSelector previewSizeSelector,
            Listenable<Boolean> previewStartSuccessListenable,
            ScopedFactory<Surface, Runnable> previewScopeEntrance) {
        mPreviewStartSuccessListenable = previewStartSuccessListenable;
        mPreviewScopeEntrance = previewScopeEntrance;
        mCloseListeners = new HashSet<>(closeListeners);
        mMaxZoom = maxZoom;
        mSupportedPreviewSizes = supportedPreviewSizes;
        mFullSizeAspectRatio = fullSizeAspectRatio;
        mDirection = direction;
        mPreviewSizeSelector = previewSizeSelector;
        mPictureTaker = pictureTaker;
        mManualAutoFocus = manualAutoFocus;
        mAFStateListenable = afStateProvider;
        mReadyStateListenable = readyStateListenable;
        mZoom = zoom;
    }

    @Override
    public void triggerFocusAndMeterAtPoint(float nx, float ny) {
        mManualAutoFocus.triggerFocusAndMeterAtPoint(nx, ny);
    }

    @Override
    public void takePicture(PhotoCaptureParameters params, CaptureSession session) {
        mPictureTaker.takePicture(params, session);
    }

    @Override
    public void startBurst(BurstParameters params, CaptureSession session) {
        // TODO delete from OneCamera interface
    }

    @Override
    public void stopBurst() {
        // TODO delete from OneCamera interface
    }

    @Override
    public void setFocusStateListener(final FocusStateListener listener) {
        mAFStateListenable.setCallback(new Callback<Integer>() {
            @Override
            public void onCallback(Integer afState) {
                // TODO delete frameNumber from FocusStateListener callback. It
                // is optional and never actually used.
                long frameNumber = -1;
                listener.onFocusStatusUpdate(AutoFocusHelper.stateFromCamera2State(afState),
                        frameNumber);
            }
        });
    }

    @Override
    public void setReadyStateChangedListener(final ReadyStateChangedListener listener) {
        mReadyStateListenable.setCallback(new Callback<Boolean>() {
            @Override
            public void onCallback(Boolean result) {
                listener.onReadyStateChanged(result);
            }
        });
    }

    @Override
    public void startPreview(Surface surface, final CaptureReadyCallback listener) {
        mPreviewStartSuccessListenable.setCallback(new Callback<Boolean>() {
            @Override
            public void onCallback(Boolean success) {
                if (success) {
                    listener.onReadyForCapture();
                } else {
                    listener.onSetupFailed();
                }
            }
        });

        mPreviewScopeEntrance.get(surface).run();
    }

    @Override
    public void close(CloseCallback closeCallback) {
        // TODO Remove CloseCallback from the interface. It is always null.
        for (SafeCloseable listener : mCloseListeners) {
            listener.close();
        }
    }

    @Override
    public Size[] getSupportedPreviewSizes() {
        return mSupportedPreviewSizes;
    }

    @Override
    public float getFullSizeAspectRatio() {
        return mFullSizeAspectRatio;
    }

    @Override
    public Facing getDirection() {
        return mDirection;
    }

    @Override
    public float getMaxZoom() {
        return mMaxZoom;
    }

    @Override
    public void setZoom(float zoom) {
        mZoom.update(zoom);
    }

    @Override
    public Size pickPreviewSize(Size pictureSize, Context context) {
        return mPreviewSizeSelector.pickPreviewSize(pictureSize, context);
    }
}
