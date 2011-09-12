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

package com.android.gallery3d.photoeditor.filters;

import android.media.effect.Effect;
import android.media.effect.EffectContext;
import android.media.effect.EffectFactory;

import com.android.gallery3d.photoeditor.Photo;

/**
 * Vignette filter applied to the image.
 */
public class VignetteFilter extends Filter {

    private float range;

    /**
     * Sets the vignette range scale.
     *
     * @param scale ranges from 0 to 1.
     */
    public void setScale(float scale) {
        // The 'range' is between 1.3 to 0.6. When scale is zero then range is 1.3
        // which means no vignette at all because the luminousity difference is
        // less than 1/256 and will cause nothing.
        range = 1.30f - (float) Math.sqrt(scale) * 0.7f;
        validate();
    }

    @Override
    public void process(EffectContext context, Photo src, Photo dst) {
        Effect effect = getEffect(context, EffectFactory.EFFECT_VIGNETTE);
        effect.setParameter("range", range);
        effect.apply(src.texture(), src.width(), src.height(), dst.texture());
    }
}
