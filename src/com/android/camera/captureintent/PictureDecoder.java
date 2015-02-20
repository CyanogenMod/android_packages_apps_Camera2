/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.camera.captureintent;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

public class PictureDecoder {
    /**
     * Decodes a jpeg byte array into a Bitmap object.
     *
     * @param data a byte array of jpeg data
     * @param downSampleFactor down-sample factor
     * @param pictureOrientation The picture orientation in degrees.
     * @param needMirror Whether the bitmap should be flipped horizontally.
     * @return decoded and down-sampled bitmap
     */
    public static Bitmap decode(
            byte[] data, int downSampleFactor, int pictureOrientation, boolean needMirror) {
        // Downsample the image
        final BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = downSampleFactor;
        final Bitmap pictureBitmap = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        if (pictureOrientation == 0 && !needMirror) {
            return pictureBitmap;
        }

        Matrix m = new Matrix();
        // Rotate if needed.
        if (pictureOrientation != 0) {
            m.preRotate(pictureOrientation);
        }
        // Flip horizontally if needed.
        if (needMirror) {
            m.setScale(-1f, 1f);
        }
        return Bitmap.createBitmap(
                pictureBitmap, 0, 0, pictureBitmap.getWidth(), pictureBitmap.getHeight(), m, false);
    }
}
