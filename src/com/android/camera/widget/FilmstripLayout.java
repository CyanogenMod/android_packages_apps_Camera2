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

package com.android.camera.widget;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.android.camera.filmstrip.FilmstripContentPanel;
import com.android.camera.filmstrip.FilmstripController;
import com.android.camera.ui.FilmstripGestureRecognizer;
import com.android.camera2.R;

/**
 * A {@link android.widget.FrameLayout} used for the parent layout of a
 * {@link com.android.camera.widget.FilmstripView} to support animating in/out the
 * filmstrip.
 */
public class FilmstripLayout extends FrameLayout implements FilmstripContentPanel {

    private static final long DEFAULT_DURATION_MS = 200;
    private static final int ANIM_DIRECTION_IN = 1;
    private static final int ANIM_DIRECTION_OUT = 2;

    /**
     * The layout containing the {@link com.android.camera.widget.FilmstripView}
     * and other controls.
     */
    private FrameLayout mFilmstripContentLayout;
    private FilmstripView mFilmstripView;
    private FilmstripGestureRecognizer mGestureRecognizer;
    private FilmstripGestureRecognizer.Listener mFilmstripGestureListener;
    private final ValueAnimator mFilmstripAnimator = ValueAnimator.ofFloat(null);
    private int mSwipeTrend;
    private MyBackgroundDrawable mBackgroundDrawable;
    private int mAnimationDirection;
    // There are two versions of background. The hiding background is simply a
    // solid black rectangle, the other is the quantum paper version.
    private boolean mDrawHidingBackground;

    private Animator.AnimatorListener mFilmstripAnimatorListener = new Animator.AnimatorListener() {
        private boolean mCanceled;

        @Override
        public void onAnimationStart(Animator animator) {
            mCanceled = false;
        }

        @Override
        public void onAnimationEnd(Animator animator) {
            if (!mCanceled) {
                if (getFilmstripTranslationX() != 0f) {
                    mFilmstripView.getController().goToFilmstrip();
                    setVisibility(INVISIBLE);
                    setDrawHidingBackground(false);
                } else {
                    setDrawHidingBackground(true);
                    notifyShown();
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
    };

    private ValueAnimator.AnimatorUpdateListener mFilmstripAnimatorUpdateListener =
            new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    if (mAnimationDirection == ANIM_DIRECTION_IN && !mDrawHidingBackground) {
                        mBackgroundDrawable.setFraction(valueAnimator.getAnimatedFraction());
                    }
                    mFilmstripContentLayout.setTranslationX((Float) valueAnimator.getAnimatedValue());
                    invalidate();
                }
            };
    private Listener mListener;

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
        mFilmstripAnimator.addUpdateListener(mFilmstripAnimatorUpdateListener);
        mFilmstripAnimator.addListener(mFilmstripAnimatorListener);
    }

    @Override
    public void setFilmstripListener(Listener listener) {
        mListener = listener;
        if (getVisibility() == VISIBLE && getFilmstripTranslationX() == 0) {
            notifyShown();
        } else {
            notifyHidden(getVisibility());
        }
        mFilmstripView.getController().setListener(listener);
    }

    private float getFilmstripTranslationX() {
        return mFilmstripContentLayout.getTranslationX();
    }

    @Override
    public void hide() {
        mFilmstripContentLayout.setTranslationX(getMeasuredWidth());
        mFilmstripAnimatorListener.onAnimationEnd(mFilmstripAnimator);
    }

    @Override
    public void show() {
        mFilmstripContentLayout.setTranslationX(0);
        mFilmstripAnimatorListener.onAnimationEnd(mFilmstripAnimator);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        notifyHidden(visibility);
    }

    private void notifyHidden(int visibility) {
        if (mListener == null) {
            return;
        }
        if (visibility != VISIBLE) {
            mListener.onFilmstripHidden();
        }
    }

