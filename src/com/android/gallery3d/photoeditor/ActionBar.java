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
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

import com.android.gallery3d.R;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

/**
 * Action bar that contains buttons such as undo, redo, save, etc.
 */
public class ActionBar extends RelativeLayout {

    private static final float ENABLED_ALPHA = 1;
    private static final float DISABLED_ALPHA = 0.47f;

    private final HashMap<Integer, Runnable> buttonRunnables = new HashMap<Integer, Runnable>();
    private final HashSet<Integer> changedButtons = new HashSet<Integer>();

    public ActionBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        // Show the action-bar title only when there's still room for it; otherwise, hide it.
        int width = 0;
        for (int i = 0; i < getChildCount(); i++) {
            width += getChildAt(i).getWidth();
        }
        findViewById(R.id.action_bar_title).setVisibility(((width > r - l)) ? INVISIBLE: VISIBLE);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        enableButton(R.id.undo_button, false);
        enableButton(R.id.redo_button, false);
        enableButton(R.id.save_button, false);
    }

    /**
     * Restores the passed action-bar.
     *
     * @return the passed parameter.
     */
    public ActionBar restore(ActionBar actionBar) {
        // Restores by runnables and enabled status of buttons that have been changed.
        for (Entry<Integer, Runnable> entry : buttonRunnables.entrySet()) {
            actionBar.setRunnable(entry.getKey(), entry.getValue());
        }
        for (int buttonId : changedButtons) {
            actionBar.enableButton(buttonId, isButtonEnabled(buttonId));
        }
        return actionBar;
    }

    public void setRunnable(int buttonId, final Runnable r) {
        findViewById(buttonId).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (isEnabled()) {
                    r.run();
                }
            }
        });
        buttonRunnables.put(buttonId, r);
    }

    public void clickButton(int buttonId) {
        findViewById(buttonId).performClick();
    }

    public boolean isButtonEnabled(int buttonId) {
        return findViewById(buttonId).isEnabled();
    }

    public void enableButton(int buttonId, boolean enabled) {
        View button = findViewById(buttonId);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? ENABLED_ALPHA : DISABLED_ALPHA);

        // Track buttons whose enabled status has been updated.
        changedButtons.add(buttonId);
    }
}
