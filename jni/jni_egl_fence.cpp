/*
 * Copyright (C) 2012 The Android Open Source Project
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

#include "jni_egl_fence.h"

#include <cutils/log.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>

void
Java_com_android_gallery3d_photoeditor_FilterStack_nativeEglSetFenceAndWait(JNIEnv* env,
                                                                          jobject thiz) {
  EGLDisplay display = eglGetCurrentDisplay();

  // Create a egl fence and wait for egl to return it.
  // Additional reference on egl fence sync can be found in:
  // http://www.khronos.org/registry/vg/extensions/KHR/EGL_KHR_fence_sync.txt
  EGLSyncKHR fence = eglCreateSyncKHR(display, EGL_SYNC_FENCE_KHR, NULL);
  if (fence == EGL_NO_SYNC_KHR) {
    return;
  }

  EGLint result = eglClientWaitSyncKHR(display,
                                       fence,
                                       EGL_SYNC_FLUSH_COMMANDS_BIT_KHR,
                                       EGL_FOREVER_KHR);
  if (result == EGL_FALSE) {
    ALOGE("EGL FENCE: error waiting for fence: %#x", eglGetError());
  }
  eglDestroySyncKHR(display, fence);
}
