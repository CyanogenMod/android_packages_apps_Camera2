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

package com.android.camera.ui;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.FrameLayout.LayoutParams;
import android.widget.LinearLayout;

import com.android.camera.Util;
import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.util.LightCycleHelper;
import com.android.gallery3d.util.UsageStatistics;

public class CameraSwitcher extends RotateImageView
        implements OnClickListener, OnTouchListener {

    private static final String TAG = "CAM_Switcher";
    private static final int SWITCHER_POPUP_ANIM_DURATION = 200;

    public static final int PHOTO_MODULE_INDEX = 0;
    public static final int VIDEO_MODULE_INDEX = 1;
    public static final int PANORAMA_MODULE_INDEX = 2;
    public static final int LIGHTCYCLE_MODULE_INDEX = 3;
    private static final int[] DRAW_IDS = {
            R.drawable.ic_switch_camera,
            R.drawable.ic_switch_video,
            R.drawable.ic_switch_pan,
            R.drawable.ic_switch_photosphere
    };
    public interface CameraSwitchListener {
        public void onCameraSelected(int i);
        public void onShowSwitcherPopup();
    }

    private CameraSwitchListener mListener;
    private int mCurrentIndex;
    private int[] mModuleIds;
    private int[] mDrawIds;
    private int mItemSize;
    private View mPopup;
    private View mParent;
    private boolean mShowingPopup;
    private boolean mNeedsAnimationSetup;
    private Drawable mIndicator;

    private float mTranslationX = 0;
    private float mTranslationY = 0;

    private AnimatorListener mHideAnimationListener;
    private AnimatorListener mShowAnimationListener;

    public CameraSwitcher(Context context) {
        super(context);
        init(context);
    }

    public CameraSwitcher(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mItemSize = context.getResources().getDimensionPixelSize(R.dimen.switcher_size);
        setOnClickListener(this);
        mIndicator = context.getResources().getDrawable(R.drawable.ic_switcher_menu_indicator);
        initializeDrawables(context);
    }

    public void initializeDrawables(Context context) {
        int totaldrawid = (LightCycleHelper.hasLightCycleCapture(context)
                ? DRAW_IDS.length : DRAW_IDS.length - 1);
        if (!ApiHelper.HAS_OLD_PANORAMA) totaldrawid--;

        int[] drawids = new int[totaldrawid];
        int[] moduleids = new int[totaldrawid];
        int ix = 0;
        for (int i = 0; i < DRAW_IDS.length; i++) {
            if (i == PANORAMA_MODULE_INDEX && !ApiHelper.HAS_OLD_PANORAMA) {
            continue; // not enabled, so don't add to UI
            }
            if (i == LIGHTCYCLE_MODULE_INDEX && !LightCycleHelper.hasLightCycleCapture(context)) {
            continue; // not enabled, so don't add to UI
            }
            moduleids[ix] = i;
            drawids[ix++] = DRAW_IDS[i];
        }
        setIds(moduleids, drawids);
    }

    public void setIds(int[] moduleids, int[] drawids) {
        mDrawIds = drawids;
        mModuleIds = moduleids;
    }

    public void setCurrentIndex(int i) {
        mCurrentIndex = i;
        setImageResource(mDrawIds[i]);
    }

    public void setSwitchListener(CameraSwitchListener l) {
        mListener = l;
    }

    @Override
    public void onClick(View v) {
        showSwitcher();
        mListener.onShowSwitcherPopup();
    }

    private void onCameraSelected(int ix) {
        hidePopup();
        if ((ix != mCurrentIndex) && (mListener != null)) {
            UsageStatistics.onEvent("CameraModeSwitch", null, null);
            UsageStatistics.setPendingTransitionCause(
                    UsageStatistics.TRANSITION_MENU_TAP);
            setCurrentIndex(ix);
            mListener.onCameraSelected(mModuleIds[ix]);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mIndicator.setBounds(getDrawable().getBounds());
        mIndicator.draw(canvas);
    }

    private void initPopup() {
        mParent = LayoutInflater.from(getContext()).inflate(R.layout.switcher_popup,
                (ViewGroup) getParent());
        LinearLayout content = (LinearLayout) mParent.findViewById(R.id.content);
        mPopup = content;
        // Set the gravity of the popup, so that it shows up at the right position
        // on screen
        LayoutParams lp = ((LayoutParams) mPopup.getLayoutParams());
        lp.gravity = ((LayoutParams) mParent.findViewById(R.id.camera_switcher)
                .getLayoutParams()).gravity;
        mPopup.setLayoutParams(lp);

        mPopup.setVisibility(View.INVISIBLE);
        mNeedsAnimationSetup = true;
        for (int i = mDrawIds.length - 1; i >= 0; i--) {
            RotateImageView item = new RotateImageView(getContext());
            item.setImageResource(mDrawIds[i]);
            item.setBackgroundResource(R.drawable.bg_pressed);
            final int index = i;
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (showsPopup()) onCameraSelected(index);
                }
            });
            switch (mDrawIds[i]) {
                case R.drawable.ic_switch_camera:
                    item.setContentDescription(getContext().getResources().getString(
                            R.string.accessibility_switch_to_camera));
                    break;
                case R.drawable.ic_switch_video:
                    item.setContentDescription(getContext().getResources().getString(
                            R.string.accessibility_switch_to_video));
                    break;
                case R.drawable.ic_switch_pan:
                    item.setContentDescription(getContext().getResources().getString(
                            R.string.accessibility_switch_to_panorama));
                    break;
                case R.drawable.ic_switch_photosphere:
                    item.setContentDescription(getContext().getResources().getString(
                            R.string.accessibility_switch_to_new_panorama));
                    break;
                default:
                    break;
            }
            content.addView(item, new LinearLayout.LayoutParams(mItemSize, mItemSize));
        }
        mPopup.measure(MeasureSpec.makeMeasureSpec(mParent.getWidth(), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(mParent.getHeight(), MeasureSpec.AT_MOST));
    }

    public boolean showsPopup() {
        return mShowingPopup;
    }

    public boolean isInsidePopup(MotionEvent evt) {
        if (!showsPopup()) return false;
        int topLeft[] = new int[2];
        mPopup.getLocationOnScreen(topLeft);
        int left = topLeft[0];
        int top = topLeft[1];
        int bottom = top + mPopup.getHeight();
        int right = left + mPopup.getWidth();
        return evt.getX() >= left && evt.getX() < right
                && evt.getY() >= top && evt.getY() < bottom;
    }

    private void hidePopup() {
        mShowingPopup = false;
        setVisibility(View.VISIBLE);
        if (mPopup != null && !animateHidePopup()) {
            mPopup.setVisibility(View.INVISIBLE);
        }
        mParent.setOnTouchListener(null);
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        if (showsPopup()) {
            ((ViewGroup) mParent).removeView(mPopup);
            mPopup = null;
            initPopup();
            mPopup.setVisibility(View.VISIBLE);
        }
    }

    private void showSwitcher() {
        mShowingPopup = true;
        if (mPopup == null) {
            initPopup();
        }
        layoutPopup();
        mPopup.setVisibility(View.VISIBLE);
        if (!animateShowPopup()) {
            setVisibility(View.INVISIBLE);
        }
        mParent.setOnTouchListener(this);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        closePopup();
        return true;
    }

    public void closePopup() {
        if (showsPopup()) {
            hidePopup();
        }
    }

    @Override
    public void setOrientation(int degree, boolean animate) {
        super.setOrientation(degree, animate);
        ViewGroup content = (ViewGroup) mPopup;
        if (content == null) return;
        for (int i = 0; i < content.getChildCount(); i++) {
            RotateImageView iv = (RotateImageView) content.getChildAt(i);
            iv.setOrientation(degree, animate);
        }
    }

    private void layoutPopup() {
        int orientation = Util.getDisplayRotation((Activity) getContext());
        int w = mPopup.getMeasuredWidth();
        int h = mPopup.getMeasuredHeight();
        if (orientation == 0) {
            mPopup.layout(getRight() - w, getBottom() - h, getRight(), getBottom());
            mTranslationX = 0;
            mTranslationY = h / 3;
        } else if (orientation == 90) {
            mTranslationX = w / 3;
            mTranslationY = - h / 3;
            mPopup.layout(getRight() - w, getTop(), getRight(), getTop() + h);
        } else if (orientation == 180) {
            mTranslationX = - w / 3;
            mTranslationY = - h / 3;
            mPopup.layout(getLeft(), getTop(), getLeft() + w, getTop() + h);
        } else {
            mTranslationX = - w / 3;
            mTranslationY = h - getHeight();
            mPopup.layout(getLeft(), getBottom() - h, getLeft() + w, getBottom());
        }
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mPopup != null) {
            layoutPopup();
        }
    }

    private void popupAnimationSetup() {
        if (!ApiHelper.HAS_VIEW_PROPERTY_ANIMATOR) {
            return;
        }
        layoutPopup();
        mPopup.setScaleX(0.3f);
        mPopup.setScaleY(0.3f);
        mPopup.setTranslationX(mTranslationX);
        mPopup.setTranslationY(mTranslationY);
        mNeedsAnimationSetup = false;
    }

    private boolean animateHidePopup() {
        if (!ApiHelper.HAS_VIEW_PROPERTY_ANIMATOR) {
            return false;
        }
        if (mHideAnimationListener == null) {
            mHideAnimationListener = new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // Verify that we weren't canceled
                    if (!showsPopup() && mPopup != null) {
                        mPopup.setVisibility(View.INVISIBLE);
                        ((ViewGroup) mParent).removeView(mPopup);
                        mPopup = null;
                    }
                }
            };
        }
        mPopup.animate()
                .alpha(0f)
                .scaleX(0.3f).scaleY(0.3f)
                .translationX(mTranslationX)
                .translationY(mTranslationY)
                .setDuration(SWITCHER_POPUP_ANIM_DURATION)
                .setListener(mHideAnimationListener);
        animate().alpha(1f).setDuration(SWITCHER_POPUP_ANIM_DURATION)
                .setListener(null);
        return true;
    }

    private boolean animateShowPopup() {
        if (!ApiHelper.HAS_VIEW_PROPERTY_ANIMATOR) {
            return false;
        }
        if (mNeedsAnimationSetup) {
            popupAnimationSetup();
        }
        if (mShowAnimationListener == null) {
            mShowAnimationListener = new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // Verify that we weren't canceled
                    if (showsPopup()) {
                        setVisibility(View.INVISIBLE);
                        // request layout to make sure popup is laid out correctly on ICS
                        mPopup.requestLayout();
                    }
                }
            };
        }
        mPopup.animate()
                .alpha(1f)
                .scaleX(1f).scaleY(1f)
                .translationX(0)
                .translationY(0)
                .setDuration(SWITCHER_POPUP_ANIM_DURATION)
                .setListener(null);
        animate().alpha(0f).setDuration(SWITCHER_POPUP_ANIM_DURATION)
                .setListener(mShowAnimationListener);
        return true;
    }
}
