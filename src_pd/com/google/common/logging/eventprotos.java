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
    public class CameraEvent {

        public class InteractionType {
            public static final int SHARE = 0;
            public static final int DELETE = 0;
        }

        public class InteractionCause {
            public static final int BUTTON = 0;
            public static final int SWIPE_LEFT = 0;
            public static final int SWIPE_UP = 0;
            public static final int SWIPE_DOWN = 0;
            public static final int SWIPE_RIGHT = 0;
        }
    }

    public class NavigationChange {
        public class Mode {
            public static final int PHOTO_CAPTURE = 0;
            public static final int FILMSTRIP = 0;
            public static final int VIDEO_CAPTURE = 0;
            public static final int VIDEO_STILL = 0;
            public static final int GALLERY = 0;
        }
    }

    public class CameraFailure {
        public class FailureReason {
            public static final int SECURITY = 0;
            public static final int OPEN_FAILURE = 0;
            public static final int RECONNECT_FAILURE = 0;
        }
    }

    public class ForegroundEvent {
        public class ForegroundSource {
            public static final int ICON_LAUNCHER = 0;
            public static final int LOCK_SCREEN = 0;
            public static final int INTENT_PICKER = 0;
        }
    }
}
