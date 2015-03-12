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
     *
     * It is possible for this to throw if there is a problem with the
     * parameters or if the camera device determined to be un-openable.
     */
    public void executeOpen(SingleDeviceOpenListener<TDevice> openListener,
          Lifetime deviceLifetime) throws UnsupportedOperationException;
    /**
     * Close the device represented by this instance.
     *
     * It is possible for this to throw if there is a problem with the
     * parameters used to create these actions.
     */
    public void executeClose(SingleDeviceCloseListener closeListener, TDevice device)
          throws UnsupportedOperationException;
}
