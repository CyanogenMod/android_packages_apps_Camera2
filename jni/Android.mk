LOCAL_PATH:= $(call my-dir)

# TinyPlanet
include $(CLEAR_VARS)

LOCAL_CPP_EXTENSION := .cc
LOCAL_LDFLAGS   := -llog -ljnigraphics
LOCAL_SDK_VERSION := 9
LOCAL_MODULE    := libjni_tinyplanet
LOCAL_SRC_FILES := tinyplanet.cc

LOCAL_CFLAGS    += -ffast-math -O3 -funroll-loops
LOCAL_ARM_MODE := arm

include $(BUILD_SHARED_LIBRARY)

# JpegUtil
include $(CLEAR_VARS)

LOCAL_CFLAGS := -std=c++11
LOCAL_NDK_STL_VARIANT := c++_static
LOCAL_LDFLAGS   := -llog -ldl -ljnigraphics
LOCAL_SDK_VERSION := 9
LOCAL_MODULE    := libjni_jpegutil
LOCAL_SRC_FILES := jpegutil.cpp jpegutilnative.cpp

LOCAL_C_INCLUDES += external/jpeg

LOCAL_SHARED_LIBRARIES := libjpeg

LOCAL_CFLAGS    += -ffast-math -O3 -funroll-loops
LOCAL_ARM_MODE := arm

include $(BUILD_SHARED_LIBRARY)

# JpegUtilTest
include $(CLEAR_VARS)

LOCAL_C_INCLUDES += external/gtest/include
LOCAL_STATIC_LIBRARIES += libgtest_host libgtest_main_host

libjpeg_src_files := \
    jcapimin.c jcapistd.c jccoefct.c jccolor.c jcdctmgr.c jchuff.c \
    jcinit.c jcmainct.c jcmarker.c jcmaster.c jcomapi.c jcparam.c \
    jcphuff.c jcprepct.c jcsample.c jctrans.c jdapimin.c jdapistd.c \
    jdatadst.c jdatasrc.c jdcoefct.c jdcolor.c jddctmgr.c jdhuff.c \
    jdinput.c jdmainct.c jdmarker.c jdmaster.c jdmerge.c jdphuff.c \
    jdpostct.c jdsample.c jdtrans.c jerror.c jfdctflt.c jfdctfst.c \
    jfdctint.c jidctflt.c jidctfst.c jidctint.c jidctred.c jquant1.c \
    jquant2.c jutils.c jmemmgr.c jmemansi.c

LOCAL_SRC_FILES := $(addprefix ../../../../external/jpeg/,$(libjpeg_src_files)) jpegutil.cpp jpegutiltest.cpp

# LOCAL_CPP_INCLUDES := $(addprefix ../../../../external/jpeg/,libjpeg_h_files)
# LOCAL_CPP_INCLUDES := $(LOCAL_PATH)/../../../../external/jpeg/
LOCAL_C_INCLUDES += external/jpeg

LOCAL_CPPFLAGS := -g -Wall -std=c++11 -lpthread
# Disable optimization for debugging tests
LOCAL_CPPFLAGS += -O0
LOCAL_LDFLAGS += -lpthread

LOCAL_MODULE := Camera2-jpegutiltest
include $(BUILD_HOST_EXECUTABLE)
