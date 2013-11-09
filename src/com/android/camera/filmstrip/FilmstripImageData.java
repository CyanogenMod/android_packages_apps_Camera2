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

import android.content.Context;
import android.net.Uri;

import com.android.camera.util.PhotoSphereHelper;

/**
 * Common interface for all images in the filmstrip.
 */
public interface FilmstripImageData {

    /**
     * Interface that is used to tell the caller whether an image is a photo
     * sphere.
     */
    public static interface PanoramaSupportCallback {
        /**
         * Called then photo sphere info has been loaded.
         *
         * @param isPanorama whether the image is a valid photo sphere
         * @param isPanorama360 whether the photo sphere is a full 360
         *            degree horizontal panorama
         */
        void panoramaInfoAvailable(boolean isPanorama, boolean isPanorama360);
    }

    // View types.
    public static final int VIEW_TYPE_NONE = 0;
    public static final int VIEW_TYPE_STICKY = 1;
    public static final int VIEW_TYPE_REMOVABLE = 2;

    // Actions allowed to be performed on the image data.
    // The actions are defined bit-wise so we can use bit operations like
    // | and &.
    public static final int ACTION_NONE = 0;
    public static final int ACTION_PROMOTE = 1;
    public static final int ACTION_DEMOTE = (1 << 1);
    /**
     * For image data that supports zoom, it should also provide a valid
     * content uri.
     */
    public static final int ACTION_ZOOM = (1 << 2);

    /**
     * SIZE_FULL can be returned by {@link FilmstripImageData#getWidth()} and
     * {@link FilmstripImageData#getHeight()}. When SIZE_FULL is returned for
     * width/height, it means the the width or height will be disregarded
     * when deciding the view size of this ImageData, just use full screen
     * size.
     */
    public static final int SIZE_FULL = -2;

    /**
     * Returns the width of the image before orientation applied.
     * The final layout of the view returned by
     * {@link FilmstripDataAdapter#getView(android.app.Activity, int)} will
     * preserve the aspect ratio of
     * {@link FilmstripImageData#getWidth()} and
     * {@link FilmstripImageData#getHeight()}.
     */
    public int getWidth();

    /**
     * Returns the height of the image before orientation applied.
     * The final layout of the view returned by
     * {@link FilmstripDataAdapter#getView(android.app.Activity, int)} will
     * preserve the aspect ratio of
     * {@link FilmstripImageData#getWidth()} and
     * {@link FilmstripImageData#getHeight()}.
     */
    public int getHeight();

    /**
     * Returns the orientation of the image.
     */
    public int getOrientation();

    /** Returns the image data type */
    public int getViewType();

    /**
     * Returns the coordinates of this item.
     *
     * @return A 2-element array containing {latitude, longitude}, or null,
     *         if no position is known for this item.
     */
    public double[] getLatLong();

    /**
     * Checks if the UI action is supported.
     *
     * @param action The UI actions to check.
     * @return {@code false} if at least one of the actions is not
     *         supported. {@code true} otherwise.
     */
    public boolean isUIActionSupported(int action);

    /**
     * Gives the data a hint when its view is going to be displayed.
     * {@code FilmStripView} should always call this function before showing
     * its corresponding view every time.
     */
    public void prepare();

    /**
     * Gives the data a hint when its view is going to be removed from the
     * view hierarchy. {@code FilmStripView} should always call this
     * function after its corresponding view is removed from the view
     * hierarchy.
     */
    public void recycle();

    /**
     * Asynchronously checks if the image is a photo sphere. Notified the
     * callback when the results are available.
     */
    public void isPhotoSphere(Context context, PanoramaSupportCallback callback);

    /**
     * If the item is a valid photo sphere panorama, this method will launch
     * the viewer.
     */
    public void viewPhotoSphere(PhotoSphereHelper.PanoramaViewHelper helper);

    /** Whether this item is a photo. */
    public boolean isPhoto();

    /**
     * Returns the content URI of this data item.
     *
     * @return {@code Uri.EMPTY} if not valid.
     */
    public Uri getContentUri();
}
