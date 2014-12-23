package com.android.camera.app;

/**
 * An interface which defines the orientation manager.
 */
public interface OrientationManager {
    public static enum DeviceOrientation {
        UNKNOWN(-1),
        CLOCKWISE_0(0),
        CLOCKWISE_90(90),
        CLOCKWISE_180(180),
        CLOCKWISE_270(270);

        private final int mDegrees;

        private DeviceOrientation(int degrees) {
            mDegrees = degrees;
        }

        /**
         * Returns the degree in clockwise.
         */
        public int getDegrees() {
            return mDegrees;
        }

        /**
         * Turns a degree value (0, 90, 180, 270) into one of CLOCKWISE_0,
         * CLOCKWISE_90, CLOCKWISE_180 or CLOCKWISE_270. If any other degree
         * value is given, UNKNOWN is returned.
         */
        public static DeviceOrientation from(int degrees) {
            switch (degrees) {
                case 0:
                    return CLOCKWISE_0;
                case 90:
                    return CLOCKWISE_90;
                case 180:
                    return CLOCKWISE_180;
                case 270:
                    return CLOCKWISE_270;
                default:
                    return UNKNOWN;
            }
        }
    }

    public interface OnOrientationChangeListener {
        /**
         * Called when the orientation changes.
         *
         * @param orientationManager The orientation manager detects the change.
         * @param orientation The new rounded orientation.
         */
        public void onOrientationChanged(OrientationManager orientationManager,
                                         DeviceOrientation orientation);
    }

    /**
     * Adds the
     * {@link com.android.camera.app.OrientationManager.OnOrientationChangeListener}.
     */
    public void addOnOrientationChangeListener(OnOrientationChangeListener listener);

    /**
     * Removes the listener.
     */
    public void removeOnOrientationChangeListener(OnOrientationChangeListener listener);

    /**
     * Returns the current rounded device orientation.
     */
    public DeviceOrientation getDeviceOrientation();

    /**
     * Returns whether the device is in landscape based on the natural orientation
     * and rotation from natural orientation.
     */
    public boolean isInLandscape();

    /**
     * Returns whether the device is in portrait based on the natural orientation
     * and rotation from natural orientation.
     */
    public boolean isInPortrait();

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

    /**
     * Return whether the orientation is locked by the app or the system.
     */
    boolean isOrientationLocked();
}
