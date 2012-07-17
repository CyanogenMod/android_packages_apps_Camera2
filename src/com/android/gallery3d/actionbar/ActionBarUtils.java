/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.actionbar;

import android.app.Activity;

import com.android.gallery3d.common.ApiHelper;

public class ActionBarUtils {

    public static ActionBarInterface getActionBar(Activity activity) {
        return ApiHelper.HAS_ACTION_BAR
                ? new SystemActionBarWrapper(activity)
                : new SimpleActionBar(activity);
    }

    public static ActionModeInterface startActionMode(
            Activity activity, ActionModeInterface.Callback callback) {
        return ApiHelper.HAS_ACTION_BAR
                ? new SystemActionModeWrapper(activity, callback)
                : new SimpleActionMode();
    }
}
