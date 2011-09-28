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

public abstract class DisplayItem {

    protected int mBoxWidth;
    protected int mBoxHeight;

    // setBox() specifies the box that the DisplayItem should render into. It
    // should be called before first render(). It may be called again between
    // render() calls to change the size of the box.
    public void setBox(int width, int height) {
        mBoxWidth = width;
        mBoxHeight = height;
    }

    // Return values of render():
    // RENDER_MORE_PASS: more pass is needed for this item
    // RENDER_MORE_FRAME: need to render next frame (used for animation)
    public static final int RENDER_MORE_PASS = 1;
    public static final int RENDER_MORE_FRAME = 2;

    public abstract int render(GLCanvas canvas, int pass);

    public abstract long getIdentity();

    public int getRotation() {
        return 0;
    }
}
