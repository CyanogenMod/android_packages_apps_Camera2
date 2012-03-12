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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ProgressBar;

import com.android.gallery3d.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Spinner model progress dialog that disables all tools for user interaction after it shows up and
 * and re-enables them after it dismisses.
 */
public class SpinnerProgressDialog extends Dialog {

    /**
     * Listener of touch events.
     */
    public interface OnTouchListener {

        public boolean onTouch(DialogInterface dialog, MotionEvent event);
    }

    private final List<View> enabledTools = new ArrayList<View>();
    private final OnTouchListener listener;

    public SpinnerProgressDialog(Context context, List<View> tools, OnTouchListener listener) {
        super(context, R.style.SpinnerProgressDialog);
        addContentView(new ProgressBar(context), new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        setCancelable(false);

        for (View view : tools) {
            if (view.isEnabled()) {
                enabledTools.add(view);
            }
        }
        this.listener = listener;
    }

    @Override
    public void show() {
        super.show();
        // Disable enabled tools when showing spinner progress dialog.
        for (View view : enabledTools) {
            view.setEnabled(false);
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();
        // Enable tools that were disabled by this spinner progress dialog.
        for (View view : enabledTools) {
            view.setEnabled(true);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return listener.onTouch(this, event);
    }
}
