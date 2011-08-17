/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.data;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.util.MediaSetUtils;

import android.mtp.MtpDeviceInfo;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// MtpDeviceSet -- MtpDevice -- MtpImage
public class MtpDeviceSet extends MediaSet {
    private static final String TAG = "MtpDeviceSet";

    private GalleryApp mApplication;
    private final ArrayList<MediaSet> mDeviceSet = new ArrayList<MediaSet>();
    private final ChangeNotifier mNotifier;
    private final MtpContext mMtpContext;
    private final String mName;

    public MtpDeviceSet(Path path, GalleryApp application, MtpContext mtpContext) {
        super(path, nextVersionNumber());
        mApplication = application;
        mNotifier = new ChangeNotifier(this, Uri.parse("mtp://"), application);
        mMtpContext = mtpContext;
        mName = application.getResources().getString(R.string.set_label_mtp_devices);
    }

    private void loadDevices() {
        DataManager dataManager = mApplication.getDataManager();
        // Enumerate all devices
        mDeviceSet.clear();
        List<android.mtp.MtpDevice> devices = mMtpContext.getMtpClient().getDeviceList();
        Log.v(TAG, "loadDevices: " + devices + ", size=" + devices.size());
        for (android.mtp.MtpDevice mtpDevice : devices) {
            int deviceId = mtpDevice.getDeviceId();
            Path childPath = mPath.getChild(deviceId);
            MtpDevice device = (MtpDevice) dataManager.peekMediaObject(childPath);
            if (device == null) {
                device = new MtpDevice(childPath, mApplication, deviceId, mMtpContext);
            }
            Log.d(TAG, "add device " + device);
            mDeviceSet.add(device);
        }

        Collections.sort(mDeviceSet, MediaSetUtils.NAME_COMPARATOR);
        for (int i = 0, n = mDeviceSet.size(); i < n; i++) {
            mDeviceSet.get(i).reload();
        }
    }

    public static String getDeviceName(MtpContext mtpContext, int deviceId) {
        android.mtp.MtpDevice device = mtpContext.getMtpClient().getDevice(deviceId);
        if (device == null) {
            return "";
        }
        MtpDeviceInfo info = device.getDeviceInfo();
        if (info == null) {
            return "";
        }
        String manufacturer = info.getManufacturer().trim();
        String model = info.getModel().trim();
        return manufacturer + " " + model;
    }

    @Override
    public MediaSet getSubMediaSet(int index) {
        return index < mDeviceSet.size() ? mDeviceSet.get(index) : null;
    }

    @Override
    public int getSubMediaSetCount() {
        return mDeviceSet.size();
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public long reload() {
        if (mNotifier.isDirty()) {
            mDataVersion = nextVersionNumber();
            loadDevices();
        }
        return mDataVersion;
    }
}
