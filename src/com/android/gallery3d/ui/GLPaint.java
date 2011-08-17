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

import com.android.gallery3d.common.Utils;


public class GLPaint {
    public static final int FLAG_ANTI_ALIAS = 0x01;

    private int mFlags = 0;
    private float mLineWidth = 1f;
    private int mColor = 0;

    public int getFlags() {
        return mFlags;
    }

    public void setFlags(int flags) {
        mFlags = flags;
    }

    public void setColor(int color) {
        mColor = color;
    }

    public int getColor() {
        return mColor;
    }

    public void setLineWidth(float width) {
        Utils.assertTrue(width >= 0);
        mLineWidth = width;
    }

    public float getLineWidth() {
        return mLineWidth;
    }

    public void setAntiAlias(boolean enabled) {
        if (enabled) {
            mFlags |= FLAG_ANTI_ALIAS;
        } else {
            mFlags &= ~FLAG_ANTI_ALIAS;
        }
    }

    public boolean getAntiAlias(){
        return (mFlags & FLAG_ANTI_ALIAS) != 0;
    }
}
