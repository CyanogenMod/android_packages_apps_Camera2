package com.android.camera.device;

/**
 * Provides a set of executable actions for a given camera device key.
 *
 * In the case of Camera2 API, this is the example signature:
 *
 * <pre><code>
 * Provider implements CameraDeviceActionProvider<CameraDevice, String>
 * </code></pre>
 *
 * @param <TDevice> The type of camera device the actions produce.
 * @param <TDeviceId> The specific device id type the actions use.
 */
public interface CameraDeviceActionProvider<TDevice, TDeviceId> {

    /**
     * Return a new set of device and api specific actions for the given
     * types.
     */
    public SingleDeviceActions<TDevice> get(CameraDeviceKey<TDeviceId> key);
}
