/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.camera.burst;

import java.util.List;
import java.util.Set;

/**
 * The result of a captured burst.
 * <p/>
 * A BurstResult consists of a series of BurstArtifacts. Artifacts store their
 * media content in BurstMediaItem containers.
 * <p/>
 * Each artifact has a type-name that specifies the kind of artifact it
 * represents (e.g. GIF, collage, etc.). These types are implementation
 * specific. An artifact only contains media items of a single type. A
 * BurstMediaItem contains media content and associated metadata and can be an
 * image, an animated GIF, a collage or any other drawable media.
 */
public interface BurstResult {
    /**
     * Returns the list of all artifacts included in this result.
     */
    public List<BurstArtifact> getArtifacts();

    /**
     * Returns the set of unique artifact types included in this result.
     */
    public Set<String> getTypes();

    /**
     * Returns all artifacts of the specified type.
     *
     * @param type the type of artifacts
     * @return list of artifacts of that type
     */
    public List<BurstArtifact> getArtifactsByType(String type);
}
