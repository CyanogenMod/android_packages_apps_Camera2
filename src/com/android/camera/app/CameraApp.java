/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.camera.app;

import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Debug;

import com.android.camera.stats.UsageStatistics;
import com.android.camera.stats.profiler.Profile;
import com.android.camera.stats.profiler.Profilers;
import com.android.camera.util.AndroidContext;
import com.android.camera.util.AndroidServices;


/**
 * The Camera application class containing important services and functionality
 * to be used across modules.
 */
public class CameraApp extends Application {
    /**
     * This is for debugging only: If set to true, application will not start
     * until a debugger is attached.
     * <p>
     * Use this if you need to debug code that is executed while the app starts
     * up and it would be too late to attach a debugger afterwards.
     */
    private static final boolean WAIT_FOR_DEBUGGER_ON_START = false;

    @Override
    public void onCreate() {
        super.onCreate();

        if (WAIT_FOR_DEBUGGER_ON_START) {
            Debug.waitForDebugger();
        }

        // Android context must be the first item initialized.
        Context context = getApplicationContext();
        AndroidContext.initialize(context);

        // This will measure and write to the exception handler if
        // the time between any two calls or the total time from
        // start to stop is over 10ms.
        Profile guard = Profilers.instance().guard("CameraApp onCreate()");

        // It is important that this gets called early in execution before the
        // app has had the opportunity to touch shared preferences.
        FirstRunDetector.instance().initializeTimeOfFirstRun(context);
        guard.mark("initializeTimeOfFirstRun");

        UsageStatistics.instance().initialize(this);
        guard.mark("UsageStatistics.initialize");

        clearNotifications();
        guard.stop("clearNotifications");
    }

    /**
     * Clears all notifications. This cleans up notifications that we might have
     * created earlier but remained after a crash.
     */
    private void clearNotifications() {
        NotificationManager manager = AndroidServices.instance().provideNotificationManager();
        if (manager != null) {
            manager.cancelAll();
        }
    }
}
