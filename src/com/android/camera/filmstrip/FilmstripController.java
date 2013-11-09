/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.camera.filmstrip;

import com.android.camera.util.PhotoSphereHelper;

/**
 * An interface which defines the controller of filmstrip.
 */
public interface FilmstripController {
    public void setListener(FilmstripListener l);

    public void setViewGap(int viewGap);

    /**
     * Sets the helper that's to be used to open photo sphere panoramas.
     */
    public void setPanoramaViewHelper(PhotoSphereHelper.PanoramaViewHelper helper);

    /**
     * @return The ID of the current item, or -1.
     */
    public int getCurrentId();

    public void setDataAdapter(FilmstripDataAdapter adapter);

    public boolean inFilmstrip();

    public boolean inFullScreen();

    public boolean isCameraPreview();

    public boolean inCameraFullscreen();

    public boolean isScaling();

    public void scroll(float deltaX);

    public void fling(float velocity);

    public void flingInsideZoomView(float velocityX, float velocityY);

    public void scrollToPosition(int position, int duration, boolean interruptible);

    public boolean goToNextItem();

    public boolean stopScrolling(boolean forced);

    public boolean isScrolling();

    public void goToFirstItem();

    public void goToFilmStrip();

    public void goToFullScreen();
}
