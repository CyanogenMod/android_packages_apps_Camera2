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

import android.content.Context;
import android.view.Surface;

import com.android.camera.async.ConcurrentState;
import com.android.camera.async.Listenable;
import com.android.camera.async.SafeCloseable;
import com.android.camera.async.Updatable;
import com.android.camera.one.OneCamera;
import com.android.camera.one.v2.AutoFocusHelper;
import com.android.camera.one.v2.autofocus.ManualAutoFocus;
import com.android.camera.one.v2.photo.PictureTaker;
import com.android.camera.session.CaptureSession;
import com.android.camera.util.Callback;
import com.android.camera.util.Size;

import java.util.concurrent.Executor;

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
class GenericOneCameraImpl implements OneCamera {

    private final SafeCloseable mCloseListener;
    private final PictureTaker mPictureTaker;
    private final ManualAutoFocus mManualAutoFocus;
    private final Executor mMainExecutor;
    private final Listenable<Integer> mAFStateListenable;
    private final Listenable<FocusState> mFocusStateListenable;
    private final Listenable<Boolean> mReadyStateListenable;
    private final float mMaxZoom;
    private final Updatable<Float> mZoom;
    private final Size[] mSupportedPreviewSizes;
    private final float mFullSizeAspectRatio;
    private final Facing mDirection;
    private final PreviewSizeSelector mPreviewSizeSelector;
    private final Listenable<Boolean> mPreviewStartSuccessListenable;
    private final PreviewStarter mPreviewStarter;

    public GenericOneCameraImpl(SafeCloseable closeListener, PictureTaker pictureTaker,
            ManualAutoFocus manualAutoFocus, Executor mainExecutor,
            Listenable<Integer> afStateProvider, Listenable<FocusState> focusStateProvider,
            Listenable<Boolean> readyStateListenable, float maxZoom, Updatable<Float> zoom,
            Size[] supportedPreviewSizes, float fullSizeAspectRatio, Facing direction,
            PreviewSizeSelector previewSizeSelector,
            Listenable<Boolean> previewStartSuccessListenable,
            PreviewStarter previewStarter) {
        mPreviewStartSuccessListenable = previewStartSuccessListenable;
        mCloseListener = closeListener;
        mMainExecutor = mainExecutor;
        mMaxZoom = maxZoom;
        mSupportedPreviewSizes = supportedPreviewSizes;
        mFullSizeAspectRatio = fullSizeAspectRatio;
        mDirection = direction;
        mPreviewSizeSelector = previewSizeSelector;
        mPictureTaker = pictureTaker;
        mManualAutoFocus = manualAutoFocus;
        mAFStateListenable = afStateProvider;
        mFocusStateListenable = focusStateProvider;
        mReadyStateListenable = readyStateListenable;
        mZoom = zoom;
        mPreviewStarter = previewStarter;
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
    public void setFocusDistanceListener(final FocusDistanceListener listener) {
        mFocusStateListenable.setCallback(new Callback<FocusState>() {
            @Override
            public void onCallback(FocusState focusState) {
                listener.onFocusDistance(focusState.diopter, focusState.isActive);
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
        // Listener must be run on the main thread, so wrap with concurrent
        // state to create a thread-safe callback to forward to the preview
        // starter.
        ConcurrentState<Boolean> previewStartSuccess = new ConcurrentState<>();
        previewStartSuccess.addCallback(new Callback<Boolean>() {
            @Override
            public void onCallback(Boolean success) {
                if (success) {
                    listener.onReadyForCapture();
                } else {
                    listener.onSetupFailed();
                }
            }
        }, mMainExecutor);

        mPreviewStarter.startPreview(surface, previewStartSuccess);
    }

    @Override
    public void close(CloseCallback closeCallback) {
        // TODO Remove CloseCallback from the interface. It is always null.
        mCloseListener.close();
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
