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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.photoeditor.actions.EffectAction;

/**
 * Effects bar that contains all effects and shows them in categorized views.
 */
public class EffectsBar extends LinearLayout {

    private final LayoutInflater inflater;
    private FilterStack filterStack;
    private EffectsMenu effectsMenu;
    private View effectsGallery;
    private EffectAction activeEffect;

    public EffectsBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void initialize(FilterStack filterStack) {
        this.filterStack = filterStack;

        effectsMenu = (EffectsMenu) findViewById(R.id.effects_menu);
        effectsMenu.setOnToggleListener(new EffectsMenu.OnToggleListener() {

            @Override
            public boolean onToggle(boolean isSelected, final int effectsId) {
                // Create and show effects-gallery only if the clicked toggle isn't selected or it's
                // selected but showing an active effect instead of effects-gallery. Set the clicked
                // toggle selected only when its effects-gallery will be created and shown.
                boolean select = !isSelected || (effectsGallery == null);
                exit(select ? new Runnable() {

                    @Override
                    public void run() {
                        createEffectsGallery(effectsId);
                    }
                } : null);
                return select;
            }
        });
    }

    private void createEffectsGallery(int effectsId) {
        // Inflate scrollable effects-gallery and desired effects into effects-bar.
        effectsGallery = inflater.inflate(R.layout.photoeditor_effects_gallery, this, false);
        ViewGroup scrollView = (ViewGroup) effectsGallery.findViewById(R.id.scroll_view);
        ViewGroup effects = (ViewGroup) inflater.inflate(effectsId, scrollView, false);
        for (int i = 0; i < effects.getChildCount(); i++) {
            setupEffect((EffectAction) effects.getChildAt(i));
        }
        scrollView.addView(effects);
        scrollView.scrollTo(0, 0);
        addView(effectsGallery, 0);
    }

    private void setupEffect(final EffectAction effect) {
        effect.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (isEnabled()) {
                    // Set the clicked effect active before exiting effects-gallery.
                    activeEffect = effect;
                    exitEffectsGallery();
                    EffectAction.ActionListener listener = new EffectAction.ActionListener() {

                        @Override
                        public void onOk() {
                            exit(null);
                        }
                    };
                    activeEffect.begin(getRootView(), filterStack, listener);
                }
            }
        });
    }

    private boolean exitEffectsGallery() {
        if (effectsGallery != null) {
            if (activeEffect != null) {
                // Detach the active effect to prevent it stopping effects-gallery from gc.
                ((ViewGroup) activeEffect.getParent()).removeView(activeEffect);
            }
            removeView(effectsGallery);
            effectsGallery = null;
            return true;
        }
        return false;
    }

    private boolean exitActiveEffect(final Runnable runnableOnDone) {
        if (activeEffect != null) {
            final Toolbar toolbar = (Toolbar) getRootView().findViewById(R.id.toolbar);
            toolbar.showSpinner();
            activeEffect.end(new Runnable() {

                @Override
                public void run() {
                    toolbar.dismissSpinner();
                    activeEffect = null;
                    if (runnableOnDone != null) {
                        runnableOnDone.run();
                    }
                }
            });
            return true;
        }
        return false;
    }

    /**
     * Exits from effects gallery or the active effect; then executes the runnable if applicable.
     *
     * @return true if exiting from effects gallery or the active effect; otherwise, false.
     */
    public boolean exit(final Runnable runnableOnDone) {
        // Exit effects-menu selected states.
        effectsMenu.clearSelected();

        if (exitActiveEffect(runnableOnDone)) {
            return true;
        }

        boolean exited = exitEffectsGallery();
        if (runnableOnDone != null) {
            runnableOnDone.run();
        }
        return exited;
    }
}
