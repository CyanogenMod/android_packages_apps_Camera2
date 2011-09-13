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
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

import com.android.gallery3d.R;

/**
 * Action bar that contains buttons such as undo, redo, save, etc. and listens to stack changes for
 * enabling/disabling buttons.
 */
public class ActionBar extends RelativeLayout implements FilterStack.StackListener {

    /**
     * Listener of action button clicked.
     */
    public interface ActionBarListener {

        void onUndo();

        void onRedo();

        void onSave();
    }

    private static final int ENABLE_BUTTON = 1;
    private static final float ENABLED_ALPHA = 1;
    private static final float DISABLED_ALPHA = 0.47f;

    private final Handler handler;
    private View undo;
    private View redo;
    private View save;

    public ActionBar(Context context, AttributeSet attrs) {
        super(context, attrs);

        handler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case ENABLE_BUTTON:
                        boolean canUndo = (msg.arg1 > 0);
                        boolean canRedo = (msg.arg2 > 0);
                        enableButton(undo, canUndo);
                        enableButton(redo, canRedo);
                        enableButton(save, canUndo);
                        break;
                }
            }
        };
    }

    /**
     * Initializes with a non-null ActionBarListener.
     */
    public void initialize(final ActionBarListener listener) {
        undo = findViewById(R.id.undo_button);
        undo.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (isEnabled()) {
                    listener.onUndo();
                }
            }
        });

        redo = findViewById(R.id.redo_button);
        redo.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (isEnabled()) {
                    listener.onRedo();
                }
            }
        });

        save = findViewById(R.id.save_button);
        save.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (isEnabled()) {
                    listener.onSave();
                }
            }
        });

        resetButtons();
    }

    public void resetButtons() {
        // Disable buttons immediately instead of waiting for ENABLE_BUTTON messages which may
        // happen some time later after stack changes.
        enableButton(undo, false);
        enableButton(redo, false);
        enableButton(save, false);
    }

    public void disableSave() {
        enableButton(save, false);
    }

    private void enableButton(View button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? ENABLED_ALPHA : DISABLED_ALPHA);
    }

    @Override
    public void onStackChanged(boolean canUndo, boolean canRedo) {
        // Listens to stack changes that may come from the worker thread; send messages to enable
        // buttons only in the UI thread.
        handler.sendMessage(handler.obtainMessage(ENABLE_BUTTON, canUndo ? 1 : 0, canRedo ? 1 : 0));
    }
}
