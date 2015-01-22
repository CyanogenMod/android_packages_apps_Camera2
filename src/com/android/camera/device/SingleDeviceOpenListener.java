package com.android.camera.device;

/**
 * Listener for camera opening lifecycle events.
 */
public interface SingleDeviceOpenListener<TDevice> {
    /**
     * Executed when a device is successfully opened.
     * @param device the open device.
     */
    public void onDeviceOpened(TDevice device);

    /**
     * Executed when an exception occurs opening the device.
     */
    public void onDeviceOpenException(Throwable throwable);

    /**
     * Executed when an exception occurs opening the device
     * and the actual device object is provided.
     */
    public void onDeviceOpenException(TDevice device);
}
