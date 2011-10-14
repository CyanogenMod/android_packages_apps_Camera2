/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;

/**
 * The time bar view, which includes the current and total time, the progress bar,
 * and the scrubber.
 */
public class TimeBar extends View {

  public interface Listener {
    void onScrubbingStart();
    void onScrubbingMove(int time);
    void onScrubbingEnd(int time);
  }

  // Padding around the scrubber to increase its touch target
  private static final int SCRUBBER_PADDING_IN_DP = 10;

  // The total padding, top plus bottom
  private static final int V_PADDING_IN_DP = 30;

  private static final int TEXT_SIZE_IN_DP = 14;

  private final Listener listener;

  // the bars we use for displaying the progress
  private final Rect progressBar;
  private final Rect playedBar;

  private final Paint progressPaint;
  private final Paint playedPaint;
  private final Paint timeTextPaint;

  private final Bitmap scrubber;
  private final int scrubberPadding; // adds some touch tolerance around the scrubber

  private int scrubberLeft;
  private int scrubberTop;
  private int scrubberCorrection;
  private boolean scrubbing;
  private boolean showTimes;
  private boolean showScrubber;

  private int totalTime;
  private int currentTime;

  private final Rect timeBounds;

  private int vPaddingInPx;

  public TimeBar(Context context, Listener listener) {
    super(context);
    this.listener = Utils.checkNotNull(listener);

    showTimes = true;
    showScrubber = true;

    progressBar = new Rect();
    playedBar = new Rect();

    progressPaint = new Paint();
    progressPaint.setColor(0xFF808080);
    playedPaint = new Paint();
    playedPaint.setColor(0xFFFFFFFF);

    DisplayMetrics metrics = context.getResources().getDisplayMetrics();
    float textSizeInPx = metrics.density * TEXT_SIZE_IN_DP;
    timeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    timeTextPaint.setColor(0xFFCECECE);
    timeTextPaint.setTextSize(textSizeInPx);
    timeTextPaint.setTextAlign(Paint.Align.CENTER);

    timeBounds = new Rect();
    timeTextPaint.getTextBounds("0:00:00", 0, 7, timeBounds);

    scrubber = BitmapFactory.decodeResource(getResources(), R.drawable.scrubber_knob);
    scrubberPadding = (int) (metrics.density * SCRUBBER_PADDING_IN_DP);

    vPaddingInPx = (int) (metrics.density * V_PADDING_IN_DP);
  }

  private void update() {
    playedBar.set(progressBar);

    if (totalTime > 0) {
      playedBar.right =
          playedBar.left + (int) ((progressBar.width() * (long) currentTime) / totalTime);
    } else {
      playedBar.right = progressBar.left;
    }

    if (!scrubbing) {
      scrubberLeft = playedBar.right - scrubber.getWidth() / 2;
    }
    invalidate();
  }

  /**
   * @return the preferred height of this view, including invisible padding
   */
  public int getPreferredHeight() {
    return timeBounds.height() + vPaddingInPx + scrubberPadding;
  }

  /**
   * @return the height of the time bar, excluding invisible padding
   */
  public int getBarHeight() {
    return timeBounds.height() + vPaddingInPx;
  }

  public void setTime(int currentTime, int totalTime) {
    if (this.currentTime == currentTime && this.totalTime == totalTime) {
        return;
    }
    this.currentTime = currentTime;
    this.totalTime = totalTime;
    update();
  }

  public void setShowTimes(boolean showTimes) {
    this.showTimes = showTimes;
    requestLayout();
  }

  public void resetTime() {
    setTime(0, 0);
  }

  public void setShowScrubber(boolean showScrubber) {
    this.showScrubber = showScrubber;
    if (!showScrubber && scrubbing) {
      listener.onScrubbingEnd(getScrubberTime());
      scrubbing = false;
    }
    requestLayout();
  }

  private boolean inScrubber(float x, float y) {
    int scrubberRight = scrubberLeft + scrubber.getWidth();
    int scrubberBottom = scrubberTop + scrubber.getHeight();
    return scrubberLeft - scrubberPadding < x && x < scrubberRight + scrubberPadding
        && scrubberTop - scrubberPadding < y && y < scrubberBottom + scrubberPadding;
  }

  private void clampScrubber() {
    int half = scrubber.getWidth() / 2;
    int max = progressBar.right - half;
    int min = progressBar.left - half;
    scrubberLeft = Math.min(max, Math.max(min, scrubberLeft));
  }

  private int getScrubberTime() {
    return (int) ((long) (scrubberLeft + scrubber.getWidth() / 2 - progressBar.left)
        * totalTime / progressBar.width());
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int w = r - l;
    int h = b - t;
    if (!showTimes && !showScrubber) {
      progressBar.set(0, 0, w, h);
    } else {
      int margin = scrubber.getWidth() / 3;
      if (showTimes) {
        margin += timeBounds.width();
      }
      int progressY = (h + scrubberPadding) / 2;
      scrubberTop = progressY - scrubber.getHeight() / 2 + 1;
      progressBar.set(
          getPaddingLeft() + margin, progressY,
          w - getPaddingRight() - margin, progressY + 4);
    }
    update();
  }

  @Override
  public void draw(Canvas canvas) {
    super.draw(canvas);

    // draw progress bars
    canvas.drawRect(progressBar, progressPaint);
    canvas.drawRect(playedBar, playedPaint);

    // draw scrubber and timers
    if (showScrubber) {
      canvas.drawBitmap(scrubber, scrubberLeft, scrubberTop, null);
    }
    if (showTimes) {
      canvas.drawText(
          stringForTime(currentTime),
          timeBounds.width() / 2 + getPaddingLeft(),
          timeBounds.height() + vPaddingInPx / 2 + scrubberPadding + 1,
          timeTextPaint);
      canvas.drawText(
          stringForTime(totalTime),
          getWidth() - getPaddingRight() - timeBounds.width() / 2,
          timeBounds.height() + vPaddingInPx / 2 + scrubberPadding + 1,
          timeTextPaint);
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {

    if (showScrubber) {
      int x = (int) event.getX();
      int y = (int) event.getY();

      switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
          if (inScrubber(x, y)) {
            scrubbing = true;
            scrubberCorrection = x - scrubberLeft;
            listener.onScrubbingStart();
            return true;
          }
          break;
        case MotionEvent.ACTION_MOVE:
          if (scrubbing) {
            scrubberLeft = x - scrubberCorrection;
            clampScrubber();
            currentTime = getScrubberTime();
            listener.onScrubbingMove(currentTime);
            invalidate();
            return true;
          }
          break;
        case MotionEvent.ACTION_UP:
          if (scrubbing) {
            listener.onScrubbingEnd(getScrubberTime());
            scrubbing = false;
            return true;
          }
          break;
      }
    }
    return false;
  }

  private String stringForTime(long millis) {
    int totalSeconds = (int) millis / 1000;
    int seconds = totalSeconds % 60;
    int minutes = (totalSeconds / 60) % 60;
    int hours = totalSeconds / 3600;
    if (hours > 0) {
      return String.format("%d:%02d:%02d", hours, minutes, seconds).toString();
    } else {
      return String.format("%02d:%02d", minutes, seconds).toString();
    }
  }

}
