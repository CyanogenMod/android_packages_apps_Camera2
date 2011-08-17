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

import static com.android.gallery3d.ui.TextButtonConfig.*;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;

public class TextButton extends Label {
    private static final String TAG = "TextButton";
    private boolean mPressed;
    private Texture mPressedBackground;
    private Texture mNormalBackground;
    private OnClickedListener mOnClickListener;

    public interface OnClickedListener {
        public void onClicked(GLView source);
    }

    public TextButton(Context context, int label) {
        super(context, label);
        setPaddings(HORIZONTAL_PADDINGS, VERTICAL_PADDINGS,
                HORIZONTAL_PADDINGS, VERTICAL_PADDINGS);
    }

    public void setOnClickListener(OnClickedListener listener) {
        mOnClickListener = listener;
    }

    public void setPressedBackground(Texture texture) {
        mPressedBackground = texture;
    }

    public void setNormalBackground(Texture texture) {
        mNormalBackground = texture;
    }

    @SuppressWarnings("fallthrough")
    @Override
    protected boolean onTouch(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mPressed = true;
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                if (mOnClickListener != null) {
                    mOnClickListener.onClicked(this);
                }
                // fall-through
            case MotionEvent.ACTION_CANCEL:
                mPressed = false;
                invalidate();
                break;
        }
        return true;
    }

    @Override
    protected void render(GLCanvas canvas) {
        Texture bg = mPressed ? mPressedBackground : mNormalBackground;
        if (bg != null) {
            int width = getWidth();
            int height = getHeight();
            if (bg instanceof NinePatchTexture) {
                Rect p = ((NinePatchTexture) bg).getPaddings();
                bg.draw(canvas, -p.left, -p.top,
                        width + p.left + p.right, height + p.top + p.bottom);
            } else {
                bg.draw(canvas, 0, 0, width, height);
            }
        }
        super.render(canvas);
    }
}
