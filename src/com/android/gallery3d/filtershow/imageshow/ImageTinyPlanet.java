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

package com.android.gallery3d.filtershow.imageshow;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.android.gallery3d.filtershow.filters.ImageFilterTinyPlanet;

public class ImageTinyPlanet extends ImageSlave {

    private float mTouchCenterX = 0;
    private float mTouchCenterY = 0;
    private float mCurrentX = 0;
    private float mCurrentY = 0;
    private float mCenterX = 0;
    private float mCenterY = 0;
    private float mStartAngle = 0;

    public ImageTinyPlanet(Context context) {
        super(context);
    }

    public ImageTinyPlanet(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    protected static float angleFor(float dx, float dy) {
        return (float) (Math.atan2(dx, dy) * 180 / Math.PI);
    }

    protected float getCurrentTouchAngle() {
        if (mCurrentX == mTouchCenterX && mCurrentY == mTouchCenterY) {
            return 0;
        }
        float dX1 = mTouchCenterX - mCenterX;
        float dY1 = mTouchCenterY - mCenterY;
        float dX2 = mCurrentX - mCenterX;
        float dY2 = mCurrentY - mCenterY;

        float angleA = angleFor(dX1, dY1);
        float angleB = angleFor(dX2, dY2);
        return (float) (((angleB - angleA) % 360) * Math.PI / 180);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        ImageFilterTinyPlanet filter = (ImageFilterTinyPlanet) getCurrentFilter();
        float x = event.getX();
        float y = event.getY();
        mCurrentX = x;
        mCurrentY = y;
        mCenterX = getWidth() / 2;
        mCenterY = getHeight() / 2;
        switch (event.getActionMasked()) {
            case (MotionEvent.ACTION_DOWN):
                mTouchCenterX = x;
                mTouchCenterY = y;
                mStartAngle = filter.getAngle();
                break;
            case (MotionEvent.ACTION_UP):
            case (MotionEvent.ACTION_MOVE):
                filter.setAngle(mStartAngle + getCurrentTouchAngle());
                break;
        }
        resetImageCaches(this);
        invalidate();
        return true;
    }
}
