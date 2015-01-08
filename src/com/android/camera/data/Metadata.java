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

package com.android.camera.data;

/**
 * Settable metadata class that is deferred loaded in other ways that
 * may be slow or non-standard.
 *
 * TODO: Replace this with implementation specific values and code.
 */
public class Metadata {
    // TODO: Consider replacing these with orientation manager values
    // (Or having a central place to deal with the standard rotations)
    private static final String ROTATE_90 = "90";
    private static final String ROTATE_270 = "270";

    private boolean mIsLoaded = false;

    private String mVideoOrientation = "";
    private int mVideoWidth = -1;
    private int mVideoHeight = -1;

    private boolean mIsPanorama = false;
    private boolean mIsPanorama360 = false;
    private boolean mUsePanoramaViewer = false;

    private boolean mHasRgbzData = false;

    public boolean isLoaded() {
        return mIsLoaded;
    }

    public void setLoaded(boolean isLoaded) {
        mIsLoaded = isLoaded;
    }

    public String getVideoOrientation() {
        return mVideoOrientation;
    }

    public void setVideoOrientation(String videoOrientation) {
        mVideoOrientation = videoOrientation;
    }

    public boolean isVideoRotated() {
        return ROTATE_90.equals(mVideoOrientation) || ROTATE_270.equals(mVideoOrientation);
    }

    public int getVideoWidth() {
        return mVideoWidth;
    }

    public void setVideoWidth(int videoWidth) {
        mVideoWidth = videoWidth;
    }

    public int getVideoHeight() {
        return mVideoHeight;
    }

    public void setVideoHeight(int videoHeight) {
        mVideoHeight = videoHeight;
    }

    public boolean isPanorama() {
        return mIsPanorama;
    }

    public void setPanorama(boolean isPanorama) {
        mIsPanorama = isPanorama;
    }

    public boolean isPanorama360() {
        return mIsPanorama360;
    }

    public void setPanorama360(boolean isPanorama360) {
        mIsPanorama360 = isPanorama360;
    }

    public boolean isUsePanoramaViewer() {
        return mUsePanoramaViewer;
    }

    public void setUsePanoramaViewer(boolean usePanoramaViewer) {
        mUsePanoramaViewer = usePanoramaViewer;
    }

    public boolean isHasRgbzData() {
        return mHasRgbzData;
    }

    public void setHasRgbzData(boolean hasRgbzData) {
        mHasRgbzData = hasRgbzData;
    }
}