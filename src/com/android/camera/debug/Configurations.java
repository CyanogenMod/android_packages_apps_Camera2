package com.android.camera.debug;

/**
 * Predefined {@link com.android.camera.debug.Config}s.
 */
public enum Configurations implements Config {
    /**
     * Enables error logs and disable all others.
     */
    RELEASE {
        @Override
        public boolean isDebugging() {
            return false;
        }

        @Override
        public boolean logDebug() {
            return false;
        }

        @Override
        public boolean logError() {
            return true;
        }

        @Override
        public boolean logInfo() {
            return false;
        }

        @Override
        public boolean logVerbose() {
            return false;
        }

        @Override
        public boolean logWarn() {
            return false;
        }
    },

    /**
     * Enables everything.
     */
    EVERYTHING_ON {
        @Override
        public boolean isDebugging() {
            return true;
        }

        @Override
        public boolean logDebug() {
            return true;
        }

        @Override
        public boolean logError() {
            return true;
        }

        @Override
        public boolean logInfo() {
            return true;
        }

        @Override
        public boolean logVerbose() {
            return true;
        }

        @Override
        public boolean logWarn() {
            return true;
        }
    },

    /**
     * Logs everything but disables debug settings.
     */
    LOG_ALL_NO_DEBUG {
        @Override
        public boolean isDebugging() {
            return false;
        }

        @Override
        public boolean logDebug() {
            return true;
        }

        @Override
        public boolean logError() {
            return true;
        }

        @Override
        public boolean logInfo() {
            return true;
        }

        @Override
        public boolean logVerbose() {
            return true;
        }

        @Override
        public boolean logWarn() {
            return true;
        }
    };
}
