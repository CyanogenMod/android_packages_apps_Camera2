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

package com.android.camera.ui;

import android.content.Context;
import android.view.GestureDetector;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.android.camera.debug.Log;

// This class aggregates two gesture detectors: GestureDetector,
// ScaleGestureDetector.
public class FilmstripGestureRecognizer {
    @SuppressWarnings("unused")
    private static final Log.Tag TAG = new Log.Tag("FStripGestureRecog");

    public interface Listener {
        boolean onSingleTapUp(float x, float y);
        boolean onDoubleTap(float x, float y);
        boolean onScroll(float x, float y, float dx, float dy);
        boolean onMouseScroll(float hscroll, float vscroll);
        boolean onFling(float velocityX, float velocityY);
        boolean onScaleBegin(float focusX, float focusY);
        boolean onScale(float focusX, float focusY, float scale);
        boolean onDown(float x, float y);
        boolean onUp(float x, float y);
        void onLongPress(float x, float y);
        void onScaleEnd();
    }

    private final GestureDetector mGestureDetector;
    private final ScaleGestureDetector mScaleDetector;
    private final Listener mListener;

    public FilmstripGestureRecognizer(Context context, Listener listener) {
        mListener = listener;
        mGestureDetector = new GestureDetector(context, new MyGestureListener(),
                null, true /* ignoreMultitouch */);
        mGestureDetector.setOnDoubleTapListener(new MyDoubleTapListener());
        mScaleDetector = new ScaleGestureDetector(
                context, new MyScaleListener());
    }

    public boolean onTouchEvent(MotionEvent event) {
        final boolean gestureProcessed = mGestureDetector.onTouchEvent(event);
        final boolean scaleProcessed = mScaleDetector.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP) {
            mListener.onUp(event.getX(), event.getY());
        }
        return (gestureProcessed | scaleProcessed);
    }

    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_SCROLL: {
                    final float hscroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
                    final float vscroll = -event.getAxisValue(MotionEvent.AXIS_VSCROLL);

                    if (hscroll != 0.0f || vscroll != 0.0f) {
                        mListener.onMouseScroll(hscroll, vscroll);
                    }
                }
            }
        }

        return true;
    }

    private class MyGestureListener
                extends GestureDetector.SimpleOnGestureListener {
        @Override
        public void onLongPress(MotionEvent e) {
            mListener.onLongPress(e.getX(), e.getY());
        }

        @Override
        public boolean onScroll(
                MotionEvent e1, MotionEvent e2, float dx, float dy) {
            return mListener.onScroll(e2.getX(), e2.getY(), dx, dy);
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                float velocityY) {
            return mListener.onFling(velocityX, velocityY);
        }

        @Override
        public boolean onDown(MotionEvent e) {
            mListener.onDown(e.getX(), e.getY());
            return super.onDown(e);
        }
    }

    /**
     * The listener that is used to notify when a double-tap or a confirmed
     * single-tap occur.
     */
    private class MyDoubleTapListener implements GestureDetector.OnDoubleTapListener {

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            return mListener.onSingleTapUp(e.getX(), e.getY());
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            return mListener.onDoubleTap(e.getX(), e.getY());
        }

        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            return true;
        }
    }

    private class MyScaleListener
            extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return mListener.onScaleBegin(
                    detector.getFocusX(), detector.getFocusY());
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            return mListener.onScale(detector.getFocusX(),
                    detector.getFocusY(), detector.getScaleFactor());
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            mListener.onScaleEnd();
        }
    }
}
