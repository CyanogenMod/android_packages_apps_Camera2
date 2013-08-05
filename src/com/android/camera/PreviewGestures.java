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

package com.android.camera;

import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.ZoomRenderer;
import com.android.gallery3d.R;

import java.util.ArrayList;
import java.util.List;

public class PreviewGestures
        implements ScaleGestureDetector.OnScaleGestureListener {

    private static final String TAG = "CAM_gestures";

    private static final long TIMEOUT_PIE = 200;
    private static final int MSG_PIE = 1;
    private static final int MODE_NONE = 0;
    private static final int MODE_PIE = 1;
    private static final int MODE_ZOOM = 2;
    private static final int MODE_MODULE = 3;
    private static final int MODE_ALL = 4;
    private static final int MODE_SWIPE = 5;

    public static final int DIR_UP = 0;
    public static final int DIR_DOWN = 1;
    public static final int DIR_LEFT = 2;
    public static final int DIR_RIGHT = 3;

    private CameraActivity mActivity;
    private SingleTapListener mTapListener;
    private RenderOverlay mOverlay;
    private PieRenderer mPie;
    private ZoomRenderer mZoom;
    private MotionEvent mDown;
    private MotionEvent mCurrent;
    private ScaleGestureDetector mScale;
    private List<View> mReceivers;
    private List<View> mUnclickableAreas;
    private int mMode;
    private int mSlop;
    private int mTapTimeout;
    private boolean mEnabled;
    private boolean mZoomOnly;
    private int mOrientation;
    private int[] mLocation;
    private SwipeListener mSwipeListener;

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == MSG_PIE) {
                mMode = MODE_PIE;
                openPie();
                cancelActivityTouchHandling(mDown);
            }
        }
    };

    public interface SingleTapListener {
        public void onSingleTapUp(View v, int x, int y);
    }

    interface SwipeListener {
        public void onSwipe(int direction);
    }

    public PreviewGestures(CameraActivity ctx, SingleTapListener tapListener,
            ZoomRenderer zoom, PieRenderer pie, SwipeListener swipe) {
        mActivity = ctx;
        mTapListener = tapListener;
        mPie = pie;
        mZoom = zoom;
        mMode = MODE_ALL;
        mScale = new ScaleGestureDetector(ctx, this);
        mSlop = (int) ctx.getResources().getDimension(R.dimen.pie_touch_slop);
        mTapTimeout = ViewConfiguration.getTapTimeout();
        mEnabled = true;
        mLocation = new int[2];
        mSwipeListener = swipe;
    }

    public void setRenderOverlay(RenderOverlay overlay) {
        mOverlay = overlay;
    }

    public void setOrientation(int orientation) {
        mOrientation = orientation;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        if (!enabled) {
            cancelPie();
        }
    }

    public void setZoomOnly(boolean zoom) {
        mZoomOnly = zoom;
    }

    public void addTouchReceiver(View v) {
        if (mReceivers == null) {
            mReceivers = new ArrayList<View>();
        }
        mReceivers.add(v);
    }

    public void removeTouchReceiver(View v) {
        if (mReceivers == null || v == null) return;
        mReceivers.remove(v);
    }

    public void addUnclickableArea(View v) {
        if (mUnclickableAreas == null) {
            mUnclickableAreas = new ArrayList<View>();
        }
        mUnclickableAreas.add(v);
    }

    public void clearTouchReceivers() {
        if (mReceivers != null) {
            mReceivers.clear();
        }
    }

    public void clearUnclickableAreas() {
        if (mUnclickableAreas != null) {
            mUnclickableAreas.clear();
        }
    }

    private boolean checkClickable(MotionEvent m) {
        if (mUnclickableAreas != null) {
            for (View v : mUnclickableAreas) {
                if (isInside(m, v)) {
                    return false;
                }
            }
        }
        return true;
    }

    public void reset() {
        clearTouchReceivers();
        clearUnclickableAreas();
    }

    public boolean dispatchTouch(MotionEvent m) {
        if (!mEnabled) {
            return mActivity.superDispatchTouchEvent(m);
        }
        mCurrent = m;
        if (MotionEvent.ACTION_DOWN == m.getActionMasked()) {
            if (checkReceivers(m)) {
                mMode = MODE_MODULE;
                return mActivity.superDispatchTouchEvent(m);
            } else {
                mMode = MODE_ALL;
                mDown = MotionEvent.obtain(m);
                if (mPie != null && mPie.showsItems()) {
                    mMode = MODE_PIE;
                    return sendToPie(m);
                }
                if (mPie != null && !mZoomOnly && checkClickable(m)) {
                    mHandler.sendEmptyMessageDelayed(MSG_PIE, TIMEOUT_PIE);
                }
                if (mZoom != null) {
                    mScale.onTouchEvent(m);
                }
                // make sure this is ok
                return mActivity.superDispatchTouchEvent(m);
            }
        } else if (mMode == MODE_NONE) {
            return false;
        } else if (mMode == MODE_SWIPE) {
            if (MotionEvent.ACTION_UP == m.getActionMasked()) {
                mSwipeListener.onSwipe(getSwipeDirection(m));
            }
            return true;
        } else if (mMode == MODE_PIE) {
            if (MotionEvent.ACTION_POINTER_DOWN == m.getActionMasked()) {
                sendToPie(makeCancelEvent(m));
                if (mZoom != null) {
                    onScaleBegin(mScale);
                }
            } else {
                return sendToPie(m);
            }
            return true;
        } else if (mMode == MODE_ZOOM) {
            mScale.onTouchEvent(m);
            if (!mScale.isInProgress() && MotionEvent.ACTION_POINTER_UP == m.getActionMasked()) {
                mMode = MODE_NONE;
                onScaleEnd(mScale);
            }
            return true;
        } else if (mMode == MODE_MODULE) {
            return mActivity.superDispatchTouchEvent(m);
        } else {
            // didn't receive down event previously;
            // assume module wasn't initialzed and ignore this event.
            if (mDown == null) {
                return true;
            }
            if (MotionEvent.ACTION_POINTER_DOWN == m.getActionMasked()) {
                if (!mZoomOnly) {
                    cancelPie();
                    sendToPie(makeCancelEvent(m));
                }
                if (mZoom != null) {
                    mScale.onTouchEvent(m);
                    onScaleBegin(mScale);
                }
            } else if ((mMode == MODE_ZOOM) && !mScale.isInProgress()
                    && MotionEvent.ACTION_POINTER_UP == m.getActionMasked()) {
                // user initiated and stopped zoom gesture without zooming
                mScale.onTouchEvent(m);
                onScaleEnd(mScale);
            }
            // not zoom or pie mode and no timeout yet
            if (mZoom != null) {
                boolean res = mScale.onTouchEvent(m);
                if (mScale.isInProgress()) {
                    cancelPie();
                    cancelActivityTouchHandling(m);
                    return res;
                }
            }
            if (MotionEvent.ACTION_UP == m.getActionMasked()) {
                cancelPie();
                // must have been tap
                if (m.getEventTime() - mDown.getEventTime() < mTapTimeout
                        && checkClickable(m)) {
                    cancelActivityTouchHandling(m);
                    mTapListener.onSingleTapUp(null,
                            (int) mDown.getX() - mOverlay.getWindowPositionX(),
                            (int) mDown.getY() - mOverlay.getWindowPositionY());
                    return true;
                } else {
                    return mActivity.superDispatchTouchEvent(m);
                }
            } else if (MotionEvent.ACTION_MOVE == m.getActionMasked()) {
                if ((Math.abs(m.getX() - mDown.getX()) > mSlop)
                        || Math.abs(m.getY() - mDown.getY()) > mSlop) {
                    // moved too far and no timeout yet, no focus or pie
                    cancelPie();
                    int dir = getSwipeDirection(m);
                    if (dir == DIR_LEFT) {
                        mMode = MODE_MODULE;
                        return mActivity.superDispatchTouchEvent(m);
                    } else {
                        cancelActivityTouchHandling(m);
                        mMode = MODE_NONE;
                    }
                }
            }
            return false;
        }
    }

    private boolean checkReceivers(MotionEvent m) {
        if (mReceivers != null) {
            for (View receiver : mReceivers) {
                if (isInside(m, receiver)) {
                    return true;
                }
            }
        }
        return false;
    }

    // left tests for finger moving right to left
    private int getSwipeDirection(MotionEvent m) {
        float dx = 0;
        float dy = 0;
        switch (mOrientation) {
        case 0:
            dx = m.getX() - mDown.getX();
            dy = m.getY() - mDown.getY();
            break;
        case 90:
            dx = - (m.getY() - mDown.getY());
            dy = m.getX() - mDown.getX();
            break;
        case 180:
            dx = -(m.getX() - mDown.getX());
            dy = m.getY() - mDown.getY();
            break;
        case 270:
            dx = m.getY() - mDown.getY();
            dy = m.getX() - mDown.getX();
            break;
        }
        if (dx < 0 && (Math.abs(dy) / -dx < 2)) return DIR_LEFT;
        if (dx > 0 && (Math.abs(dy) / dx < 2)) return DIR_RIGHT;
        if (dy > 0) return DIR_DOWN;
        return DIR_UP;
    }

    private boolean isInside(MotionEvent evt, View v) {
        v.getLocationInWindow(mLocation);
        // when view is flipped horizontally
        if ((int) v.getRotationY() == 180) {
            mLocation[0] -= v.getWidth();
        }
        // when view is flipped vertically
        if ((int) v.getRotationX() == 180) {
            mLocation[1] -= v.getHeight();
        }
        return (v.getVisibility() == View.VISIBLE
                && evt.getX() >= mLocation[0] && evt.getX() < mLocation[0] + v.getWidth()
                && evt.getY() >= mLocation[1] && evt.getY() < mLocation[1] + v.getHeight());
    }

    public void cancelActivityTouchHandling(MotionEvent m) {
        mActivity.superDispatchTouchEvent(makeCancelEvent(m));
    }

    private MotionEvent makeCancelEvent(MotionEvent m) {
        MotionEvent c = MotionEvent.obtain(m);
        c.setAction(MotionEvent.ACTION_CANCEL);
        return c;
    }

    private void openPie() {
        mDown.offsetLocation(-mOverlay.getWindowPositionX(),
                -mOverlay.getWindowPositionY());
        mOverlay.directDispatchTouch(mDown, mPie);
    }

    private void cancelPie() {
        mHandler.removeMessages(MSG_PIE);
    }

    private boolean sendToPie(MotionEvent m) {
        m.offsetLocation(-mOverlay.getWindowPositionX(),
                -mOverlay.getWindowPositionY());
        return mOverlay.directDispatchTouch(m, mPie);
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        return mZoom.onScale(detector);
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        if (mMode != MODE_ZOOM) {
            mMode = MODE_ZOOM;
            cancelActivityTouchHandling(mCurrent);
        }
        if (mCurrent.getActionMasked() != MotionEvent.ACTION_MOVE) {
            return mZoom.onScaleBegin(detector);
        } else {
            return true;
        }
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        if (mCurrent.getActionMasked() != MotionEvent.ACTION_MOVE) {
            mZoom.onScaleEnd(detector);
        }
    }
}
