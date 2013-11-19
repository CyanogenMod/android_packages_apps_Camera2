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
import android.animation.ValueAnimator;
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

/**
 * ModeListView class displays all camera modes and settings in the form
 * of a list. A swipe to the right will bring up this list. Then tapping on
 * any of the items in the list will take the user to that corresponding mode
 * with an animation. To dismiss this list, simply swipe left or select a mode.
 */
public class ModeListView extends ScrollView {
    private static final String TAG = "ModeListView";

    // Animation Durations
    private static final int DEFAULT_DURATION_MS = 200;
    private static final int FLY_IN_DURATION_MS = 850;
    private static final int HOLD_DURATION_MS = 0;
    private static final int FLY_OUT_DURATION_MS = 850;
    private static final int START_DELAY_MS = 100;
    private static final int TOTAL_DURATION_MS = FLY_IN_DURATION_MS + HOLD_DURATION_MS
            + FLY_OUT_DURATION_MS;

    // Different modes in the mode list
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
    private static final int NO_ITEM_SELECTED = -1;

    // Scrolling states
    private static final int IDLE = 0;
    private static final int FULLY_SHOWN = 1;
    private static final int ACCORDION_ANIMATION = 2;
    private static final int SCROLLING = 3;

    private final int[] mIconResId = {R.drawable.photo, R.drawable.video,
            R.drawable.photosphere, R.drawable.craft, R.drawable.timelapse,
            R.drawable.wideangle, R.drawable.settings,};

    private final int[] mTextResId = {R.string.mode_camera, R.string.mode_video,
            R.string.mode_photosphere, R.string.mode_craft, R.string.mode_timelapse,
            R.string.mode_wideangle, R.string.mode_settings};

    private final int[] mIconBlockColor = {R.color.camera_mode_color,
            R.color.video_mode_color, R.color.photosphere_mode_color, R.color.craft_mode_color,
            R.color.timelapse_mode_color, R.color.wideangle_mode_color,
            R.color.settings_mode_color};

    private final GestureDetector mGestureDetector;
    private final int mIconBlockWidth;

    private int mListBackgroundColor;
    private LinearLayout mListView;
    private int mState = IDLE;
    private int mTotalModes;
    private ModeSelectorItem[] mModeSelectorItems;
    private AnimatorSet mAnimatorSet;
    private int mFocusItem = NO_ITEM_SELECTED;

    // Width and height of this view. They get updated in onLayout()
    // Unit for width and height are pixels.
    private int mWidth;
    private int mHeight;
    private float mScrollTrendX = 0f;
    private float mScrollTrendY = 0f;
    private ModeSwitchListener mListener = null;

    public interface ModeSwitchListener {
        public void onModeSelected(int modeIndex);
    }

    /**
     * This is a highly customized interpolator. The purpose of having this subclass
     * is to encapsulate intricate animation timing, so that the actual animation
     * implementation can be re-used with other interpolators to achieve different
     * animation effects.
     *
     * The accordion animation consists of three stages:
     * 1) Animate into the screen within a pre-specified fly in duration.
     * 2) Hold in place for a certain amount of time (Optional).
     * 3) Animate out of the screen within the given time.
     *
     * The accordion animator is initialized with 3 parameter: 1) initial position,
     * 2) how far out the view should be before flying back out,  3) end position.
     * The interpolation output should be [0f, 0.5f] during animation between 1)
     * to 2), and [0.5f, 1f] for flying from 2) to 3).
     */
    private TimeInterpolator mAccordionInterpolator = new TimeInterpolator() {
        @Override
        public float getInterpolation(float input) {

            float flyInDuration = (float) FLY_OUT_DURATION_MS / (float) TOTAL_DURATION_MS;
            float holdDuration = (float) (FLY_OUT_DURATION_MS + HOLD_DURATION_MS)
                    / (float) TOTAL_DURATION_MS;
            if (input == 0) {
                return 0;
            }else if (input < flyInDuration) {
                // Stage 1, project result to [0f, 0.5f]
                input /= flyInDuration;
                float result = Gusterpolator.INSTANCE.getInterpolation(input);
                return result * 0.5f;
            } else if (input < holdDuration) {
                // Stage 2
                return 0.5f;
            } else {
                // Stage 3, project result to [0.5f, 1f]
                input -= holdDuration;
                input /= (1 - holdDuration);
                float result = Gusterpolator.INSTANCE.getInterpolation(input);
                return 0.5f + result * 0.5f;
            }
        }
    };

    /**
     * The listener that is used to notify when gestures occur.
     * Here we only listen to a subset of gestures.
     */
    private GestureDetector.OnGestureListener mOnGestureListener
            = new GestureDetector.SimpleOnGestureListener(){
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                float distanceX, float distanceY) {

            if (mState == ACCORDION_ANIMATION) {
                // Scroll happens during accordion animation.
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
            // Scroll based on the scrolling distance on the currently focused
            // item.
            scroll(mFocusItem, distanceX, distanceY);
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent ev) {
            if (mState != FULLY_SHOWN) {
                // Only allows tap to choose mode when the list is fully shown
                return false;
            }
            int index = getFocusItem(ev.getX(), ev.getY());
            // Validate the selection
            if (index != NO_ITEM_SELECTED) {
                onModeSelected(index);
            }
            return true;
        }
    };

