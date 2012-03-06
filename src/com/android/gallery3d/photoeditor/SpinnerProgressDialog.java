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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ProgressBar;

import com.android.gallery3d.R;

import java.util.ArrayList;

/**
 * Spinner model progress dialog that disables all tools for user interaction after it shows up and
 * and re-enables them after it dismisses; this class along with all its methods should be accessed
 * in only UI thread and allows only one instance at a time.
 */
public class SpinnerProgressDialog extends Dialog {

    private static SpinnerProgressDialog dialog;
    private final Toolbar toolbar;
    private final ArrayList<View> enabledTools = new ArrayList<View>();

    public static void showDialog(Toolbar toolbar) {
        // There should be only one progress dialog running at a time.
        if (dialog == null) {
            dialog = new SpinnerProgressDialog(toolbar);
            dialog.show();
        }
    }

    public static void dismissDialog() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    private SpinnerProgressDialog(Toolbar toolbar) {
        super(toolbar.getContext(), R.style.SpinnerProgressDialog);
        addContentView(new ProgressBar(toolbar.getContext()), new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        setCancelable(false);
        this.toolbar = toolbar;
    }

    @Override
    public void show() {
        super.show();
        // Disable enabled tools when showing spinner progress dialog.
        for (View view : toolbar.getTools()) {
            if (view.isEnabled()) {
                enabledTools.add(view);
                view.setEnabled(false);
            }
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
        super.onTouchEvent(event);
        // Pass touch events to tools for killing idle even when the progress dialog is shown.
        return toolbar.dispatchTouchEvent(event);
    }
}
