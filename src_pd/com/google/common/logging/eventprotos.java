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


package com.google.common.logging;

public class eventprotos {
    public class MediaInteraction {
        public class InteractionType {
            public static final int SHARE = 10000;
            public static final int EDIT = 10000;
            public static final int DELETE = 10000;
            public static final int DETAILS = 10000;
        }
    }

    public class NavigationChange {
        public class InteractionCause {
            public static final int BUTTON = 10000;
            public static final int SWIPE_LEFT = 10000;
            public static final int SWIPE_UP = 10000;
            public static final int SWIPE_DOWN = 10000;
            public static final int SWIPE_RIGHT = 10000;
        }
        public class Mode {
            public static final int UNKNOWN_MODE = 10000;
            public static final int PHOTO_CAPTURE = 10000;
            public static final int FILMSTRIP = 10000;
            public static final int VIDEO_CAPTURE = 10000;
            public static final int VIDEO_STILL = 10000;
            public static final int PHOTO_SPHERE = 10000;
            public static final int LENS_BLUR = 10000;
            public static final int HDR_PLUS = 10000;
            public static final int PANORAMA = 10000;
            public static final int GALLERY = 10000;
        }
    }

    public class CameraFailure {
        public class FailureReason {
            public static final int UNKNOWN_REASON = 10000;
            public static final int SECURITY = 10000;
            public static final int OPEN_FAILURE = 10000;
            public static final int RECONNECT_FAILURE = 10000;
            public static final int API_RUNTIME_EXCEPTION = 10000;
            public static final int API_TIMEOUT = 10000;
        }
    }

    public class ForegroundEvent {
        public class ForegroundSource {
            public static final int UNKNOWN_SOURCE = 10000;
            public static final int ACTION_IMAGE_CAPTURE = 10000;
            public static final int ACTION_IMAGE_CAPTURE_SECURE = 10000;
            public static final int ACTION_VIDEO_CAPTURE = 10000;
            public static final int ACTION_MAIN = 10000;
            public static final int ACTION_STILL_IMAGE_CAMERA_SECURE = 10000;
            public static final int ACTION_VIDEO_CAMERA = 10000;
            public static final int ACTION_STILL_IMAGE_CAMERA = 10000;
        }
    }

    public class ControlEvent {
        public class ControlType {
            public static final int TAP_TO_FOCUS = 10000;
            public static final int MENU_FULL_FROM_HIDDEN = 10000;
            public static final int MENU_FULL_FROM_SCROLL = 10000;
            public static final int MENU_SCROLL_FROM_SHIMMY = 10000;
            public static final int OVERALL_SETTINGS = 10000;
        }
    }
}
