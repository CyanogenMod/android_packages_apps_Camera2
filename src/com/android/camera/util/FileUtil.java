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

package com.android.camera.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Common file operations.
 */
public class FileUtil {
    /**
     * Deletes the given directory and all it's contents, including
     * sub-directories.
     *
     * @param directory The directory to delete.
     * @return Whether The deletion was a success.
     */
    public static boolean deleteDirectoryRecursively(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return false;
        }

        for (File entry : directory.listFiles()) {
            if (entry.isDirectory()) {
                deleteDirectoryRecursively(entry);
            }
            if (!entry.delete()) {
                return false;
            }
        }
        return directory.delete();
    }

    /**
     * Reads the content of a {@code File} as a byte array.
     *
     * @param file The file to read
     * @return  The content of the file
     * @throws java.io.IOException if the content of the {@code File} could not be read
     */
    public static byte[] readFileToByteArray(File file) throws IOException {
        int length = (int) file.length();
        byte[] data = new byte[length];
        FileInputStream stream = new FileInputStream(file);
        try {
            int offset = 0;
            while (offset < length) {
                offset += stream.read(data, offset, length - offset);
            }
        } catch (IOException e) {
            throw e;
        } finally {
            stream.close();
        }
        return data;
    }

}
