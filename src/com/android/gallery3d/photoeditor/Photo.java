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

package com.android.gallery3d.photoeditor;

import android.graphics.Bitmap;

/**
 * Photo that holds a GL texture and all its methods must be only accessed from the GL thread.
 */
public class Photo {

    private int texture;
    private int width;
    private int height;

    /**
     * Factory method to ensure every Photo instance holds a valid texture.
     */
    public static Photo create(Bitmap bitmap) {
        return (bitmap != null) ? new Photo(
                RendererUtils.createTexture(bitmap), bitmap.getWidth(), bitmap.getHeight()) : null;
    }

    public static Photo create(int width, int height) {
        return new Photo(RendererUtils.createTexture(), width, height);
    }

    private Photo(int texture, int width, int height) {
        this.texture = texture;
        this.width = width;
        this.height = height;
    }

    public int texture() {
        return texture;
    }

    public boolean matchDimension(Photo photo) {
        return ((photo.width == width) && (photo.height == height));
    }

    public void changeDimension(int width, int height) {
        this.width = width;
        this.height = height;
        RendererUtils.clearTexture(texture);
        texture = RendererUtils.createTexture();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Bitmap save() {
        return RendererUtils.saveTexture(texture, width, height);
    }

    /**
     * Clears the texture; this instance should not be used after its clear() is called.
     */
    public void clear() {
        RendererUtils.clearTexture(texture);
    }
}
