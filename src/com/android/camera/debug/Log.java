/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera.debug;

public class Log {
    public static final String CAMERA_LOGTAG_PREFIX = "CAM_";
    private static final Log.Tag TAG = new Log.Tag("Log");

    /**
     * This class restricts the length of the log tag to be less than the
     * framework limit and also prepends the common tag prefix defined by
     * {@code CAMERA_LOGTAG_PREFIX}.
     */
    public static final class Tag {

        // The length limit from Android framework is 23.
        private static final int MAX_TAG_LEN = 23 - CAMERA_LOGTAG_PREFIX.length();

        final String mValue;

        public Tag(String tag) {
            final int lenDiff = tag.length() - MAX_TAG_LEN;
            if (lenDiff > 0) {
                w(TAG, "Tag " + tag + " is " + lenDiff + " chars longer than limit.");
            }
            mValue = CAMERA_LOGTAG_PREFIX + (lenDiff > 0 ? tag.substring(0, MAX_TAG_LEN) : tag);
        }

        @Override
        public String toString() {
            return mValue;
        }
    }

    private interface Logger {
        void log(Tag tag, String msg);
        void log(Tag tag, String msg, Throwable tr);
    }

    private static final Logger SILENT_LOGGER = new Logger() {
        @Override
        public void log(Tag tag, String msg) {
            // Do nothing.
        }

        @Override
        public void log(Tag tag, String msg, Throwable tr) {
            // Do nothing.
        }
    };

    private static final Logger LOGGER_D = (!CurrentConfig.get().logDebug() ? SILENT_LOGGER : new Logger() {
        final int level = android.util.Log.DEBUG;

        @Override
        public void log(Tag tag, String msg) {
            if (isLoggable(tag, level)) {
                android.util.Log.d(tag.toString(), msg);
            }
        }

        @Override
        public void log(Tag tag, String msg, Throwable tr) {
            if (isLoggable(tag, level)) {
                android.util.Log.d(tag.toString(), msg, tr);
            }
        }
    });

    private static final Logger LOGGER_E = (!CurrentConfig.get().logError() ? SILENT_LOGGER : new Logger() {
        final int level = android.util.Log.ERROR;

        @Override
        public void log(Tag tag, String msg) {
            if (isLoggable(tag, level)) {
                android.util.Log.e(tag.toString(), msg);
            }
        }

        @Override
        public void log(Tag tag, String msg, Throwable tr) {
            if (isLoggable(tag, level)) {
                android.util.Log.e(tag.toString(), msg, tr);
            }
        }
    });

    private static final Logger LOGGER_I = (!CurrentConfig.get().logInfo() ? SILENT_LOGGER : new Logger() {
        final int level = android.util.Log.INFO;

        @Override
        public void log(Tag tag, String msg) {
            if (isLoggable(tag, level)) {
                android.util.Log.i(tag.toString(), msg);
            }
        }

        @Override
        public void log(Tag tag, String msg, Throwable tr) {
            if (isLoggable(tag, level)) {
                android.util.Log.i(tag.toString(), msg, tr);
            }
        }
    });

    private static final Logger LOGGER_V = (!CurrentConfig.get().logVerbose() ? SILENT_LOGGER : new Logger() {
        final int level = android.util.Log.VERBOSE;

        @Override
        public void log(Tag tag, String msg) {
            if (isLoggable(tag, level)) {
                android.util.Log.v(tag.toString(), msg);
            }
        }

        @Override
        public void log(Tag tag, String msg, Throwable tr) {
            if (isLoggable(tag, level)) {
                android.util.Log.v(tag.toString(), msg, tr);
            }
        }
    });

    private static final Logger LOGGER_W = (!CurrentConfig.get().logWarn() ? SILENT_LOGGER : new Logger() {
        final int level = android.util.Log.WARN;

        @Override
        public void log(Tag tag, String msg) {
            if (isLoggable(tag, level)) {
                android.util.Log.w(tag.toString(), msg);
            }
        }

        @Override
        public void log(Tag tag, String msg, Throwable tr) {
            if (isLoggable(tag, level)) {
                android.util.Log.w(tag.toString(), msg, tr);
            }
        }
    });


    public static void d(Tag tag, String msg) {
        LOGGER_D.log(tag, msg);
    }

    public static void d(Tag tag, String msg, Throwable tr) {
        LOGGER_D.log(tag, msg, tr);
    }

    public static void e(Tag tag, String msg) {
        LOGGER_E.log(tag, msg);
    }

    public static void e(Tag tag, String msg, Throwable tr) {
        LOGGER_E.log(tag, msg, tr);
    }

    public static void i(Tag tag, String msg) {
        LOGGER_I.log(tag, msg);
    }

    public static void i(Tag tag, String msg, Throwable tr) {
        LOGGER_I.log(tag, msg, tr);
    }

    public static void v(Tag tag, String msg) {
        LOGGER_V.log(tag, msg);
    }

    public static void v(Tag tag, String msg, Throwable tr) {
        LOGGER_V.log(tag, msg, tr);
    }

    public static void w(Tag tag, String msg) {
        LOGGER_W.log(tag, msg);
    }

    public static void w(Tag tag, String msg, Throwable tr) {
        LOGGER_W.log(tag, msg, tr);
    }

    private static boolean isLoggable(Tag tag, int level) {
        try {
            return CurrentConfig.get().isDebugging()
                    || android.util.Log.isLoggable(tag.toString(), level);
        } catch (IllegalArgumentException ex) {
            e(TAG, "Tag too long:" + tag);
            return false;
        }
    }
}
