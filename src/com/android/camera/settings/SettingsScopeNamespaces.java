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

package com.android.camera.settings;

/**
 * String namespaces to define a set of common settings in a scope. For instance
 * most 'Camera mode' related modules will store and retrieve settings with a
 * {@link #PHOTO} namespace.
 */
public final class SettingsScopeNamespaces {
    // Settings namespace for all typical photo modes (PhotoModule,
    // CaptureModule, CaptureIntentModule, etc.).
    public static final String PHOTO = "PhotoModule";
    public static final String VIDEO = "VideoModule";
    // Settings namespace for all panorama/wideangle modes.
    public static final String PANORAMA = "PanoramaModule";
    public static final String REFOCUS = "RefocusModule";
}
