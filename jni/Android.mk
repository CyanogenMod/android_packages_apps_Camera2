LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_CFLAGS += -DEGL_EGLEXT_PROTOTYPES

LOCAL_SRC_FILES := jni_egl_fence.cpp

LOCAL_SDK_VERSION := 9

LOCAL_LDFLAGS :=  -llog -lEGL

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE := libjni_eglfence

include $(BUILD_SHARED_LIBRARY)
