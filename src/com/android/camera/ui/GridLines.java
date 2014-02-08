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

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.camera2.R;

/**
 * GridLines is a view which directly overlays the preview and draws
 * evenly spaced grid lines.
 */
public class GridLines extends View
    implements PreviewStatusListener.PreviewAreaSizeChangedListener {

    Paint mPaint = new Paint();
    float mPreviewWidth = 0f;
    float mPreviewHeight = 0f;

    public GridLines(Context context, AttributeSet attrs) {
        super(context, attrs);
        int strokeWidth = getResources().getDimensionPixelSize(R.dimen.grid_line_width);
        mPaint.setStrokeWidth(strokeWidth);
        mPaint.setColor(getResources().getColor(R.color.grid_line));
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (params.gravity != Gravity.CENTER_HORIZONTAL) {
                params.gravity = Gravity.CENTER_HORIZONTAL;
            }
        } else {
            if (params.gravity != Gravity.CENTER_VERTICAL) {
                params.gravity = Gravity.CENTER_VERTICAL;
            }
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mPreviewWidth > 0 && mPreviewHeight > 0) {
            float thirdWidth = mPreviewWidth / 3;
            float thirdHeight = mPreviewHeight / 3;
            // draw the first vertical line
            canvas.drawLine(thirdWidth, 0, thirdWidth, mPreviewHeight, mPaint);
            // draw the second vertical line
            canvas.drawLine(thirdWidth*2, 0, thirdWidth*2, mPreviewHeight, mPaint);

            // draw the first horizontal line
            canvas.drawLine(0, thirdHeight, mPreviewWidth, thirdHeight, mPaint);
            // draw the second horizontal line
            canvas.drawLine(0, thirdHeight*2, mPreviewWidth, thirdHeight*2, mPaint);
        }
    }

    @Override
    public void onPreviewAreaSizeChanged(RectF previewArea) {
        mPreviewWidth = previewArea.width();
        mPreviewHeight = previewArea.height();
        matchPreviewDimensions();
    }

    /**
     * Reset the height and width of this view to match cached the height
     * and width of the preview.
     */
   private void matchPreviewDimensions() {
        if (mPreviewWidth > 0 && mPreviewHeight > 0) {
            ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) getLayoutParams();
            params.width = (int) mPreviewWidth;
            params.height = (int) mPreviewHeight;
            setLayoutParams(params);
        }
    }
}
