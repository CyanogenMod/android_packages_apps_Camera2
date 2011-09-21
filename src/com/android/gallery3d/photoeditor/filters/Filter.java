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

import com.android.gallery3d.photoeditor.Photo;

/**
 * Image filter for photo editing.
 */
public abstract class Filter {

    // TODO: This should be set in MFF instead.
    private static final int DEFAULT_TILE_SIZE = 640;

    private boolean isValid;
    private EffectContext context;
    private Effect effect;

    protected void validate() {
        isValid = true;
    }

    protected Effect getEffect(EffectContext context, String name) {
        if (this.context != context) {
            effect = context.getFactory().createEffect(name);
            effect.setParameter("tile_size", DEFAULT_TILE_SIZE);
            this.context = context;
        }
        return effect;
    }

    /**
     * Some filters, e.g. lighting filters, are initially invalid until set up with parameters while
     * others, e.g. Sepia or Posterize filters, are initially valid without parameters.
     */
    public boolean isValid() {
        return isValid;
    }

    public void release() {
        if (effect != null) {
            effect.release();
            effect = null;
        }
    }

    /**
     * Processes the source bitmap and matrix and output the destination bitmap and matrix.
     *
     * @param context effect context bound to a GL context to create GL effect.
     * @param src source photo as the input.
     * @param dst destination photo having the same dimension as source photo as the output.
     */
    public abstract void process(EffectContext context, Photo src, Photo dst);
}
