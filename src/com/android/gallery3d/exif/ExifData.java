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


public class ExifData {
    public static final int TYPE_IFD_0 = 0;
    public static final int TYPE_IFD_EXIF = 1;
    public static final int TYPE_IFD_1 = 2;
    public static final int TYPE_IFD_GPS = 3;
    public static final int TYPE_IFD_INTEROPERABILITY = 4;

    private final IfdData[] mIfdDatas = new IfdData[5];

    public IfdData getIfdData(int ifdType) {
        return mIfdDatas[ifdType];
    }

    public void addIfdData(IfdData data) {
        mIfdDatas[data.getIfdType()] = data;
    }
}
