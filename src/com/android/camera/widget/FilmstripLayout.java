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
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;

import com.android.camera.filmstrip.FilmstripContentPanel;
import com.android.camera.filmstrip.FilmstripController;
import com.android.camera.ui.FilmstripGestureRecognizer;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.Gusterpolator;
import com.android.camera2.R;

/**
 * A {@link android.widget.FrameLayout} used for the parent layout of a
 * {@link com.android.camera.widget.FilmstripView} to support animating in/out the
 * filmstrip.
 */
public class FilmstripLayout extends FrameLayout implements FilmstripContentPanel {

    private static final long DEFAULT_DURATION_MS = 250;
    /**
     *  If the fling velocity exceeds this threshold, open filmstrip at a constant
     *  speed. Unit: pixel/ms.
     */
    private static final float FLING_VELOCITY_THRESHOLD = 4.0f;

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
    private FilmstripBackground mBackgroundDrawable;
    private Handler mHandler;
    // We use this to record the current translation position instead of using
    // the real value because we might set the translation before onMeasure()
    // thus getMeasuredWidth() can be 0.
    private float mFilmstripContentTranslationProgress;

    private Animator.AnimatorListener mFilmstripAnimatorListener = new Animator.AnimatorListener() {
        private boolean mCanceled;

        @Override
        public void onAnimationStart(Animator animator) {
            mCanceled = false;
        }

        @Override
        public void onAnimationEnd(Animator animator) {
            if (!mCanceled) {
                if (mFilmstripContentTranslationProgress != 0f) {
                    mFilmstripView.getController().goToFilmstrip();
                    setVisibility(INVISIBLE);
                } else {
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
                    translateContentLayout((Float) valueAnimator.getAnimatedValue());
                    mBackgroundDrawable.invalidateSelf();
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
        mGestureRecognizer = new FilmstripGestureRecognizer(context, new OpenFilmstripGesture());
        mFilmstripAnimator.setDuration(DEFAULT_DURATION_MS);
        TimeInterpolator interpolator;
        if (ApiHelper.isLOrHigher()) {
            interpolator = AnimationUtils.loadInterpolator(
                    getContext(), android.R.interpolator.fast_out_slow_in);
        } else {
            interpolator = Gusterpolator.INSTANCE;
        }
        mFilmstripAnimator.setInterpolator(interpolator);
        mFilmstripAnimator.addUpdateListener(mFilmstripAnimatorUpdateListener);
        mFilmstripAnimator.addListener(mFilmstripAnimatorListener);
        mHandler = new Handler(Looper.getMainLooper());
        mBackgroundDrawable = new FilmstripBackground();
        mBackgroundDrawable.setCallback(new Drawable.Callback() {
            @Override
            public void invalidateDrawable(Drawable drawable) {
                FilmstripLayout.this.invalidate();
            }

            @Override
            public void scheduleDrawable(Drawable drawable, Runnable runnable, long l) {
                mHandler.postAtTime(runnable, drawable, l);
            }

            @Override
            public void unscheduleDrawable(Drawable drawable, Runnable runnable) {
                mHandler.removeCallbacks(runnable, drawable);
            }
        });
        setBackground(mBackgroundDrawable);
    }

    @Override
    public void setFilmstripListener(Listener listener) {
        mListener = listener;
        if (getVisibility() == VISIBLE && mFilmstripContentTranslationProgress == 0f) {
            notifyShown();
        } else {
            if (getVisibility() != VISIBLE) {
                notifyHidden();
            }
        }
        mFilmstripView.getController().setListener(listener);
    }

    @Override
    public void hide() {
        translateContentLayout(1f);
        mFilmstripAnimatorListener.onAnimationEnd(mFilmstripAnimator);
    }

    @Override
    public void show() {
        translateContentLayout(0f);
        mFilmstripAnimatorListener.onAnimationEnd(mFilmstripAnimator);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility != VISIBLE) {
            notifyHidden();
        }
    }

    private void notifyHidden() {
        if (mListener == null) {
            return;
        }
        mListener.onFilmstripHidden();
    }

    private void notifyShown() {
        if (mListener == null) {
            return;
        }
        mListener.onFilmstripShown();
        mFilmstripView.zoomAtIndexChanged();
        FilmstripController controller = mFilmstripView.getController();
        int currentId = controller.getCurrentAdapterIndex();
        if (controller.inFilmstrip()) {
            mListener.onEnterFilmstrip(currentId);
        } else if (controller.inFullScreen()) {
            mListener.onEnterFullScreenUiShown(currentId);
        }
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed && mFilmstripView != null && getVisibility() == INVISIBLE) {
            hide();
        } else {
            translateContentLayout(mFilmstripContentTranslationProgress);
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

    public void hideFilmstrip() {
        // run the same view show/hides and animations
        // that happen with a swipe gesture.
        onSwipeOutBegin();
        runAnimation(mFilmstripContentTranslationProgress, 1f);
    }

    public void showFilmstrip() {
        setVisibility(VISIBLE);
        runAnimation(mFilmstripContentTranslationProgress, 0f);
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

    private void translateContentLayout(float fraction) {
        mFilmstripContentTranslationProgress = fraction;
        mFilmstripContentLayout.setTranslationX(fraction * getMeasuredWidth());
    }

    private void translateContentLayoutByPixel(float pixel) {
        mFilmstripContentLayout.setTranslationX(pixel);
        mFilmstripContentTranslationProgress = pixel / getMeasuredWidth();
    }

    private void onSwipeOut() {
        if (mListener != null) {
            mListener.onSwipeOut();
        }
    }

    private void onSwipeOutBegin() {
        if (mListener != null) {
            mListener.onSwipeOutBegin();
        }
    }

    /**
     * A gesture listener which passes all the gestures to the
     * {@code mFilmstripView} by default and only intercepts scroll gestures
     * when the {@code mFilmstripView} is not in full-screen.
     */
    private class OpenFilmstripGesture implements FilmstripGestureRecognizer.Listener {
        @Override
        public boolean onScroll(float x, float y, float dx, float dy) {
            if (mFilmstripView.getController().getCurrentAdapterIndex() == -1) {
                return true;
            }
            if (mFilmstripAnimator.isRunning()) {
                return true;
            }
            if (mFilmstripContentLayout.getTranslationX() == 0f &&
                    mFilmstripGestureListener.onScroll(x, y, dx, dy)) {
                return true;
            }
            mSwipeTrend = (((int) dx) >> 1) + (mSwipeTrend >> 1);
            if (dx < 0 && mFilmstripContentLayout.getTranslationX() == 0) {
                mBackgroundDrawable.setOffset(0);
                FilmstripLayout.this.onSwipeOutBegin();
            }

            // When we start translating the filmstrip in, we want the left edge of the
            // first view to always be at the rightmost edge of the screen so that it
            // appears instantly, regardless of the view's distance from the edge of the
            // filmstrip view. To do so, on our first translation, jump the filmstrip view
            // to the correct position, and then smoothly animate the translation from that
            // initial point.
            if (dx > 0 && mFilmstripContentLayout.getTranslationX() == getMeasuredWidth()) {
                final int currentItemLeft = mFilmstripView.getCurrentItemLeft();
                dx = currentItemLeft;
                mBackgroundDrawable.setOffset(currentItemLeft);
            }

            float translate = mFilmstripContentLayout.getTranslationX() - dx;
            if (translate < 0f) {
                translate = 0f;
            } else {
                if (translate > getMeasuredWidth()) {
                    translate = getMeasuredWidth();
                }
            }
            translateContentLayoutByPixel(translate);
            if (translate == 0 && dx > 0) {
                // This will only happen once since when this condition holds
                // the onScroll() callback will be forwarded to the filmstrip
                // view.
                mFilmstripAnimatorListener.onAnimationEnd(mFilmstripAnimator);
            }
            mBackgroundDrawable.invalidateSelf();
            return true;
        }

        @Override
        public boolean onMouseScroll(float hscroll, float vscroll) {
            if (mFilmstripContentTranslationProgress == 0f) {
                return mFilmstripGestureListener.onMouseScroll(hscroll, vscroll);
            }
            return false;
        }

        @Override
        public boolean onSingleTapUp(float x, float y) {
            if (mFilmstripContentTranslationProgress == 0f) {
                return mFilmstripGestureListener.onSingleTapUp(x, y);
            }
            return false;
        }

        @Override
        public boolean onDoubleTap(float x, float y) {
            if (mFilmstripContentTranslationProgress == 0f) {
                return mFilmstripGestureListener.onDoubleTap(x, y);
            }
            return false;
        }

        /**
         * @param velocityX The fling velocity in the X direction.
         * @return Whether the filmstrip should be opened,
         * given velocityX and mSwipeTrend.
         */
        private boolean flingShouldOpenFilmstrip(float velocityX) {
            return (mSwipeTrend > 0) &&
                    (velocityX < 0.0f) &&
                    (Math.abs(velocityX / 1000.0f) > FLING_VELOCITY_THRESHOLD);
        }

        @Override
        public boolean onFling(float velocityX, float velocityY) {
            if (mFilmstripContentTranslationProgress == 0f) {
                return mFilmstripGestureListener.onFling(velocityX, velocityY);
            } else if (flingShouldOpenFilmstrip(velocityX)) {
                showFilmstrip();
                return true;
            }

            return false;
        }

        @Override
        public boolean onScaleBegin(float focusX, float focusY) {
            if (mFilmstripContentTranslationProgress == 0f) {
                return mFilmstripGestureListener.onScaleBegin(focusX, focusY);
            }
            return false;
        }

        @Override
        public boolean onScale(float focusX, float focusY, float scale) {
            if (mFilmstripContentTranslationProgress == 0f) {
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
                onSwipeOut();
            } else {
                if (mFilmstripContentLayout.getTranslationX() >= getMeasuredWidth() / 2) {
                    hideFilmstrip();
                    onSwipeOut();
                } else {
                    showFilmstrip();
                }
            }
            mSwipeTrend = 0;
            return false;
        }

        @Override
        public void onLongPress(float x, float y) {
            mFilmstripGestureListener.onLongPress(x, y);
        }

        @Override
        public void onScaleEnd() {
            if (mFilmstripContentLayout.getTranslationX() == 0f) {
                mFilmstripGestureListener.onScaleEnd();
            }
        }
    }

    private class FilmstripBackground extends Drawable {
        private Paint mPaint;
        private int mOffset;

        public FilmstripBackground() {
            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setColor(getResources().getColor(R.color.camera_gray_background));
            mPaint.setAlpha(255);
        }

        /**
         * Adjust the target width and translation calculation when we start translating
         * from a point where width != translationX so that alpha scales smoothly.
         */
        public void setOffset(int offset) {
            mOffset = offset;
        }

        @Override
        public void setAlpha(int i) {
            mPaint.setAlpha(i);
        }

        private void setAlpha(float a) {
            setAlpha((int) (a*255.0f));
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
            int width = getMeasuredWidth() - mOffset;
            float translation = mFilmstripContentLayout.getTranslationX() - mOffset;
            if (translation == width) {
                return;
            }

            setAlpha(1.0f - mFilmstripContentTranslationProgress);
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), mPaint);
        }
    }
}
