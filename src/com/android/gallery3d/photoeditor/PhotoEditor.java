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
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.gallery3d.R;

/**
 * Main activity of the photo editor that opens a photo and prepares tools for photo editing.
 */
public class PhotoEditor extends Activity {

    private Uri uri;
    private FilterStack filterStack;
    private ActionBar actionBar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.photoeditor_main);

        Intent intent = getIntent();
        uri = Intent.ACTION_EDIT.equalsIgnoreCase(intent.getAction()) ? intent.getData() : null;

        actionBar = (ActionBar) findViewById(R.id.action_bar);
        filterStack = new FilterStack((PhotoView) findViewById(R.id.photo_view),
                new FilterStack.StackListener() {

                    @Override
                    public void onStackChanged(boolean canUndo, boolean canRedo) {
                        actionBar.enableButton(R.id.undo_button, canUndo);
                        actionBar.enableButton(R.id.redo_button, canRedo);
                        actionBar.enableButton(R.id.save_button, canUndo);
                    }
        });

        EffectsBar effectsBar = (EffectsBar) findViewById(R.id.effects_bar);
        effectsBar.initialize(filterStack);

        actionBar.setRunnable(R.id.undo_button, createUndoRedoRunnable(true, effectsBar));
        actionBar.setRunnable(R.id.redo_button, createUndoRedoRunnable(false, effectsBar));
        actionBar.setRunnable(R.id.save_button, createSaveRunnable(effectsBar));
        actionBar.setRunnable(R.id.action_bar_back, createBackRunnable(effectsBar));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        actionBar = actionBar.restore((ActionBar) recreateView(
                actionBar, inflater, R.layout.photoeditor_actionbar));
    }

    /**
     * Recreates the view by inflating the given layout id.
     */
    private View recreateView(View view, LayoutInflater inflater, int layoutId) {
        ViewGroup parent = (ViewGroup) view.getParent();
        int index = parent.indexOfChild(view);
        parent.removeViewAt(index);

        View recreated = inflater.inflate(layoutId, parent, false);
        parent.addView(recreated, index);
        return recreated;
    }

    private SpinnerProgressDialog createProgressDialog() {
        return SpinnerProgressDialog.show((ViewGroup) findViewById(R.id.toolbar));
    }

    private void openPhoto() {
        final SpinnerProgressDialog progressDialog = createProgressDialog();
        LoadScreennailTask.Callback callback = new LoadScreennailTask.Callback() {

            @Override
            public void onComplete(final Bitmap result) {
                filterStack.setPhotoSource(result, new OnDoneCallback() {

                    @Override
                    public void onDone() {
                        progressDialog.dismiss();
                    }
                });
            }
        };
        new LoadScreennailTask(this, callback).execute(uri);
    }

    private Runnable createUndoRedoRunnable(final boolean undo, final EffectsBar effectsBar) {
        return new Runnable() {

            @Override
            public void run() {
                effectsBar.exit(new Runnable() {

                    @Override
                    public void run() {
                        final SpinnerProgressDialog progressDialog = createProgressDialog();
                        OnDoneCallback callback = new OnDoneCallback() {

                            @Override
                            public void onDone() {
                                progressDialog.dismiss();
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

    private Runnable createSaveRunnable(final EffectsBar effectsBar) {
        return new Runnable() {

            @Override
            public void run() {
                effectsBar.exit(new Runnable() {

                    @Override
                    public void run() {
                        final SpinnerProgressDialog progressDialog = createProgressDialog();
                        filterStack.saveBitmap(new OnDoneBitmapCallback() {

                            @Override
                            public void onDone(Bitmap bitmap) {
                                SaveCopyTask.Callback callback = new SaveCopyTask.Callback() {

                                    @Override
                                    public void onComplete(Uri result) {
                                        progressDialog.dismiss();
                                        actionBar.enableButton(R.id.save_button, (result == null));
                                    }
                                };
                                new SaveCopyTask(PhotoEditor.this, uri, callback).execute(bitmap);
                            }
                        });
                    }
                });
            }
        };
    }

    private Runnable createBackRunnable(final EffectsBar effectsBar) {
        return new Runnable() {

            @Override
            public void run() {
                // Exit effects or go back to the previous activity on pressing back button.
                if (!effectsBar.exit(null)) {
                    // Pop-up a dialog to save unsaved photo.
                    if (actionBar.isButtonEnabled(R.id.save_button)) {
                        new YesNoCancelDialogBuilder(PhotoEditor.this, new Runnable() {

                            @Override
                            public void run() {
                                actionBar.clickButton(R.id.save_button);
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
    public void onBackPressed() {
        actionBar.clickButton(R.id.action_bar_back);
    }

    @Override
    protected void onPause() {
        // TODO: Close running progress dialogs as all pending operations will be paused.
        super.onPause();
        filterStack.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        filterStack.onResume();
        openPhoto();
    }
}
