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

package com.android.gallery3d.filtershow.cache;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;

public class DelayedPresetCache extends DirectPresetCache implements Callback {
    private HandlerThread mHandlerThread = null;

    private final static int NEW_PRESET = 0;
    private final static int COMPUTE_PRESET = 1;

    private Handler mProcessingHandler = null;
    private final Handler mUIHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NEW_PRESET: {
                    CachedPreset cache = (CachedPreset) msg.obj;
                    didCompute(cache);
                    break;
                }
            }
        }
    };

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case COMPUTE_PRESET: {
                CachedPreset cache = (CachedPreset) msg.obj;
                compute(cache);
                Message uimsg = mUIHandler.obtainMessage(NEW_PRESET, cache);
                mUIHandler.sendMessage(uimsg);
                break;
            }
        }
        return false;
    }

    public DelayedPresetCache(ImageLoader loader, int size) {
        super(loader, size);
        mHandlerThread = new HandlerThread("ImageProcessing", Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();
        mProcessingHandler = new Handler(mHandlerThread.getLooper(), this);
    }

    @Override
    protected void willCompute(CachedPreset cache) {
        if (cache == null) {
            return;
        }
        cache.setBusy(true);
        Message msg = mProcessingHandler.obtainMessage(COMPUTE_PRESET, cache);
        mProcessingHandler.sendMessage(msg);
    }
}
