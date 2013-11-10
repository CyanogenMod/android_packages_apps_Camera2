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
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.android.camera.util.Gusterpolator;
import com.android.camera2.R;

import java.util.ArrayList;

public class ModeListView extends ScrollView {
    private static final int DEFAULT_DURATION = 200;
    private static final int FLY_IN_DURATION_MS = 850;
    private static final int HOLD_DURATION_MS = 0;
    private static final int FLY_OUT_DURATION_MS = 850;
    private static final int START_DELAY_MS = 100;
    private static final int TOTAL_DURATION_MS = FLY_IN_DURATION_MS + HOLD_DURATION_MS
            + FLY_OUT_DURATION_MS;

    public static final int MODE_PHOTO = 0;
    public static final int MODE_VIDEO = 1;
    public static final int MODE_PHOTOSPHERE = 2;
    public static final int MODE_CRAFT = 3;
    public static final int MODE_TIMELAPSE = 4;
    public static final int MODE_WIDEANGLE = 5;
    public static final int MODE_SETTING = 6;
    // Special case
    public static final int MODE_GCAM = 100;

    private static final int MODE_TOTAL = 7;
    private static final float ROWS_TO_SHOW_IN_LANDSCAPE = 4.5f;

    // Scrolling states
    private static final int IDLE = 0;
    private static final int FULLY_SHOWN = 1;
    private static final int ACCORDION_ANIMATION = 2;
    private static final int SCROLLING = 3;

    private LinearLayout mListView;

    private int mState = IDLE;
    private int mTotalModes;
    private ModeSelectorItem[] mList;
    private AnimatorSet mAnimatorSet;
    private int mVisibleModes;

    private final int[] mResId = {R.drawable.photo, R.drawable.video,
            R.drawable.photosphere, R.drawable.craft, R.drawable.timelapse,
            R.drawable.wideangle, R.drawable.settings,};

    private final int[] mTextResId = {R.string.mode_camera, R.string.mode_video,
            R.string.mode_photosphere, R.string.mode_craft, R.string.mode_timelapse,
            R.string.mode_wideangle, R.string.mode_settings};

    private final int[] mBackgroundColor = {R.color.camera_mode_color,
            R.color.video_mode_color, R.color.photosphere_mode_color, R.color.craft_mode_color,
            R.color.timelapse_mode_color, R.color.wideangle_mode_color,
            R.color.settings_mode_color};

    private static final int NO_ITEM_SELECTED = -1;
    private int mFocusItem = NO_ITEM_SELECTED;
    private int mWidth;
    private int mHeight;
    private float mScrollTrendX = 0f;
    private float mScrollTrendY = 0f;
    private final GestureDetector mGestureDetector;
    private ModeSwitchListener mListener = null;
    private final int mSnapToFullScreenThreshold;

    public interface ModeSwitchListener {
        public void onModeSelected(int modeIndex);
    }

    private TimeInterpolator mAccordionInterpolator = new TimeInterpolator() {
        @Override
        public float getInterpolation(float input) {

            float flyInDuration = (float) FLY_OUT_DURATION_MS / (float) TOTAL_DURATION_MS;
            float holdDuration = (float) (FLY_OUT_DURATION_MS + HOLD_DURATION_MS)
                    / (float) TOTAL_DURATION_MS;
            if (input == 0) {
                return 0;
            }else if (input < flyInDuration) {
                // Project result to [0f, 0.5f]
                input /= flyInDuration;
                float result = Gusterpolator.INSTANCE.getInterpolation(input);
                return result * 0.5f;
            } else if (input < holdDuration) {
                return 0.5f;
            } else {
                // Project result to [0.5f, 1f]
                input -= holdDuration;
                input /= (1 - holdDuration);
                float result = Gusterpolator.INSTANCE.getInterpolation(input);
                return 0.5f + result * 0.5f;
            }
        }
    };

