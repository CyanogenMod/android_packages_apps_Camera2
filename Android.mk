LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v13
LOCAL_STATIC_JAVA_LIBRARIES += com.android.gallery3d.common2
LOCAL_STATIC_JAVA_LIBRARIES += xmp_toolkit
LOCAL_STATIC_JAVA_LIBRARIES += mp4parser

LOCAL_SRC_FILES := $(call all-java-files-under, src) $(call all-renderscript-files-under, src)
LOCAL_SRC_FILES += $(call all-java-files-under, src_pd)
LOCAL_SRC_FILES += $(call all-java-files-under, ../Camera/src)

LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/res 
LOCAL_RESOURCE_DIR += packages/apps/Camera/res

LOCAL_AAPT_FLAGS := --auto-add-overlay \
    --extra-packages com.android.camera

LOCAL_PACKAGE_NAME := Gallery2

LOCAL_OVERRIDES_PACKAGES := Gallery Gallery3D GalleryNew3D

LOCAL_SDK_VERSION := current

# If this is an unbundled build (to install seprately) then include
# the libraries in the APK, otherwise just put them in /system/lib and
# leave them out of the APK
ifneq (,$(TARGET_BUILD_APPS))
  LOCAL_JNI_SHARED_LIBRARIES := libjni_mosaic libjni_eglfence libjni_filtershow_filters
else
  LOCAL_REQUIRED_MODULES := libjni_mosaic libjni_eglfence libjni_filtershow_filters
endif

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)

include $(call all-makefiles-under, jni)

ifeq ($(strip $(LOCAL_PACKAGE_OVERRIDES)),)
# Use the following include to make gallery test apk.
include $(call all-makefiles-under, $(LOCAL_PATH))

# Use the following include to make camera test apk.
include $(call all-makefiles-under, ../Camera)

endif
