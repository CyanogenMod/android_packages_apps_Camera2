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
import android.graphics.Matrix;
import android.graphics.RectF;

import com.android.gallery3d.filtershow.imageshow.GeometryMetadata;

import java.util.Vector;

public class ImageFilterRedEye extends ImageFilter {
    private static final String LOGTAG = "ImageFilterRedEye";
    FilterRedEyeRepresentation mParameters = new FilterRedEyeRepresentation();

    public ImageFilterRedEye() {
        mName = "Red Eye";
    }

    @Override
    public FilterRepresentation getDefaultRepresentation() {
        FilterRedEyeRepresentation representation = new FilterRedEyeRepresentation();

        return representation;
    }

    public boolean isNil() {
        if (mParameters.getCandidates() != null && mParameters.getCandidates().size() > 0) {
            return false;
        }
        return true;
    }

    public Vector<RedEyeCandidate> getCandidates() {
        if (!mParameters.hasCandidates()) {
            mParameters.setCandidates(new Vector<RedEyeCandidate>());
        }
        return mParameters.getCandidates();
    }

    public void clear() {
        if (!mParameters.hasCandidates()) {
            mParameters.setCandidates(new Vector<RedEyeCandidate>());
        }
        mParameters.clearCandidates();
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h, short[] matrix);

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, int quality) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        short[] rect = new short[4];
        int size = mParameters.getNumberOfCandidates();

        for (int i = 0; i < size; i++) {
            RectF r = new RectF(mParameters.getCandidate(i).mRect);
            GeometryMetadata geo = getImagePreset().mGeoData;
            Matrix originalToScreen = geo.getOriginalToScreen(true,
                    getImagePreset().getImageLoader().getOriginalBounds().width(),
                    getImagePreset().getImageLoader().getOriginalBounds().height(),
                    w, h);
            originalToScreen.mapRect(r);
            if (r.left < 0) {
                r.left = 0;
            }
            if (r.left > w) {
                r.left = w;
            }
            if (r.top < 0) {
                r.top = 0;
            }
            if (r.top > h) {
                r.top = h;
            }
            if (r.right < 0) {
                r.right = 0;
            }
            if (r.right > w) {
                r.right = w;
            }
            if (r.bottom < 0) {
                r.bottom = 0;
            }
            if (r.bottom > h) {
                r.bottom = h;
            }
            rect[0] = (short) r.left;
            rect[1] = (short) r.top;
            rect[2] = (short) r.width();
            rect[3] = (short) r.height();
            nativeApplyFilter(bitmap, w, h, rect);
        }

        return bitmap;
    }
}
