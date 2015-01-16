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

package com.android.camera.one;

import com.android.camera.async.ForwardingObservable;
import com.android.camera.async.Observable;
import com.android.camera.async.Observables;
import com.google.common.base.Function;

/**
 * Translates from the Flash Mode setting (stored as a string) to the
 * appropriate {@link OneCamera.PhotoCaptureParameters.Flash} value.
 */
public class FlashSetting extends ForwardingObservable<OneCamera.PhotoCaptureParameters.Flash> {
    private static class FlashStringToEnum implements
            Function<String, OneCamera.PhotoCaptureParameters.Flash> {
        @Override
        public OneCamera.PhotoCaptureParameters.Flash apply(String settingString) {
            return OneCamera.PhotoCaptureParameters.Flash.decodeSettingsString(settingString);
        }
    }

    public FlashSetting(Observable<String> flashSettingString) {
        super(Observables.transform(flashSettingString, new FlashStringToEnum()));
    }
}
