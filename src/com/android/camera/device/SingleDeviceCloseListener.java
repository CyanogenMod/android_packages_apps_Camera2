package com.android.camera.device;

/**
 * Listener for device closing lifecycle events.
 */
public interface SingleDeviceCloseListener {
    /**
     * Occurs when the device is closed.
     */
    public void onDeviceClosed();

    /**
     * Occurs when there is an exception closing the device.
     */
    public void onDeviceClosingException(Throwable exception);
}
