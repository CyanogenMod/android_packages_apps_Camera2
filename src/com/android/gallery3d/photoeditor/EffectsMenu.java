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
import android.view.ViewGroup;

import com.android.gallery3d.R;

/**
 * Effects menu that contains toggles mapping to corresponding groups of effects.
 */
public class EffectsMenu extends RestorableView {

    /**
     * Listener of toggle changes.
     */
    public interface OnToggleListener {

        /**
         * Listens to the selected status and mapped effects-id of the clicked toggle.
         *
         * @return true to make the toggle selected; otherwise, make it unselected.
         */
        boolean onToggle(boolean isSelected, int effectsId);
    }

    public EffectsMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected int childLayoutId() {
        return R.layout.photoeditor_effects_menu;
    }

    public void setOnToggleListener(OnToggleListener listener) {
        setToggleRunnalbe(listener, R.id.exposure_button, R.layout.photoeditor_effects_exposure);
        setToggleRunnalbe(listener, R.id.artistic_button, R.layout.photoeditor_effects_artistic);
        setToggleRunnalbe(listener, R.id.color_button, R.layout.photoeditor_effects_color);
        setToggleRunnalbe(listener, R.id.fix_button, R.layout.photoeditor_effects_fix);
    }

    private void setToggleRunnalbe(final OnToggleListener listener, final int toggleId,
            final int effectsId) {
        setClickRunnable(toggleId, new Runnable() {

            @Override
            public void run() {
                boolean selected = findViewById(toggleId).isSelected();
                setViewSelected(toggleId, listener.onToggle(selected, effectsId));
            }
        });
    }

    public void clearSelected() {
        ViewGroup menu = (ViewGroup) findViewById(R.id.toggles);
        for (int i = 0; i < menu.getChildCount(); i++) {
            View toggle = menu.getChildAt(i);
            if (toggle.isSelected()) {
                setViewSelected(toggle.getId(), false);
            }
        }
    }
}
