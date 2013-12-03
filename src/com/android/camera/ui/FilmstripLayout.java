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

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.android.camera2.R;

/**
 * A {@link android.widget.FrameLayout} used for the parent layout of a
 * {@link com.android.camera.ui.FilmstripView} to support animating in/out the
 * filmstrip.
 */
public class FilmstripLayout extends FrameLayout {
    private static final long DEFAULT_DURATION_MS = 200;
    private FilmstripView mFilmstripView;
    private FilmstripGestureRecognizer mGestureRecognizer;
    private FilmstripGestureRecognizer.Listener mFilmstripGestureListener;
    private final ValueAnimator mFilmstripAnimator = ValueAnimator.ofFloat(null);
    private int mSwipeTrend;

    public FilmstripLayout(Context context) {
        super(context);
        init(context);
    }

    public FilmstripLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FilmstripLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        mGestureRecognizer = new FilmstripGestureRecognizer(context, new MyGestureListener());
        mFilmstripAnimator.setDuration(DEFAULT_DURATION_MS);

        ValueAnimator.AnimatorUpdateListener updateListener =
                new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        mFilmstripView.setTranslationX((Float) valueAnimator.getAnimatedValue());
                    }
                };
        mFilmstripAnimator.addUpdateListener(updateListener);

        mFilmstripAnimator.addListener(new Animator.AnimatorListener() {
            private boolean mCanceled;

            @Override
            public void onAnimationStart(Animator animator) {
                mCanceled = false;
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                if (!mCanceled) {
                    if (mFilmstripView.getTranslationX() != 0f) {
                        setVisibility(INVISIBLE);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animator) {
                mCanceled = true;
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
                // Nothing.
            }
        });
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed && mFilmstripView != null && getVisibility() == INVISIBLE) {
            mFilmstripView.setTranslationX(getMeasuredWidth());
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mGestureRecognizer.onTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            // TODO: Remove this after the touch flow refactor is done in
            // MainAtivityLayout.
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return false;
    }

    @Override
    public void onFinishInflate() {
        mFilmstripView = (FilmstripView) findViewById(R.id.filmstrip_view);
        mFilmstripView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                mGestureRecognizer.onTouchEvent(motionEvent);
                return true;
            }
        });
        mFilmstripGestureListener = mFilmstripView.getGestureListener();
    }

    /**
     * Called when the back key is pressed.
     *
     * @return Whether the UI responded to the key event.
     */
    public boolean onBackPressed() {
        if (getVisibility() == VISIBLE) {
            if (!mFilmstripAnimator.isRunning()) {
                hideFilmstrip();
            }
            return true;
        }
        return false;
    }

    private void hideFilmstrip() {
        if (mFilmstripAnimator.isRunning()) {
            mFilmstripAnimator.cancel();
        }
        mFilmstripAnimator.setFloatValues(mFilmstripView.getTranslationX(), getMeasuredWidth());
        mFilmstripAnimator.start();
    }

    private void showFilmstrip() {
        if (mFilmstripAnimator.isRunning()) {
            mFilmstripAnimator.cancel();
        }
        mFilmstripAnimator.setFloatValues(mFilmstripView.getTranslationX(), 0);
        mFilmstripAnimator.start();
    }

    /**
     * A gesture listener which passes all the gestures to the
     * {@code mFilmstripView} by default and only intercepts scroll gestures
     * when the {@code mFilmstripView} is not in full-screen.
     */
    private class MyGestureListener implements FilmstripGestureRecognizer.Listener {
        @Override
        public boolean onScroll(float x, float y, float dx, float dy) {
            if (mFilmstripView.getTranslationX() == 0f &&
                    mFilmstripGestureListener.onScroll(x, y, dx, dy)) {
                return true;
            }
            mSwipeTrend = (((int) dx) >> 1)  + (mSwipeTrend >> 1);
            float translate = mFilmstripView.getTranslationX() - dx;
            if (translate < 0f) {
                translate = 0f;
            } else {
                if (translate > getMeasuredWidth()) {
                    translate = getMeasuredWidth();
                }
            }
            mFilmstripView.setTranslationX(translate);
            invalidate();
            return true;
        }

        @Override
        public boolean onSingleTapUp(float x, float y) {
            if (mFilmstripView.getTranslationX() == 0f) {
                return mFilmstripGestureListener.onSingleTapUp(x, y);
            }
            return false;
        }

        @Override
        public boolean onDoubleTap(float x, float y) {
            if (mFilmstripView.getTranslationX() == 0f) {
                return mFilmstripGestureListener.onDoubleTap(x, y);
            }
            return false;
        }

        @Override
        public boolean onFling(float velocityX, float velocityY) {
            if (mFilmstripView.getTranslationX() == 0f) {
                return mFilmstripGestureListener.onFling(velocityX, velocityY);
            }
            return false;
        }

        @Override
        public boolean onScaleBegin(float focusX, float focusY) {
            if (mFilmstripView.getTranslationX() == 0f) {
                return mFilmstripGestureListener.onScaleBegin(focusX, focusY);
            }
            return false;
        }

        @Override
        public boolean onScale(float focusX, float focusY, float scale) {
            if (mFilmstripView.getTranslationX() == 0f) {
                return mFilmstripGestureListener.onScale(focusX, focusY, scale);
            }
            return false;
        }

        @Override
        public boolean onDown(float x, float y) {
            mFilmstripAnimator.cancel();
            if (mFilmstripView.getTranslationX() == 0f) {
                return mFilmstripGestureListener.onDown(x, y);
            }
            return false;
        }

        @Override
        public boolean onUp(float x, float y) {
            if (mFilmstripView.getTranslationX() == 0f) {
                return mFilmstripGestureListener.onUp(x, y);
            }
            if (mSwipeTrend < 0) {
                hideFilmstrip();
            } else {
                showFilmstrip();
            }
            mSwipeTrend = 0;
            return false;
        }

        @Override
        public void onScaleEnd () {
            if (mFilmstripView.getTranslationX() == 0f) {
                mFilmstripGestureListener.onScaleEnd();
            }
        }
    }
}
