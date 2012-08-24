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

package com.android.gallery3d.app;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;

/**
 * The trim time bar view, which includes the current and total time, the progress
 * bar, and the scrubbers for current time, start and end time for trimming.
 */
public class TrimTimeBar extends TimeBar {

    public static final int SCRUBBER_NONE = 0;
    public static final int SCRUBBER_START = 1;
    public static final int SCRUBBER_CURRENT = 2;
    public static final int SCRUBBER_END = 3;

    private int mPressedThumb = SCRUBBER_NONE;

    // On touch event, the setting order is Scrubber Position -> Time ->
    // PlayedBar. At the setTimes(), activity can update the Time directly, then
    // PlayedBar will be updated too.
    private int mTrimStartScrubberLeft;
    private int mTrimEndScrubberLeft;

    private int mTrimStartScrubberTop;
    private int mTrimEndScrubberTop;

    private int mTrimStartTime;
    private int mTrimEndTime;

    public TrimTimeBar(Context context, Listener listener) {
        super(context, listener);

        mTrimStartTime = 0;
        mTrimEndTime = 0;
        mTrimStartScrubberLeft = 0;
        mTrimEndScrubberLeft = 0;
        mTrimStartScrubberTop = 0;
        mTrimEndScrubberTop = 0;

        // Increase the size of this trimTimeBar.
        mScrubberPadding = mScrubberPadding * 2;
        mVPaddingInPx = mVPaddingInPx * 2;
    }

    private int calculatePlayedBarBoundary(int time) {
        return mProgressBar.left + (int) ((mProgressBar.width() * (long) time) / mTotalTime);
    }

    // Based on all the time info (current, total, trimStart, trimEnd), we
    // decide the playedBar size.
    private void updatePlayedBarAndScrubberFromTime() {
        // According to the Time, update the Played Bar
        mPlayedBar.set(mProgressBar);
        if (mTotalTime > 0) {
            // set playedBar according to the trim time.
            mPlayedBar.left = calculatePlayedBarBoundary(mTrimStartTime);
            mPlayedBar.right = calculatePlayedBarBoundary(mCurrentTime);
            if (!mScrubbing) {
                mScrubberLeft = mPlayedBar.right - mScrubber.getWidth() / 2;
                mTrimStartScrubberLeft = mPlayedBar.left - mScrubber.getWidth() / 2;
                mTrimEndScrubberLeft = calculatePlayedBarBoundary(mTrimEndTime)
                        - mScrubber.getWidth() / 2;
            }
        } else {
            // If the video is not prepared, just show the scrubber at the end
            // of progressBar
            mPlayedBar.right = mProgressBar.left;
            mScrubberLeft = mProgressBar.left - mScrubber.getWidth() / 2;
            mTrimStartScrubberLeft = mProgressBar.left - mScrubber.getWidth() / 2;
            mTrimEndScrubberLeft = mProgressBar.right - mScrubber.getWidth() / 2;
        }
    }

    private void initTrimTimeIfNeeded() {
        if (mTotalTime > 0 && mTrimEndTime == 0) {
            mTrimEndTime = mTotalTime;
        }
    }

    private void update() {
        initTrimTimeIfNeeded();
        updatePlayedBarAndScrubberFromTime();
        invalidate();
    }

    @Override
    public void setTime(int currentTime, int totalTime,
            int trimStartTime, int trimEndTime) {
        if (mCurrentTime == currentTime && mTotalTime == totalTime
                && mTrimStartTime == trimStartTime && mTrimEndTime == trimEndTime) {
            return;
        }
        mCurrentTime = currentTime;
        mTotalTime = totalTime;
        mTrimStartTime = trimStartTime;
        mTrimEndTime = trimEndTime;
        update();
    }

    private int whichScrubber(float x, float y) {
        if (inScrubber(mTrimStartScrubberLeft, mTrimStartScrubberTop, x, y)) {
            return SCRUBBER_START;
        } else if (inScrubber(mTrimEndScrubberLeft, mTrimEndScrubberTop, x, y)) {
            return SCRUBBER_END;
        } else if (inScrubber(mScrubberLeft, mScrubberTop, x, y)) {
            return SCRUBBER_CURRENT;
        }
        return SCRUBBER_NONE;
    }

    private boolean inScrubber(int startX, int startY, float x, float y) {
        int scrubberRight = startX + mScrubber.getWidth();
        int scrubberBottom = startY + mScrubber.getHeight();
        return startX - mScrubberPadding < x && x < scrubberRight + mScrubberPadding
                && startY - mScrubberPadding < y && y < scrubberBottom + mScrubberPadding;
    }

    private int clampScrubber(int scrubberX) {
        int half = mScrubber.getWidth() / 2;
        int max = mProgressBar.right - half;
        int min = mProgressBar.left - half;
        return Math.min(max, Math.max(min, scrubberX));
    }

