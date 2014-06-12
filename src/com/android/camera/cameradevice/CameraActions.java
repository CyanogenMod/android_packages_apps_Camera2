/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"),
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

package com.android.camera.cameradevice;

class CameraActions {
    // Camera initialization/finalization
    public static final int OPEN_CAMERA = 1;
    public static final int RELEASE =     2;
    public static final int RECONNECT =   3;
    public static final int UNLOCK =      4;
    public static final int LOCK =        5;
    // Preview
    public static final int SET_PREVIEW_TEXTURE_ASYNC =        101;
    public static final int START_PREVIEW_ASYNC =              102;
    public static final int STOP_PREVIEW =                     103;
    public static final int SET_PREVIEW_CALLBACK_WITH_BUFFER = 104;
    public static final int ADD_CALLBACK_BUFFER =              105;
    public static final int SET_PREVIEW_DISPLAY_ASYNC =        106;
    public static final int SET_PREVIEW_CALLBACK =             107;
    public static final int SET_ONE_SHOT_PREVIEW_CALLBACK =    108;
    // Parameters
    public static final int SET_PARAMETERS =     201;
    public static final int GET_PARAMETERS =     202;
    public static final int REFRESH_PARAMETERS = 203;
    public static final int APPLY_SETTINGS =     204;
    // Focus, Zoom
    public static final int AUTO_FOCUS =                   301;
    public static final int CANCEL_AUTO_FOCUS =            302;
    public static final int SET_AUTO_FOCUS_MOVE_CALLBACK = 303;
    public static final int SET_ZOOM_CHANGE_LISTENER =     304;
    // Face detection
    public static final int SET_FACE_DETECTION_LISTENER = 461;
    public static final int START_FACE_DETECTION =        462;
    public static final int STOP_FACE_DETECTION =         463;
    public static final int SET_ERROR_CALLBACK =          464;
    // Presentation
    public static final int ENABLE_SHUTTER_SOUND =    501;
    public static final int SET_DISPLAY_ORIENTATION = 502;
    // Capture
    public static final int CAPTURE_PHOTO = 601;
}
