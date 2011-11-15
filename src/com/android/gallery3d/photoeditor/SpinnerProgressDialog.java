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
import android.view.ViewGroup;
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

    private static ViewGroup toolbar;
    private static SpinnerProgressDialog dialog;
    private final ArrayList<View> enabledTools = new ArrayList<View>();

    public static void initialize(ViewGroup toolbar) {
        SpinnerProgressDialog.toolbar = toolbar;
    }

    public static void showDialog() {
        // There should be only one progress dialog running at a time.
        if (dialog == null) {
            dialog = new SpinnerProgressDialog();
            dialog.setCancelable(false);
            dialog.show();
            // Disable enabled tools when showing spinner progress dialog.
            for (int i = 0; i < toolbar.getChildCount(); i++) {
                View view = toolbar.getChildAt(i);
                if (view.isEnabled()) {
                    dialog.enabledTools.add(view);
                    view.setEnabled(false);
                }
            }
        }
    }

    public static void dismissDialog() {
        if (dialog != null) {
            dialog.dismiss();
            // Enable tools that were disabled by this spinner progress dialog.
            for (View view : dialog.enabledTools) {
                view.setEnabled(true);
            }
            dialog = null;
        }
    }

    private SpinnerProgressDialog() {
        super(toolbar.getContext(), R.style.SpinnerProgressDialog);
        addContentView(new ProgressBar(toolbar.getContext()), new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        // Pass touch events to tools for killing idle even when the progress dialog is shown.
        return toolbar.dispatchTouchEvent(event);
    }
}
