package com.android.camera;

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

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

import com.android.camera.PreviewGestures.SwipeListener;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.ZoomRenderer;
import com.android.gallery3d.R;

import java.util.ArrayList;
import java.util.List;

public class NewPreviewGestures
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

    private NewCameraActivity mActivity;
    private SingleTapListener mTapListener;
    private CancelEventListener mCancelEventListener;
    private RenderOverlay mOverlay;
    private PieRenderer mPie;
    private ZoomRenderer mZoom;
    private MotionEvent mDown;
    private MotionEvent mCurrent;
    private ScaleGestureDetector mScale;
    private List<View> mReceivers;
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

    public interface CancelEventListener {
        public void onTouchEventCancelled(MotionEvent cancelEvent);
    }

    interface SwipeListener {
        public void onSwipe(int direction);
    }

    public NewPreviewGestures(NewCameraActivity ctx, SingleTapListener tapListener,
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

    public void setCancelEventListener(CancelEventListener listener) {
        mCancelEventListener = listener;
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

    public void clearTouchReceivers() {
        if (mReceivers != null) {
            mReceivers.clear();
        }
    }

    public boolean dispatchTouch(MotionEvent m) {
        if (!mEnabled) {
            return false;
        }
        mCurrent = m;
        if (MotionEvent.ACTION_DOWN == m.getActionMasked()) {
            if (checkReceivers(m)) {
                mMode = MODE_MODULE;
                return false;
            } else {
                mMode = MODE_ALL;
                mDown = MotionEvent.obtain(m);
                if (mPie != null && mPie.showsItems()) {
                    mMode = MODE_PIE;
                    return sendToPie(m);
                }
                if (mPie != null && !mZoomOnly) {
                    mHandler.sendEmptyMessageDelayed(MSG_PIE, TIMEOUT_PIE);
                }
                if (mZoom != null) {
                    mScale.onTouchEvent(m);
                }
                // make sure this is ok
                return false;
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
            return false;
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
                cancelActivityTouchHandling(m);
                // must have been tap
                if (m.getEventTime() - mDown.getEventTime() < mTapTimeout) {
                    mTapListener.onSingleTapUp(null,
                            (int) mDown.getX() - mOverlay.getWindowPositionX(),
                            (int) mDown.getY() - mOverlay.getWindowPositionY());
                    return true;
                } else {
                    return false;
                }
            } else if (MotionEvent.ACTION_MOVE == m.getActionMasked()) {
                if ((Math.abs(m.getX() - mDown.getX()) > mSlop)
                        || Math.abs(m.getY() - mDown.getY()) > mSlop) {
                    // moved too far and no timeout yet, no focus or pie
                    cancelPie();
                    int dir = getSwipeDirection(m);
                    if (dir == DIR_LEFT) {
                        mMode = MODE_MODULE;
                        return false;
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
        return (v.getVisibility() == View.VISIBLE
                && evt.getX() >= mLocation[0] && evt.getX() < mLocation[0] + v.getWidth()
                && evt.getY() >= mLocation[1] && evt.getY() < mLocation[1] + v.getHeight());
    }

    public void cancelActivityTouchHandling(MotionEvent m) {
        if (mCancelEventListener != null) {
            mCancelEventListener.onTouchEventCancelled(makeCancelEvent(m));
        }
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

