ifeq (0,1)
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := tests

LOCAL_SDK_VERSION := 16

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := CameraTests

LOCAL_INSTRUMENTATION_FOR := Gallery2

include $(BUILD_PACKAGE)
endif
