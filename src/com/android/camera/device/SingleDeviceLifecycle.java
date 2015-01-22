package com.android.camera.device;

import com.android.camera.async.Lifetime;
import com.android.camera.async.SafeCloseable;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Lifecycle for a single device from open to close.
 */
public interface SingleDeviceLifecycle<TDevice, TKey> extends SafeCloseable {
    /**
     * Get the camera device key for this lifecycle.
     */
    public TKey getId();

    /**
     * This should create a new request for each invocation and
     * should cancel the previous request (assuming that the previous
     * request has not been completed).
     */
    public ListenableFuture<TDevice> createRequest(Lifetime lifetime);

    /**
     * Tell this instance that it should attempt to get the device to
     * an open and ready state.
     */
    public void open();
}
