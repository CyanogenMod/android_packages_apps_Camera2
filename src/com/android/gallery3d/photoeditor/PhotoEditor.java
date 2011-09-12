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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import com.android.gallery3d.R;

/**
 * Main activity of the photo editor.
 */
public class PhotoEditor extends Activity {

    private Uri uri;
    private FilterStack filterStack;
    private Toolbar toolbar;
    private View backButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.photoeditor_main);

        Intent intent = getIntent();
        uri = Intent.ACTION_EDIT.equalsIgnoreCase(intent.getAction()) ? intent.getData() : null;

        final ActionBar actionBar = (ActionBar) findViewById(R.id.action_bar);
        filterStack = new FilterStack((PhotoView) findViewById(R.id.photo_view), actionBar);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.initialize(filterStack);

        final EffectsBar effectsBar = (EffectsBar) findViewById(R.id.effects_bar);
        backButton = findViewById(R.id.action_bar_back);
        backButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (actionBar.isEnabled()) {
                    // Exit effects or go back to the previous activity on pressing back button.
                    if (!effectsBar.exit(null)) {
                        tryRun(new Runnable() {

                            @Override
                            public void run() {
                                finish();
                            }
                        });
                    }
                }
            }
        });
    }

    private void tryRun(final Runnable runnable) {
        if (findViewById(R.id.save_button).isEnabled()) {
            // Pop-up a dialog before executing the runnable to save unsaved photo.
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            toolbar.savePhoto(new Runnable() {

                                @Override
                                public void run() {
                                    runnable.run();
                                }
                            });
                        }
                    })
                    .setNeutralButton(R.string.no, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            runnable.run();
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // no-op
                        }
                    });
            builder.setMessage(R.string.save_photo).show();
            return;
        }

        runnable.run();
    }

    @Override
    public void onBackPressed() {
        backButton.performClick();
    }

    @Override
    protected void onPause() {
        // TODO: Close running spinner progress dialogs as all pending operations will be paused.
        super.onPause();
        filterStack.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        filterStack.onResume();
        toolbar.openPhoto(uri);
    }
}
