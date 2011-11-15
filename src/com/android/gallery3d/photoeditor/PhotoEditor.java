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
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;

import com.android.gallery3d.R;

/**
 * Main activity of the photo editor that opens a photo and prepares tools for photo editing.
 */
public class PhotoEditor extends Activity {

    private static final String SAVE_URI_KEY = "save_uri";

    private Uri sourceUri;
    private Uri saveUri;
    private FilterStack filterStack;
    private ActionBar actionBar;
    private EffectsBar effectsBar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.photoeditor_main);
        SpinnerProgressDialog.initialize((ViewGroup) findViewById(R.id.toolbar));

        Intent intent = getIntent();
        if (Intent.ACTION_EDIT.equalsIgnoreCase(intent.getAction())) {
            sourceUri = intent.getData();
        }

        actionBar = (ActionBar) findViewById(R.id.action_bar);
        filterStack = new FilterStack((PhotoView) findViewById(R.id.photo_view),
                new FilterStack.StackListener() {

                    @Override
                    public void onStackChanged(boolean canUndo, boolean canRedo) {
                        actionBar.updateButtons(canUndo, canRedo);
                    }
        }, savedInstanceState);
        if (savedInstanceState != null) {
            saveUri = savedInstanceState.getParcelable(SAVE_URI_KEY);
            actionBar.updateSave(saveUri == null);
        }

        // Effects-bar is initially disabled until photo is successfully loaded.
        effectsBar = (EffectsBar) findViewById(R.id.effects_bar);
        effectsBar.initialize(filterStack);
        effectsBar.setEnabled(false);

        actionBar.setClickRunnable(R.id.undo_button, createUndoRedoRunnable(true));
        actionBar.setClickRunnable(R.id.redo_button, createUndoRedoRunnable(false));
        actionBar.setClickRunnable(R.id.save_button, createSaveRunnable());
        actionBar.setClickRunnable(R.id.share_button, createShareRunnable());
        actionBar.setClickRunnable(R.id.action_bar_back, createBackRunnable());
    }

    private void openPhoto() {
        SpinnerProgressDialog.showDialog();
        LoadScreennailTask.Callback callback = new LoadScreennailTask.Callback() {

            @Override
            public void onComplete(final Bitmap result) {
                filterStack.setPhotoSource(result, new OnDoneCallback() {

                    @Override
                    public void onDone() {
                        SpinnerProgressDialog.dismissDialog();
                        effectsBar.setEnabled(result != null);
                    }
                });
            }
        };
        new LoadScreennailTask(this, callback).execute(sourceUri);
    }

    private Runnable createUndoRedoRunnable(final boolean undo) {
        return new Runnable() {

            @Override
            public void run() {
                effectsBar.exit(new Runnable() {

                    @Override
                    public void run() {
                        SpinnerProgressDialog.showDialog();
                        OnDoneCallback callback = new OnDoneCallback() {

                            @Override
                            public void onDone() {
                                SpinnerProgressDialog.dismissDialog();
                            }
                        };
                        if (undo) {
                            filterStack.undo(callback);
                        } else {
                            filterStack.redo(callback);
                        }
                    }
                });
            }
        };
    }

    private Runnable createSaveRunnable() {
        return new Runnable() {

            @Override
            public void run() {
                effectsBar.exit(new Runnable() {

                    @Override
                    public void run() {
                        SpinnerProgressDialog.showDialog();
                        filterStack.getOutputBitmap(new OnDoneBitmapCallback() {

                            @Override
                            public void onDone(Bitmap bitmap) {
                                SaveCopyTask.Callback callback = new SaveCopyTask.Callback() {

                                    @Override
                                    public void onComplete(Uri result) {
                                        SpinnerProgressDialog.dismissDialog();
                                        saveUri = result;
                                        actionBar.updateSave(saveUri == null);
                                    }
                                };
                                new SaveCopyTask(PhotoEditor.this, sourceUri, callback).execute(
                                        bitmap);
                            }
                        });
                    }
                });
            }
        };
    }

    private Runnable createShareRunnable() {
        return new Runnable() {

            @Override
            public void run() {
                effectsBar.exit(new Runnable() {

                    @Override
                    public void run() {
                        if (saveUri != null) {
                            Intent intent = new Intent(Intent.ACTION_SEND);
                            intent.putExtra(Intent.EXTRA_STREAM, saveUri);
                            intent.setType("image/*");
                            startActivity(intent);
                        }
                    }
                });
            }
        };
    }

    private Runnable createBackRunnable() {
        return new Runnable() {

            @Override
            public void run() {
                // Exit effects or go back to the previous activity on pressing back button.
                if (!effectsBar.exit(null)) {
                    // Pop-up a dialog to save unsaved photo.
                    if (actionBar.canSave()) {
                        new YesNoCancelDialogBuilder(PhotoEditor.this, new Runnable() {

                            @Override
                            public void run() {
                                actionBar.clickSave();
                            }
                        }, new Runnable() {

                            @Override
                            public void run() {
                                finish();
                            }
                        }, R.string.save_photo).show();
                    } else {
                        finish();
                    }
                }
            }
        };
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        filterStack.saveStacks(outState);
        outState.putParcelable(SAVE_URI_KEY, saveUri);
    }

    @Override
    public void onBackPressed() {
        actionBar.clickBack();
    }

    @Override
    protected void onPause() {
        super.onPause();
        filterStack.onPause();
        // Dismiss any running progress dialog as all operations are paused.
        SpinnerProgressDialog.dismissDialog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        filterStack.onResume();
        openPhoto();
    }
}
