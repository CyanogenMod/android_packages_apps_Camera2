package com.android.camera.device;

import com.android.camera.async.Lifetime;

/**
 * Device specific actions for opening and closing a device.
 */
public interface SingleDeviceActions<TDevice> {

    /**
     * Open the device represented by this instance. This should only
     * be called if there is a reasonable expectation that the device is
     * available and openable.
     */
    public void executeOpen(SingleDeviceOpenListener<TDevice> openListener,
          Lifetime deviceLifetime);
    /**
     * Close the device represented by this instance.
     */
    public void executeClose(SingleDeviceCloseListener closeListener, TDevice device);
}
