LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v13
LOCAL_STATIC_JAVA_LIBRARIES += xmp_toolkit


LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_SRC_FILES += $(call all-java-files-under, src_pd)

LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/res

LOCAL_AAPT_FLAGS := --auto-add-overlay

LOCAL_PACKAGE_NAME := Camera2

LOCAL_SDK_VERSION := current

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)

include $(call all-makefiles-under, $(LOCAL_PATH))