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
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.RelativeLayout;

import com.android.gallery3d.R;

/**
 * Toolbar that contains all tools and handles all operations for editing photo.
 */
public class Toolbar extends RelativeLayout {

    private final ToolbarIdleHandler idleHandler;
    private FilterStack filterStack;
    private EffectsBar effectsBar;
    private ActionBar actionBar;
    private Uri sourceUri;

    public Toolbar(Context context, AttributeSet attrs) {
        super(context, attrs);

        idleHandler = new ToolbarIdleHandler(context);
        setOnHierarchyChangeListener(idleHandler);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        idleHandler.killIdle();
        return super.dispatchTouchEvent(ev);
    }

    public void initialize(FilterStack filterStack) {
        this.filterStack = filterStack;
        effectsBar = (EffectsBar) findViewById(R.id.effects_bar);
        effectsBar.initialize(filterStack);
        actionBar = (ActionBar) findViewById(R.id.action_bar);
        actionBar.initialize(createActionBarListener());
        idleHandler.killIdle();
    }

    private ActionBar.ActionBarListener createActionBarListener() {
        actionBar = (ActionBar) findViewById(R.id.action_bar);
        return new ActionBar.ActionBarListener() {

            @Override
            public void onUndo() {
                effectsBar.exit(new Runnable() {

                    @Override
                    public void run() {
                        final SpinnerProgressDialog progressDialog = SpinnerProgressDialog.show(
                                Toolbar.this);
                        filterStack.undo(new OnDoneCallback() {

                            @Override
                            public void onDone() {
                                progressDialog.dismiss();
                            }
                        });
                    }
                });
            }

            @Override
            public void onRedo() {
                effectsBar.exit(new Runnable() {

                    @Override
                    public void run() {
                        final SpinnerProgressDialog progressDialog = SpinnerProgressDialog.show(
                                Toolbar.this);
                        filterStack.redo(new OnDoneCallback() {

                            @Override
                            public void onDone() {
                                progressDialog.dismiss();
                            }
                        });
                    }
                });
            }

            @Override
            public void onSave() {
                effectsBar.exit(new Runnable() {

                    @Override
                    public void run() {
                        savePhoto(null);
                    }
                });
            }
        };
    }

    public void openPhoto(Uri uri) {
        sourceUri = uri;

        final SpinnerProgressDialog progressDialog = SpinnerProgressDialog.show(this);
        new LoadScreennailTask(getContext(), new LoadScreennailTask.Callback() {

            @Override
            public void onComplete(final Bitmap bitmap) {
                filterStack.setPhotoSource(bitmap, new OnDoneCallback() {

                    @Override
                    public void onDone() {
                        progressDialog.dismiss();
                    }
                });
            }
        }).execute(sourceUri);
    }

    /**
     * Saves photo and executes runnable (if provided) after saving done.
     */
    public void savePhoto(final Runnable runnable) {
        final SpinnerProgressDialog progressDialog = SpinnerProgressDialog.show(this);
        filterStack.saveBitmap(new OnDoneBitmapCallback() {

            @Override
            public void onDone(Bitmap bitmap) {
                new SaveCopyTask(getContext(), sourceUri, new SaveCopyTask.Callback() {

                    @Override
                    public void onComplete(Uri uri) {
                        // TODO: Handle saving failure.
                        progressDialog.dismiss();
                        actionBar.disableSave();
                        if (runnable != null) {
                            runnable.run();
                        }
                    }
                }).execute(bitmap);
            }
        });
    }
}
