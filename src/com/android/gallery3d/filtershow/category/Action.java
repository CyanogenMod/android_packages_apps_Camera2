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

package com.android.gallery3d.filtershow.category;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import com.android.gallery3d.filtershow.cache.RenderingRequest;
import com.android.gallery3d.filtershow.cache.RenderingRequestCaller;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.presets.ImagePreset;

public class Action implements RenderingRequestCaller {

    private static final String LOGTAG = "Action";
    private FilterRepresentation mRepresentation;
    private String mName;
    private Rect mImageFrame;
    private Bitmap mImage;
    private CategoryAdapter mAdapter;
    public static final int FULL_VIEW = 0;
    public static final int CROP_VIEW = 1;
    private int mType = CROP_VIEW;
    private Bitmap mPortraitImage;
    private Bitmap mOverlayBitmap;
    private Context mContext;

    public Action(Context context, FilterRepresentation representation, int type) {
        mContext = context;
        setRepresentation(representation);
        setType(type);
    }

    public Action(Context context, FilterRepresentation representation) {
        this(context, representation, CROP_VIEW);
    }

    public FilterRepresentation getRepresentation() {
        return mRepresentation;
    }

    public void setRepresentation(FilterRepresentation representation) {
        mRepresentation = representation;
        mName = representation.getName();
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    public void setImageFrame(Rect imageFrame) {
        if (mImageFrame != null && mImageFrame.equals(imageFrame)) {
            return;
        }
        Bitmap bitmap = MasterImage.getImage().getLargeThumbnailBitmap();
        if (bitmap != null) {
            mImageFrame = imageFrame;
            int w = mImageFrame.width();
            int h = mImageFrame.height();
            if (mType == CROP_VIEW) {
                w /= 2;
            }
            Bitmap bitmapCrop = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            drawCenteredImage(bitmap, bitmapCrop, true);

            postNewIconRenderRequest(bitmapCrop);
        }
    }

    public Bitmap getImage() {
        return mImage;
    }

    public void setImage(Bitmap image) {
        mImage = image;
    }

    public void setAdapter(CategoryAdapter adapter) {
        mAdapter = adapter;
    }

    public void setType(int type) {
        mType = type;
    }

    private void postNewIconRenderRequest(Bitmap bitmap) {
        if (bitmap != null && mRepresentation != null) {
            ImagePreset preset = new ImagePreset();
            preset.addFilter(mRepresentation);
            RenderingRequest.post(bitmap,
                    preset, RenderingRequest.ICON_RENDERING, this);
        }
    }

    private void drawCenteredImage(Bitmap source, Bitmap destination, boolean scale) {
        RectF image = new RectF(0, 0, source.getWidth(), source.getHeight());
        int border = 0;
        if (!scale) {
            border = destination.getWidth() - destination.getHeight();
            if (border < 0) {
                border = 0;
            }
        }
        RectF frame = new RectF(border, 0,
                destination.getWidth() - border,
                destination.getHeight());
        Matrix m = new Matrix();
        m.setRectToRect(frame, image, Matrix.ScaleToFit.CENTER);
        image.set(frame);
        m.mapRect(image);
        m.setRectToRect(image, frame, Matrix.ScaleToFit.FILL);
        Canvas canvas = new Canvas(destination);
        canvas.drawBitmap(source, m, new Paint());
    }

    @Override
    public void available(RenderingRequest request) {
        mImage = request.getBitmap();
        if (mImage == null) {
            return;
        }
        if (mRepresentation.getOverlayId() != 0 && mOverlayBitmap == null) {
            mOverlayBitmap = BitmapFactory.decodeResource(
                    mContext.getResources(),
                    mRepresentation.getOverlayId());
        }
        if (mOverlayBitmap != null) {
            if (getRepresentation().getPriority() == FilterRepresentation.TYPE_BORDER) {
                Canvas canvas = new Canvas(mImage);
                canvas.drawBitmap(mOverlayBitmap, new Rect(0, 0, mOverlayBitmap.getWidth(), mOverlayBitmap.getHeight()),
                        new Rect(0, 0, mImage.getWidth(), mImage.getHeight()), new Paint());
            } else {
                Canvas canvas = new Canvas(mImage);
                canvas.drawARGB(128, 0, 0, 0);
                drawCenteredImage(mOverlayBitmap, mImage, false);
            }
        }
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    public void setPortraitImage(Bitmap portraitImage) {
        mPortraitImage = portraitImage;
    }

    public Bitmap getPortraitImage() {
        return mPortraitImage;
    }

    public Bitmap getOverlayBitmap() {
        return mOverlayBitmap;
    }

    public void setOverlayBitmap(Bitmap overlayBitmap) {
        mOverlayBitmap = overlayBitmap;
    }
}
