/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.camera.debug.Log.Tag;
import com.google.common.annotations.VisibleForTesting;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Set of commonly used loggers.
 */
@ParametersAreNonnullByDefault
public class Loggers {
    /**
     * This creates a factory that will eat all log input.
     */
    public static Logger.Factory noOpFactory() {
        return NoOpLoggerFactory.instance();
    }

    /**
     * This creates a factory that will use the standard android static log
     * methods.
     */
    public static Logger.Factory tagFactory() {
        return TagLoggerFactory.instance();
    }

    /**
     * Creates a logger factory which always returns the given logger.
     */
    public static Logger.Factory factoryFor(final Logger logger) {
        return new Logger.Factory() {
            @Override
            public Logger create(Tag tag) {
                return logger;
            }
        };
    }

    /**
     * Creates loggers that eat all input and does nothing.
     */
    private static class NoOpLoggerFactory implements Logger.Factory {
        private static class Singleton {
            private static final NoOpLoggerFactory INSTANCE = new NoOpLoggerFactory();
        }

        public static NoOpLoggerFactory instance() {
            return Singleton.INSTANCE;
        }

        private final NoOpLogger mNoOpLogger;

        public NoOpLoggerFactory() {
            mNoOpLogger = new NoOpLogger();
        }

        @Override
        public Logger create(Tag tag) {
            return mNoOpLogger;
        }
    }

    /**
     * Creates loggers that use tag objects to write to standard android log
     * output.
     */
    private static class TagLoggerFactory implements Logger.Factory {
        private static class Singleton {
            private static final TagLoggerFactory INSTANCE = new TagLoggerFactory();
        }

        public static TagLoggerFactory instance() {
            return Singleton.INSTANCE;
        }

        @Override
        public Logger create(Tag tag) {
            return new TagLogger(tag);
        }
    }

    /**
     * NoOp logger eats all input messages and does not display them.
     */
    private static class NoOpLogger implements Logger {
        @Override
        public void d(String msg) {
        }

        @Override
        public void d(String msg, Throwable tr) {
        }

        @Override
        public void e(String msg) {
        }

        @Override
        public void e(String msg, Throwable tr) {
        }

        @Override
        public void i(String msg) {
        }

        @Override
        public void i(String msg, Throwable tr) {
        }

        @Override
        public void v(String msg) {
        }

        @Override
        public void v(String msg, Throwable tr) {
        }

        @Override
        public void w(String msg) {
        }

        @Override
        public void w(String msg, Throwable tr) {
        }
    }

    /**
     * TagLogger logger writes to the standard static log output with the given
     * tag object.
     */
    private static class TagLogger implements Logger {
        private final Log.Tag mTag;

        public TagLogger(Log.Tag tag) {
            mTag = tag;
        }

        @Override
        public void d(String msg) {
            Log.d(mTag, msg);
        }

        @Override
        public void d(String msg, Throwable tr) {
            Log.d(mTag, msg, tr);
        }

        @Override
        public void e(String msg) {
            Log.e(mTag, msg);
        }

        @Override
        public void e(String msg, Throwable tr) {
            Log.e(mTag, msg, tr);
        }

        @Override
        public void i(String msg) {
            Log.i(mTag, msg);
        }

        @Override
        public void i(String msg, Throwable tr) {
            Log.i(mTag, msg, tr);
        }

        @Override
        public void v(String msg) {
            Log.v(mTag, msg);
        }

        @Override
        public void v(String msg, Throwable tr) {
            Log.v(mTag, msg, tr);
        }

        @Override
        public void w(String msg) {
            Log.w(mTag, msg);
        }

        @Override
        public void w(String msg, Throwable tr) {
            Log.w(mTag, msg, tr);
        }
    }
}
