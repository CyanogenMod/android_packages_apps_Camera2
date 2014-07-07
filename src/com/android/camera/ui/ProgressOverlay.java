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

package com.android.camera.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

/**
 * ProgressOverlay is a view that sits under the PreviewOverlay.
 * It does not respond to touch events, and only serves to show a
 * centered progress bar.
 */
public class ProgressOverlay extends View {
    private final ProgressRenderer mProgressRenderer;
    private int mCenterX;
    private int mCenterY;

    /**
     * Intialize a new ProgressOverlay with a ProgressRenderer.
     */
    public ProgressOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        mProgressRenderer = new ProgressRenderer(context, this);
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            mCenterX = (right - left) / 2;
            mCenterY = (bottom - top) / 2;
        }
    }

    /**
     * Reposition the view within a given set of bounds, defined by a
     * {@link android.graphics.RectF}.
     */
    public void setBounds(RectF area) {
        if (area.width() > 0 && area.height() > 0) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
            params.width = (int) area.width();
            params.height= (int) area.height();
            params.setMargins((int) area.left, (int) area.top, 0, 0);
            setLayoutParams(params);
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        mProgressRenderer.onDraw(canvas, mCenterX, mCenterY);
    }

    /**
     * Set the progress state as a percent from 0-100.
     */
    public void setProgress(int percent) {
        mProgressRenderer.setProgress(percent);
    }
}