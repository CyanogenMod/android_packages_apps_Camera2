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

package com.android.camera.app;

import android.util.SparseArray;

/**
 * A class which implements {@link com.android.camera.app.ModuleManager}.
 */
public class ModuleManagerImpl implements ModuleManager {
    private static final String TAG = "ModuleManagerImpl";

    private SparseArray<ModuleAgent> mRegisteredModuleAgents = new
            SparseArray<ModuleAgent>(2);
    private int mDefaultModuleId = MODULE_INDEX_NONE;

    public ModuleManagerImpl() {
    }

    @Override
    public void registerModule(ModuleAgent agent) {
        if (agent == null) {
            throw new NullPointerException("Registering a null ModuleAgent.");
        }
        final int moduleId = agent.getModuleId();
        if (moduleId == MODULE_INDEX_NONE) {
            throw new IllegalArgumentException(
                    "ModuleManager: The module ID can not be " + "MODULE_INDEX_NONE");
        }
        if (mRegisteredModuleAgents.get(moduleId) != null) {
            throw new IllegalArgumentException("Module ID is registered already:" + moduleId);
        }
        mRegisteredModuleAgents.put(moduleId, agent);
    }

    @Override
    public boolean unregisterModule(int moduleId) {
        if (mRegisteredModuleAgents.get(moduleId) == null) {
            return false;
        }
        mRegisteredModuleAgents.delete(moduleId);
        if (moduleId == mDefaultModuleId) {
            mDefaultModuleId = -1;
        }
        return true;
    }

    @Override
    public boolean setDefaultModuleIndex(int moduleId) {
        if (mRegisteredModuleAgents.get(moduleId) != null) {
            mDefaultModuleId = moduleId;
            return true;
        }
        return false;
    }

    @Override
    public int getDefaultModuleIndex() {
        return mDefaultModuleId;
    }

    @Override
    public ModuleAgent getModuleAgent(int moduleId) {
        ModuleAgent agent = mRegisteredModuleAgents.get(moduleId);
        if (agent == null) {
            return mRegisteredModuleAgents.get(mDefaultModuleId);
        }
        return mRegisteredModuleAgents.get(moduleId);
    }
}
