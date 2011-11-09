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

import android.os.Parcel;

/**
 * Filter base that has a scale value ranging from 0 to 1 for adjustments and can persist states.
 */
public abstract class AbstractScaleFilter extends Filter {

    protected float scale;

    /**
     * Sets the scale from 0 to 1.
     */
    public void setScale(float scale) {
        this.scale = scale;
        validate();
    }

    @Override
    protected void writeToParcel(Parcel out) {
        out.writeFloat(scale);
    }

    @Override
    protected void readFromParcel(Parcel in) {
        scale = in.readFloat();
    }
}
