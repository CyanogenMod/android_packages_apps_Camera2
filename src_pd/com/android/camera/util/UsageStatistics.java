package com.android.camera.util;

import android.content.Context;

public class UsageStatistics {

    public static final String COMPONENT_GALLERY = "Gallery";
    public static final String COMPONENT_CAMERA = "Camera";
    public static final String COMPONENT_EDITOR = "Editor";
    public static final String COMPONENT_IMPORTER = "Importer";

    public static final String TRANSITION_BACK_BUTTON = "BackButton";
    public static final String TRANSITION_UP_BUTTON = "UpButton";
    public static final String TRANSITION_PINCH_IN = "PinchIn";
    public static final String TRANSITION_PINCH_OUT = "PinchOut";
    public static final String TRANSITION_INTENT = "Intent";
    public static final String TRANSITION_ITEM_TAP = "ItemTap";
    public static final String TRANSITION_MENU_TAP = "MenuTap";
    public static final String TRANSITION_BUTTON_TAP = "ButtonTap";
    public static final String TRANSITION_SWIPE = "Swipe";

    public static final String ACTION_CAPTURE_START = "CaptureStart";
    public static final String ACTION_CAPTURE_FAIL = "CaptureFail";
    public static final String ACTION_CAPTURE_DONE = "CaptureDone";
    public static final String ACTION_SHARE = "Share";

    public static final String CATEGORY_LIFECYCLE = "AppLifecycle";
    public static final String CATEGORY_BUTTON_PRESS = "ButtonPress";

    public static final String LIFECYCLE_START = "Start";

    public static void initialize(Context context) {}
    public static void setPendingTransitionCause(String cause) {}
    public static void onContentViewChanged(String screenComponent, String screenName) {}
    public static void onEvent(String category, String action, String label) {};
    public static void onEvent(String category, String action, String label, long optional_value) {};
}
