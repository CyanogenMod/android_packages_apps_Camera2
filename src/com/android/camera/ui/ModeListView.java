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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.android.camera.util.Gusterpolator;
import com.android.camera.widget.AnimationEffects;
import com.android.camera2.R;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

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
    private static final int MODE_SELECTED = 4;

    // Scrolling delay between non-focused item and focused item
    private static final int DELAY_MS = 25;

    private static final int[] mIconResId = {R.drawable.ic_camera_normal,
            R.drawable.ic_video_normal, R.drawable.ic_photo_sphere_normal,
            R.drawable.ic_craft_normal, R.drawable.ic_timelapse_normal,
            R.drawable.ic_panorama_normal, R.drawable.ic_settings_normal,};

    private static final int[] mTextResId = {R.string.mode_camera, R.string.mode_video,
            R.string.mode_photosphere, R.string.mode_craft, R.string.mode_timelapse,
            R.string.mode_panorama, R.string.mode_settings};

    private static final int[] mIconBlockColor = {R.color.camera_mode_color,
            R.color.video_mode_color, R.color.photosphere_mode_color, R.color.craft_mode_color,
            R.color.timelapse_mode_color, R.color.panorama_mode_color,
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
    private AnimationEffects mCurrentEffect;

    // Width and height of this view. They get updated in onLayout()
    // Unit for width and height are pixels.
    private int mWidth;
    private int mHeight;
    private float mScrollTrendX = 0f;
    private float mScrollTrendY = 0f;
    private ModeSwitchListener mListener = null;
    private int[] mSupportedModes;
    private final LinkedList<TimeBasedPosition> mPositionHistory
            = new LinkedList<TimeBasedPosition>();
    private long mCurrentTime;

    public interface ModeSwitchListener {
        public void onModeSelected(int modeIndex);
    }

    /**
     * This class aims to help store time and position in pairs.
     */
    private static class TimeBasedPosition {
        private float mPosition;
        private long mTimeStamp;
        public TimeBasedPosition(float position, long time) {
            mPosition = position;
            mTimeStamp = time;
        }

        public float getPosition() {
            return mPosition;
        }

        public long getTimeStamp() {
            return mTimeStamp;
        }
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
                int modeId = getModeIndex(index);
                mModeSelectorItems[index].highlight();
                mState = MODE_SELECTED;
                PeepholeAnimationEffect effect = new PeepholeAnimationEffect();
                effect.setSize(mWidth, mHeight);
                effect.setAnimationEndAction(new Runnable() {
                    @Override
                    public void run() {
                        setVisibility(INVISIBLE);
                        mCurrentEffect = null;
                        snapBack(false);
                    }
                });
                effect.setAnimationStartingPosition((int) ev.getX(), (int) ev.getY());
                mCurrentEffect = effect;

                onModeSelected(modeId);
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

    /**
     * Initialize mode list with a list of indices of supported modes.
     *
     * @param modeIndexList a list of indices of supported modes
     */
    public void init(List<Integer> modeIndexList) {
        boolean[] modeIsSupported = new boolean[MODE_TOTAL];
        // Setting should always be supported
        modeIsSupported[MODE_SETTING] = true;
        mTotalModes = 1;

        // Mark the supported modes in a boolean array to preserve the
        // sequence of the modes
        for (int i = 0; i < modeIndexList.size(); i++) {
            int mode = modeIndexList.get(i);
            if (mode >= MODE_TOTAL) {
                // This is a mode that we don't display in the mode list, skip.
                continue;
            }
            if (modeIsSupported[mode] == false) {
                modeIsSupported[mode] = true;
                mTotalModes++;
            }
        }
        // Put the indices of supported modes into an array preserving their
        // display order.
        mSupportedModes = new int[mTotalModes];
        int modeCount = 0;
        for (int i = 0; i < MODE_TOTAL; i++) {
            if (modeIsSupported[i]) {
                mSupportedModes[modeCount] = i;
                modeCount++;
            }
        }

        initializeModeSelectorItems();
    }

    // TODO: Initialize mode selectors with different sizes based on number of modes supported
    private void initializeModeSelectorItems() {
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
                selectorItem.setDefaultBackgroundColor(getResources()
                        .getColor(R.color.mode_selector_background_light));
            } else {
                selectorItem.setDefaultBackgroundColor(getResources()
                        .getColor(R.color.mode_selector_background_dark));
            }
            int modeId = getModeIndex(i);
            selectorItem.setIconBackgroundColor(getResources()
                    .getColor(mIconBlockColor[modeId]));

            // Set image
            selectorItem.setImageResource(mIconResId[modeId]);

            // Set text
            CharSequence text = getResources().getText(mTextResId[modeId]);
            selectorItem.setText(text);
            mModeSelectorItems[i] = selectorItem;
        }

        resetModeSelectors();
    }

    /**
     * Maps between the UI mode selector index to the actual mode id.
     *
     * @param modeSelectorIndex the index of the UI item
     * @return the index of the corresponding camera mode
     */
    private int getModeIndex(int modeSelectorIndex) {
        if (modeSelectorIndex < mTotalModes && modeSelectorIndex >= 0) {
            return mSupportedModes[modeSelectorIndex];
        }
        Log.e(TAG, "Invalid mode selector index: " + modeSelectorIndex + ", total modes: "
                + mTotalModes);
        return MODE_PHOTO;
    }

    /** Notify ModeSwitchListener, if any, of the mode change. */
    private void onModeSelected(int modeIndex) {
        if (mListener != null) {
            mListener.onModeSelected(modeIndex);
        }
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
        if (mCurrentEffect != null) {
            return mCurrentEffect.onTouchEvent(ev);
        }

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
        if (mCurrentEffect != null) {
            mCurrentEffect.setSize(mWidth, mHeight);
        }
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
            setVerticalScrollBarEnabled(true);
        } else {
            height = height / mTotalModes;
            setVerticalScrollBarEnabled(false);
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

    @Override
    public void draw(Canvas canvas) {
        if (mCurrentEffect != null) {
            mCurrentEffect.drawBackground(canvas);
            super.draw(canvas);
            mCurrentEffect.drawForeground(canvas);
        } else {
            super.draw(canvas);
        }
    }

    /**
     * This starts the accordion animation, unless it's already running, in which
     * case the start animation call will be ignored.
     */
    public void startAccordionAnimation() {
        if (mState != IDLE) {
            return;
        }
        if (mAnimatorSet != null && mAnimatorSet.isRunning()) {
            return;
        }
        mState = ACCORDION_ANIMATION;
        resetModeSelectors();
        animateListToWidth(START_DELAY_MS, TOTAL_DURATION_MS, mAccordionInterpolator,
                0, mIconBlockWidth, 0);
    }

    /**
     * This starts the accordion animation with a delay.
     *
     * @param delay delay in milliseconds before starting animation
     */
    public void startAccordionAnimationWithDelay(int delay) {
        postDelayed(new Runnable() {
            @Override
            public void run() {
                startAccordionAnimation();
            }
        }, delay);
    }

    /**
     * Resets the visible width of all the mode selectors to 0.
     */
    private void resetModeSelectors() {
        for (int i = 0; i < mModeSelectorItems.length; i++) {
            mModeSelectorItems[i].setVisibleWidth(0);
            mModeSelectorItems[i].unHighlight();
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
        mCurrentTime = SystemClock.uptimeMillis();
        float longestWidth;
        if (itemId != NO_ITEM_SELECTED) {
            longestWidth = mModeSelectorItems[itemId].getVisibleWidth() - deltaX;
        } else {
            longestWidth = mModeSelectorItems[0].getVisibleWidth() - deltaX;
        }
        insertNewPosition(longestWidth, mCurrentTime);

        for (int i = 0; i < mModeSelectorItems.length; i++) {
            mModeSelectorItems[i].setVisibleWidth(calculateVisibleWidthForItem(i,
                    (int) longestWidth));
        }
        if (longestWidth <= 0) {
            reset();
        }

        itemId = itemId == NO_ITEM_SELECTED ? 0 : itemId;
        onVisibleWidthChanged(mModeSelectorItems[itemId].getVisibleWidth());
    }

    /**
     * Calculate the width of a specified item based on its position relative to
     * the item with longest width.
     */
    private int calculateVisibleWidthForItem(int itemId, int longestWidth) {
        if (itemId == mFocusItem || mFocusItem == NO_ITEM_SELECTED) {
            return longestWidth;
        }

        int delay = Math.abs(itemId - mFocusItem) * DELAY_MS;
        return (int) getPosition(mCurrentTime - delay);
    }

    /**
     * Insert new position and time stamp into the history position list, and
     * remove stale position items.
     *
     * @param position latest position of the focus item
     * @param time  current time in milliseconds
     */
    private void insertNewPosition(float position, long time) {
        // TODO: Consider re-using stale position objects rather than
        // always creating new position objects.
        mPositionHistory.add(new TimeBasedPosition(position, time));

        // Positions that are from too long ago will not be of any use for
        // future position interpolation. So we need to remove those positions
        // from the list.
        long timeCutoff = time - (mTotalModes - 1) * DELAY_MS;
        while (mPositionHistory.size() > 0) {
            // Remove all the position items that are prior to the cutoff time.
            TimeBasedPosition historyPosition = mPositionHistory.getFirst();
            if (historyPosition.getTimeStamp() < timeCutoff) {
                mPositionHistory.removeFirst();
            } else {
                break;
            }
        }
    }

    /**
     * Gets the interpolated position at the specified time. This involves going
     * through the recorded positions until a {@link TimeBasedPosition} is found
     * such that the position the recorded before the given time, and the
     * {@link TimeBasedPosition} after that is recorded no earlier than the given
     * time. These two positions are then interpolated to get the position at the
     * specified time.
     */
    private float getPosition(long time) {
        int i;
        for (i = 0; i < mPositionHistory.size(); i++) {
            TimeBasedPosition historyPosition = mPositionHistory.get(i);
            if (historyPosition.getTimeStamp() > time) {
                // Found the winner. Now interpolate between position i and position i - 1
                if (i == 0) {
                    return historyPosition.getPosition();
                } else {
                    TimeBasedPosition prevTimeBasedPosition = mPositionHistory.get(i - 1);
                    // Start interpolation
                    float fraction = (float) (time - prevTimeBasedPosition.getTimeStamp()) /
                            (float) (historyPosition.getTimeStamp() - prevTimeBasedPosition.getTimeStamp());
                    float position = fraction * (historyPosition.getPosition()
                            - prevTimeBasedPosition.getPosition()) + prevTimeBasedPosition.getPosition();
                    return position;
                }
            }
        }
        // It should never get here.
        Log.e(TAG, "Invalid time input for getPosition(). time: " + time);
        if (mPositionHistory.size() == 0) {
            Log.e(TAG, "TimeBasedPosition history size is 0");
        } else {
            Log.e(TAG, "First position recorded at " + mPositionHistory.getFirst().getTimeStamp()
            + " , last position recorded at " + mPositionHistory.getLast().getTimeStamp());
        }
        assert (i < mPositionHistory.size());
        return i;
    }

    private void reset() {
        resetModeSelectors();
        mScrollTrendX = 0f;
        mScrollTrendY = 0f;
        mCurrentEffect = null;
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

    @Override
    public void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) {
            // Reset mode list if the window is no longer visible.
            reset();
            mState = IDLE;
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

    /**
     * Snaps back out of the screen.
     *
     * @param withAnimation whether snapping back should be animated
     */
    public void snapBack(boolean withAnimation) {
        if (withAnimation) {
            animateListToWidth(0);
            mState = IDLE;
        } else {
            setVisibility(INVISIBLE);
            resetModeSelectors();
            mState = IDLE;
        }
    }

    /**
     * Snaps the mode list back out with animation.
     */
    private void snapBack() {
        snapBack(true);
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

    /**
     * Get the theme color of a specific mode.
     *
     * @param modeIndex index of the mode
     * @return theme color of the mode if input index is valid, otherwise 0
     */
    public static int getModeThemeColor(int modeIndex) {
        // Photo and gcam has the same theme color
        if (modeIndex == MODE_GCAM) {
            return mIconBlockColor[MODE_PHOTO];
        }
        if (modeIndex < 0 || modeIndex >= MODE_TOTAL) {
            return 0;
        } else {
            return mIconBlockColor[modeIndex];
        }
    }

    /**
     * Get the mode icon resource id of a specific mode.
     *
     * @param modeIndex index of the mode
     * @return icon resource id if the index is valid, otherwise 0
     */
    public static int getModeIconResourceId(int modeIndex) {
        // Photo and gcam has the same mode icon
        if (modeIndex == MODE_GCAM) {
            return mIconResId[MODE_PHOTO];
        }
        if (modeIndex < 0 || modeIndex >= MODE_TOTAL) {
            return 0;
        } else {
            return mIconResId[modeIndex];
        }
    }

    public void startModeSelectionAnimation() {
        if (mState != MODE_SELECTED || mCurrentEffect == null) {
            setVisibility(INVISIBLE);
            snapBack(false);
            mCurrentEffect = null;
        } else {
            mCurrentEffect.startAnimation();
        }

    }

    private class PeepholeAnimationEffect extends AnimationEffects {

        private final static int UNSET = -1;
        private final static int PEEP_HOLE_ANIMATION_DURATION_MS = 650;

        private int mWidth;
        private int mHeight;

        private int mPeepHoleCenterX = UNSET;
        private int mPeepHoleCenterY = UNSET;
        private float mRadius = 0f;
        private ValueAnimator mPeepHoleAnimator;
        private Runnable mEndAction;
        private final Paint mMaskPaint = new Paint();

        public PeepholeAnimationEffect() {
            mMaskPaint.setAlpha(0);
            mMaskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        public void setSize(int width, int height) {
            mWidth = width;
            mHeight = height;
        }

        public void drawForeground(Canvas canvas) {
            // Draw the circle in clear mode
            if (mPeepHoleAnimator != null) {
                // Draw a transparent circle using clear mode
                canvas.drawCircle(mPeepHoleCenterX, mPeepHoleCenterY, mRadius, mMaskPaint);
            }
        }

        public void setAnimationStartingPosition(int x, int y) {
            mPeepHoleCenterX = x;
            mPeepHoleCenterY = y;
        }

        public void startAnimation() {
            if (mPeepHoleAnimator != null && mPeepHoleAnimator.isRunning()) {
                return;
            }
            if (mPeepHoleCenterY == UNSET || mPeepHoleCenterX == UNSET) {
                mPeepHoleCenterX = mWidth / 2;
                mPeepHoleCenterY = mHeight / 2;
            }

            int horizontalDistanceToFarEdge = Math.max(mPeepHoleCenterX, mWidth - mPeepHoleCenterX);
            int verticalDistanceToFarEdge = Math.max(mPeepHoleCenterY, mHeight - mPeepHoleCenterY);
            int endRadius = (int) (Math.sqrt(horizontalDistanceToFarEdge * horizontalDistanceToFarEdge
                    + verticalDistanceToFarEdge * verticalDistanceToFarEdge));

            mPeepHoleAnimator = ValueAnimator.ofFloat(0, endRadius);
            mPeepHoleAnimator.setDuration(PEEP_HOLE_ANIMATION_DURATION_MS);
            mPeepHoleAnimator.setInterpolator(Gusterpolator.INSTANCE);
            mPeepHoleAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    // Modify mask by enlarging the hole
                    mRadius = (Float) mPeepHoleAnimator.getAnimatedValue();
                    invalidate();
                }
            });

            mPeepHoleAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mEndAction != null) {
                        post(mEndAction);
                        mEndAction = null;
                        post(new Runnable() {
                            @Override
                            public void run() {
                                mPeepHoleAnimator = null;
                                mRadius = 0;
                                mPeepHoleCenterX = UNSET;
                                mPeepHoleCenterY = UNSET;
                            }
                        });
                    } else {
                        mPeepHoleAnimator = null;
                        mRadius = 0;
                        mPeepHoleCenterX = UNSET;
                        mPeepHoleCenterY = UNSET;
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            mPeepHoleAnimator.start();
        }

        public void setAnimationEndAction(Runnable runnable) {
            mEndAction = runnable;
        }
    }
}
