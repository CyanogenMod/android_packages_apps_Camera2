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

import com.android.camera.util.SystemProperties;

public class DebugPropertyHelper {
    /** Override for 3A properties. */
    private static final boolean ALL_3A_DEBUG_ON = false;

    private static final String OFF_VALUE = "0";
    private static final String ON_VALUE = "1";

    private static final String PREFIX = "persist.camera";

    /** Switch between PhotoModule and the new CaptureModule. */
    private static final String PROP_ENABLE_CAPTURE_MODULE = PREFIX + ".newcapture";
    /** Enable additional focus logging. */
    private static final String PROP_FOCUS_DEBUG_LOG = PREFIX + ".focus_debug_log";
    /** Enable additional debug UI to show AE, AF, Face detection states */
    private static final String PROP_3A_DEBUG_UI = PREFIX + ".3A_debug_ui";
    /** Write data about each capture request to disk. */
    private static final String PROP_WRITE_CAPTURE_DATA = PREFIX + ".capture_write";

    private static boolean isPropertyOn(String property) {
        return ON_VALUE.equals(SystemProperties.get(property, OFF_VALUE));
    }

    public static boolean isCaptureModuleEnabled() {
        return ALL_3A_DEBUG_ON || isPropertyOn(PROP_ENABLE_CAPTURE_MODULE);
    }

    public static boolean showFocusDebugLog() {
        return ALL_3A_DEBUG_ON || isPropertyOn(PROP_FOCUS_DEBUG_LOG);
    }

    public static boolean showFocusDebugUI() {
        return ALL_3A_DEBUG_ON || isPropertyOn(PROP_3A_DEBUG_UI);
    }

    public static boolean showFaceDebugUI() {
        return ALL_3A_DEBUG_ON || isPropertyOn(PROP_3A_DEBUG_UI);
    }

    public static boolean writeCaptureData() {
        return isPropertyOn(PROP_WRITE_CAPTURE_DATA);
    }
}