    private void notifyShown() {
        if (mListener == null) {
            return;
        }
        mListener.onFilmstripShown();
        FilmstripController controller = mFilmstripView.getController();
        int currentId = controller.getCurrentId();
        if (controller.inFilmstrip()) {
            mListener.onEnterFilmstrip(currentId);
        } else if (controller.inFullScreen()) {
            mListener.onEnterFullScreen(currentId);
        }
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed && mFilmstripView != null && getVisibility() == INVISIBLE) {
            hide();
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
        mBackgroundDrawable = new MyBackgroundDrawable();
        setBackground(mBackgroundDrawable);
        mFilmstripView = (FilmstripView) findViewById(R.id.filmstrip_view);
        mFilmstripView.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                // Adjust the coordinates back since they are relative to the
                // child view.
                motionEvent.setLocation(motionEvent.getX() + mFilmstripContentLayout.getX(),
                        motionEvent.getY() + mFilmstripContentLayout.getY());
                mGestureRecognizer.onTouchEvent(motionEvent);
                return true;
            }
        });
        mFilmstripGestureListener = mFilmstripView.getGestureListener();
        mFilmstripContentLayout = (FrameLayout) findViewById(R.id.camera_filmstrip_content_layout);
    }

    @Override
    public boolean onBackPressed() {
        return animateHide();
    }

    @Override
    public boolean animateHide() {
        if (getVisibility() == VISIBLE) {
            if (!mFilmstripAnimator.isRunning()) {
                hideFilmstrip();
            }
            return true;
        }
        return false;
    }

    private void hideFilmstrip() {
        mAnimationDirection = ANIM_DIRECTION_OUT;
        runAnimation(getFilmstripTranslationX(), getMeasuredWidth());
    }

    private void showFilmstrip() {
        mAnimationDirection = ANIM_DIRECTION_IN;
        runAnimation(getFilmstripTranslationX(), 0);
    }

    private void runAnimation(float begin, float end) {
        if (mFilmstripAnimator.isRunning()) {
            return;
        }
        if (begin == end) {
            // No need to start animation.
            mFilmstripAnimatorListener.onAnimationEnd(mFilmstripAnimator);
            return;
        }
        mFilmstripAnimator.setFloatValues(begin, end);
        mFilmstripAnimator.start();
    }

    private void setDrawHidingBackground(boolean hiding) {
        mDrawHidingBackground = hiding;
        if (!mDrawHidingBackground) {
            mBackgroundDrawable.setFraction(0f);
        }
    }

    /**
     * A gesture listener which passes all the gestures to the
     * {@code mFilmstripView} by default and only intercepts scroll gestures
     * when the {@code mFilmstripView} is not in full-screen.
     */
    private class MyGestureListener implements FilmstripGestureRecognizer.Listener {
        @Override
        public boolean onScroll(float x, float y, float dx, float dy) {
            if (mFilmstripView.getController().getCurrentId() == -1) {
                return true;
            }
            if (mFilmstripAnimator.isRunning()) {
                return true;
            }
            if (getFilmstripTranslationX() == 0f &&
                    mFilmstripGestureListener.onScroll(x, y, dx, dy)) {
                return true;
            }
            mSwipeTrend = (((int) dx) >> 1) + (mSwipeTrend >> 1);
            float translate = getFilmstripTranslationX() - dx;
            if (translate < 0f) {
                translate = 0f;
            } else {
                if (translate > getMeasuredWidth()) {
                    translate = getMeasuredWidth();
                }
            }
            mFilmstripContentLayout.setTranslationX(translate);
            invalidate();
            return true;
        }

        @Override
        public boolean onSingleTapUp(float x, float y) {
            if (getFilmstripTranslationX() == 0f) {
                return mFilmstripGestureListener.onSingleTapUp(x, y);
            }
            return false;
        }

        @Override
        public boolean onDoubleTap(float x, float y) {
            if (getFilmstripTranslationX() == 0f) {
                return mFilmstripGestureListener.onDoubleTap(x, y);
            }
            return false;
        }

        @Override
        public boolean onFling(float velocityX, float velocityY) {
            if (getFilmstripTranslationX() == 0f) {
                return mFilmstripGestureListener.onFling(velocityX, velocityY);
            }
            return false;
        }

        @Override
        public boolean onScaleBegin(float focusX, float focusY) {
            if (getFilmstripTranslationX() == 0f) {
                return mFilmstripGestureListener.onScaleBegin(focusX, focusY);
            }
            return false;
        }

        @Override
        public boolean onScale(float focusX, float focusY, float scale) {
            if (getFilmstripTranslationX() == 0f) {
                return mFilmstripGestureListener.onScale(focusX, focusY, scale);
            }
            return false;
        }

        @Override
        public boolean onDown(float x, float y) {
            if (mFilmstripContentLayout.getTranslationX() == 0f) {
                return mFilmstripGestureListener.onDown(x, y);
            }
            return false;
        }

        @Override
        public boolean onUp(float x, float y) {
            if (mFilmstripContentLayout.getTranslationX() == 0f) {
                return mFilmstripGestureListener.onUp(x, y);
            }
            if (mSwipeTrend < 0) {
                hideFilmstrip();
            } else if (mSwipeTrend > 0) {
                showFilmstrip();
            } else {
                if (mFilmstripContentLayout.getTranslationX() >= getMeasuredWidth() / 2) {
                    hideFilmstrip();
                } else {
                    showFilmstrip();
                }
            }
            mSwipeTrend = 0;
            return false;
        }

        @Override
        public void onScaleEnd() {
            if (mFilmstripContentLayout.getTranslationX() == 0f) {
                mFilmstripGestureListener.onScaleEnd();
            }
        }
    }

    private class MyBackgroundDrawable extends Drawable {
        private Paint mPaint;
        private float mFraction;

        public MyBackgroundDrawable() {
            mPaint = new Paint();
            mPaint.setColor(0);
            mPaint.setAlpha(255);
        }

        public void setFraction(float f) {
            mFraction = f;
        }

        @Override
        public void setAlpha(int i) {
            mPaint.setAlpha(i);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            mPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void draw(Canvas canvas) {
            int width = getMeasuredWidth();
            float translation = mFilmstripContentLayout.getTranslationX();
            if (translation == width) {
                return;
            }
            canvas.drawColor(Color.argb((int) (127 * (width - translation) / width), 0, 0, 0));
            if (mDrawHidingBackground) {
                drawHiding(canvas);
            } else {
                drawShowing(canvas);
            }
        }

        private void drawHiding(Canvas canvas) {
            canvas.drawRect(
                    mFilmstripContentLayout.getLeft() + mFilmstripContentLayout.getTranslationX(),
                    mFilmstripContentLayout.getTop() + mFilmstripContentLayout.getTranslationY(),
                    getMeasuredWidth(), getMeasuredHeight(), mPaint);
        }

        private void drawShowing(Canvas canvas) {
            int width = getMeasuredWidth();
            float translation = mFilmstripContentLayout.getTranslationX();
            if (translation == 0f) {
                canvas.drawRect(getBounds(), mPaint);
                return;
            }
            final float height = getMeasuredHeight();
            float x = width * (1.1f + mFraction * 0.9f);
            float y = height / 2f;
            float refX = width * (1 - mFraction);
            float refY = y * (1 - mFraction);
            canvas.drawCircle(x, getMeasuredHeight() / 2,
                    FloatMath.sqrt((x - refX) * (x - refX) + (y - refY) * (y - refY)), mPaint);
        }
    }
}
