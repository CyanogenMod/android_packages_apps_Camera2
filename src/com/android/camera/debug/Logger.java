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

package com.android.camera.debug;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Like {@link android.util.Log}.
 */
@ParametersAreNonnullByDefault
public class Logger {
    private final Log.Tag mTag;

    public Logger(Log.Tag tag) {
        mTag = tag;
    }

    public static Logger create(String tag) {
        return new Logger(new Log.Tag(tag));
    }

    /**
     * See {@link Log#d}.
     * @param msg
     */
    public void d(String msg) {
        Log.d(mTag, msg);
    }

    /**
     * See {@link Log#d}.
     */
    public void d(String msg, Throwable tr) {
        Log.d(mTag, msg, tr);
    }

    /**
     * See {@link Log#e}.
     */
    public void e(String msg) {
        Log.e(mTag, msg);
    }

    /**
     * See {@link Log#e}.
     */
    public void e(String msg, Throwable tr) {
        Log.e(mTag, msg, tr);
    }

    /**
     * See {@link Log#i}.
     */
    public void i(String msg) {
        Log.e(mTag, msg);
    }

    /**
     * See {@link Log#i}.
     */
    public void i(String msg, Throwable tr) {
        Log.e(mTag, msg, tr);
    }

    /**
     * See {@link Log#v}.
     */
    public void v(String msg) {
        Log.e(mTag, msg);
    }

    /**
     * See {@link Log#v}.
     */
    public void v(String msg, Throwable tr) {
        Log.e(mTag, msg, tr);
    }

    /**
     * See {@link Log#w}.
     */
    public void w(String msg) {
        Log.e(mTag, msg);
    }

    /**
     * See {@link Log#w}.
     */
    public void w(String msg, Throwable tr) {
        Log.e(mTag, msg, tr);
    }
}
