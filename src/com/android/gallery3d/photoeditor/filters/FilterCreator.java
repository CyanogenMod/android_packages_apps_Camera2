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
import android.os.Parcelable;

import java.lang.reflect.Array;

/**
 * Creator that creates the specific parcelable filter from the parcel.
 */
public class FilterCreator<T extends Filter> implements Parcelable.Creator<T> {

    private final Class<T> filterClass;

    public FilterCreator(Class<T> filterClass) {
        this.filterClass = filterClass;
    }

    @Override
    public T createFromParcel(Parcel source) {
        try {
            T filter = filterClass.newInstance();
            filter.readFromParcel(source);
            return filter;
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public T[] newArray(int size) {
        return (T[]) Array.newInstance(filterClass, size);
    }
}
