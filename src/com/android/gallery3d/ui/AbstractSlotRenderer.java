/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.ui;

import android.content.Context;
import android.graphics.Rect;

import com.android.gallery3d.R;

public abstract class AbstractSlotRenderer implements SlotView.SlotRenderer {

    private final ResourceTexture mVideoOverlay;
    private final ResourceTexture mVideoPlayIcon;
    private final NinePatchTexture mPanoramaBorder;
    private final NinePatchTexture mFramePressed;
    private final NinePatchTexture mFrameSelected;

    protected AbstractSlotRenderer(Context context) {
        mVideoOverlay = new ResourceTexture(context, R.drawable.ic_video_thumb);
        mVideoPlayIcon = new ResourceTexture(context, R.drawable.ic_gallery_play);
        mPanoramaBorder = new NinePatchTexture(context, R.drawable.ic_pan_thumb);
        mFramePressed = new NinePatchTexture(context, R.drawable.grid_pressed);
        mFrameSelected = new NinePatchTexture(context, R.drawable.grid_selected);
    }

    protected void drawContent(GLCanvas canvas,
            Texture content, int width, int height, int rotation) {
        canvas.save(GLCanvas.SAVE_FLAG_MATRIX);

        if (rotation != 0) {
            canvas.rotate(rotation, 0, 0, 1);
            if (((rotation % 90) & 1) != 0) {
                int temp = height;
                height = width;
                width = height;
            }
        }

        // Fit the content into the box
        float scale = Math.min(
                (float) width / content.getWidth(),
                (float) height / content.getHeight());
        canvas.scale(scale, scale, 1);
        content.draw(canvas, 0, 0);

        canvas.restore();
    }

    protected void drawVideoOverlay(GLCanvas canvas, int width, int height) {
        // Scale the video overlay to the height of the thumbnail and put it
        // on the left side.
        ResourceTexture v = mVideoOverlay;
        float scale = (float) height / v.getHeight();
        int w = Math.round(scale * v.getWidth());
        int h = Math.round(scale * v.getHeight());
        v.draw(canvas, 0, 0, w, h);

        int s = Math.min(width, height) / 6;
        mVideoPlayIcon.draw(canvas, (width - s) / 2, (height - s) / 2, s, s);
    }

    protected void drawPanoramaBorder(GLCanvas canvas, int width, int height) {
        float scale = (float) width / mPanoramaBorder.getWidth();
        int w = Math.round(scale * mPanoramaBorder.getWidth());
        int h = Math.round(scale * mPanoramaBorder.getHeight());
        // draw at the top
        mPanoramaBorder.draw(canvas, 0, 0, w, h);
        // draw at the bottom
        mPanoramaBorder.draw(canvas, 0, height - h, w, h);
    }

    protected void drawPressedFrame(GLCanvas canvas, int width, int height) {
        drawFrame(canvas, mFramePressed, 0, 0, width, height);
    }

    protected void drawSelectedFrame(GLCanvas canvas, int width, int height) {
        drawFrame(canvas, mFrameSelected, 0, 0, width, height);
    }

    protected static void drawFrame(GLCanvas canvas, NinePatchTexture frame,
            int x, int y, int width, int height) {
        Rect p = frame.getPaddings();
        frame.draw(canvas, x - p.left, y - p.top, width + p.left + p.right,
                 height + p.top + p.bottom);
    }
}
