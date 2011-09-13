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

package com.android.gallery3d.photoeditor.actions;

import android.content.Context;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;

/**
 * A view that detects user gestures and touch motions.
 */
class TouchView extends FullscreenToolView {

    /**
     * Listener of swipes.
     */
    public interface SwipeListener {

        void onSwipeLeft();

        void onSwipeRight();

        void onSwipeUp();

        void onSwipeDown();
    }

    /**
     * Listener of single tap on a point (relative to photo coordinates).
     */
    public interface SingleTapListener {

        void onSingleTap(PointF point);
    }

    private final GestureDetector gestureDetector;

    private SwipeListener swipeListener;
    private SingleTapListener singleTapListener;

    public TouchView(Context context, AttributeSet attrs) {
        super(context, attrs);

        final int swipeThreshold = (int) (500 * getResources().getDisplayMetrics().density);
        gestureDetector = new GestureDetector(
                context, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onDown(MotionEvent e) {
                // GestureDetector onTouchEvent returns true for fling events only when their
                // preceding down events are consumed.
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (singleTapListener != null) {
                    PointF point = new PointF();
                    mapPhotoPoint(e.getX(), e.getY(), point);
                    singleTapListener.onSingleTap(point);
                }
                return true;
            }

            @Override
            public boolean onFling(
                    MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
                if (swipeListener != null) {
                    float absX = Math.abs(velocityX);
                    float absY = Math.abs(velocityY);
                    float deltaX = me2.getX() - me1.getX();
                    float deltaY = me2.getY() - me1.getY();
                    int travelX = getWidth() / 4;
                    int travelY = getHeight() / 4;
                    if (velocityX > swipeThreshold && absY < absX && deltaX > travelX) {
                        swipeListener.onSwipeRight();
                    } else if (velocityX < -swipeThreshold && absY < absX && deltaX < -travelX) {
                        swipeListener.onSwipeLeft();
                    } else if (velocityY < -swipeThreshold && absX < absY && deltaY < -travelY) {
                        swipeListener.onSwipeUp();
                    } else if (velocityY > swipeThreshold && absX < absY / 2 && deltaY > travelY) {
                        swipeListener.onSwipeDown();
                    }
                }
                return true;
            }
        });
        gestureDetector.setIsLongpressEnabled(false);
    }

    public void setSwipeListener(SwipeListener listener) {
        swipeListener = listener;
    }

    public void setSingleTapListener(SingleTapListener listener) {
        singleTapListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return isEnabled() && gestureDetector.onTouchEvent(event);
    }
}
