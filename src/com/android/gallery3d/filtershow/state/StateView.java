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

package com.android.gallery3d.filtershow.state;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.LinearLayout;
import com.android.gallery3d.filtershow.FilterShowActivity;

public class StateView extends View {

    private Path mPath = new Path();
    private Paint mPaint = new Paint();

    public static int DEFAULT = 0;
    public static int BEGIN = 1;
    public static int END = 2;

    public static int UP = 1;
    public static int DOWN = 2;
    public static int LEFT = 3;
    public static int RIGHT = 4;

    private int mType = DEFAULT;
    private float mAlpha = 1.0f;
    private String mText = "Default";
    private float mTextSize = 32;
    private static int sMargin = 16;
    private static int sArrowHeight = 16;
    private static int sArrowWidth = 8;
    private int mOrientation = LinearLayout.VERTICAL;
    private int mDirection = DOWN;
    private boolean mDuplicateButton;
    private State mState;

    public StateView(Context context) {
        this(context, DEFAULT);
    }

    public StateView(Context context, int type) {
        super(context);
        mType = type;
    }

    public StateView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public String getText() {
        return mText;
    }

    public void setText(String text) {
        mText = text;
        invalidate();
    }

    public void setType(int type) {
        mType = type;
        invalidate();
    }

    @Override
    public void setSelected(boolean value) {
        super.setSelected(value);
        if (!value) {
            mDuplicateButton = false;
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            ViewParent parent = getParent();
            if (parent instanceof PanelTrack) {
                ((PanelTrack) getParent()).onTouch(event, this);
            }
        }
        return true;
    }

    public void drawText(Canvas canvas) {
        if (mText == null) {
            return;
        }
        mPaint.reset();
        if (isSelected()) {
            mPaint.setColor(Color.BLACK);
        } else {
            mPaint.setColor(Color.WHITE);
        }
        mPaint.setTextSize(mTextSize);
        float textWidth = mPaint.measureText(mText);
        int x = (int) ((canvas.getWidth() - textWidth) / 2);
        int y = canvas.getHeight() - sMargin;
        if (canvas.getHeight() > canvas.getWidth()) {
            y = canvas.getHeight() - (canvas.getHeight() - canvas.getWidth() - 2 * sMargin) / 2;
        }
        canvas.drawText(mText, x, y, mPaint);
    }

    public void onDraw(Canvas canvas) {
        canvas.drawARGB(0, 0, 0, 0);
        mPath.reset();

        float w = canvas.getWidth();
        float h = canvas.getHeight();
        float r = sArrowHeight;
        float d = sArrowWidth;

        if (mOrientation == LinearLayout.HORIZONTAL) {
            drawHorizontalPath(w, h, r, d);
        } else {
            if (mDirection == DOWN) {
                drawVerticalDownPath(w, h, r, d);
            } else {
                drawVerticalPath(w, h, r, d);
            }
        }

        if (mType == DEFAULT) {
            if (mDuplicateButton) {
                mPaint.setARGB(255, 200, 0, 0);
            } else if (isSelected()) {
                mPaint.setARGB(255, 200, 200, 200);
            } else {
                mPaint.setARGB(255, 70, 70, 70);
            }
        } else {
            mPaint.setARGB(255, 150, 150, 150);
        }
        canvas.drawPath(mPath, mPaint);
        drawText(canvas);
    }

    private void drawHorizontalPath(float w, float h, float r, float d) {
        mPath.moveTo(0, 0);
        if (mType == END) {
            mPath.lineTo(w, 0);
            mPath.lineTo(w, h);
        } else {
            mPath.lineTo(w - d, 0);
            mPath.lineTo(w - d, r);
            mPath.lineTo(w, r + d);
            mPath.lineTo(w - d, r + d + r);
            mPath.lineTo(w - d, h);
        }
        mPath.lineTo(0, h);
        if (mType != BEGIN) {
            mPath.lineTo(0, r + d + r);
            mPath.lineTo(d, r + d);
            mPath.lineTo(0, r);
        }
        mPath.close();
    }

    private void drawVerticalPath(float w, float h, float r, float d) {
        if (mType == BEGIN) {
            mPath.moveTo(0, 0);
            mPath.lineTo(w, 0);
        } else {
            mPath.moveTo(0, d);
            mPath.lineTo(r, d);
            mPath.lineTo(r + d, 0);
            mPath.lineTo(r + d + r, d);
            mPath.lineTo(w, d);
        }
        mPath.lineTo(w, h);
        if (mType != END) {
            mPath.lineTo(r + d + r, h);
            mPath.lineTo(r + d, h - d);
            mPath.lineTo(r, h);
        }
        mPath.lineTo(0, h);
        mPath.close();
    }

    private void drawVerticalDownPath(float w, float h, float r, float d) {
        mPath.moveTo(0, 0);
        if (mType != BEGIN) {
            mPath.lineTo(r, 0);
            mPath.lineTo(r + d, d);
            mPath.lineTo(r + d + r, 0);
        }
        mPath.lineTo(w, 0);

        if (mType != END) {
            mPath.lineTo(w, h - d);

            mPath.lineTo(r + d + r, h - d);
            mPath.lineTo(r + d, h);
            mPath.lineTo(r, h - d);

            mPath.lineTo(0, h - d);
        } else {
            mPath.lineTo(w, h);
            mPath.lineTo(0, h);
        }

        mPath.close();
    }

    public void setBackgroundAlpha(float alpha) {
        if (mType != DEFAULT) {
            return;
        }
        mAlpha = alpha;
        invalidate();
    }

    public float getAlpha() {
        return mAlpha;
    }

    public void setOrientation(int orientation) {
        mOrientation = orientation;
    }

    public void setDuplicateButton(boolean b) {
        mDuplicateButton = b;
        invalidate();
    }

    public State getState() {
        return mState;
    }

    public void setState(State state) {
        mState = state;
        mText = mState.getText();
        mType = mState.getType();
        setBackgroundAlpha(1.0f);
        invalidate();
    }

    public void resetPosition() {
        setTranslationX(0);
        setTranslationY(0);
    }

    public boolean isDraggable() {
        return mState.isDraggable();
    }
}
