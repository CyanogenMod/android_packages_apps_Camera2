/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.camera.session;

import java.io.File;
import java.io.IOException;

import javax.annotation.Nonnull;

/**
 * Interface for the session storage manager which handles management of storage
 * space that can be used for temporary session files.
 */
public interface SessionStorageManager {

    /**
     * Returns the directory that can be used for temporary sessions of a
     * specific type, defined by 'subDirectory'.
     * <p>
     * Before returning, this method will make sure the returned directory is
     * clean of expired session data.
     *
     * @param subDirectory The subdirectory to use/create within the temporary
     *            session space, e.g. "foo".
     * @return A valid file object that points to an existing directory.
     * @throws IOException If the directory could not be made available.
     */
    public File getSessionDirectory(String subDirectory) throws IOException;

    /**
     * Initializes the directories for storing the final session output
     * temporarily before it is copied to the final location after calling
     * {@link #finish()}.
     * <p>
     * This method will make sure the directories and file exists and is
     * writeable, otherwise it will throw an exception.
     *
     * @param title the title of this session. Will be used to create a unique
     *            sub-directory.
     * @return The path to a JPEG file which can be used to write the final
     *         output to.
     * @throws IOException If the directory could not be created.
     */
    @Nonnull
    public File createTemporaryOutputPath(String subDirectory, String title) throws IOException;
}