    private GestureDetector.OnGestureListener mOnGestureListener
            = new GestureDetector.SimpleOnGestureListener(){
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                float distanceX, float distanceY) {

            if (mState == ACCORDION_ANIMATION) {
                if (isRunningAccordionAnimation()) {
                    mAnimatorSet.cancel();
                }
                setVisibility(VISIBLE);
            }

            if (mState == IDLE) {
                resetModeSelectors();
                setVisibility(VISIBLE);
            }

            mState = SCROLLING;
            scroll(mFocusItem, distanceX, distanceY);
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent ev) {
            if (mState != FULLY_SHOWN) {
                return false;
            }
            int index = getFocusItem(ev.getX(), ev.getY());
            if (index != NO_ITEM_SELECTED) {
                onModeSelected(index);
            }
            return true;
        }
    };

    public ModeListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mGestureDetector = new GestureDetector(context, mOnGestureListener);
        mSnapToFullScreenThreshold = getResources()
                .getDimensionPixelSize(R.dimen.mode_selector_icon_block_width);
    }

    @Override
    public void onFinishInflate() {
        // TODO: Total modes will need to be dynamically queried in the beginning of
        // app startup.
        mTotalModes = MODE_TOTAL;
        mList = new ModeSelectorItem[mTotalModes];
        LayoutInflater inflater = (LayoutInflater) getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mListView = (LinearLayout) findViewById(R.id.mode_list);
        for (int i = 0; i < mTotalModes; i++) {
            ModeSelectorItem selectorItem =
                    (ModeSelectorItem) inflater.inflate(R.layout.mode_selector, null);
            mListView.addView(selectorItem);

            if (i % 2 == 0) {
                selectorItem.setBackgroundColor(getResources()
                        .getColor(R.color.mode_selector_background_light));
            } else {
                selectorItem.setBackgroundColor(getResources()
                        .getColor(R.color.mode_selector_background_dark));
            }

            selectorItem.setIconBackgroundColor(getResources().getColor(mBackgroundColor[i]));

            // Set image
            // TODO: Down-sampling here is temporary, will be removed when we get assets
            // from UX. The goal will be to fit the icon into 32dp x 32dp rect.
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = 4;
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), mResId[i], opt);
            selectorItem.setImageBitmap(bitmap);
            // Set text
            CharSequence text = getResources().getText(mTextResId[i]);
            selectorItem.setText(text);
            mList[i] = selectorItem;
        }

        resetModeSelectors();
    }

    private void onModeSelected(int modeIndex) {
        // Notify listener
        if (mListener != null) {
            mListener.onModeSelected(modeIndex);
        }
        // TODO: There will be another animation indicating selection
        // for now, just snap back.
        snapBack();
    }

    /**
     * Sets a listener that listens to receive mode switch event
     * @param listener A listener that gets notified when mode changes.
     */
    public void setModeSwitchListener(ModeSwitchListener listener) {
        mListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        super.onTouchEvent(ev);
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
            if (mState == FULLY_SHOWN) {
                mFocusItem = NO_ITEM_SELECTED;
                setSwipeMode(false);
            } else {
                mFocusItem = getFocusItem(ev.getX(), ev.getY());
                setSwipeMode(true);
            }
        }
        mGestureDetector.onTouchEvent(ev);
        if (ev.getActionMasked() == MotionEvent.ACTION_UP ||
                ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            snap();
            mFocusItem = NO_ITEM_SELECTED;
        }
        return true;
    }

    /**
     * Sets the swipe mode to indicate whether this is a swiping in
     * or out, and therefore we can have different animations.
     * @param swipeIn indicates whether the swipe should reveal/hide the list
     */
    private void setSwipeMode(boolean swipeIn) {
        for (int i = 0 ; i < mList.length; i++) {
            mList[i].onSwipeModeChanged(swipeIn);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mWidth = right - left;
        mHeight = bottom - top - getPaddingTop() - getPaddingBottom();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float height = MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop()
                - getPaddingBottom();

        Configuration config = getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            height = height / ROWS_TO_SHOW_IN_LANDSCAPE;
            mVisibleModes = (int) Math.ceil(ROWS_TO_SHOW_IN_LANDSCAPE);
        } else {
            height = height / MODE_TOTAL;
            mVisibleModes = MODE_TOTAL;
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(mWidth, 0);
        lp.width = LayoutParams.MATCH_PARENT;
        for (int i = 0; i < mTotalModes; i++) {
            // This is to avoid rounding that would cause the total height of the
            // list a few pixels off the height of the screen.
            int itemHeight = (int) (height * (i + 1)) - (int) (height * i);
            lp.height = itemHeight;
            mList[i].setLayoutParams(lp);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void startAccordionAnimation() {
        if (mAnimatorSet != null && mAnimatorSet.isRunning()) {
            return;
        }
        mState = ACCORDION_ANIMATION;
        int width = getResources().getDimensionPixelSize(R.dimen.mode_selector_icon_block_width);
        resetModeSelectors();
        animateListToWidth(START_DELAY_MS, TOTAL_DURATION_MS, mAccordionInterpolator, 0, width, 0);
    }

    private void resetModeSelectors() {
        for (int i = 0; i < mList.length; i++) {
            mList[i].setVisibleWidth(0);
        }
    }

    private boolean isRunningAccordionAnimation() {
        return mAnimatorSet != null && mAnimatorSet.isRunning();
    }

    private int getFocusItem(float x, float y) {
        x += getScrollX();
        y += getScrollY();

        for (int i = 0; i < mList.length; i++) {
            if (mList[i].getTop() <= y && mList[i].getBottom() >= y) {
                return i;
            }
        }
        return NO_ITEM_SELECTED;
    }

    private void scroll(int itemId, float deltaX, float deltaY) {
        mScrollTrendX = mScrollTrendX * 0.3f + deltaX * 0.7f;
        mScrollTrendY = mScrollTrendY * 0.3f + deltaY * 0.7f;

        float longestWidth;
        if (itemId >= 0) {
            longestWidth = mList[itemId].getVisibleWidth() - deltaX;
        } else {
            longestWidth = mList[0].getVisibleWidth() - deltaX;
        }

        for (int i = 0; i < mList.length; i++) {
            float factor = itemId < 0 ? 1f : (float) Math.pow(0.8, Math.abs(itemId - i));
            mList[i].setVisibleWidth((int) (longestWidth * factor));
        }
        if (longestWidth <= 0) {
            reset();
        }
    }

    private void reset() {
        resetModeSelectors();
        mScrollTrendX = 0f;
        mScrollTrendY = 0f;
        setVisibility(INVISIBLE);
    }

    /**
     * The list view should either snap back or snap to full screen after a gesture.
     * This function is called when an up or cancel event is received, and then based
     * on the current position of the list and the gesture we can decide which way
     * to snap.
     */
    private void snap() {
        if (mState == SCROLLING) {
            int itemId = Math.max(0, mFocusItem);
            if (mList[itemId].getVisibleWidth() < mSnapToFullScreenThreshold) {
                snapBack();
            } else if (Math.abs(mScrollTrendX) > Math.abs(mScrollTrendY) && mScrollTrendX > 0) {
                snapBack();
            } else {
                goToFullScreen();
            }
        }
    }

    private void snapBack() {
        animateListToWidth(0);
        mState = IDLE;
    }

    private void goToFullScreen() {
        animateListToWidth(mWidth);
        mState = FULLY_SHOWN;
    }

    private void animateListToWidth(int... width) {
        animateListToWidth(0, DEFAULT_DURATION, null, width);
    }

    private void animateListToWidth(int delay, int duration,
                                    TimeInterpolator interpolator, int... width) {
        if (mAnimatorSet != null && mAnimatorSet.isRunning()) {
            mAnimatorSet.end();
        }

        ArrayList<Animator> animators = new ArrayList<Animator>();
        for (int i = 0; i < mTotalModes; i++) {
            ObjectAnimator animator = ObjectAnimator.ofInt(mList[i], "visibleWidth", width);
            animator.setDuration(duration);
            animator.setStartDelay(i * delay);
            animators.add(animator);
        }

        // TODO: Need to dim the background in the mean time.

        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.playTogether(animators);
        mAnimatorSet.setInterpolator(interpolator);
        mAnimatorSet.addListener(new Animator.AnimatorListener() {

            @Override
            public void onAnimationStart(Animator animation) {
                setVisibility(VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimatorSet = null;
                if (mState == ACCORDION_ANIMATION || mState == IDLE) {
                    resetModeSelectors();
                    setVisibility(INVISIBLE);
                    mState = IDLE;
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        mAnimatorSet.start();
    }
}
