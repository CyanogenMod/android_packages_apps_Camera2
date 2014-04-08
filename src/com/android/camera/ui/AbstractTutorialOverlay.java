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

package com.android.camera.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.camera2.R;

/**
 * Abstract class that is the foundation for a tutorial overlay modules can show
 * to explain their functionality.
 */
public abstract class AbstractTutorialOverlay {
    /**
     * Use this interface to get informed when the tutorial was closed.
     */
    public interface CloseListener {
        /**
         * Called when the tutorial is being closed.
         */
        public void onTutorialClosed();
    }

    private final int mLayoutResId;
    protected final CloseListener mCloseListener;
    private ViewGroup mPlaceholderWrapper;

    /**
     * Create a new overlay.
     *
     * @param layoutResId the resource ID of the tutorial layout.
     * @param inflater The inflater used to inflate the tutorial view.
     * @param closeListener Called when the user has seen the whole tutorial and
     *            closed it.
     */
    public AbstractTutorialOverlay(int layoutResId, CloseListener closeListener) {
        mLayoutResId = layoutResId;
        mCloseListener = closeListener;
    }

    /**
     * Shows the tutorial on the screen.
     *
     * @param placeHolderWrapper the view group in which the tutorial will be
     *            embedded.
     */
    public final void show(ViewGroup placeHolderWrapper, LayoutInflater inflater) {
        mPlaceholderWrapper = placeHolderWrapper;
        if (mPlaceholderWrapper != null) {
            mPlaceholderWrapper.removeAllViews();
        }

        mPlaceholderWrapper.setVisibility(View.VISIBLE);
        ViewGroup placeHolder = (ViewGroup) inflater.inflate(R.layout.tutorials_placeholder,
                mPlaceholderWrapper).findViewById(R.id.tutorials_placeholder);
        onInflated(inflater.inflate(mLayoutResId, placeHolder));
    }

    /**
     * Called when the view was inflated.
     *
     * @param view the inflated tutorial view.
     */
    protected abstract void onInflated(View view);

    /**
     * Removes all views from the place holder wrapper (including the place
     * holder itself) and sets the visibility of the wrapper to GONE, so that it
     * doesn't catch any touch events.
     */
    public void removeOverlayAndHideWrapper() {
        if (mPlaceholderWrapper != null) {
            mPlaceholderWrapper.removeAllViews();
        }
        mPlaceholderWrapper.setVisibility(View.GONE);
    }

    /**
     * Removes the UI and calls the close listener.
     */
    public void close() {
        removeOverlayAndHideWrapper();
        if (mCloseListener != null) {
            mCloseListener.onTutorialClosed();
        }
    }
}
