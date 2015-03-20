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
 */
public interface CameraDeviceActionProvider<TDevice> {

    /**
     * Return a new set of device and api specific actions for the given
     * types.
     */
    public SingleDeviceActions<TDevice> get(CameraDeviceKey key);
}
