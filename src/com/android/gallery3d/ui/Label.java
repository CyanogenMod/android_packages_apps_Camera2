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

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;

public class Label extends GLView {
    private static final String TAG = "Label";
    public static final int NULL_ID = 0;

    private static final int FONT_SIZE = 18;
    private static final int FONT_COLOR = Color.WHITE;

    private String mText;
    private StringTexture mTexture;
    private int mFontSize, mFontColor;

    public Label(Context context, int stringId,
            int fontSize, int fontColor) {
        this(context, context.getString(stringId), fontSize, fontColor);
    }

    public Label(Context context, int stringId) {
        this(context, stringId, FONT_SIZE, FONT_COLOR);
    }

    public Label(Context context, String text) {
        this(context, text, FONT_SIZE, FONT_COLOR);
    }

    public Label(Context context, String text, int fontSize, int fontColor) {
        //TODO: cut the text if it is too long
        mText = text;
        mTexture = StringTexture.newInstance(text, fontSize, fontColor);
        mFontSize = fontSize;
        mFontColor = fontColor;
    }

    public void setText(String text) {
        if (!mText.equals(text)) {
            mText = text;
            mTexture = StringTexture.newInstance(text, mFontSize, mFontColor);
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = mTexture.getWidth();
        int height = mTexture.getHeight();
        MeasureHelper.getInstance(this)
                .setPreferredContentSize(width, height)
                .measure(widthSpec, heightSpec);
    }

    @Override
    protected void render(GLCanvas canvas) {
        Rect p = mPaddings;

        int width = getWidth() - p.left - p.right;
        int height = getHeight() - p.top - p.bottom;

        int xoffset = p.left + (width - mTexture.getWidth()) / 2;
        int yoffset = p.top + (height - mTexture.getHeight()) / 2;

        mTexture.draw(canvas, xoffset, yoffset);
    }
}
