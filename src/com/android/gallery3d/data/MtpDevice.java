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

import com.android.gallery3d.app.GalleryApp;

import android.hardware.usb.UsbDevice;
import android.mtp.MtpConstants;
import android.mtp.MtpObjectInfo;
import android.mtp.MtpStorageInfo;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MtpDevice extends MediaSet {
    private static final String TAG = "MtpDevice";

    private final GalleryApp mApplication;
    private final int mDeviceId;
    private final String mDeviceName;
    private final DataManager mDataManager;
    private final MtpContext mMtpContext;
    private final String mName;
    private final ChangeNotifier mNotifier;
    private final Path mItemPath;
    private List<MtpObjectInfo> mJpegChildren;

    public MtpDevice(Path path, GalleryApp application, int deviceId,
            String name, MtpContext mtpContext) {
        super(path, nextVersionNumber());
        mApplication = application;
        mDeviceId = deviceId;
        mDeviceName = UsbDevice.getDeviceName(deviceId);
        mDataManager = application.getDataManager();
        mMtpContext = mtpContext;
        mName = name;
        mNotifier = new ChangeNotifier(this, Uri.parse("mtp://"), application);
        mItemPath = Path.fromString("/mtp/item/" + String.valueOf(deviceId));
        mJpegChildren = new ArrayList<MtpObjectInfo>();
    }

    public MtpDevice(Path path, GalleryApp application, int deviceId,
            MtpContext mtpContext) {
        this(path, application, deviceId,
                MtpDeviceSet.getDeviceName(mtpContext, deviceId), mtpContext);
    }

    private List<MtpObjectInfo> loadItems() {
        ArrayList<MtpObjectInfo> result = new ArrayList<MtpObjectInfo>();

        List<MtpStorageInfo> storageList = mMtpContext.getMtpClient()
                 .getStorageList(mDeviceName);
        if (storageList == null) return result;

        for (MtpStorageInfo info : storageList) {
            collectJpegChildren(info.getStorageId(), 0, result);
        }

        return result;
    }

    private void collectJpegChildren(int storageId, int objectId,
            ArrayList<MtpObjectInfo> result) {
        ArrayList<MtpObjectInfo> dirChildren = new ArrayList<MtpObjectInfo>();

        queryChildren(storageId, objectId, result, dirChildren);

        for (int i = 0, n = dirChildren.size(); i < n; i++) {
            MtpObjectInfo info = dirChildren.get(i);
            collectJpegChildren(storageId, info.getObjectHandle(), result);
        }
    }

    private void queryChildren(int storageId, int objectId,
            ArrayList<MtpObjectInfo> jpeg, ArrayList<MtpObjectInfo> dir) {
        List<MtpObjectInfo> children = mMtpContext.getMtpClient().getObjectList(
                mDeviceName, storageId, objectId);
        if (children == null) return;

        for (MtpObjectInfo obj : children) {
            int format = obj.getFormat();
            switch (format) {
                case MtpConstants.FORMAT_JFIF:
                case MtpConstants.FORMAT_EXIF_JPEG:
                    jpeg.add(obj);
                    break;
                case MtpConstants.FORMAT_ASSOCIATION:
                    dir.add(obj);
                    break;
                default:
                    Log.w(TAG, "other type: name = " + obj.getName()
                            + ", format = " + format);
            }
        }
    }

    public static MtpObjectInfo getObjectInfo(MtpContext mtpContext, int deviceId,
            int objectId) {
        String deviceName = UsbDevice.getDeviceName(deviceId);
        return mtpContext.getMtpClient().getObjectInfo(deviceName, objectId);
    }

    @Override
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        ArrayList<MediaItem> result = new ArrayList<MediaItem>();
        int begin = start;
        int end = Math.min(start + count, mJpegChildren.size());

        DataManager dataManager = mApplication.getDataManager();
        for (int i = begin; i < end; i++) {
            MtpObjectInfo child = mJpegChildren.get(i);
            Path childPath = mItemPath.getChild(child.getObjectHandle());
            MtpImage image = (MtpImage) dataManager.peekMediaObject(childPath);
            if (image == null) {
                image = new MtpImage(
                        childPath, mApplication, mDeviceId, child, mMtpContext);
            } else {
                image.updateContent(child);
            }
            result.add(image);
        }
        return result;
    }

    @Override
    public int getMediaItemCount() {
        return mJpegChildren.size();
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public long reload() {
        if (mNotifier.isDirty()) {
            mDataVersion = nextVersionNumber();
            mJpegChildren = loadItems();
        }
        return mDataVersion;
    }

    @Override
    public int getSupportedOperations() {
        return SUPPORT_IMPORT;
    }

    @Override
    public boolean Import() {
        return mMtpContext.copyAlbum(mDeviceName, mName, mJpegChildren);
    }

    @Override
    public boolean isLeafAlbum() {
        return true;
    }
}
