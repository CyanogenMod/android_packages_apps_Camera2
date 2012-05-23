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

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.gallery3d.R;

/**
 * The playback controller for the Movie Player.
 */
public class MovieControllerOverlay extends FrameLayout implements
    ControllerOverlay,
    OnClickListener,
    AnimationListener,
    TimeBar.Listener {

  private enum State {
    PLAYING,
    PAUSED,
    ENDED,
    ERROR,
    LOADING
  }

  private static final float ERROR_MESSAGE_RELATIVE_PADDING = 1.0f / 6;

  private Listener listener;

  private final View background;
  private final TimeBar timeBar;

  private View mainView;
  private final LinearLayout loadingView;
  private final TextView errorView;
  private final ImageView playPauseReplayView;

  private final Handler handler;
  private final Runnable startHidingRunnable;
  private final Animation hideAnimation;

  private State state;

  private boolean hidden;

  private boolean canReplay = true;

  public MovieControllerOverlay(Context context) {
    super(context);

    state = State.LOADING;

    LayoutParams wrapContent =
        new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    LayoutParams matchParent =
        new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

    background = new View(context);
    background.setBackgroundColor(context.getResources().getColor(R.color.darker_transparent));
    addView(background, matchParent);

    timeBar = new TimeBar(context, this);
    addView(timeBar, wrapContent);

    loadingView = new LinearLayout(context);
    loadingView.setOrientation(LinearLayout.VERTICAL);
    loadingView.setGravity(Gravity.CENTER_HORIZONTAL);
    ProgressBar spinner = new ProgressBar(context);
    spinner.setIndeterminate(true);
    loadingView.addView(spinner, wrapContent);
    TextView loadingText = createOverlayTextView(context);
    loadingText.setText(R.string.loading_video);
    loadingView.addView(loadingText, wrapContent);
    addView(loadingView, wrapContent);

    playPauseReplayView = new ImageView(context);
    playPauseReplayView.setImageResource(R.drawable.ic_vidcontrol_play);
    playPauseReplayView.setBackgroundResource(R.drawable.bg_vidcontrol);
    playPauseReplayView.setScaleType(ScaleType.CENTER);
    playPauseReplayView.setFocusable(true);
    playPauseReplayView.setClickable(true);
    playPauseReplayView.setOnClickListener(this);
    addView(playPauseReplayView, wrapContent);

    errorView = createOverlayTextView(context);
    addView(errorView, matchParent);

    handler = new Handler();
    startHidingRunnable = new Runnable() {
      public void run() {
        startHiding();
      }
    };

    hideAnimation = AnimationUtils.loadAnimation(context, R.anim.player_out);
    hideAnimation.setAnimationListener(this);

    RelativeLayout.LayoutParams params =
        new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    setLayoutParams(params);
    hide();
  }

  private TextView createOverlayTextView(Context context) {
    TextView view = new TextView(context);
    view.setGravity(Gravity.CENTER);
    view.setTextColor(0xFFFFFFFF);
    view.setPadding(0, 15, 0, 15);
    return view;
  }

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  public void setCanReplay(boolean canReplay) {
    this.canReplay = canReplay;
  }

  public View getView() {
    return this;
  }

  public void showPlaying() {
    state = State.PLAYING;
    showMainView(playPauseReplayView);
  }

  public void showPaused() {
    state = State.PAUSED;
    showMainView(playPauseReplayView);
  }

  public void showEnded() {
    state = State.ENDED;
    showMainView(playPauseReplayView);
  }

  public void showLoading() {
    state = State.LOADING;
    showMainView(loadingView);
  }

  public void showErrorMessage(String message) {
    state = State.ERROR;
    int padding = (int) (getMeasuredWidth() * ERROR_MESSAGE_RELATIVE_PADDING);
    errorView.setPadding(padding, errorView.getPaddingTop(), padding, errorView.getPaddingBottom());
    errorView.setText(message);
    showMainView(errorView);
  }

  public void setTimes(int currentTime, int totalTime) {
    timeBar.setTime(currentTime, totalTime);
  }

  public void hide() {
    boolean wasHidden = hidden;
    hidden = true;
    playPauseReplayView.setVisibility(View.INVISIBLE);
    loadingView.setVisibility(View.INVISIBLE);
    background.setVisibility(View.INVISIBLE);
    timeBar.setVisibility(View.INVISIBLE);
    setVisibility(View.INVISIBLE);
    setFocusable(true);
    requestFocus();
    if (listener != null && wasHidden != hidden) {
      listener.onHidden();
    }
  }

  private void showMainView(View view) {
    mainView = view;
    errorView.setVisibility(mainView == errorView ? View.VISIBLE : View.INVISIBLE);
    loadingView.setVisibility(mainView == loadingView ? View.VISIBLE : View.INVISIBLE);
    playPauseReplayView.setVisibility(
        mainView == playPauseReplayView ? View.VISIBLE : View.INVISIBLE);
    show();
  }

  public void show() {
    boolean wasHidden = hidden;
    hidden = false;
    updateViews();
    setVisibility(View.VISIBLE);
    setFocusable(false);
    if (listener != null && wasHidden != hidden) {
      listener.onShown();
    }
    maybeStartHiding();
  }

  private void maybeStartHiding() {
    cancelHiding();
    if (state == State.PLAYING) {
      handler.postDelayed(startHidingRunnable, 2500);
    }
  }

  private void startHiding() {
    startHideAnimation(background);
    startHideAnimation(timeBar);
    startHideAnimation(playPauseReplayView);
  }

  private void startHideAnimation(View view) {
    if (view.getVisibility() == View.VISIBLE) {
      view.startAnimation(hideAnimation);
    }
  }

  private void cancelHiding() {
    handler.removeCallbacks(startHidingRunnable);
    background.setAnimation(null);
    timeBar.setAnimation(null);
    playPauseReplayView.setAnimation(null);
  }

  public void onAnimationStart(Animation animation) {
    // Do nothing.
  }

  public void onAnimationRepeat(Animation animation) {
    // Do nothing.
  }

  public void onAnimationEnd(Animation animation) {
    hide();
  }

  public void onClick(View view) {
    if (listener != null) {
      if (view == playPauseReplayView) {
        if (state == State.ENDED) {
          if (canReplay) {
              listener.onReplay();
          }
        } else if (state == State.PAUSED || state == State.PLAYING) {
          listener.onPlayPause();
        }
      }
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (hidden) {
      show();
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (super.onTouchEvent(event)) {
      return true;
    }

    if (hidden) {
      show();
      return true;
    }
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        cancelHiding();
        if (state == State.PLAYING || state == State.PAUSED) {
          listener.onPlayPause();
        }
        break;
      case MotionEvent.ACTION_UP:
        maybeStartHiding();
        break;
    }
    return true;
  }

  // The paddings of 4 sides which covered by system components. E.g.
  //    +-----------------+\
  //    |   Action Bar    | insets.top
  //    +-----------------+/
  //    |                 |
  //    |  Content Area   |  insets.right = insets.left = 0
  //    |                 |
  //    +-----------------+\
  //    | Navigation Bar  | insets.bottom
  //    +-----------------+/
  // Please see View.fitSystemWindows() for more details.
  private final Rect mWindowInsets = new Rect();

  @Override
  protected boolean fitSystemWindows(Rect insets) {
    // We don't set the paddings of this View, otherwise,
    // the content will get cropped outside window
    mWindowInsets.set(insets);
    return true;
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    Rect insets = mWindowInsets;
    int pl = insets.left; // the left paddings
    int pr = insets.right;
    int pt = insets.top;
    int pb = insets.bottom;

    int h = bottom - top;
    int w = right - left;
    boolean error = errorView.getVisibility() == View.VISIBLE;

    int y = h - pb;
    // Put both TimeBar and Background just above the bottom system component.
    // But extend the background to the width of the screen, since we don't
    // care if it will be covered by a system component and it looks better.
    background.layout(0, y - timeBar.getBarHeight(), w, y);
    timeBar.layout(pl, y - timeBar.getPreferredHeight(), w - pr, y);

    // Needed, otherwise the framework will not re-layout in case only the padding is changed
    timeBar.requestLayout();

    // Put the play/pause/next/ previous button in the center of the screen
    layoutCenteredView(playPauseReplayView, 0, 0, w, h);

    if (mainView != null) {
      layoutCenteredView(mainView, 0, 0, w, h);
    }
  }

  private void layoutCenteredView(View view, int l, int t, int r, int b) {
    int cw = view.getMeasuredWidth();
    int ch = view.getMeasuredHeight();
    int cl = (r - l - cw) / 2;
    int ct = (b - t - ch) / 2;
    view.layout(cl, ct, cl + cw, ct + ch);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    measureChildren(widthMeasureSpec, heightMeasureSpec);
  }

  private void updateViews() {
    if (hidden) {
      return;
    }
    background.setVisibility(View.VISIBLE);
    timeBar.setVisibility(View.VISIBLE);
    playPauseReplayView.setImageResource(
        state == State.PAUSED ? R.drawable.ic_vidcontrol_play :
          state == State.PLAYING ? R.drawable.ic_vidcontrol_pause :
            R.drawable.ic_vidcontrol_reload);
    playPauseReplayView.setVisibility(
        (state != State.LOADING && state != State.ERROR &&
                !(state == State.ENDED && !canReplay))
        ? View.VISIBLE : View.GONE);
    requestLayout();
  }

  // TimeBar listener

  public void onScrubbingStart() {
    cancelHiding();
    listener.onSeekStart();
  }

  public void onScrubbingMove(int time) {
    cancelHiding();
    listener.onSeekMove(time);
  }

  public void onScrubbingEnd(int time) {
    maybeStartHiding();
    listener.onSeekEnd(time);
  }
}
