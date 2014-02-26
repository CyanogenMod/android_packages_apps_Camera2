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

import com.android.camera.widget.FilmstripLayout;

/**
 * The filmstrip panel holding the filmstrip and other controls/widgets.
 */
public interface FilmstripContentPanel {
    /**
     * An listener interface extending {@link
     * com.android.camera.filmstrip.FilmstripController.FilmstripListener} defining extra callbacks
     * for filmstrip being shown and hidden.
     */
    interface Listener extends FilmstripController.FilmstripListener {

        /**
         * Callback on a swipe out of filmstrip.
         */
        public void onSwipeOut();

        /**
         * Callback on a swiping out begins.
         */
        public void onSwipeOutBegin();

        /**
         * Callback when the filmstrip becomes invisible or gone.
         */
        public void onFilmstripHidden();

        /**
         * Callback when the filmstrip is shown in full-screen.
         */
        public void onFilmstripShown();
    }

    /** Sets the listener. */
    void setFilmstripListener(FilmstripLayout.Listener listener);

    /**
     * Hides this panel with animation.
     *
     * @return {@code false} if already hidden.
     */
    boolean animateHide();

    /** Hides this panel */
    void hide();

    /** Shows this panel */
    void show();

    /**
     * Called when the back key is pressed.
     *
     * @return Whether the UI responded to the key event.
     */
    boolean onBackPressed();
}
