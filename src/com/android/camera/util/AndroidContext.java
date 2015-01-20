/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.camera.util;

import android.content.Context;
import android.support.annotation.NonNull;

/**
 * Initializable singleton for providing the application level context
 * object instead of initializing each singleton separately.
 */
public class AndroidContext {
    private static AndroidContext sInstance;

    /**
     * The android context object cannot be created until the android
     * has created the application object. The AndroidContext object
     * must be initialized before other singletons can use it.
     */
    public static void initialize(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new AndroidContext(context);
        }
    }

    /**
     * Return a previously initialized instance, throw if it has not been
     * initialized yet.
     */
    public static AndroidContext instance() {
        if (sInstance == null) {
            throw new IllegalStateException("Android context was not initialized.");
        }
        return sInstance;
    }

    private final Context mContext;
    private AndroidContext(Context context) {
        mContext = context;
    }

    public Context get() {
        return mContext;
    }
}