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

import com.android.camera.async.FilteredCallback;
import com.android.camera.async.Listenable;
import com.android.camera.async.SafeCloseable;
import com.android.camera.async.Updatable;
import com.android.camera.one.OneCamera;
import com.android.camera.one.PreviewSizeSelector;
import com.android.camera.one.v2.AutoFocusHelper;
import com.android.camera.one.v2.autofocus.ManualAutoFocus;
import com.android.camera.one.v2.photo.PictureTaker;
import com.android.camera.session.CaptureSession;
import com.android.camera.ui.motion.LinearScale;
import com.android.camera.util.Callback;
import com.android.camera.util.Size;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;

import javax.annotation.Nonnull;

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
    private final LinearScale mLensRange;
    private final Executor mMainExecutor;
    private final Listenable<Integer> mAFStateListenable;
    private final Listenable<FocusState> mFocusStateListenable;
    private final Listenable<Boolean> mReadyStateListenable;
    private final float mMaxZoom;
    private final Updatable<Float> mZoom;
    private final Facing mDirection;
    private final PreviewSizeSelector mPreviewSizeSelector;
    private final PreviewStarter mPreviewStarter;

    public GenericOneCameraImpl(SafeCloseable closeListener, PictureTaker pictureTaker,
            ManualAutoFocus manualAutoFocus, LinearScale lensRange, Executor mainExecutor,
            Listenable<Integer> afStateProvider, Listenable<FocusState> focusStateProvider,
            Listenable<Boolean> readyStateListenable, float maxZoom, Updatable<Float> zoom,
            Facing direction, PreviewSizeSelector previewSizeSelector,
            PreviewStarter previewStarter) {
        mCloseListener = closeListener;
        mMainExecutor = mainExecutor;
        mMaxZoom = maxZoom;
        mDirection = direction;
        mPreviewSizeSelector = previewSizeSelector;
        mPictureTaker = pictureTaker;
        mManualAutoFocus = manualAutoFocus;
        mLensRange = lensRange;
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
    public void setFocusStateListener(final FocusStateListener listener) {
        mAFStateListenable.setCallback(new Callback<Integer>() {
            @Override
            public void onCallback(@Nonnull Integer afState) {
                // TODO delete frameNumber from FocusStateListener callback. It
                // is optional and never actually used.
                long frameNumber = -1;
                if(listener !=null) {
                    listener.onFocusStatusUpdate(AutoFocusHelper.stateFromCamera2State(afState),
                            frameNumber);
                }
            }
        });
    }

    @Override
    public void setFocusDistanceListener(final FocusDistanceListener listener) {
        if (listener == null) {
            mFocusStateListenable.clear();
            return;
        }
        mFocusStateListenable.setCallback(new Callback<FocusState>() {
            @Override
            public void onCallback(@Nonnull FocusState focusState) {
                if (focusState.isActive) {
                    listener.onFocusDistance(focusState.lensDistance, mLensRange);
                }
            }
        });
    }

    @Override
    public void setReadyStateChangedListener(final ReadyStateChangedListener listener) {
        if (listener == null) {
            mReadyStateListenable.clear();
            return;
        }

        Callback<Boolean> readyStateCallback = new Callback<Boolean>() {
            @Override
            public void onCallback(@Nonnull Boolean result) {
                listener.onReadyStateChanged(result);
            }
        };

        mReadyStateListenable.setCallback(new FilteredCallback<>(readyStateCallback));
    }

    @Override
    public void startPreview(Surface surface, final CaptureReadyCallback listener) {
        ListenableFuture<Void> result = mPreviewStarter.startPreview(surface);
        Futures.addCallback(result, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nonnull Void aVoid) {
                listener.onReadyForCapture();
            }

            @Override
            public void onFailure(@Nonnull Throwable throwable) {
                listener.onSetupFailed();
            }
        });
    }

    @Override
    public void close() {
        mCloseListener.close();
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
        return mPreviewSizeSelector.pickPreviewSize(pictureSize);
    }
}
