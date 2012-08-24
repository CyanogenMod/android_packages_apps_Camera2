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

package com.android.gallery3d.exif;
/**
 *  This class stores the EXIF header in IFDs according to the JPEG specification.
 *  It is the result produced by {@link ExifReader}.
 *  @see ExifReader
 *  @see IfdData
 */
public class ExifData {
    private final IfdData[] mIfdDatas = new IfdData[IfdId.TYPE_IFD_COUNT];

    /**
     * Gets the IFD data of the specified IFD.
     *
     * @see IfdId#TYPE_IFD_0
     * @see IfdId#TYPE_IFD_1
     * @see IfdId#TYPE_IFD_EXIF
     * @see IfdId#TYPE_IFD_GPS
     * @see IfdId#TYPE_IFD_INTEROPERABILITY
     */
    public IfdData getIfdData(int ifdId) {
        return mIfdDatas[ifdId];
    }

    /**
     * Adds IFD data. If IFD data of the same type already exists,
     * it will be replaced by the new data.
     */
    public void addIfdData(IfdData data) {
        mIfdDatas[data.getId()] = data;
    }
}