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

package com.android.photos.drawables;

import android.mtp.MtpDevice;
import android.mtp.MtpObjectInfo;

import com.android.gallery3d.ingest.MtpDeviceIndex;

import java.io.InputStream;

public class MtpThumbnailDrawable extends AutoThumbnailDrawable<MtpObjectInfo> {
    public void setImage(MtpObjectInfo data) {
        if (data == null) {
            setImage(null, 0, 0);
        } else {
            setImage(data, data.getImagePixWidth(), data.getImagePixHeight());
        }
    }

    @Override
    protected byte[] getPreferredImageBytes(MtpObjectInfo data) {
        if (data == null) {
            return null;
        }
        MtpDevice device = MtpDeviceIndex.getInstance().getDevice();
        if (device != null) {
            return device.getThumbnail(data.getObjectHandle());
        } else {
            return null;
        }
    }

    @Override
    protected InputStream getFallbackImageStream(MtpObjectInfo data) {
        // No fallback
        return null;
    }

    @Override
    protected boolean dataChangedLocked(MtpObjectInfo data) {
        // We only fetch the MtpObjectInfo once when creating
        // the index so checking the reference is enough
        return mData == data;
    }

}
