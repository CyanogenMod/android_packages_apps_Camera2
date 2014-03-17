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
import android.view.View;

/**
 * Common interface for all images in the filmstrip.
 */
public interface ImageData {

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
     * SIZE_FULL can be returned by {@link ImageData#getWidth()} and
     * {@link ImageData#getHeight()}. When SIZE_FULL is returned for
     * width/height, it means the the width or height will be disregarded
     * when deciding the view size of this ImageData, just use full screen
     * size.
     */
    public static final int SIZE_FULL = -2;

    /**
     * Returns the width in pixels of the image before orientation applied.
     * The final layout of the view returned by
     * {@link DataAdapter#getView(Context, int)} will
     * preserve the aspect ratio of
     * {@link ImageData#getWidth()} and
     * {@link ImageData#getHeight()}.
     */
    public int getWidth();

    /**
     * Returns the height in pixels of the image before orientation applied.
     * The final layout of the view returned by
     * {@link DataAdapter#getView(Context, int)} will
     * preserve the aspect ratio of
     * {@link ImageData#getWidth()} and
     * {@link ImageData#getHeight()}.
     */
    public int getHeight();

    /**
     * Returns the rotation of the image in degrees clockwise. The valid values
     * are 0, 90, 180, and 270.
     */
    public int getRotation();

    /** Returns the image data type. The current valid values are
     * {@code VIEW_TYPE_*}.
     */
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
     * @return Whether at all of the actions set in {@code action} are
     * supported.
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
    public void recycle(View view);

    /**
     * @return The URI of this data. Must be a unique one and not null.
     */
    public Uri getUri();
}
