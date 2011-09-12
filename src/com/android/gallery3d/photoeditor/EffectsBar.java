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
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.photoeditor.actions.EffectAction;
import com.android.gallery3d.photoeditor.actions.EffectToolFactory;

/**
 * Effects bar that contains all effects and shows them in categorized views.
 */
public class EffectsBar extends LinearLayout {

    private FilterStack filterStack;
    private LayoutInflater inflater;
    private View effectsGallery;
    private ViewGroup effectToolPanel;
    private EffectAction activeEffect;

    public EffectsBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void initialize(FilterStack filterStack) {
        this.filterStack = filterStack;
        inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        setupMenuToggle(R.id.exposure_button, R.layout.photoeditor_effects_exposure);
        setupMenuToggle(R.id.artistic_button, R.layout.photoeditor_effects_artistic);
        setupMenuToggle(R.id.color_button, R.layout.photoeditor_effects_color);
        setupMenuToggle(R.id.fix_button, R.layout.photoeditor_effects_fix);

        setEnabled(false);
    }

    private void setupMenuToggle(int toggleId, final int effectsId) {
        final View toggle = findViewById(toggleId);
        toggle.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // Toggle off to exit effects gallery that is showing. Or toggle on to show effects
                // gallery after exiting an active effect if applicable.
                exit((toggle.isSelected() && (effectsGallery != null)) ? null : new Runnable() {

                    @Override
                    public void run() {
                        toggle.setSelected(true);
                        showEffectsGallery(effectsId);
                    }
                });
            }
        });
    }

    private void setupEffectListener(final EffectAction effect) {
        effect.setListener(new EffectAction.Listener() {

            @Override
            public void onClick() {
                if (isEnabled()) {
                    // Set the clicked effect active before exiting effects-gallery.
                    activeEffect = effect;
                    exitEffectsGallery();
                    // Create effect tool panel first before the factory could create tools within.
                    createEffectToolPanel();
                    activeEffect.begin(
                            filterStack, new EffectToolFactory(effectToolPanel, inflater));
                }
            }

            @Override
            public void onDone() {
                exit(null);
            }
        });
    }

    private void createEffectToolPanel() {
        effectToolPanel = (ViewGroup) inflater.inflate(
                R.layout.photoeditor_effect_tool_panel, this, false);
        ((TextView) effectToolPanel.findViewById(R.id.effect_label)).setText(activeEffect.name());
        addView(effectToolPanel, 0);
    }

    private void showEffectsGallery(int effectsId) {
        // Inflate scrollable effects-gallery and desired effects into effects-bar.
        effectsGallery = inflater.inflate(R.layout.photoeditor_effects_gallery, this, false);
        ViewGroup scrollView = (ViewGroup) effectsGallery.findViewById(R.id.scroll_view);
        ViewGroup effects = (ViewGroup) inflater.inflate(effectsId, scrollView, false);
        for (int i = 0; i < effects.getChildCount(); i++) {
            setupEffectListener((EffectAction) effects.getChildAt(i));
        }
        scrollView.addView(effects);
        scrollView.scrollTo(0, 0);
        addView(effectsGallery, 0);
    }

    private boolean exitEffectsGallery() {
        if (effectsGallery != null) {
            if (activeEffect != null) {
                // Detach the active effect from effects-gallery that could be recycled by gc.
                ViewGroup scrollView = (ViewGroup) effectsGallery.findViewById(R.id.scroll_view);
                ((ViewGroup) scrollView.getChildAt(0)).removeView(activeEffect);
            }
            removeView(effectsGallery);
            effectsGallery = null;
            return true;
        }
        return false;
    }

    private boolean exitActiveEffect(final Runnable runnableOnDone) {
        if (activeEffect != null) {
            final SpinnerProgressDialog progressDialog = SpinnerProgressDialog.show(
                    (ViewGroup) getRootView().findViewById(R.id.toolbar));
            activeEffect.end(new Runnable() {

                @Override
                public void run() {
                    progressDialog.dismiss();
                    View fullscreenTool = getRootView().findViewById(R.id.fullscreen_effect_tool);
                    if (fullscreenTool != null) {
                        ((ViewGroup) fullscreenTool.getParent()).removeView(fullscreenTool);
                    }
                    removeView(effectToolPanel);
                    effectToolPanel = null;
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
        ViewGroup menu = (ViewGroup) findViewById(R.id.effects_menu);
        for (int i = 0; i < menu.getChildCount(); i++) {
            View toggle = menu.getChildAt(i);
            if (toggle.isSelected()) {
                toggle.setSelected(false);
            }
        }

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
