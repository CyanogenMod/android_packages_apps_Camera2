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

import android.mtp.MtpDeviceInfo;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.MediaSetUtils;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// MtpDeviceSet -- MtpDevice -- MtpImage
public class MtpDeviceSet extends MediaSet
        implements FutureListener<ArrayList<MediaSet>> {
    private static final String TAG = "MtpDeviceSet";

    private GalleryApp mApplication;
    private final ChangeNotifier mNotifier;
    private final MtpContext mMtpContext;
    private final String mName;
    private final Handler mHandler;

    private Future<ArrayList<MediaSet>> mLoadTask;
    private ArrayList<MediaSet> mDeviceSet = new ArrayList<MediaSet>();
    private ArrayList<MediaSet> mLoadBuffer;
    private boolean mIsLoading;

    public MtpDeviceSet(Path path, GalleryApp application, MtpContext mtpContext) {
        super(path, nextVersionNumber());
        mApplication = application;
        mNotifier = new ChangeNotifier(this, Uri.parse("mtp://"), application);
        mMtpContext = mtpContext;
        mName = application.getResources().getString(R.string.set_label_mtp_devices);
        mHandler = new Handler(mApplication.getMainLooper());
    }

    private class DevicesLoader implements Job<ArrayList<MediaSet>> {
        @Override
        public ArrayList<MediaSet> run(JobContext jc) {
            DataManager dataManager = mApplication.getDataManager();
            ArrayList<MediaSet> result = new ArrayList<MediaSet>();

            // Enumerate all devices
            List<android.mtp.MtpDevice> devices = mMtpContext.getMtpClient().getDeviceList();
            Log.v(TAG, "loadDevices: " + devices + ", size=" + devices.size());
            for (android.mtp.MtpDevice mtpDevice : devices) {
                synchronized (DataManager.LOCK) {
                    int deviceId = mtpDevice.getDeviceId();
                    Path childPath = mPath.getChild(deviceId);
                    MtpDevice device = (MtpDevice) dataManager.peekMediaObject(childPath);
                    if (device == null) {
                        device = new MtpDevice(childPath, mApplication, deviceId, mMtpContext);
                    }
                    Log.d(TAG, "add device " + device);
                    result.add(device);
                }
            }
            Collections.sort(result, MediaSetUtils.NAME_COMPARATOR);
            return result;
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
    public synchronized boolean isLoading() {
        return mIsLoading;
    }

    @Override
    public synchronized long reload() {
        if (mNotifier.isDirty()) {
            if (mLoadTask != null) mLoadTask.cancel();
            mIsLoading = true;
            mLoadTask = mApplication.getThreadPool().submit(new DevicesLoader(), this);
        }
        if (mLoadBuffer != null) {
            mDeviceSet = mLoadBuffer;
            mLoadBuffer = null;
            for (MediaSet device : mDeviceSet) {
                device.reload();
            }
            mDataVersion = nextVersionNumber();
        }
        return mDataVersion;
    }

    @Override
    public synchronized void onFutureDone(Future<ArrayList<MediaSet>> future) {
        if (future != mLoadTask) return;
        mLoadBuffer = future.get();
        mIsLoading = false;
        if (mLoadBuffer == null) mLoadBuffer = new ArrayList<MediaSet>();

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                notifyContentChanged();
            }
        });
    }
}
