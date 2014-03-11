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

/**
 * An interface which defines the debug configurations.
 */
public interface Config {

    /** @return {@code true} to switch on debug settings. */
    boolean isDebugging();

    /** @return {@code true} to enable isDebugging level logs. */
    boolean logDebug();

    /** @return {@code true} to enable ERROR level logs. */
    boolean logError();

    /** @return {@code true} to enable INFO level logs. */
    boolean logInfo();

    /** @return {@code true} to enable VERBOSE level logs. */
    boolean logVerbose();

    /** @return {@code true} to enable WARN level logs. */
    boolean logWarn();
}
