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

    /**
     * Sets the {@link com.android.camera.filmstrip.FilmstripDataAdapter}.
     */
    public void setDataAdapter(FilmstripDataAdapter adapter);

    /**
     * Returns whether the filmstrip is in filmstrip view.
     */
    public boolean inFilmstrip();

    /**
     * Returns whether the filmstrip is in full-screen view.
     */
    public boolean inFullScreen();

    /**
     * Returns whether the current view in filmstrip is camera preview.
     */
    public boolean isCameraPreview();

    /**
     * Returns whether the filmstrip is in full-screen camrea preview.
     */
    public boolean inCameraFullscreen();

    /**
     * Returns whether the filmstrip is in scaling animation.
     * @return
     */
    public boolean isScaling();

    /**
     * Scrolls the filmstrip horizontally.
     *
     * @param deltaX The distance in pixel The filmstrip will be scrolled by.
     */
    public void scroll(float deltaX);

    /**
     * Flings the filmstrip horizontally.
     *
     * @param velocity
     */
    public void fling(float velocity);

    /**
     * Scrolls the filmstrip horizontally to a specific position.
     *
     * @param position The final position.
     * @param duration The duration of this scrolling.
     * @param interruptible Whether this scrolling can be interrupted.
     */
    public void scrollToPosition(int position, int duration, boolean interruptible);

    /**
     * Scrolls the filmstrip horizontally to the center of the next item.
     *
     * @return Whether the next item exists.
     */
    public boolean goToNextItem();

    /**
     * Stops the scrolling.
     *
     * @param forced Forces to stop even if the scrolling can not be
     *               interrupted.
     * @return Whether the scrolling is stopped.
     */
    public boolean stopScrolling(boolean forced);

    /**
     * Returns whether the filmstrip is scrolling.
     * @return
     */
    public boolean isScrolling();

    /**
     * Puts the first item in the center in full-screen.
     */
    public void goToFirstItem();

    /**
     * Scales down to filmstrip mode. If the current item is camera preview,
     * scrolls to the next item.
     */
    public void goToFilmStrip();

    /**
     * Scales up to full-screen mode.
     */
    public void goToFullScreen();
}
