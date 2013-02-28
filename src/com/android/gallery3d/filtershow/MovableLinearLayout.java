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

package com.android.gallery3d.filtershow;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.imageshow.MasterImage;

public class MovableLinearLayout extends LinearLayout {

    private Point mTouchDown = new Point();
    private FilterRepresentation mFilterRepresentation;
    private int mTouchSlope = 3;
    private static final String LOGTAG = "MovableLinearLayout";

    public MovableLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private void resetView() {
        setTranslationX(0);
        mTouchDown.x = 0;
        mTouchDown.y = 0;
        setAlpha(1.0f);
        setBackgroundResource(R.drawable.filtershow_button_background);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int ex = (int) event.getX();
        int ey = (int) event.getY();
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mTouchDown.x = ex;
            mTouchDown.y = ey;
            FilterShowActivity activity = (FilterShowActivity) getContext();
            activity.getPanelController().showComponentWithRepresentation(mFilterRepresentation);
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            int delta = ex - mTouchDown.x;
            if (delta > 0 && (delta - getTranslationX()) > mTouchSlope) {
                setTranslationX(delta);
                float alpha = (getWidth() - getTranslationX()) / getWidth();
                int backgroundColor = Color.argb((int) (1.0f - alpha * 255), 255, 0, 0);
                setBackgroundColor(backgroundColor);
                setAlpha(alpha);
            }
        }
        if (event.getAction() == MotionEvent.ACTION_UP
                || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (getTranslationX() > getWidth() / 4) {
                delete(mFilterRepresentation);
            } else {
                resetView();
            }
        }
        return true;
    }

    private void delete(FilterRepresentation filterRepresentation) {
        FilterShowActivity activity = (FilterShowActivity) getContext();
        activity.getPanelController().removeFilterRepresentation(filterRepresentation);
    }

    public void setFilterRepresentation(FilterRepresentation filterRepresentation) {
        mFilterRepresentation = filterRepresentation;
        resetView();
    }

}
