package com.android.camera.device;

/**
 * Listen to full shutdown events of a single device and api combination.
 */
public interface SingleDeviceShutdownListener<TKey> {
    /**
     * This should be called once, and only once, when a single device
     * represented by the provided key is completely shut down.
     */
    public void onShutdown(TKey key);
}
