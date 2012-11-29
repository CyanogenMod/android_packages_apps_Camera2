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

package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public class ImageFilterBorder extends ImageFilter {
    Drawable mNinePatch = null;

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilterBorder filter = (ImageFilterBorder) super.clone();
        filter.setDrawable(mNinePatch);
        return filter;
    }

    public ImageFilterBorder(Drawable ninePatch) {
        setFilterType(TYPE_BORDER);
        mName = "Border";
        mNinePatch = ninePatch;
    }

    @Override
    public boolean isNil() {
        if (mNinePatch == null) {
            return  true;
        }
        return false;
    }

    @Override
    public boolean same(ImageFilter filter) {
        boolean isBorderFilter = super.same(filter);
        if (!isBorderFilter) {
            return false;
        }
        if (!(filter instanceof ImageFilterBorder)) {
            return false;
        }
        ImageFilterBorder borderFilter = (ImageFilterBorder) filter;
        if (mNinePatch != borderFilter.mNinePatch) {
            return false;
        }
        return true;
    }

    public void setDrawable(Drawable ninePatch) {
        // TODO: for now we only use nine patch
        mNinePatch = ninePatch;
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        if (mNinePatch == null) {
            return bitmap;
        }

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        float scale = scaleFactor * 2.0f;
        Rect bounds = new Rect(0, 0, (int) (w / scale), (int) (h / scale));
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(scale, scale);
        mNinePatch.setBounds(bounds);
        mNinePatch.draw(canvas);
        return bitmap;
    }
}
