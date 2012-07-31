LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_CFLAGS += -DEGL_EGLEXT_PROTOTYPES

LOCAL_SRC_FILES := jni_egl_fence.cpp

ifeq ($(TARGET_ARCH), arm)
        LOCAL_NDK_VERSION := 5
        LOCAL_SDK_VERSION := 9
endif

ifeq ($(TARGET_ARCH), x86)
        LOCAL_NDK_VERSION := 6
        LOCAL_SDK_VERSION := 9
endif

LOCAL_LDFLAGS :=  -llog -lEGL

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE := libjni_eglfence

include $(BUILD_SHARED_LIBRARY)
