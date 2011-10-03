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

import com.android.gallery3d.R;

import android.content.Context;
import android.graphics.Rect;
import android.util.TypedValue;

public class ScrollBarView extends GLView {
    @SuppressWarnings("unused")
    private static final String TAG = "ScrollBarView";

    public interface Listener {
        void onScrollBarPositionChanged(int position);
    }

    private int mBarHeight;

    private int mGripHeight;
    private int mGripPosition;  // left side of the grip
    private int mGripWidth;     // zero if the grip is disabled
    private int mGivenGripWidth;

    private int mContentPosition;
    private int mContentTotal;

    private Listener mListener;
    private NinePatchTexture mScrollBarTexture;

    public ScrollBarView(Context context, int gripHeight, int gripWidth) {
        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(
                android.R.attr.scrollbarThumbHorizontal, outValue, true);
        mScrollBarTexture = new NinePatchTexture(
                context, outValue.resourceId);
        mGripPosition = 0;
        mGripWidth = 0;
        mGivenGripWidth = gripWidth;
        mGripHeight = gripHeight;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        if (!changed) return;
        mBarHeight = bottom - top;
    }

    // The content position is between 0 to "total". The current position is
    // in "position".
    public void setContentPosition(int position, int total) {
        if (position == mContentPosition && total == mContentTotal) {
            return;
        }

        invalidate();

        mContentPosition = position;
        mContentTotal = total;

        // If the grip cannot move, don't draw it.
        if (mContentTotal <= 0) {
            mGripPosition = 0;
            mGripWidth = 0;
            return;
        }

        // Map from the content range to scroll bar range.
        //
        // mContentTotal --> getWidth() - mGripWidth
        // mContentPosition --> mGripPosition
        mGripWidth = mGivenGripWidth;
        float r = (getWidth() - mGripWidth) / (float) mContentTotal;
        mGripPosition = Math.round(r * mContentPosition);
    }

    private void notifyContentPositionFromGrip() {
        if (mContentTotal <= 0) return;
        float r = (getWidth() - mGripWidth) / (float) mContentTotal;
        int newContentPosition = Math.round(mGripPosition / r);
        mListener.onScrollBarPositionChanged(newContentPosition);
    }

    @Override
    protected void render(GLCanvas canvas) {
        super.render(canvas);
        if (mGripWidth == 0) return;
        Rect b = bounds();
        int y = (mBarHeight - mGripHeight) / 2;
        mScrollBarTexture.draw(canvas, mGripPosition, y, mGripWidth, mGripHeight);
    }

    // The onTouch() handler is disabled because now we don't want the user
    // to drag the bar (it's an indicator only).
    /*
    @Override
    protected boolean onTouch(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                int x = (int) event.getX();
                return (x >= mGripPosition && x < mGripPosition + mGripWidth);
            }
            case MotionEvent.ACTION_MOVE: {
                // Adjust x by mGripWidth / 2 so the center of the grip
                // matches the touch position.
                int x = (int) event.getX() - mGripWidth / 2;
                x = Utils.clamp(x, 0, getWidth() - mGripWidth);
                if (mGripPosition != x) {
                    mGripPosition = x;
                    notifyContentPositionFromGrip();
                    invalidate();
                }
                break;
            }
        }
        return true;
    }
    */
}