    public ModeListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mGestureDetector = new GestureDetector(context, mOnGestureListener);
        mIconBlockWidth = getResources()
                .getDimensionPixelSize(R.dimen.mode_selector_icon_block_width);
        mListBackgroundColor = getResources().getColor(R.color.mode_list_background);
    }

    /**
     * Sets the alpha on the list background. This is called whenever the list
     * is scrolling or animating, so that background can adjust its dimness.
     *
     * @param alpha new alpha to be applied on list background color
     */
    private void setBackgroundAlpha(int alpha) {
        // Make sure alpha is valid.
        alpha = alpha & 0xFF;
        // Change alpha on the background color.
        mListBackgroundColor = mListBackgroundColor & 0xFFFFFF;
        mListBackgroundColor = mListBackgroundColor | (alpha << 24);
        // Set new color to list background.
        mListView.setBackgroundColor(mListBackgroundColor);
    }

    @Override
    public void onFinishInflate() {
        // TODO: Total modes will need to be dynamically queried in the beginning of
        // app startup.
        mTotalModes = MODE_TOTAL;
        mModeSelectorItems = new ModeSelectorItem[mTotalModes];

        // Inflate the mode selector items and add them to a linear layout
        LayoutInflater inflater = (LayoutInflater) getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mListView = (LinearLayout) findViewById(R.id.mode_list);
        for (int i = 0; i < mTotalModes; i++) {
            ModeSelectorItem selectorItem =
                    (ModeSelectorItem) inflater.inflate(R.layout.mode_selector, null);
            mListView.addView(selectorItem);

            // Set alternating background color for each mode selector in the list
            if (i % 2 == 0) {
                selectorItem.setBackgroundColor(getResources()
                        .getColor(R.color.mode_selector_background_light));
            } else {
                selectorItem.setBackgroundColor(getResources()
                        .getColor(R.color.mode_selector_background_dark));
            }
            selectorItem.setIconBackgroundColor(getResources().getColor(mIconBlockColor[i]));

            // Set image
            // TODO: Down-sampling here is temporary, will be removed when we get assets
            // from UX. The goal will be to fit the icon into 32dp x 32dp rect.
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = 4;
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), mIconResId[i], opt);
            selectorItem.setImageBitmap(bitmap);

            // Set text
            CharSequence text = getResources().getText(mTextResId[i]);
            selectorItem.setText(text);
            mModeSelectorItems[i] = selectorItem;
        }

        resetModeSelectors();
    }

    /** Notify ModeSwitchListener, if any, of the mode change. */
    private void onModeSelected(int modeIndex) {
        if (mListener != null) {
            mListener.onModeSelected(modeIndex);
        }
        // TODO: There will be another animation indicating selection
        // for now, just snap back.
        snapBack();
    }

    /**
     * Sets a listener that listens to receive mode switch event.
     *
     * @param listener a listener that gets notified when mode changes.
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
        // Pass all touch events to gesture detector for gesture handling.
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
     *
     * @param swipeIn indicates whether the swipe should reveal/hide the list.
     */
    private void setSwipeMode(boolean swipeIn) {
        for (int i = 0 ; i < mModeSelectorItems.length; i++) {
            mModeSelectorItems[i].onSwipeModeChanged(swipeIn);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mWidth = right - left;
        mHeight = bottom - top - getPaddingTop() - getPaddingBottom();
    }

    /**
     * Here we calculate the children size based on the orientation, change
     * their layout parameters if needed before propagating onMeasure call
     * to the children, so the newly changed params will take effect in this
     * pass.
     *
     * @param widthMeasureSpec Horizontal space requirements as imposed by the
     *        parent
     * @param heightMeasureSpec Vertical space requirements as imposed by the
     *        parent
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float height = MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop()
                - getPaddingBottom();

        Configuration config = getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            height = height / ROWS_TO_SHOW_IN_LANDSCAPE;
        } else {
            height = height / MODE_TOTAL;
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(mWidth, 0);
        lp.width = LayoutParams.MATCH_PARENT;
        for (int i = 0; i < mTotalModes; i++) {
            // This is to avoid rounding that would cause the total height of the
            // list a few pixels off the height of the screen.
            int itemHeight = (int) (height * (i + 1)) - (int) (height * i);
            lp.height = itemHeight;
            mModeSelectorItems[i].setLayoutParams(lp);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * This starts the accordion animation, unless it's already running, in which
     * case the start animation call will be ignored.
     */
    public void startAccordionAnimation() {
        if (mAnimatorSet != null && mAnimatorSet.isRunning()) {
            return;
        }
        mState = ACCORDION_ANIMATION;
        resetModeSelectors();
        animateListToWidth(START_DELAY_MS, TOTAL_DURATION_MS, mAccordionInterpolator,
                0, mIconBlockWidth, 0);
    }

    /**
     * Resets the visible width of all the mode selectors to 0.
     */
    private void resetModeSelectors() {
        for (int i = 0; i < mModeSelectorItems.length; i++) {
            mModeSelectorItems[i].setVisibleWidth(0);
        }
    }

    private boolean isRunningAccordionAnimation() {
        return mAnimatorSet != null && mAnimatorSet.isRunning();
    }

    /**
     * Calculate the mode selector item in the list that is at position (x, y).
     *
     * @param x horizontal position
     * @param y vertical position
     * @return index of the item that is at position (x, y)
     */
    private int getFocusItem(float x, float y) {
        // Take into account the scrolling offset
        x += getScrollX();
        y += getScrollY();

        for (int i = 0; i < mModeSelectorItems.length; i++) {
            if (mModeSelectorItems[i].getTop() <= y && mModeSelectorItems[i].getBottom() >= y) {
                return i;
            }
        }
        return NO_ITEM_SELECTED;
    }

    private void scroll(int itemId, float deltaX, float deltaY) {
        // Scrolling trend on X and Y axis, to track the trend by biasing
        // towards latest touch events.
        mScrollTrendX = mScrollTrendX * 0.3f + deltaX * 0.7f;
        mScrollTrendY = mScrollTrendY * 0.3f + deltaY * 0.7f;

        // TODO: Change how the curve is calculated below when UX finalize their design.
        float longestWidth;
        if (itemId != NO_ITEM_SELECTED) {
            longestWidth = mModeSelectorItems[itemId].getVisibleWidth() - deltaX;
        } else {
            longestWidth = mModeSelectorItems[0].getVisibleWidth() - deltaX;
        }

        for (int i = 0; i < mModeSelectorItems.length; i++) {
            // Only form a curve for swiping in.
            float factor = itemId == NO_ITEM_SELECTED ? 1f
                    : (float) Math.pow(0.8, Math.abs(itemId - i));
            mModeSelectorItems[i].setVisibleWidth((int) (longestWidth * factor));
        }
        if (longestWidth <= 0) {
            reset();
        }

        itemId = itemId == NO_ITEM_SELECTED ? 0 : itemId;
        onVisibleWidthChanged(mModeSelectorItems[itemId].getVisibleWidth());
    }

    private void reset() {
        resetModeSelectors();
        mScrollTrendX = 0f;
        mScrollTrendY = 0f;
        setVisibility(INVISIBLE);
    }

    /**
     * When visible width of list is changed, the background of the list needs
     * to darken/lighten correspondingly.
     */
    private void onVisibleWidthChanged(int focusItemWidth) {
        // Background alpha should be 0 before the icon block is entirely visible,
        // and when the longest mode item is entirely shown (across the screen), the
        // background should be 50% transparent.
        if (focusItemWidth <= mIconBlockWidth) {
            setBackgroundAlpha(0);
        } else {
            // Alpha should increase linearly when mode item goes beyond the icon block
            // till it reaches its max width
            int alpha = 127 * (focusItemWidth - mIconBlockWidth) / (mWidth - mIconBlockWidth);
            setBackgroundAlpha(alpha);
        }
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
            if (mModeSelectorItems[itemId].getVisibleWidth() < mIconBlockWidth) {
                snapBack();
            } else if (Math.abs(mScrollTrendX) > Math.abs(mScrollTrendY) && mScrollTrendX > 0) {
                snapBack();
            } else {
                snapToFullScreen();
            }
        }
    }

    private void snapBack() {
        animateListToWidth(0);
        mState = IDLE;
    }

    private void snapToFullScreen() {
        animateListToWidth(mWidth);
        mState = FULLY_SHOWN;
    }

    /**
     * Overloaded function to provide a simple way to start animation. Animation
     * will use default duration, and a value of <code>null</code> for interpolator
     * means linear interpolation will be used.
     *
     * @param width a set of values that the animation will animate between over time
     */
    private void animateListToWidth(int... width) {
        animateListToWidth(0, DEFAULT_DURATION_MS, null, width);
    }

    /**
     * Animate the mode list between the given set of visible width.
     *
     * @param delay start delay between consecutive mode item
     * @param duration duration for the animation of each mode item
     * @param interpolator interpolator to be used by the animation
     * @param width a set of values that the animation will animate between over time
     */
    private void animateListToWidth(int delay, int duration,
                                    TimeInterpolator interpolator, int... width) {
        if (mAnimatorSet != null && mAnimatorSet.isRunning()) {
            mAnimatorSet.end();
        }

        ArrayList<Animator> animators = new ArrayList<Animator>();
        int focusItem = mFocusItem == NO_ITEM_SELECTED ? 0 : mFocusItem;
        for (int i = 0; i < mTotalModes; i++) {
            ObjectAnimator animator = ObjectAnimator.ofInt(mModeSelectorItems[i],
                    "visibleWidth", width);
            animator.setDuration(duration);
            animator.setStartDelay(i * delay);
            animators.add(animator);
            if (i == focusItem) {
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        onVisibleWidthChanged((Integer) animation.getAnimatedValue());
                    }
                });
            }
        }

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
