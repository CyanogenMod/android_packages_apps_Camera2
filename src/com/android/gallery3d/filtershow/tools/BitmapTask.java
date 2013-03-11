/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.filtershow.tools;

import android.graphics.Bitmap;
import android.os.AsyncTask;

/**
 * Asynchronous task filtering or doign I/O with bitmaps.
 */
public class BitmapTask <T> extends AsyncTask<T, Void, Bitmap> {

    private Callbacks<T> mCallbacks;
    private static final String LOGTAG = "BitmapTask";

    public BitmapTask(Callbacks<T> callbacks) {
        mCallbacks = callbacks;
    }

    @Override
    protected Bitmap doInBackground(T... params) {
        if (params == null || mCallbacks == null) {
            return null;
        }
        return mCallbacks.onExecute(params[0]);
    }

    @Override
    protected void onPostExecute(Bitmap result) {
        if (mCallbacks == null) {
            return;
        }
        mCallbacks.onComplete(result);
    }

    @Override
    protected void onCancelled() {
        if (mCallbacks == null) {
            return;
        }
        mCallbacks.onCancel();
    }

    /**
     * Callbacks for the asynchronous task.
     */
    public interface Callbacks<P> {
        void onComplete(Bitmap result);

        void onCancel();

        Bitmap onExecute(P param);
    }
}
