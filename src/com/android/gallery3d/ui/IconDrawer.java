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
    private final String TAG = "IconDrawer";
    private final ResourceTexture mLocalSetIcon;
    private final ResourceTexture mCameraIcon;
    private final ResourceTexture mPicasaIcon;
    private final ResourceTexture mMtpIcon;
    private final Texture mVideoOverlay;
    private final Texture mVideoPlayIcon;

    public static class IconDimension {
        int x;
        int y;
        int width;
        int height;
    }

    public IconDrawer(Context context) {
        mLocalSetIcon = new ResourceTexture(context, R.drawable.ic_album_overlay_folder_holo);
        mCameraIcon = new ResourceTexture(context, R.drawable.ic_album_overlay_camera_holo);
        mPicasaIcon = new ResourceTexture(context, R.drawable.ic_album_overlay_picassa_holo);
        mMtpIcon = new ResourceTexture(context, R.drawable.ic_album_overlay_ptp_holo);
        mVideoOverlay = new ResourceTexture(context,
                R.drawable.thumbnail_album_video_overlay_holo);
        mVideoPlayIcon = new ResourceTexture(context,
                R.drawable.videooverlay);
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
        float scale = 0.25f * width / icon.getWidth();
        id.width = (int) (scale * icon.getWidth());
        id.height = (int) (scale * icon.getHeight());
        id.x = -width / 2;
        id.y = height / 2 - id.height;
        return id;
    }

    protected void drawVideoOverlay(GLCanvas canvas, int mediaType,
            int x, int y, int width, int height, int topIndex) {
        if (mediaType != MediaObject.MEDIA_TYPE_VIDEO) return;
        mVideoOverlay.draw(canvas, x, y, width, height);
        if (topIndex == 0) {
            int side = Math.min(width, height) / 6;
            mVideoPlayIcon.draw(canvas, -side / 2, -side / 2, side, side);
        }
    }

    @Override
    public void drawFocus(GLCanvas canvas, int width, int height) {
    }
}
