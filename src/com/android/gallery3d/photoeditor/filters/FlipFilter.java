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
import android.media.effect.EffectFactory;
import android.os.Parcel;

import com.android.gallery3d.photoeditor.Photo;

/**
 * Flip filter applied to the image.
 */
public class FlipFilter extends Filter {

    public static final Creator<FlipFilter> CREATOR = creatorOf(FlipFilter.class);

    private final boolean[] flips = new boolean[2];

    public void setFlip(boolean flipHorizontal, boolean flipVertical) {
        flips[0] = flipHorizontal;
        flips[1] = flipVertical;
    }

    @Override
    public void process(Photo src, Photo dst) {
        Effect effect = getEffect(EffectFactory.EFFECT_FLIP);
        effect.setParameter("horizontal", flips[0]);
        effect.setParameter("vertical", flips[1]);
        effect.apply(src.texture(), src.width(), src.height(), dst.texture());
    }

    @Override
    protected void writeToParcel(Parcel out) {
        out.writeBooleanArray(flips);
    }

    @Override
    protected void readFromParcel(Parcel in) {
        in.readBooleanArray(flips);
    }
}
