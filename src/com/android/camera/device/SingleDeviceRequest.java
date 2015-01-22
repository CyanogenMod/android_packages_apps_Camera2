package com.android.camera.device;

import com.android.camera.async.Lifetime;
import com.android.camera.async.SafeCloseable;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.concurrent.ThreadSafe;

/**
 * ThreadSafe class to deal with the combined future and lifetime
 * for a single device request.
 */
@ThreadSafe
public class SingleDeviceRequest<TDevice> implements SafeCloseable {
    private final SettableFuture<TDevice> mFuture;
    private final Lifetime mLifetime;
    private final AtomicBoolean mIsClosed;

    public SingleDeviceRequest(Lifetime lifetime) {
        mLifetime = lifetime;
        mFuture = SettableFuture.create();
        mIsClosed = new AtomicBoolean(false);
    }

    /**
     * Return the future instance for this request.
     */
    public ListenableFuture<TDevice> getFuture() {
        return mFuture;
    }

    /**
     * Return the lifetime instance for this request.
     */
    public Lifetime getLifetime() {
        return mLifetime;
    }

    /**
     * If the future has not been set, set the value.
     */
    public boolean set(TDevice device) {
        if (!mIsClosed.get()) {
            return mFuture.set(device);
        } else {
            return false;
        }
    }

    public boolean isClosed() {
        return mIsClosed.get();
    }

    public void closeWithException(Throwable throwable) {
        if (!mIsClosed.getAndSet(true)) {
            mFuture.setException(throwable);
            mLifetime.close();
        }
    }

    @Override
    public void close() {
        if (!mIsClosed.getAndSet(true)) {
            mFuture.cancel(true /* mayInterruptIfRunning */);
            mLifetime.close();
        }
    }
}
