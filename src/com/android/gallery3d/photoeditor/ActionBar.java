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
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.widget.ViewSwitcher;

import com.android.gallery3d.R;

/**
 * Action bar that contains buttons such as undo, redo, save, etc.
 */
public class ActionBar extends RestorableView {

    public ActionBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected int childLayoutId() {
        return R.layout.photoeditor_actionbar;
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
        updateButtons(false, false);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        showSaveOrShare();
    }

    /**
     * Save/share button may need being switched when undo/save enabled status is changed/restored.
     */
    private void showSaveOrShare() {
        // Show share-button only after photo is edited and saved; otherwise, show save-button.
        boolean showShare = findViewById(R.id.undo_button).isEnabled()
                && !findViewById(R.id.save_button).isEnabled();
        ViewSwitcher switcher = (ViewSwitcher) findViewById(R.id.save_share_buttons);
        int next = switcher.getNextView().getId();
        if ((showShare && (next == R.id.share_button))
                || (!showShare && (next == R.id.save_button))) {
            switcher.showNext();
        }
    }

    public void updateButtons(boolean canUndo, boolean canRedo) {
        setViewEnabled(R.id.undo_button, canUndo);
        setViewEnabled(R.id.redo_button, canRedo);
        setViewEnabled(R.id.save_button, canUndo);
        showSaveOrShare();
    }

    public void updateSave(boolean canSave) {
        setViewEnabled(R.id.save_button, canSave);
        showSaveOrShare();
    }

    public void clickBack() {
        findViewById(R.id.action_bar_back).performClick();
    }

    public void clickSave() {
        findViewById(R.id.save_button).performClick();
    }

    public boolean canSave() {
        return findViewById(R.id.save_button).isEnabled();
    }
}
