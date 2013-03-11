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

import android.os.AsyncTask;

/**
 * Asynchronous task wrapper class for doing Bitmap I/O.
 */
public class BitmapTask <T, K> extends AsyncTask<T, Void, K> {

    private Callbacks<T, K> mCallbacks;
    private static final String LOGTAG = "BitmapTask";

    public BitmapTask(Callbacks<T, K> callbacks) {
        mCallbacks = callbacks;
    }

    @Override
    protected K doInBackground(T... params) {
        if (params == null || mCallbacks == null) {
            return null;
        }
        return mCallbacks.onExecute(params[0]);
    }

    @Override
    protected void onPostExecute(K result) {
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
    public interface Callbacks<P, J> {
        void onComplete(J result);

        void onCancel();

        J onExecute(P param);
    }
}