    private int getScrubberTime(int scrubberX) {
        return (int) ((long) (scrubberX + mScrubber.getWidth() / 2 - mProgressBar.left)
                * mTotalTime / mProgressBar.width());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        if (!mShowTimes && !mShowScrubber) {
            mProgressBar.set(0, 0, w, h);
        } else {
            int margin = mScrubber.getWidth() / 3;
            if (mShowTimes) {
                margin += mTimeBounds.width();
            }
            int progressY = (h + mScrubberPadding) / 2;
            int scrubberY = progressY - mScrubber.getHeight() / 2 + 1;
            mScrubberTop = scrubberY;
            mTrimStartScrubberTop = scrubberY - mScrubber.getHeight() / 2;
            mTrimEndScrubberTop = scrubberY + mScrubber.getHeight() / 2;
            mProgressBar.set(
                    getPaddingLeft() + margin, progressY,
                    w - getPaddingRight() - margin, progressY + 4);
        }
        update();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // draw extra scrubbers
        if (mShowScrubber) {
            canvas.drawBitmap(mScrubber, mTrimStartScrubberLeft, mTrimStartScrubberTop, null);
            canvas.drawBitmap(mScrubber, mTrimEndScrubberLeft, mTrimEndScrubberTop, null);
        }
    }

    private void updateTimeFromPos() {
        mCurrentTime = getScrubberTime(mScrubberLeft);
        mTrimStartTime = getScrubberTime(mTrimStartScrubberLeft);
        mTrimEndTime = getScrubberTime(mTrimEndScrubberLeft);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mShowScrubber) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mPressedThumb = whichScrubber(x, y);
                    switch (mPressedThumb) {
                        case SCRUBBER_NONE:
                            break;
                        case SCRUBBER_CURRENT:
                            mScrubbing = true;
                            mScrubberCorrection = x - mScrubberLeft;
                            break;
                        case SCRUBBER_START:
                            mScrubbing = true;
                            mScrubberCorrection = x - mTrimStartScrubberLeft;
                            break;
                        case SCRUBBER_END:
                            mScrubbing = true;
                            mScrubberCorrection = x - mTrimEndScrubberLeft;
                            break;
                    }
                    if (mScrubbing == true) {
                        mListener.onScrubbingStart();
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mScrubbing) {
                        int seekToPos = -1;
                        switch (mPressedThumb) {
                            case SCRUBBER_CURRENT:
                                mScrubberLeft = x - mScrubberCorrection;
                                // Limit current within (start, end)
                                if (mScrubberLeft <= mTrimStartScrubberLeft) {
                                    mScrubberLeft = mTrimStartScrubberLeft;
                                } else if (mScrubberLeft >= mTrimEndScrubberLeft) {
                                    mScrubberLeft = mTrimEndScrubberLeft;
                                }
                                mScrubberLeft = clampScrubber(mScrubberLeft);
                                seekToPos = mScrubberLeft;
                                break;
                            case SCRUBBER_START:
                                mTrimStartScrubberLeft = x - mScrubberCorrection;
                                // Limit start <= end
                                if (mTrimStartScrubberLeft > mTrimEndScrubberLeft) {
                                    mTrimStartScrubberLeft = mTrimEndScrubberLeft;
                                }
                                seekToPos = mTrimStartScrubberLeft;
                                mTrimStartScrubberLeft = clampScrubber(mTrimStartScrubberLeft);
                                break;
                            case SCRUBBER_END:
                                mTrimEndScrubberLeft = x - mScrubberCorrection;
                                // Limit end >= start
                                if (mTrimEndScrubberLeft < mTrimStartScrubberLeft) {
                                    mTrimEndScrubberLeft = mTrimStartScrubberLeft;
                                }
                                seekToPos = mTrimEndScrubberLeft;
                                mTrimEndScrubberLeft = clampScrubber(mTrimEndScrubberLeft);
                                break;
                        }
                        updateTimeFromPos();
                        updatePlayedBarAndScrubberFromTime();
                        if (seekToPos != -1) {
                            mListener.onScrubbingMove(getScrubberTime(seekToPos));
                        }
                        invalidate();
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (mScrubbing) {
                        int seekToPos = 0;
                        switch (mPressedThumb) {
                            case SCRUBBER_CURRENT:
                                seekToPos = mScrubberLeft;
                                break;
                            case SCRUBBER_START:
                                seekToPos = mTrimStartScrubberLeft;
                                mScrubberLeft = mTrimStartScrubberLeft;
                                break;
                            case SCRUBBER_END:
                                seekToPos = mTrimEndScrubberLeft;
                                mScrubberLeft = mTrimEndScrubberLeft;
                                break;
                        }
                        updateTimeFromPos();
                        mListener.onScrubbingEnd(getScrubberTime(seekToPos),
                                getScrubberTime(mTrimStartScrubberLeft),
                                getScrubberTime(mTrimEndScrubberLeft));
                        mScrubbing = false;
                        mPressedThumb = SCRUBBER_NONE;
                        return true;
                    }
                    break;
            }
        }
        return false;
    }
}
