/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.photoeditor;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.RelativeLayout;

import com.android.gallery3d.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Toolbar that contains all tools and controls their idle/awake behaviors from UI thread.
 */
public class Toolbar extends RelativeLayout {

    private final ToolbarIdleHandler idleHandler;
    private final List<View> tools = new ArrayList<View>();
    private SpinnerProgressDialog spinner;

    public Toolbar(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOnHierarchyChangeListener(new OnHierarchyChangeListener() {

            @Override
            public void onChildViewAdded(View parent, View child) {
                // Photo-view isn't treated as a tool that responds to user events.
                if (child.getId() != R.id.photo_view) {
                    tools.add(child);
                }
            }

            @Override
            public void onChildViewRemoved(View parent, View child) {
                tools.remove(child);
            }
        });

        idleHandler = new ToolbarIdleHandler(context, tools);
        idleHandler.killIdle();
    }

    public void showSpinner() {
        // There should be only one progress spinner running at a time.
        if (spinner == null) {
            spinner = new SpinnerProgressDialog(getContext(), tools,
                    new SpinnerProgressDialog.OnTouchListener() {

                @Override
                public boolean onTouch(DialogInterface dialog, MotionEvent event) {
                    // Kill idle even when the progress dialog is shown.
                    idleHandler.killIdle();
                    return true;
                }
            });
            spinner.show();
        }
    }

    public void dismissSpinner() {
        if (spinner != null) {
            spinner.dismiss();
            spinner = null;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        idleHandler.killIdle();
        return super.dispatchTouchEvent(ev);
    }

    private static class ToolbarIdleHandler {

        private static final int MAKE_IDLE = 1;
        private static final int TIMEOUT_IDLE = 8000;

        private final List<View> tools;
        private final Handler mainHandler;
        private final Animation fadeIn;
        private final Animation fadeOut;
        private boolean idle;

        public ToolbarIdleHandler(Context context, final List<View> tools) {
            this.tools = tools;
            mainHandler = new Handler() {

                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case MAKE_IDLE:
                            if (!idle) {
                                idle = true;
                                for (View view : tools) {
                                    view.startAnimation(fadeOut);
                                }
                            }
                            break;
                    }
                }
            };

            fadeIn = AnimationUtils.loadAnimation(context, R.anim.photoeditor_fade_in);
            fadeOut = AnimationUtils.loadAnimation(context, R.anim.photoeditor_fade_out);
        }

        public void killIdle() {
            mainHandler.removeMessages(MAKE_IDLE);
            if (idle) {
                idle = false;
                for (View view : tools) {
                    view.startAnimation(fadeIn);
                }
            }
            mainHandler.sendEmptyMessageDelayed(MAKE_IDLE, TIMEOUT_IDLE);
        }
    }
}
