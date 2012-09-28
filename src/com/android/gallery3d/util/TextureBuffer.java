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

package com.android.gallery3d.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Keeps a buffer of data as read from a frame buffer. This can be used with
 * glTexSubImage2D to write to a texture.
 */
public class TextureBuffer {
    private int mWidth;
    private int mHeight;
    private ByteBuffer mBuffer;

    public TextureBuffer(int width, int height) {
        mWidth = width;
        mHeight = height;

        mBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public ByteBuffer getBuffer() {
        return mBuffer;
    }
}
