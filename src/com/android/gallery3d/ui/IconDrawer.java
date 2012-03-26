/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.gallery3d.R;
import com.android.gallery3d.data.MediaObject;

public abstract class IconDrawer extends SelectionDrawer {
    private static final String TAG = "IconDrawer";

    private final NinePatchTexture mFramePressed;
    private final NinePatchTexture mFrameSelected;
    private final NinePatchTexture mDarkStrip;
    private final NinePatchTexture mPanoramaBorder;
    private final Texture mVideoOverlay;
    private final Texture mVideoPlayIcon;

    public static class IconDimension {
        int x;
        int y;
        int width;
        int height;
    }

    public IconDrawer(Context context) {
        mVideoOverlay = new ResourceTexture(context, R.drawable.ic_video_thumb);
        mVideoPlayIcon = new ResourceTexture(context, R.drawable.ic_gallery_play);
        mPanoramaBorder = new NinePatchTexture(context, R.drawable.ic_pan_thumb);
        mFramePressed = new NinePatchTexture(context, R.drawable.grid_pressed);
        mFrameSelected = new NinePatchTexture(context, R.drawable.grid_selected);
        mDarkStrip = new NinePatchTexture(context, R.drawable.dark_strip);
    }

    @Override
    public void prepareDrawing() {
    }

    protected void drawMediaTypeOverlay(GLCanvas canvas, int mediaType,
            boolean isPanorama, int x, int y, int width, int height) {
        if (mediaType == MediaObject.MEDIA_TYPE_VIDEO) {
            drawVideoOverlay(canvas, x, y, width, height);
        }
        if (isPanorama) {
            drawPanoramaBorder(canvas, x, y, width, height);
        }
    }

    protected void drawVideoOverlay(GLCanvas canvas, int x, int y,
            int width, int height) {
        // Scale the video overlay to the height of the thumbnail and put it
        // on the left side.
        float scale = (float) height / mVideoOverlay.getHeight();
        int w = Math.round(scale * mVideoOverlay.getWidth());
        int h = Math.round(scale * mVideoOverlay.getHeight());
        mVideoOverlay.draw(canvas, x, y, w, h);

        int side = Math.min(width, height) / 6;
        mVideoPlayIcon.draw(canvas, -side / 2, -side / 2, side, side);
    }

    protected void drawPanoramaBorder(GLCanvas canvas, int x, int y,
            int width, int height) {
        float scale = (float) width / mPanoramaBorder.getWidth();
        int w = Math.round(scale * mPanoramaBorder.getWidth());
        int h = Math.round(scale * mPanoramaBorder.getHeight());
        // draw at the top
        mPanoramaBorder.draw(canvas, x, y, w, h);
        // draw at the bottom
        mPanoramaBorder.draw(canvas, x, y + width - h, w, h);
    }

    protected void drawLabelBackground(GLCanvas canvas, int width, int height,
            int drawLabelBackground) {
        int x = -width / 2;
        int y = (height + 1) / 2 - drawLabelBackground;
        drawFrame(canvas, mDarkStrip, x, y, width, drawLabelBackground);
    }

    protected void drawPressedFrame(GLCanvas canvas, int x, int y, int width,
            int height) {
        drawFrame(canvas, mFramePressed, x, y, width, height);
    }

    protected void drawSelectedFrame(GLCanvas canvas, int x, int y, int width,
            int height) {
        drawFrame(canvas, mFrameSelected, x, y, width, height);
    }

    @Override
    public void drawFocus(GLCanvas canvas, int width, int height) {
    }
}
