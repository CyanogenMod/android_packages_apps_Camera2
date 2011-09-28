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

import com.android.gallery3d.R;
import com.android.gallery3d.data.MediaObject;

import android.content.Context;

public abstract class IconDrawer extends SelectionDrawer {
    private static final String TAG = "IconDrawer";
    private static final int LABEL_BACKGROUND_COLOR = 0x99000000;  // 60% black

    private final ResourceTexture mLocalSetIcon;
    private final ResourceTexture mCameraIcon;
    private final ResourceTexture mPicasaIcon;
    private final ResourceTexture mMtpIcon;
    private final NinePatchTexture mFramePressed;
    private final NinePatchTexture mFrameSelected;
    private final NinePatchTexture mDarkStrip;
    private final NinePatchTexture mPanoramaBorder;
    private final Texture mVideoOverlay;
    private final Texture mVideoPlayIcon;
    private final int mIconSize;

    public static class IconDimension {
        int x;
        int y;
        int width;
        int height;
    }

    public IconDrawer(Context context) {
        mLocalSetIcon = new ResourceTexture(context, R.drawable.frame_overlay_gallery_folder);
        mCameraIcon = new ResourceTexture(context, R.drawable.frame_overlay_gallery_camera);
        mPicasaIcon = new ResourceTexture(context, R.drawable.frame_overlay_gallery_picasa);
        mMtpIcon = new ResourceTexture(context, R.drawable.frame_overlay_gallery_ptp);
        mVideoOverlay = new ResourceTexture(context, R.drawable.ic_video_thumb);
        mVideoPlayIcon = new ResourceTexture(context, R.drawable.ic_gallery_play);
        mPanoramaBorder = new NinePatchTexture(context, R.drawable.ic_pan_thumb);
        mFramePressed = new NinePatchTexture(context, R.drawable.grid_pressed);
        mFrameSelected = new NinePatchTexture(context, R.drawable.grid_selected);
        mDarkStrip = new NinePatchTexture(context, R.drawable.dark_strip);
        mIconSize = context.getResources().getDimensionPixelSize(
                R.dimen.albumset_icon_size);
    }

    @Override
    public void prepareDrawing() {
    }

    protected IconDimension drawIcon(GLCanvas canvas, int width, int height,
            int dataSourceType) {
        ResourceTexture icon = getIcon(dataSourceType);

        if (icon != null) {
            IconDimension id = getIconDimension(icon, width, height);
            icon.draw(canvas, id.x, id.y, id.width, id.height);
            return id;
        }
        return null;
    }

    protected ResourceTexture getIcon(int dataSourceType) {
        ResourceTexture icon = null;
        switch (dataSourceType) {
            case DATASOURCE_TYPE_LOCAL:
                icon = mLocalSetIcon;
                break;
            case DATASOURCE_TYPE_PICASA:
                icon = mPicasaIcon;
                break;
            case DATASOURCE_TYPE_CAMERA:
                icon = mCameraIcon;
                break;
            case DATASOURCE_TYPE_MTP:
                icon = mMtpIcon;
                break;
            default:
                break;
        }

        return icon;
    }

    protected IconDimension getIconDimension(ResourceTexture icon, int width,
            int height) {
        IconDimension id = new IconDimension();
        float scale = (float) mIconSize / icon.getWidth();
        id.width = Math.round(scale * icon.getWidth());
        id.height = Math.round(scale * icon.getHeight());
        id.x = -width / 2;
        id.y = (height + 1) / 2 - id.height;
        return id;
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
