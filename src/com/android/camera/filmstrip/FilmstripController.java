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

/**
 * An interface which defines the controller of filmstrip.
 * A filmstrip has 4 states:
 * <ol>
 *     <li>Filmstrip</li>
 *     Images are scaled down and the user can navigate quickly by swiping.
 *     Action bar and controls are shown.
 *     <li>Full-screen</li>
 *     One single image occupies the whole screen. Action bar and controls are
 *     hidden.
 *     <li>Zoom view</li>
 *     Zoom in to view the details of one single image.
 * </ol>
 * Only the following state transitions can happen:
 * <ol>
 * <li>filmstrip --> full-screen</li>
 * <li>full-screen --> filmstrip</li>
 * <li>full-screen --> full-screen with UIs</li>
 * <li>full-screen --> zoom view</li>
 * <li>zoom view --> full-screen</li>
 * </ol>
 *
 * Upon entering/leaving each of the states, the
 * {@link com.android.camera.filmstrip.FilmstripController.FilmstripListener} will be notified.
 */
public interface FilmstripController {

    /**
     * Sets the listener for filmstrip events.
     *
     * @param l
     */
    public void setListener(FilmstripListener l);

    /**
     * Sets the gap width between each images on the filmstrip.
     *
     * @param imageGap The gap width in pixels.
     */
    public void setImageGap(int imageGap);

    /**
     * @return The ID of the current item, or -1.
     */
    public int getCurrentId();

    /**
     * Sets the {@link DataAdapter}.
     */
    public void setDataAdapter(DataAdapter adapter);

    /**
     * Returns whether the filmstrip is in filmstrip mode.
     */
    public boolean inFilmstrip();

    /**
     * @return Whether the filmstrip is in full-screen mode.
     */
    public boolean inFullScreen();

    /**
     * @return Whether the current view in filmstrip is camera preview.
     */
    public boolean isCameraPreview();

    /**
     * @return Whether the filmstrip is in full-screen camrea preview.
     */
    public boolean inCameraFullscreen();

    /**
     * @return Whether the filmstrip is in scaling animation.
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
     * Scrolls the filmstrip horizontally to the center of the previous item.
     *
     * @return Whether the previous item exists.
     */
    public boolean goToPreviousItem();

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
    public void goToFilmstrip();

    /**
     * Scales up to full-screen mode.
     */
    public void goToFullScreen();

    /**
     * An interface which defines the FilmStripView UI action listener.
     */
    interface FilmstripListener {

        /**
         * Callback when the data item is promoted. A data is promoted if the user
         * swipe up a data vertically.
         *
         * @param dataID The ID of the promoted data.
         */
        public void onFocusedDataPromoted(int dataID);

        /**
         * Callback when the data item is demoted. A data is promoted if the user
         * swipe down a data vertically.
         *
         * @param dataID The ID of the demoted data.
         */
        public void onFocusedDataDemoted(int dataID);

        /**
         * Callback when the data item is long-pressed.
         *
         * @param dataID The ID of the long-pressed data.
         */
        public void onFocusedDataLongPressed(int dataID);

        /**
         * Called when all the data has been reloaded.
         */
        public void onDataReloaded();

        /**
         * Called when data is updated.
         *
         * @param dataId The ID of the updated data.
         */
        public void onDataUpdated(int dataId);

        /**
         * The callback when the item enters augmented full-screen state.
         *
         * @param dataId The ID of the current focused image data.
         */
        public void onEnterFullScreenUiShown(int dataId);

        /**
         * The callback when the item leaves augmented full-screen.
         *
         * @param dataId The ID of the current focused image data.
         */
        public void onLeaveFullScreenUiShown(int dataId);

        /**
         * The callback when the filmstrip enters no UI full-screen.
         *
         * @param dataId The ID of the current focused image data.
         */
        public void onEnterFullScreenUiHidden(int dataId);

        /**
         * The callback when the filmstrip leaves no UI full-screen.
         *
         * @param dataId The ID of the current focused image data.
         */
        public void onLeaveFullScreenUiHidden(int dataId);

        /**
         * The callback when the item enters filmstrip.
         *
         * @param dataId The ID of the current focused image data.
         */
        public void onEnterFilmstrip(int dataId);

        /**
         * The callback when the item leaves filmstrip.
         *
         * @param dataId The ID of the current focused image data.
         */
        public void onLeaveFilmstrip(int dataId);

        /**
         * The callback when the item enters zoom view.
         *
         * @param dataID
         */
        public void onEnterZoomView(int dataID);

        /**
         * Called when current item or zoom level has changed.
         *
         * @param dataId The ID of the current focused image data.
         * @param zoom Zoom level.
         */
        public void onZoomAtIndexChanged(int dataId, float zoom);

        /**
         * The callback when the data focus changed.
         *
         * @param prevDataId The ID of the previously focused data or {@code -1} if
         *                   none.
         * @param newDataId The ID of the focused data of {@code -1} if none.
         */
        public void onDataFocusChanged(int prevDataId, int newDataId);

        /**
         * The callback when we scroll.
         *
         * @param firstVisiblePosition The position of the first rendered item
         *                             (may be slightly offscreen depending on
         *                             the orientation of the device).
         * @param visibleItemCount The total number of rendered items.
         * @param totalItemCount The total number of items in the filmstrip.
         */
        public void onScroll(int firstVisiblePosition, int visibleItemCount, int totalItemCount);
    }
}
