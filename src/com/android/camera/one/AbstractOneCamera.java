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

package com.android.camera.one;

/**
 * A common abstract {@link OneCamera} implementation that contains some utility
 * functions and plumbing we don't want every sub-class of {@link OneCamera} to
 * duplicate. Hence all {@link OneCamera} implementations should sub-class this
 * class instead.
 */
public abstract class AbstractOneCamera implements OneCamera {
    protected CameraErrorListener mCameraErrorListener;
    protected FocusStateListener mFocusStateListener;

    @Override
    public final void setCameraErrorListener(CameraErrorListener listener) {
        mCameraErrorListener = listener;
    }

    @Override
    public final void setFocusStateListener(FocusStateListener listener) {
        mFocusStateListener = listener;
    }
}
