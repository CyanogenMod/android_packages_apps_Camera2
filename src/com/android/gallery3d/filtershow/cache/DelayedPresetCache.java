
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
    private Handler mUIHandler = new Handler() {
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

    public DelayedPresetCache(int size) {
        super(size);
        mHandlerThread = new HandlerThread("ImageProcessing", Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();
        mProcessingHandler = new Handler(mHandlerThread.getLooper(), this);
    }

    protected void willCompute(CachedPreset cache) {
        if (cache == null) {
            return;
        }
        cache.setBusy(true);
        Message msg = mProcessingHandler.obtainMessage(COMPUTE_PRESET, cache);
        mProcessingHandler.sendMessage(msg);
    }
}
