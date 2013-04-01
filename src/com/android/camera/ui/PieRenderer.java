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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.LinearInterpolator;
import android.view.animation.Transformation;

import com.android.gallery3d.R;

import java.util.ArrayList;
import java.util.List;

public class PieRenderer extends OverlayRenderer
        implements FocusIndicator {

    private static final String TAG = "CAM Pie";

    // Sometimes continuous autofocus starts and stops several times quickly.
    // These states are used to make sure the animation is run for at least some
    // time.
    private volatile int mState;
    private ScaleAnimation mAnimation = new ScaleAnimation();
    private static final int STATE_IDLE = 0;
    private static final int STATE_FOCUSING = 1;
    private static final int STATE_FINISHING = 2;
    private static final int STATE_PIE = 8;

    private static final float MATH_PI_2 = (float)(Math.PI / 2);

    private Runnable mDisappear = new Disappear();
    private Animation.AnimationListener mEndAction = new EndAction();
    private static final int SCALING_UP_TIME = 600;
    private static final int SCALING_DOWN_TIME = 100;
    private static final int DISAPPEAR_TIMEOUT = 200;
    private static final int DIAL_HORIZONTAL = 157;
    // fade out timings
    private static final int PIE_FADE_OUT_DURATION = 600;

    private static final long PIE_FADE_IN_DURATION = 200;
    private static final long PIE_XFADE_DURATION = 200;
    private static final long PIE_SELECT_FADE_DURATION = 300;
    private static final long PIE_OPEN_SUB_DELAY = 400;

    private static final int MSG_OPEN = 0;
    private static final int MSG_CLOSE = 1;
    private static final int MSG_OPENSUBMENU = 2;
    private static final float PIE_SWEEP = (float)(Math.PI / 2);
    // geometry
    private Point mSliceCenter;
    private int mRadius;

    // the detection if touch is inside a slice is offset
    // inbounds by this amount to allow the selection to show before the
    // finger covers it
    private int mTouchOffset;

    private List<PieItem> mOpen;

    private Paint mSelectedPaint;
    private Paint mSubPaint;
    private Paint mMenuArcPaint;

    // touch handling
    private PieItem mCurrentItem;

    private Paint mFocusPaint;
    private int mSuccessColor;
    private int mFailColor;
    private int mCircleSize;
    private int mFocusX;
    private int mFocusY;
    private int mCenterX;
    private int mCenterY;
    private int mPieCenterX;
    private int mPieCenterY;
    private int mIconRadius;
    private int mArcRadius;
    private int mArcOffset;

    private int mDialAngle;
    private RectF mCircle;
    private RectF mDial;
    private Point mPoint1;
    private Point mPoint2;
    private int mStartAnimationAngle;
    private boolean mFocused;
    private int mInnerOffset;
    private int mOuterStroke;
    private int mInnerStroke;
    private boolean mTapMode;
    private boolean mBlockFocus;
    private int mTouchSlopSquared;
    private Point mDown;
    private boolean mOpening;
    private LinearAnimation mXFade;
    private LinearAnimation mFadeIn;
    private FadeOutAnimation mFadeOut;
    private volatile boolean mFocusCancelled;

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch(msg.what) {
            case MSG_OPEN:
                if (mListener != null) {
                    mListener.onPieOpened(mPieCenterX, mPieCenterY);
                }
                break;
            case MSG_CLOSE:
                if (mListener != null) {
                    mListener.onPieClosed();
                }
                break;
            case MSG_OPENSUBMENU:
                onEnterOpen();
                break;
            }

        }
    };

    private PieListener mListener;

    static public interface PieListener {
        public void onPieOpened(int centerX, int centerY);
        public void onPieClosed();
    }

    public void setPieListener(PieListener pl) {
        mListener = pl;
    }

    public PieRenderer(Context context) {
        init(context);
    }

    private void init(Context ctx) {
        setVisible(false);
        mOpen = new ArrayList<PieItem>();
        mOpen.add(new PieItem(null, 0));
        Resources res = ctx.getResources();
        mRadius = (int) res.getDimensionPixelSize(R.dimen.pie_radius_start);
        mCircleSize = mRadius - res.getDimensionPixelSize(R.dimen.focus_radius_offset);
        mTouchOffset = (int) res.getDimensionPixelSize(R.dimen.pie_touch_offset);
        mSliceCenter = new Point(0,0);
        mSelectedPaint = new Paint();
        mSelectedPaint.setColor(Color.argb(255, 51, 181, 229));
        mSelectedPaint.setAntiAlias(true);
        mSubPaint = new Paint();
        mSubPaint.setAntiAlias(true);
        mSubPaint.setColor(Color.argb(200, 250, 230, 128));
        mFocusPaint = new Paint();
        mFocusPaint.setAntiAlias(true);
        mFocusPaint.setColor(Color.WHITE);
        mFocusPaint.setStyle(Paint.Style.STROKE);
        mSuccessColor = Color.GREEN;
        mFailColor = Color.RED;
        mCircle = new RectF();
        mDial = new RectF();
        mPoint1 = new Point();
        mPoint2 = new Point();
        mInnerOffset = res.getDimensionPixelSize(R.dimen.focus_inner_offset);
        mOuterStroke = res.getDimensionPixelSize(R.dimen.focus_outer_stroke);
        mInnerStroke = res.getDimensionPixelSize(R.dimen.focus_inner_stroke);
        mState = STATE_IDLE;
        mBlockFocus = false;
        mTouchSlopSquared = ViewConfiguration.get(ctx).getScaledTouchSlop();
        mTouchSlopSquared = mTouchSlopSquared * mTouchSlopSquared;
        mDown = new Point();
        mMenuArcPaint = new Paint();
        mMenuArcPaint.setAntiAlias(true);
        mMenuArcPaint.setColor(Color.argb(140, 255, 255, 255));
        mMenuArcPaint.setStrokeWidth(10);
        mMenuArcPaint.setStyle(Paint.Style.STROKE);
        mIconRadius = res.getDimensionPixelSize(R.dimen.pie_item_radius);
        mArcRadius = res.getDimensionPixelSize(R.dimen.pie_arc_radius);
        mArcOffset = res.getDimensionPixelSize(R.dimen.pie_arc_offset);
    }

    private PieItem getRoot() {
        return mOpen.get(0);
    }

    public boolean showsItems() {
        return mTapMode;
    }

    public void addItem(PieItem item) {
        // add the item to the pie itself
        getRoot().addItem(item);
    }

    public void clearItems() {
        getRoot().clearItems();
    }

    public void showInCenter() {
        if ((mState == STATE_PIE) && isVisible()) {
            mTapMode = false;
            show(false);
        } else {
            if (mState != STATE_IDLE) {
                cancelFocus();
            }
            mState = STATE_PIE;
            resetPieCenter();
            setCenter(mPieCenterX, mPieCenterY);
            mTapMode = true;
            show(true);
        }
    }

    public void hide() {
        show(false);
    }

    /**
     * guaranteed has center set
     * @param show
     */
    private void show(boolean show) {
        if (show) {
            mState = STATE_PIE;
            // ensure clean state
            mCurrentItem = null;
            PieItem root = getRoot();
            for (PieItem openItem : mOpen) {
                if (openItem.hasItems()) {
                    for (PieItem item : openItem.getItems()) {
                        item.setSelected(false);
                    }
                }
            }
            mOpen.clear();
            mOpen.add(root);
            layoutPie();
            fadeIn();
        } else {
            mState = STATE_IDLE;
            mTapMode = false;
            if (mXFade != null) {
                mXFade.cancel();
            }
        }
        setVisible(show);
        mHandler.sendEmptyMessage(show ? MSG_OPEN : MSG_CLOSE);
    }

    private void fadeIn() {
        mFadeIn = new LinearAnimation(0, 1);
        mFadeIn.setDuration(PIE_FADE_IN_DURATION);
        mFadeIn.setAnimationListener(new AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mFadeIn = null;
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        mFadeIn.startNow();
        mOverlay.startAnimation(mFadeIn);
    }

    public void setCenter(int x, int y) {
        mPieCenterX = x;
        mPieCenterY = y;
        mSliceCenter.x = x;
        mSliceCenter.y = y - mArcOffset + mIconRadius;
    }

    @Override
    public void layout(int l, int t, int r, int b) {
        super.layout(l, t, r, b);
        mCenterX = (r - l) / 2;
        mCenterY = (b - t) / 2;

        mFocusX = mCenterX;
        mFocusY = mCenterY;
        resetPieCenter();
        setCircle(mFocusX, mFocusY);
        if (isVisible() && mState == STATE_PIE) {
            setCenter(mPieCenterX, mPieCenterY);
            layoutPie();
        }
    }

    private void resetPieCenter() {
        mPieCenterX = mCenterX;
        mPieCenterY = mCenterY + mCenterY / 3;
    }

    private void layoutPie() {
        int inner = mIconRadius;
        int outer = inner + mTouchOffset;
        int gap = 1;
        layoutItems(0, getRoot().getItems(), (float) (Math.PI / 2), inner, outer, gap);
    }

    private void layoutItems(int level, List<PieItem> items, float centerAngle, int inner, int outer, int gap) {
        float emptyangle = PIE_SWEEP / 16;
        float sweep = (float) (PIE_SWEEP - 2 * emptyangle) / items.size();
        float angle = centerAngle - PIE_SWEEP / 2 + emptyangle + sweep / 2;
        // check if we have custom geometry
        // first item we find triggers custom sweep for all
        // this allows us to re-use the path
        for (PieItem item : items) {
            if (item.getCenter() >= 0) {
                sweep = item.getSweep();
                break;
            }
        }
        Point p = new Point(mSliceCenter);
        p.y -= level * mTouchOffset;
        Path path = makeSlice(getDegrees(0) - gap, getDegrees(sweep) + gap,
                outer, inner, p);
        for (PieItem item : items) {
            // shared between items
            item.setPath(path);
            if (item.getCenter() >= 0) {
                angle = item.getCenter();
            }
            int w = item.getIntrinsicWidth();
            int h = item.getIntrinsicHeight();
            // move views to outer border
            int r = inner + (outer - inner) * 2 / 3;
            int x = (int) (r * Math.cos(angle));
            int y = mSliceCenter.y - (level * mTouchOffset) - (int) (r * Math.sin(angle)) - h / 2;
            x = mSliceCenter.x + x - w / 2;
            item.setBounds(x, y, x + w, y + h);
            float itemstart = angle - sweep / 2;
            item.setGeometry(itemstart, sweep, inner, outer);
            if (item.hasItems()) {
                layoutItems(level + 1, item.getItems(), MATH_PI_2, inner,
                        outer, gap);
            }
            angle += sweep;
        }
    }

    private Path makeSlice(float start, float end, int outer, int inner, Point center) {
        RectF bb =
                new RectF(center.x - outer, center.y - outer, center.x + outer,
                        center.y + outer);
        RectF bbi =
                new RectF(center.x - inner, center.y - inner, center.x + inner,
                        center.y + inner);
        Path path = new Path();
        path.arcTo(bb, start, end - start, true);
        path.arcTo(bbi, end, start - end);
        path.close();
        return path;
    }

    /**
     * converts a
     * @param angle from 0..PI to Android degrees (clockwise starting at 3 o'clock)
     * @return skia angle
     */
    private float getDegrees(double angle) {
        return (float) (360 - 180 * angle / Math.PI);
    }

    private void startFadeOut(final PieItem item) {
        if (mFadeIn != null) {
            mFadeIn.cancel();
        }
        if (mXFade != null) {
            mXFade.cancel();
        }
        mFadeOut = new FadeOutAnimation();
        mFadeOut.setDuration(PIE_FADE_OUT_DURATION);
        mFadeOut.setAnimationListener(new AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                item.performClick();
                mFadeOut = null;
                deselect();
                show(false);
                mOverlay.setAlpha(1);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        mFadeOut.startNow();
        mOverlay.startAnimation(mFadeOut);
    }

    // root does not count
    private boolean hasOpenItem() {
        return mOpen.size() > 1;
    }

    // pop an item of the open item stack
    private PieItem closeOpenItem() {
        PieItem item = getOpenItem();
        mOpen.remove(mOpen.size() -1);
        return item;
    }

    private PieItem getOpenItem() {
        return mOpen.get(mOpen.size() - 1);
    }

    // return the children either the root or parent of the current open item
    private PieItem getParent() {
        return mOpen.get(Math.max(0, mOpen.size() - 2));
    }

    private int getLevel() {
        return mOpen.size() - 1;
    }

    @Override
    public void onDraw(Canvas canvas) {
        float alpha = 1;
        if (mXFade != null) {
            alpha = mXFade.getValue();
        } else if (mFadeIn != null) {
            alpha = mFadeIn.getValue();
        } else if (mFadeOut != null) {
            alpha = mFadeOut.getValue();
        }
        int state = canvas.save();
        if (mFadeIn != null) {
            float sf = 0.9f + alpha * 0.1f;
            canvas.scale(sf, sf, mPieCenterX, mPieCenterY);
        }
        if (mState != STATE_PIE) {
            drawFocus(canvas);
        }
        if (mState == STATE_FINISHING) {
            canvas.restoreToCount(state);
            return;
        }
        if (!hasOpenItem() || (mXFade != null)) {
            // draw base menu
            drawArc(canvas, getLevel());
            for (PieItem item : getParent().getItems()) {
                drawItem(Math.max(0, mOpen.size() - 2), canvas, item, alpha);
            }
        }
        if (hasOpenItem()) {
            int level = getLevel();
            drawArc(canvas, level);
            for (PieItem inner : getOpenItem().getItems()) {
                if (mFadeOut != null) {
                    drawItem(level, canvas, inner, alpha);
                } else {
                    drawItem(level, canvas, inner, (mXFade != null) ? (1 - 0.5f * alpha) : 1);
                }
            }
        }
        canvas.restoreToCount(state);
    }

    private void drawArc(Canvas canvas, int level) {
        // arc
        if (mState == STATE_PIE) {
            int nr = mArcRadius;
            int cy = mPieCenterY - mArcOffset + mArcRadius  - level * mTouchOffset;
            canvas.drawArc(new RectF(mPieCenterX - nr, cy - mArcRadius,
                    mPieCenterX + nr, cy + mArcRadius),
                    252, 36, false, mMenuArcPaint);
        }
    }
    private void drawItem(int level, Canvas canvas, PieItem item, float alpha) {
        if (mState == STATE_PIE) {
            if (item.getPath() != null) {
                int y = mSliceCenter.y - level * mTouchOffset;
                if (item.isSelected()) {
                    Paint p = mSelectedPaint;
                    int state = canvas.save();
                    float r = getDegrees(item.getStartAngle());
                    canvas.rotate(r, mSliceCenter.x, y);
                    if (mFadeOut != null) {
                        p.setAlpha((int)(255 * alpha));
                    }
                    canvas.drawPath(item.getPath(), p);
                    if (mFadeOut != null) {
                        p.setAlpha(255);
                    }
                    canvas.restoreToCount(state);
                }
                if (mFadeOut == null) {
                    alpha = alpha * (item.isEnabled() ? 1 : 0.3f);
                    // draw the item view
                    item.setAlpha(alpha);
                }
                item.draw(canvas);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent evt) {
        float x = evt.getX();
        float y = evt.getY();
        int action = evt.getActionMasked();
        PointF polar = getPolar(x, y, !mTapMode);
        if (MotionEvent.ACTION_DOWN == action) {
            mDown.x = (int) evt.getX();
            mDown.y = (int) evt.getY();
            mOpening = false;
            if (mTapMode) {
                PieItem item = findItem(polar);
                if ((item != null) && (mCurrentItem != item)) {
                    mState = STATE_PIE;
                    onEnter(item);
                }
            } else {
                setCenter((int) x, (int) y);
                show(true);
            }
            return true;
        } else if (MotionEvent.ACTION_UP == action) {
            if (isVisible()) {
                PieItem item = mCurrentItem;
                if (mTapMode) {
                    item = findItem(polar);
                    if (mOpening) {
                        mOpening = false;
                        return true;
                    }
                }
                if (item == null) {
                    mTapMode = false;
                    show(false);
                } else if (!mOpening && !item.hasItems()) {
                        startFadeOut(item);
                        mTapMode = false;
                } else {
                    mTapMode = true;
                }
                return true;
            }
        } else if (MotionEvent.ACTION_CANCEL == action) {
            if (isVisible() || mTapMode) {
                show(false);
            }
            deselect();
            mHandler.removeMessages(MSG_OPENSUBMENU);
            return false;
        } else if (MotionEvent.ACTION_MOVE == action) {
            if (pulledToCenter(polar)) {
                mHandler.removeMessages(MSG_OPENSUBMENU);
                if (hasOpenItem()) {
                    if (mCurrentItem != null) {
                        mCurrentItem.setSelected(false);
                    }
                    closeOpenItem();
                    mCurrentItem = null;
                } else {
                    deselect();
                }
                return false;
            }
            PieItem item = findItem(polar);
            boolean moved = hasMoved(evt);
            if ((item != null) && (mCurrentItem != item) && (!mOpening || moved)) {
                mHandler.removeMessages(MSG_OPENSUBMENU);
                // only select if we didn't just open or have moved past slop
                if (moved) {
                    // switch back to swipe mode
                    mTapMode = false;
                }
                onEnterSelect(item);
                mHandler.sendEmptyMessageDelayed(MSG_OPENSUBMENU, PIE_OPEN_SUB_DELAY);
            }
        }
        return false;
    }

    private boolean pulledToCenter(PointF polarCoords) {
        return polarCoords.y < mIconRadius - mArcOffset;
    }

    private PointF getPolar(float x, float y, boolean useOffset) {
        PointF res = new PointF();
        // get angle and radius from x/y
        res.x = (float) Math.PI / 2;
        x = x - mSliceCenter.x;
        y = mSliceCenter.y - getLevel() * mTouchOffset - y;
        res.y = (float) Math.sqrt(x * x + y * y);
        if (x != 0) {
            res.x = (float) Math.atan2(y,  x);
            if (res.x < 0) {
                res.x = (float) (2 * Math.PI + res.x);
            }
        }
        res.y = res.y + (useOffset ? mTouchOffset : 0);
        return res;
    }

    private boolean hasMoved(MotionEvent e) {
        return mTouchSlopSquared < (e.getX() - mDown.x) * (e.getX() - mDown.x)
                + (e.getY() - mDown.y) * (e.getY() - mDown.y);
    }

    private void onEnterSelect(PieItem item) {
        if (mCurrentItem != null) {
            mCurrentItem.setSelected(false);
        }
        if (item != null && item.isEnabled()) {
            item.setSelected(true);
            mCurrentItem = item;
        } else {
            mCurrentItem = null;
        }
    }

    private void onEnterOpen() {
        if ((mCurrentItem != getOpenItem()) && mCurrentItem.hasItems()) {
            openCurrentItem();
        }
    }

    /**
     * enter a slice for a view
     * updates model only
     * @param item
     */
    private void onEnter(PieItem item) {
        if (mCurrentItem != null) {
            mCurrentItem.setSelected(false);
        }
        if (item != null && item.isEnabled()) {
            item.setSelected(true);
            mCurrentItem = item;
            if ((mCurrentItem != getOpenItem()) && mCurrentItem.hasItems()) {
                openCurrentItem();
            }
        } else {
            mCurrentItem = null;
        }
    }

    private void deselect() {
        if (mCurrentItem != null) {
            mCurrentItem.setSelected(false);
        }
        if (hasOpenItem()) {
            PieItem item = closeOpenItem();
            onEnter(item);
        } else {
            mCurrentItem = null;
        }
    }

    private void openCurrentItem() {
        if ((mCurrentItem != null) && mCurrentItem.hasItems()) {
            mOpen.add(mCurrentItem);
            mOpening = true;
            if (mFadeIn != null) {
                mFadeIn.cancel();
            }
            mXFade = new LinearAnimation(1, 0);
            mXFade.setDuration(PIE_XFADE_DURATION);
            final PieItem ci = mCurrentItem;
            mXFade.setAnimationListener(new AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    mXFade = null;
                    ci.setSelected(false);
                    mOpening = false;
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            mXFade.startNow();
            mOverlay.startAnimation(mXFade);
        }
    }

    /**
     * @param polar x: angle, y: dist
     * @return the item at angle/dist or null
     */
    private PieItem findItem(PointF polar) {
        // find the matching item:
        List<PieItem> items = getOpenItem().getItems();
        for (PieItem item : items) {
            if (inside(polar, item)) {
                return item;
            }
        }
        return null;
    }

    private boolean inside(PointF polar, PieItem item) {
        return (item.getInnerRadius() < polar.y)
                && (item.getStartAngle() < polar.x)
                && (item.getStartAngle() + item.getSweep() > polar.x)
                && (!mTapMode || (item.getOuterRadius() > polar.y));
    }

    @Override
    public boolean handlesTouch() {
        return true;
    }

    // focus specific code

    public void setBlockFocus(boolean blocked) {
        mBlockFocus = blocked;
        if (blocked) {
            clear();
        }
    }

    public void setFocus(int x, int y) {
        mFocusX = x;
        mFocusY = y;
        setCircle(mFocusX, mFocusY);
    }

    public void alignFocus(int x, int y) {
        mOverlay.removeCallbacks(mDisappear);
        mAnimation.cancel();
        mAnimation.reset();
        mFocusX = x;
        mFocusY = y;
        mDialAngle = DIAL_HORIZONTAL;
        setCircle(x, y);
        mFocused = false;
    }

    public int getSize() {
        return 2 * mCircleSize;
    }

    private int getRandomRange() {
        return (int)(-60 + 120 * Math.random());
    }

    private void setCircle(int cx, int cy) {
        mCircle.set(cx - mCircleSize, cy - mCircleSize,
                cx + mCircleSize, cy + mCircleSize);
        mDial.set(cx - mCircleSize + mInnerOffset, cy - mCircleSize + mInnerOffset,
                cx + mCircleSize - mInnerOffset, cy + mCircleSize - mInnerOffset);
    }

    public void drawFocus(Canvas canvas) {
        if (mBlockFocus) return;
        mFocusPaint.setStrokeWidth(mOuterStroke);
        canvas.drawCircle((float) mFocusX, (float) mFocusY, (float) mCircleSize, mFocusPaint);
        if (mState == STATE_PIE) return;
        int color = mFocusPaint.getColor();
        if (mState == STATE_FINISHING) {
            mFocusPaint.setColor(mFocused ? mSuccessColor : mFailColor);
        }
        mFocusPaint.setStrokeWidth(mInnerStroke);
        drawLine(canvas, mDialAngle, mFocusPaint);
        drawLine(canvas, mDialAngle + 45, mFocusPaint);
        drawLine(canvas, mDialAngle + 180, mFocusPaint);
        drawLine(canvas, mDialAngle + 225, mFocusPaint);
        canvas.save();
        // rotate the arc instead of its offset to better use framework's shape caching
        canvas.rotate(mDialAngle, mFocusX, mFocusY);
        canvas.drawArc(mDial, 0, 45, false, mFocusPaint);
        canvas.drawArc(mDial, 180, 45, false, mFocusPaint);
        canvas.restore();
        mFocusPaint.setColor(color);
    }

    private void drawLine(Canvas canvas, int angle, Paint p) {
        convertCart(angle, mCircleSize - mInnerOffset, mPoint1);
        convertCart(angle, mCircleSize - mInnerOffset + mInnerOffset / 3, mPoint2);
        canvas.drawLine(mPoint1.x + mFocusX, mPoint1.y + mFocusY,
                mPoint2.x + mFocusX, mPoint2.y + mFocusY, p);
    }

    private static void convertCart(int angle, int radius, Point out) {
        double a = 2 * Math.PI * (angle % 360) / 360;
        out.x = (int) (radius * Math.cos(a) + 0.5);
        out.y = (int) (radius * Math.sin(a) + 0.5);
    }

    @Override
    public void showStart() {
        if (mState == STATE_PIE) return;
        cancelFocus();
        mStartAnimationAngle = 67;
        int range = getRandomRange();
        startAnimation(SCALING_UP_TIME,
                false, mStartAnimationAngle, mStartAnimationAngle + range);
        mState = STATE_FOCUSING;
    }

    @Override
    public void showSuccess(boolean timeout) {
        if (mState == STATE_FOCUSING) {
            startAnimation(SCALING_DOWN_TIME,
                    timeout, mStartAnimationAngle);
            mState = STATE_FINISHING;
            mFocused = true;
        }
    }

    @Override
    public void showFail(boolean timeout) {
        if (mState == STATE_FOCUSING) {
            startAnimation(SCALING_DOWN_TIME,
                    timeout, mStartAnimationAngle);
            mState = STATE_FINISHING;
            mFocused = false;
        }
    }

    private void cancelFocus() {
        mFocusCancelled = true;
        mOverlay.removeCallbacks(mDisappear);
        if (mAnimation != null && !mAnimation.hasEnded()) {
            mAnimation.cancel();
        }
        mFocusCancelled = false;
        mFocused = false;
        mState = STATE_IDLE;
    }

    @Override
    public void clear() {
        if (mState == STATE_PIE) return;
        cancelFocus();
        mOverlay.post(mDisappear);
    }

    private void startAnimation(long duration, boolean timeout,
            float toScale) {
        startAnimation(duration, timeout, mDialAngle,
                toScale);
    }

    private void startAnimation(long duration, boolean timeout,
            float fromScale, float toScale) {
        setVisible(true);
        mAnimation.reset();
        mAnimation.setDuration(duration);
        mAnimation.setScale(fromScale, toScale);
        mAnimation.setAnimationListener(timeout ? mEndAction : null);
        mOverlay.startAnimation(mAnimation);
        update();
    }

    private class EndAction implements Animation.AnimationListener {
        @Override
        public void onAnimationEnd(Animation animation) {
            // Keep the focus indicator for some time.
            if (!mFocusCancelled) {
                mOverlay.postDelayed(mDisappear, DISAPPEAR_TIMEOUT);
            }
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        @Override
        public void onAnimationStart(Animation animation) {
        }
    }

    private class Disappear implements Runnable {
        @Override
        public void run() {
            if (mState == STATE_PIE) return;
            setVisible(false);
            mFocusX = mCenterX;
            mFocusY = mCenterY;
            mState = STATE_IDLE;
            setCircle(mFocusX, mFocusY);
            mFocused = false;
        }
    }

    private class FadeOutAnimation extends Animation {

        private float mAlpha;

        public float getValue() {
            return mAlpha;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            if (interpolatedTime < 0.2) {
                mAlpha = 1;
            } else if (interpolatedTime < 0.3) {
                mAlpha = 0;
            } else {
                mAlpha = 1 - (interpolatedTime - 0.3f) / 0.7f;
            }
        }
    }

    private class ScaleAnimation extends Animation {
        private float mFrom = 1f;
        private float mTo = 1f;

        public ScaleAnimation() {
            setFillAfter(true);
        }

        public void setScale(float from, float to) {
            mFrom = from;
            mTo = to;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            mDialAngle = (int)(mFrom + (mTo - mFrom) * interpolatedTime);
        }
    }

    private class LinearAnimation extends Animation {
        private float mFrom;
        private float mTo;
        private float mValue;

        public LinearAnimation(float from, float to) {
            setFillAfter(true);
            setInterpolator(new LinearInterpolator());
            mFrom = from;
            mTo = to;
        }

        public float getValue() {
            return mValue;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            mValue = (mFrom + (mTo - mFrom) * interpolatedTime);
        }
    }

}
