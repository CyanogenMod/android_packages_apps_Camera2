package com.android.camera.app;

import android.os.Handler;
import android.view.OrientationEventListener;

/**
 * An interface which defines the orientation manager.
 */
public interface OrientationManager {
    public final static int ORIENTATION_UNKNOWN = OrientationEventListener.ORIENTATION_UNKNOWN;

    public interface OnOrientationChangeListener {
        /**
         * Called when the orientation changes.
         *
         * @param orientation The current orientation.
         */
        public void onOrientationChanged(int orientation);
    }

    /**
     * Adds the
     * {@link com.android.camera.app.OrientationManager.OnOrientationChangeListener}.
     */
    public void addOnOrientationChangeListener(
            Handler handler, OnOrientationChangeListener listener);

    /**
     * Removes the listener.
     */
    public void removeOnOrientationChangeListener(
            Handler handler, OnOrientationChangeListener listener);

    /**
     * Lock the framework orientation to the current device orientation
     * rotates. No effect if the system setting of auto-rotation is off.
     */
    void lockOrientation();

    /**
     * Unlock the framework orientation, so it can change when the device
     * rotates. No effect if the system setting of auto-rotation is off.
     */
    void unlockOrientation();

    /** @return Whether the orientation is locked by the app or the system. */
    boolean isOrientationLocked();

    /**
     * Returns the display rotation degrees relative to the natural orientation
     * in clockwise.
     *
     * @return 0, 90, 180, or 270.
     */
    int getDisplayRotation();
}
