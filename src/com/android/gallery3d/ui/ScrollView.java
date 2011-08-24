/*
 * Copyright (C) 2011 The Android Open Source Project
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
import com.android.gallery3d.common.Utils;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View.MeasureSpec;

// The current implementation can only scroll vertically.
public class ScrollView extends GLView {

    private static final int MIN_SCROLLER_HEIGHT = 20;

    private NinePatchTexture mScroller;
    private int mScrollLimit = 0;
    private int mScrollerHeight = MIN_SCROLLER_HEIGHT;
    private GestureDetector mGestureDetector;

    public ScrollView(Context context) {
        mScroller = new NinePatchTexture(context, R.drawable.scrollbar_handle_holo_dark);
        mGestureDetector = new GestureDetector(context, new MyGestureListener());
    }

    private GLView getContentView() {
        return getComponentCount() == 0 ? null : getComponent(0);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        GLView view = getContentView();
        if (view != null) {
            view.measure(widthSpec, heightSpec);
            MeasureHelper.getInstance(this)
                    .setPreferredContentSize(view.getMeasuredWidth(),
                            view.getMeasuredHeight())
                    .measure(widthSpec, heightSpec);
        }
    }

    @Override
    public void onLayout(boolean sizeChange, int l, int t, int r, int b) {
        GLView content = getContentView();
        int width = getWidth();
        int height = getHeight();
        content.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.UNSPECIFIED);
        int contentHeight = content.getMeasuredHeight();
        content.layout(0, 0, width, contentHeight);
        if (height < contentHeight) {
            mScrollLimit = contentHeight - height;
            mScrollerHeight = Math.max(MIN_SCROLLER_HEIGHT,
                    height * height / contentHeight);
        } else {
            mScrollLimit = 0;
        }
        mScrollY = Utils.clamp(mScrollY, 0, mScrollLimit);
    }

    @Override
    public void render(GLCanvas canvas) {
        GLView content = getContentView();
        if (content == null) return;
        int width = getWidth();
        int height = getHeight();

        canvas.save(GLCanvas.SAVE_FLAG_CLIP);
        canvas.clipRect(0, 0, width, height);
        super.render(canvas);
        if (mScrollLimit > 0) {
            int x = getWidth() - mScroller.getWidth();
            int y = (height - mScrollerHeight) * mScrollY / mScrollLimit;
            mScroller.draw(canvas, x, y, mScroller.getWidth(), mScrollerHeight);
        }
        canvas.restore();
    }

    @Override
    public boolean onTouch(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        return true;
    }

    private class MyGestureListener
            extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1,
                MotionEvent e2, float distanceX, float distanceY) {
            mScrollY = Utils.clamp(mScrollY + (int) distanceY, 0, mScrollLimit);
            invalidate();
            return true;
        }
    }
}
