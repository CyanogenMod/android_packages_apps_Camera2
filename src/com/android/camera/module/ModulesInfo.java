/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.camera.module;

import android.content.Context;

import com.android.camera.PhotoModule;
import com.android.camera.VideoModule;
import com.android.camera.WideAnglePanoramaModule;
import com.android.camera.app.CameraServices;
import com.android.camera.app.ModuleManager;
import com.android.camera.ui.ModeListView;
import com.android.camera.util.GcamHelper;
import com.android.camera.util.PhotoSphereHelper;
import com.android.camera.util.RefocusHelper;

/**
 * A class holding the module information and registers them to
 * {@link com.android.camera.app.ModuleManager}.
 */
public class ModulesInfo {
    private static final String TAG = "ModulesInfo";

    // TODO: Decouple the logic between modules and modes.
    public static final int MODULE_PHOTO = ModeListView.MODE_PHOTO;
    public static final int MODULE_VIDEO = ModeListView.MODE_VIDEO;
    public static final int MODULE_PHOTOSPHERE = ModeListView.MODE_PHOTOSPHERE;
    public static final int MODULE_CRAFT = ModeListView.MODE_CRAFT;
    public static final int MODULE_TIMELAPSE = ModeListView.MODE_TIMELAPSE;
    public static final int MODULE_WIDEANGLE = ModeListView.MODE_WIDEANGLE;
    public static final int MODULE_GCAM = ModeListView.MODE_GCAM;

    public static void setupModules(Context context, ModuleManager moduleManager) {
        registerPhotoModule(moduleManager);
        moduleManager.setDefaultModuleIndex(MODULE_PHOTO);
        registerVideoModule(moduleManager);
        registerWideAngleModule(moduleManager);
        if (PhotoSphereHelper.hasLightCycleCapture(context)) {
            registerPhotoSphereModule(moduleManager);
        }
        if (RefocusHelper.hasRefocusCapture(context)) {
            registerRefocusModule(moduleManager);
        }
        if (GcamHelper.hasGcamCapture()) {
            registerGcamModule(moduleManager);
        }
    }

    private static void registerPhotoModule(ModuleManager moduleManager) {
        moduleManager.registerModule(new ModuleManager.ModuleAgent() {
            @Override
            public int getModuleId() {
                return MODULE_PHOTO;
            }

            @Override
            public boolean requestAppForCamera() {
                return true;
            }

            @Override
            public ModuleController createModule(CameraServices services) {
                return new PhotoModule(services);
            }
        });
    }

    private static void registerVideoModule(ModuleManager moduleManager) {
        moduleManager.registerModule(new ModuleManager.ModuleAgent() {
            @Override
            public int getModuleId() {
                return MODULE_VIDEO;
            }

            @Override
            public boolean requestAppForCamera() {
                return true;
            }

            @Override
            public ModuleController createModule(CameraServices services) {
                return new VideoModule(services);
            }
        });
    }

    private static void registerWideAngleModule(ModuleManager moduleManager) {
        moduleManager.registerModule(new ModuleManager.ModuleAgent() {
            @Override
            public int getModuleId() {
                return MODULE_WIDEANGLE;
            }

            @Override
            public boolean requestAppForCamera() {
                return false;
            }

            @Override
            public ModuleController createModule(CameraServices services) {
                return new WideAnglePanoramaModule(services);
            }
        });
    }

    private static void registerPhotoSphereModule(ModuleManager moduleManager) {
        moduleManager.registerModule(new ModuleManager.ModuleAgent() {
            @Override
            public int getModuleId() {
                return MODULE_PHOTOSPHERE;
            }

            @Override
            public boolean requestAppForCamera() {
                return true;
            }

            @Override
            public ModuleController createModule(CameraServices services) {
                // TODO: remove the type casting.
                return (ModuleController) PhotoSphereHelper.createPanoramaModule(services);
            }
        });
    }

    private static void registerRefocusModule(ModuleManager moduleManager) {
        moduleManager.registerModule(new ModuleManager.ModuleAgent() {
            @Override
            public int getModuleId() {
                return MODULE_CRAFT;
            }

            @Override
            public boolean requestAppForCamera() {
                return true;
            }

            @Override
            public ModuleController createModule(CameraServices services) {
                // TODO: remove the type casting.
                return (ModuleController) RefocusHelper.createRefocusModule(services);
            }
        });
    }

    private static void registerGcamModule(ModuleManager moduleManager) {
        moduleManager.registerModule(new ModuleManager.ModuleAgent() {
            @Override
            public int getModuleId() {
                return MODULE_GCAM;
            }

            @Override
            public boolean requestAppForCamera() {
                return false;
            }

            @Override
            public ModuleController createModule(CameraServices services) {
                return (ModuleController) GcamHelper.createGcamModule(services);
            }
        });
    }
}
